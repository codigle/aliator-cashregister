package com.aliator.cashregister.v6;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
import java.util.*;

/** A restaurant is not created until a one-time code proves control of the email. */
@RestController
@RequestMapping("/api/v2/auth/register")
public class V6EmailRegistration {
    public record Start(@NotBlank String restaurantName, @NotBlank String slug,
                        @NotBlank @Email String email, @NotBlank String password) {}
    public record Verify(@NotBlank @Email String email, @NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate db;
    private final JavaMailSender sender;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(11);
    private final byte[] signingKey;
    private final String senderAddress;

    public V6EmailRegistration(JdbcTemplate db, JavaMailSender sender,
            @Value("${registration.otp.secret:}") String secret,
            @Value("${registration.email.from:hello@aliator.com}") String senderAddress) {
        this.db=db;this.sender=sender;this.senderAddress=senderAddress;
        if (secret.length()<48) throw new IllegalStateException("REGISTRATION_OTP_SECRET must have at least 48 chars");
        this.signingKey=secret.getBytes(StandardCharsets.UTF_8);
    }
    private static ResponseStatusException fail(HttpStatus status, String reason) {
        return new ResponseStatusException(status, reason);
    }
    private static String email(String value) { return value.strip().toLowerCase(Locale.ROOT); }
    private static String clean(String s, int limit) {
        String v=s==null?"":s.strip();
        if (v.isEmpty() || v.length()>limit) throw fail(HttpStatus.BAD_REQUEST,"Invalid field length.");
        return v;
    }
    private String hmac(String value) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("HMAC failure",ex); }
    }
    private static long n(Map<String,Object> row,String key) {return ((Number)row.get(key)).longValue();}

    @PostMapping("/start")
    @Transactional(noRollbackFor=ResponseStatusException.class)
    public Map<String,Object> start(@Valid @RequestBody Start request,HttpServletRequest http) {
        String name=clean(request.restaurantName(),140);
        String slug=clean(request.slug(),90).toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9][a-z0-9-]{2,89}")) throw fail(HttpStatus.BAD_REQUEST,"Restaurant slug should contain only a-z, 0-9 and hyphens.");
        String destination=email(clean(request.email(),254));
        if (request.password().length()<12 || request.password().length()>128)
            throw fail(HttpStatus.BAD_REQUEST,"Password should be 12–128 characters.");
        // Independent, persisted per-IP guard. Hash the IP; do not retain raw network addresses.
        String source=hmac("IP:"+http.getRemoteAddr());
        List<Map<String,Object>> rates=db.queryForList("""
            INSERT INTO cr6_registration_rate_ip(ip_hash,window_started,attempt_count)
            VALUES (?,now(),1)
            ON CONFLICT(ip_hash) DO UPDATE SET
              attempt_count=CASE WHEN cr6_registration_rate_ip.window_started<now()-interval '1 hour'
                                 THEN 1 ELSE cr6_registration_rate_ip.attempt_count+1 END,
              window_started=CASE WHEN cr6_registration_rate_ip.window_started<now()-interval '1 hour'
                                  THEN now() ELSE cr6_registration_rate_ip.window_started END
            RETURNING attempt_count
            """,source);
        if (n(rates.get(0),"attempt_count")>8) throw fail(HttpStatus.TOO_MANY_REQUESTS,"Too many registration emails. Try later.");
        // Pending email verification is NOT an account. The only registered addresses live in cr2_users.
        // This check also protects pre-V6 legacy accounts; do not silently overwrite them.
        if (!db.queryForList("SELECT id FROM cr2_users WHERE email=?",destination).isEmpty())
            throw fail(HttpStatus.CONFLICT,"This email already has an existing account (possibly created before email verification was introduced). Please sign in or contact the administrator.");
        if (!db.queryForList("SELECT id FROM cr2_restaurants WHERE slug=?",slug).isEmpty())
            throw fail(HttpStatus.CONFLICT,"The restaurant URL is already in use. Choose a different restaurant URL.");
        // Expired pending entries may be removed without touching any registered restaurants.
        db.update("DELETE FROM cr6_pending_registrations WHERE expires_at<now()");
        var current=db.queryForList("SELECT last_sent_at>now()-interval '60 seconds' AS recently_sent FROM cr6_pending_registrations WHERE email=? FOR UPDATE",destination);
        if(!current.isEmpty()) {
            if(Boolean.TRUE.equals(current.get(0).get("recently_sent")))
                throw fail(HttpStatus.TOO_MANY_REQUESTS,"Please wait 60 seconds before requesting another code.");
        }
        if (!db.queryForList("SELECT email FROM cr6_pending_registrations WHERE slug=? AND email<>?",slug,destination).isEmpty())
            throw fail(HttpStatus.CONFLICT,"This restaurant URL is temporarily reserved.");
        String code=String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000));
        String hash=hmac("OTP:"+destination+":"+code);
        db.update("""
            INSERT INTO cr6_pending_registrations(email,restaurant_name,slug,password_hash,otp_hash,attempts,expires_at,last_sent_at)
            VALUES (?,?,?,?,?,0,now()+interval '10 minutes',now())
            ON CONFLICT(email) DO UPDATE SET
               restaurant_name=EXCLUDED.restaurant_name,slug=EXCLUDED.slug,
               password_hash=EXCLUDED.password_hash,otp_hash=EXCLUDED.otp_hash,
               attempts=0,expires_at=EXCLUDED.expires_at,last_sent_at=now()
            """,destination,name,slug,bcrypt.encode(request.password()),hash);
        SimpleMailMessage message=new SimpleMailMessage();
        message.setFrom(senderAddress);message.setTo(destination);
        message.setSubject("Aliator CashRegister - Verify your restaurant email");
        message.setText("Your Aliator CashRegister verification code is: "+code+"\n\n"
            +"This code expires in 10 minutes and can be used only once.\n"
            +"If you didn't request this registration, you can ignore this email.\n\n"
            +"レストラン登録の確認コード: "+code+"\n有効期限は10分です。\n");
        try { sender.send(message); }
        catch (MailException ex) { throw new MailUnavailable(); } // rollback pending row if mail cannot be sent
        return Map.of("sent",true,"expiresInSeconds",600);
    }

    @PostMapping("/verify")
    @Transactional(noRollbackFor=ResponseStatusException.class)
    public Map<String,Object> verify(@Valid @RequestBody Verify request) {
        String destination=email(clean(request.email(),254));
        var found=db.queryForList("""
            SELECT email,restaurant_name,slug,password_hash,otp_hash,attempts,
                   expires_at>now() AS still_valid
            FROM cr6_pending_registrations WHERE email=? FOR UPDATE
            """,destination);
        if(found.isEmpty()) throw fail(HttpStatus.BAD_REQUEST,"Invalid or expired verification code.");
        var pending=found.get(0);
        if(!Boolean.TRUE.equals(pending.get("still_valid"))) {
            db.update("DELETE FROM cr6_pending_registrations WHERE email=?",destination);
            throw fail(HttpStatus.BAD_REQUEST,"Code expired. Request a new code.");
        }
        if(n(pending,"attempts")>=5) throw fail(HttpStatus.TOO_MANY_REQUESTS,"Too many incorrect codes. Request a new code.");
        byte[] sent=((String)pending.get("otp_hash")).getBytes(StandardCharsets.US_ASCII);
        byte[] actual=hmac("OTP:"+destination+":"+request.code()).getBytes(StandardCharsets.US_ASCII);
        if(!MessageDigest.isEqual(sent,actual)) {
            db.update("UPDATE cr6_pending_registrations SET attempts=attempts+1 WHERE email=?",destination);
            throw fail(HttpStatus.BAD_REQUEST,"Invalid code.");
        }
        if(!db.queryForList("SELECT id FROM cr2_users WHERE email=?",destination).isEmpty() ||
           !db.queryForList("SELECT id FROM cr2_restaurants WHERE slug=?",pending.get("slug")).isEmpty())
            throw fail(HttpStatus.CONFLICT,"Account already exists. Please sign in.");
        Long restaurantId=db.queryForObject("INSERT INTO cr2_restaurants(name,slug) VALUES (?,?) RETURNING id",Long.class,
            pending.get("restaurant_name"),pending.get("slug"));
        db.update("INSERT INTO cr2_users(restaurant_id,email,password_hash,role) VALUES (?,?,?,'OWNER')",
            restaurantId,destination,pending.get("password_hash"));
        db.update("DELETE FROM cr6_pending_registrations WHERE email=?",destination);
        return Map.of("verified",true,"restaurant",pending.get("slug"));
    }

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public static class MailUnavailable extends RuntimeException {
        public MailUnavailable() { super("Verification email could not be sent. Please try later."); }
    }
}
