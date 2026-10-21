package com.aliator.cashregister.v7;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Password recovery for existing verified AND pre-OTP legacy accounts; never creates restaurants. */
@RestController
@RequestMapping("/api/v2/auth/password-reset")
public class V7PasswordReset {
    public record Start(@NotBlank @Email @Size(max=254) String email) {}
    public record Confirm(@NotBlank @Email @Size(max=254) String email,
                          @NotBlank @Pattern(regexp="[0-9]{6}") String code,
                          @NotBlank String newPassword) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(11);
    private final JdbcTemplate db;
    private final JavaMailSender sender;
    private final String from;
    private final byte[] secret;

    public V7PasswordReset(JdbcTemplate db, JavaMailSender sender,
            @Value("${registration.otp.secret:}") String secret,
            @Value("${registration.email.from:hello@aliator.com}") String from) {
        if (secret.length() < 48) throw new IllegalStateException("Registration OTP secret missing");
        this.db = db; this.sender = sender; this.from = from;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }
    private static String normalized(String address) { return address.strip().toLowerCase(Locale.ROOT); }
    private static ResponseStatusException err(HttpStatus code, String reason) {
        return new ResponseStatusException(code, reason);
    }
    private static long num(Map<String,Object> row, String name) {return ((Number)row.get(name)).longValue();}
    private String hmac(String input) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {throw new IllegalStateException("HMAC unavailable", e);}
    }
    private String clientIp(HttpServletRequest req) {
        // Nginx overwrites X-Real-IP. The app port is bound to VPS localhost only.
        String addr=req.getHeader("X-Real-IP");
        if (addr==null || addr.length()>45 || !addr.matches("[0-9A-Fa-f:.]+")) addr=req.getRemoteAddr();
        return addr;
    }

    @PostMapping("/start")
    @Transactional(noRollbackFor=ResponseStatusException.class)
    public Map<String,Object> start(@Valid @RequestBody Start input, HttpServletRequest request) {
        String address=normalized(input.email());
        String source=hmac("RESET-IP:"+clientIp(request));
        List<Map<String,Object>> r=db.queryForList("""
            INSERT INTO cr7_reset_rate_ip(ip_hash,window_started,attempt_count)
            VALUES (?,now(),1)
            ON CONFLICT(ip_hash) DO UPDATE SET
              attempt_count=CASE WHEN cr7_reset_rate_ip.window_started < now()-interval '1 hour'
                  THEN 1 ELSE cr7_reset_rate_ip.attempt_count+1 END,
              window_started=CASE WHEN cr7_reset_rate_ip.window_started < now()-interval '1 hour'
                  THEN now() ELSE cr7_reset_rate_ip.window_started END
            RETURNING attempt_count
            """,source);
        if (num(r.get(0),"attempt_count")>20)
            throw err(HttpStatus.TOO_MANY_REQUESTS,"Too many requests. Try later.");

        var old=db.queryForList("""
            SELECT last_sent_at > now() - interval '60 seconds' AS cooldown,
                   CASE WHEN hour_started < now() - interval '1 hour' THEN 0 ELSE hourly_count END AS recent_count
            FROM cr7_password_resets WHERE email=? FOR UPDATE
            """,address);
        if (!old.isEmpty() && (Boolean.TRUE.equals(old.get(0).get("cooldown")) || num(old.get(0),"recent_count") >= 5))
            throw err(HttpStatus.TOO_MANY_REQUESTS,"Please wait before requesting another code.");

        // The exact same success response is returned for known and unknown emails.
        var accounts=db.queryForList("SELECT id FROM cr2_users WHERE email=?",address);
        if (accounts.isEmpty()) return Map.of("sent",true,"expiresInSeconds",600);

        String code=String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000));
        db.update("""
            INSERT INTO cr7_password_resets(email,otp_hash,attempts,expires_at,last_sent_at,hour_started,hourly_count)
            VALUES (?,?,0,now()+interval '10 minutes',now(),now(),1)
            ON CONFLICT(email) DO UPDATE SET
              otp_hash=EXCLUDED.otp_hash,attempts=0,expires_at=EXCLUDED.expires_at,
              last_sent_at=now(),
              hourly_count=CASE WHEN cr7_password_resets.hour_started < now()-interval '1 hour'
                                THEN 1 ELSE cr7_password_resets.hourly_count+1 END,
              hour_started=CASE WHEN cr7_password_resets.hour_started < now()-interval '1 hour'
                                THEN now() ELSE cr7_password_resets.hour_started END
            """,address,hmac("RESET:"+address+":"+code));
        SimpleMailMessage mail=new SimpleMailMessage();
        mail.setFrom(from); mail.setTo(address);
        mail.setSubject("Aliator CashRegister - Reset your password");
        mail.setText("Your password reset code: "+code+"\n\nValid for 10 minutes, one use only.\n"
            +"If you did not ask for this reset, ignore this email.\n\n"
            +"パスワード再設定コード: "+code+"\n有効期限: 10分\n");
        try {sender.send(mail);}
        catch (MailException e) {
            // Rollback pending code as well; never log the code, password or SMTP credentials.
            throw new IllegalStateException("Password reset email could not be sent",e);
        }
        return Map.of("sent",true,"expiresInSeconds",600);
    }

    @PostMapping("/confirm")
    @Transactional(noRollbackFor=ResponseStatusException.class)
    public Map<String,Object> confirm(@Valid @RequestBody Confirm input) {
        if (input.newPassword().length()<12 || input.newPassword().length()>128)
            throw err(HttpStatus.BAD_REQUEST,"Password must contain 12–128 characters.");
        String address=normalized(input.email());
        var rows=db.queryForList("""
            SELECT otp_hash,attempts,expires_at>now() AS valid
            FROM cr7_password_resets WHERE email=? FOR UPDATE
            """,address);
        if (rows.isEmpty()) throw err(HttpStatus.BAD_REQUEST,"Invalid or expired code.");
        var row=rows.get(0);
        if (!Boolean.TRUE.equals(row.get("valid"))) {
            db.update("DELETE FROM cr7_password_resets WHERE email=?",address);
            throw err(HttpStatus.BAD_REQUEST,"Code expired. Request a new one.");
        }
        if (num(row,"attempts")>=5) throw err(HttpStatus.TOO_MANY_REQUESTS,"Too many attempts. Request a new code.");
        byte[] expected=((String)row.get("otp_hash")).getBytes(StandardCharsets.US_ASCII);
        byte[] actual=hmac("RESET:"+address+":"+input.code()).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected,actual)) {
            db.update("UPDATE cr7_password_resets SET attempts=attempts+1 WHERE email=?",address);
            throw err(HttpStatus.BAD_REQUEST,"Invalid code.");
        }
        String hash=BCRYPT.encode(input.newPassword());
        int modified=db.update("UPDATE cr2_users SET password_hash=? WHERE email=?",hash,address);
        db.update("DELETE FROM cr7_password_resets WHERE email=?",address);
        if (modified!=1) throw err(HttpStatus.BAD_REQUEST,"Reset failed. Request another code.");
        db.update("DELETE FROM cr2_staff_sessions WHERE user_id IN (SELECT id FROM cr2_users WHERE email=?)",address);
        return Map.of("reset",true);
    }
}
