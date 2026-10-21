package com.aliator.cashregister.v11;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/** One physical Raspberry Pi, multiple explicitly authorized restaurant print destinations. */
@Component
@Order(920)
class V11PrintSchema implements CommandLineRunner {
    private final JdbcTemplate db;
    V11PrintSchema(JdbcTemplate db){this.db=db;}
    public void run(String... args){
        db.execute("""
            CREATE TABLE IF NOT EXISTS cr11_station_invites (
                token_hash VARCHAR(64) PRIMARY KEY,
                station_device_id BIGINT NOT NULL REFERENCES cr4_print_devices(id),
                issuer_restaurant_id BIGINT NOT NULL REFERENCES cr2_restaurants(id),
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                expires_at TIMESTAMPTZ NOT NULL,
                used_at TIMESTAMPTZ
            )
        """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS cr11_station_grants (
                id BIGSERIAL PRIMARY KEY,
                station_device_id BIGINT NOT NULL REFERENCES cr4_print_devices(id),
                restaurant_id BIGINT NOT NULL REFERENCES cr2_restaurants(id),
                logical_device_id BIGINT NOT NULL UNIQUE REFERENCES cr4_print_devices(id),
                enabled BOOLEAN NOT NULL DEFAULT TRUE,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                UNIQUE(station_device_id,restaurant_id)
            )
        """);
        db.execute("CREATE INDEX IF NOT EXISTS cr11_active_station_grants ON cr11_station_grants(station_device_id,logical_device_id) WHERE enabled=true");
    }
}

@RestController
@RequestMapping("/api/v2/print/station")
public class V11PrinterSharing {
    private final JdbcTemplate db;
    private final SecureRandom rng=new SecureRandom();
    public V11PrinterSharing(JdbcTemplate db){this.db=db;}
    record Staff(long tenant, String role) {}
    record InviteInput(@NotNull Long deviceId) {}
    record JoinInput(@NotBlank String inviteCode) {}
    record RevokeInput(@NotNull Long logicalDeviceId) {}
    private static ResponseStatusException bad(HttpStatus status,String msg){return new ResponseStatusException(status,msg);}
    private static String hash(String s){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private String secret(){byte[] x=new byte[32];rng.nextBytes(x);return Base64.getUrlEncoder().withoutPadding().encodeToString(x);}
    private Staff owner(HttpServletRequest request){
        String token="";
        if(request.getCookies()!=null)for(var c:request.getCookies())if("cr2_staff".equals(c.getName()))token=c.getValue();
        if(!token.matches("[A-Za-z0-9_-]{30,200}"))throw bad(HttpStatus.UNAUTHORIZED,"Owner sign-in required");
        var found=db.queryForList("""
            SELECT u.restaurant_id,u.role FROM cr2_staff_sessions s JOIN cr2_users u ON u.id=s.user_id
            WHERE s.token_hash=? AND s.expires_at>now()
        """,hash(token));
        if(found.isEmpty())throw bad(HttpStatus.UNAUTHORIZED,"Session expired");
        String role=(String)found.get(0).get("role");
        if(!Set.of("OWNER","MANAGER").contains(role))throw bad(HttpStatus.FORBIDDEN,"Owner/manager required");
        return new Staff(((Number)found.get(0).get("restaurant_id")).longValue(),role);
    }
    @PostMapping("/invite")
    @Transactional
    public Map<String,Object> invite(HttpServletRequest req,@Valid @RequestBody InviteInput in){
        Staff s=owner(req);
        var station=db.queryForList("""
            SELECT id FROM cr4_print_devices WHERE id=? AND restaurant_id=? AND enabled=true
              AND NOT EXISTS(SELECT 1 FROM cr11_station_grants WHERE logical_device_id=cr4_print_devices.id)
        """,in.deviceId(),s.tenant());
        if(station.isEmpty())throw bad(HttpStatus.NOT_FOUND,"This restaurant does not own an active physical station");
        String code=secret();
        db.update("INSERT INTO cr11_station_invites(token_hash,station_device_id,issuer_restaurant_id,expires_at) VALUES(?,?,?,now()+interval '10 minutes')",
                hash(code),in.deviceId(),s.tenant());
        return Map.of("inviteCode",code,"expiresInSeconds",600,"deviceId",in.deviceId(),
                "notice","Share this invitation privately with the other restaurant owner. Never publish it.");
    }
    @PostMapping("/join")
    @Transactional
    public Map<String,Object> join(HttpServletRequest req,@Valid @RequestBody JoinInput in){
        Staff s=owner(req);
        String code=in.inviteCode().trim();
        if(!code.matches("[A-Za-z0-9_-]{43}"))throw bad(HttpStatus.BAD_REQUEST,"Invalid invitation code");
        var invites=db.queryForList("""
            SELECT station_device_id,issuer_restaurant_id FROM cr11_station_invites
            WHERE token_hash=? AND expires_at>now() AND used_at IS NULL FOR UPDATE
        """,hash(code));
        if(invites.isEmpty())throw bad(HttpStatus.GONE,"Invitation expired or already used");
        long station=((Number)invites.get(0).get("station_device_id")).longValue();
        long issuer=((Number)invites.get(0).get("issuer_restaurant_id")).longValue();
        if(issuer==s.tenant())throw bad(HttpStatus.BAD_REQUEST,"This is already your restaurant's printer");
        var active=db.queryForList("SELECT id FROM cr4_print_devices WHERE id=? AND restaurant_id=? AND enabled=true",station,issuer);
        if(active.isEmpty())throw bad(HttpStatus.GONE,"Station was disabled");
        var prior=db.queryForList("""
            SELECT g.logical_device_id,g.enabled FROM cr11_station_grants g
            WHERE g.station_device_id=? AND g.restaurant_id=? FOR UPDATE
        """,station,s.tenant());
        long logical;
        if(!prior.isEmpty()){
            logical=((Number)prior.get(0).get("logical_device_id")).longValue();
            db.update("UPDATE cr11_station_grants SET enabled=true WHERE station_device_id=? AND restaurant_id=?",station,s.tenant());
            db.update("UPDATE cr4_print_devices SET enabled=true WHERE id=? AND restaurant_id=?",logical,s.tenant());
        }else{
            var setting=db.queryForList("""
                SELECT name,width_mm,height_mm,gap_mm,label_header,label_footer,density,show_table,text_align
                FROM cr4_print_devices WHERE id=? AND restaurant_id=?
            """,station,issuer);
            var m=setting.get(0);
            logical=db.queryForObject("""
                INSERT INTO cr4_print_devices(restaurant_id,name,token_hash,width_mm,height_mm,gap_mm,
                    label_header,label_footer,density,show_table,text_align)
                VALUES(?,?,?,?,?,?,?,?,?,?,?) RETURNING id
            """,Long.class,s.tenant(),"Shared: "+String.valueOf(m.get("name")).substring(0,Math.min(75,String.valueOf(m.get("name")).length())),
                    hash(secret()),m.get("width_mm"),m.get("height_mm"),m.get("gap_mm"),
                    m.get("label_header"),m.get("label_footer"),m.get("density"),m.get("show_table"),m.get("text_align"));
            db.update("INSERT INTO cr11_station_grants(station_device_id,restaurant_id,logical_device_id) VALUES(?,?,?)",
                    station,s.tenant(),logical);
        }
        db.update("UPDATE cr11_station_invites SET used_at=now() WHERE token_hash=?",hash(code));
        return Map.of("linked",true,"deviceId",logical,"stationId",station,
            "notice","Raspberry Pi credential unchanged. Select this shared printer in Workflow Settings.");
    }
    @GetMapping("/links")
    public List<Map<String,Object>> links(HttpServletRequest req){
        Staff s=owner(req);
        return db.queryForList("""
            SELECT g.logical_device_id AS "deviceId",d.name AS "name",g.enabled,
                p.name AS "stationName",d.last_seen_at AS "lastSeenAt"
            FROM cr11_station_grants g JOIN cr4_print_devices d ON d.id=g.logical_device_id
            JOIN cr4_print_devices p ON p.id=g.station_device_id
            WHERE g.restaurant_id=? ORDER BY g.id
        """,s.tenant());
    }
    @GetMapping("/owned")
    public List<Map<String,Object>> owned(HttpServletRequest req){
        Staff s=owner(req);
        return db.queryForList("""
            SELECT d.id,d.name,d.enabled,d.last_seen_at AS "lastSeenAt",
                (SELECT count(*) FROM cr11_station_grants g WHERE g.station_device_id=d.id AND g.enabled) AS "sharedWith"
            FROM cr4_print_devices d WHERE d.restaurant_id=?
             AND NOT EXISTS(SELECT 1 FROM cr11_station_grants g WHERE g.logical_device_id=d.id)
            ORDER BY d.id
        """,s.tenant());
    }
    @PostMapping("/revoke")
    @Transactional
    public Map<String,Object> revoke(HttpServletRequest req,@Valid @RequestBody RevokeInput in){
        Staff s=owner(req);
        int updated=db.update("""
            UPDATE cr11_station_grants g SET enabled=false
            WHERE g.logical_device_id=? AND g.restaurant_id=? AND g.enabled=true
        """,in.logicalDeviceId(),s.tenant());
        if(updated!=1)throw bad(HttpStatus.NOT_FOUND,"Active shared printer not found");
        db.update("UPDATE cr4_print_devices SET enabled=false WHERE id=? AND restaurant_id=?",in.logicalDeviceId(),s.tenant());
        return Map.of("unlinked",true,"deviceId",in.logicalDeviceId());
    }
    @PostMapping("/owner-revoke")
    @Transactional
    public Map<String,Object> ownerRevoke(HttpServletRequest req,@Valid @RequestBody RevokeInput in){
        Staff s=owner(req);
        int changed=db.update("""
            UPDATE cr11_station_grants g SET enabled=false
            FROM cr4_print_devices p
            WHERE g.logical_device_id=? AND g.station_device_id=p.id AND p.restaurant_id=? AND g.enabled=true
        """,in.logicalDeviceId(),s.tenant());
        if(changed!=1)throw bad(HttpStatus.NOT_FOUND,"Active share not found");
        db.update("UPDATE cr4_print_devices SET enabled=false WHERE id=?",in.logicalDeviceId());
        return Map.of("revoked",true,"deviceId",in.logicalDeviceId());
    }
}
