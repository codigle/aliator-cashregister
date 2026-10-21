package com.aliator.cashregister.v4;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Component
class V4PrintSchema implements org.springframework.boot.CommandLineRunner {
    private final JdbcTemplate db;
    V4PrintSchema(JdbcTemplate db) { this.db = db; }
    @Override public void run(String... args) {
        db.execute("""
            CREATE TABLE IF NOT EXISTS cr4_print_devices (
              id BIGSERIAL PRIMARY KEY,
              restaurant_id BIGINT NOT NULL REFERENCES cr2_restaurants(id),
              name VARCHAR(90) NOT NULL,
              token_hash VARCHAR(64) NOT NULL UNIQUE,
              enabled BOOLEAN NOT NULL DEFAULT TRUE,
              last_seen_at TIMESTAMPTZ,
              created_at TIMESTAMPTZ NOT NULL DEFAULT now()
            )
            """);
        db.execute("""
            CREATE TABLE IF NOT EXISTS cr4_print_jobs (
              id UUID PRIMARY KEY,
              restaurant_id BIGINT NOT NULL REFERENCES cr2_restaurants(id),
              device_id BIGINT NOT NULL REFERENCES cr4_print_devices(id),
              kind VARCHAR(12) NOT NULL CHECK(kind IN ('TEST','ORDER','RECEIPT')),
              reference_id BIGINT,
              content TEXT NOT NULL,
              status VARCHAR(12) NOT NULL DEFAULT 'PENDING'
                CHECK(status IN ('PENDING','CLAIMED','SENT','UNCERTAIN')),
              created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
              claimed_at TIMESTAMPTZ,
              finished_at TIMESTAMPTZ,
              CONSTRAINT cr4_job_max_content CHECK (length(content) < 20000)
            )
            """);
        db.execute("ALTER TABLE cr4_print_devices ADD COLUMN IF NOT EXISTS width_mm INTEGER NOT NULL DEFAULT 100");
        db.execute("ALTER TABLE cr4_print_devices ADD COLUMN IF NOT EXISTS height_mm INTEGER NOT NULL DEFAULT 150");
        db.execute("ALTER TABLE cr4_print_devices ADD COLUMN IF NOT EXISTS gap_mm INTEGER NOT NULL DEFAULT 2");
        db.execute("ALTER TABLE cr4_print_devices ADD COLUMN IF NOT EXISTS label_header VARCHAR(64) NOT NULL DEFAULT 'ALIATOR RESTAURANT'");
        db.execute("ALTER TABLE cr4_print_devices ADD COLUMN IF NOT EXISTS label_footer VARCHAR(100) NOT NULL DEFAULT ''");
        db.execute("ALTER TABLE cr4_print_devices ADD COLUMN IF NOT EXISTS density VARCHAR(12) NOT NULL DEFAULT 'NORMAL'");
        db.execute("ALTER TABLE cr4_print_devices ADD COLUMN IF NOT EXISTS show_table BOOLEAN NOT NULL DEFAULT TRUE");
        db.execute("ALTER TABLE cr4_print_devices ADD COLUMN IF NOT EXISTS text_align VARCHAR(12) NOT NULL DEFAULT 'LEFT'");
        db.execute("CREATE INDEX IF NOT EXISTS cr4_jobs_device_queue ON cr4_print_jobs(device_id,created_at) WHERE status='PENDING'");
        db.execute("CREATE INDEX IF NOT EXISTS cr4_jobs_tenant_created ON cr4_print_jobs(restaurant_id,created_at DESC)");
    }
}

@RestController
@RequestMapping("/api/v2/print")
public class V4PrintApi {
    private final JdbcTemplate db;
    private final com.aliator.cashregister.v8.V8Workflow workflow;
    private final SecureRandom random = new SecureRandom();
    public V4PrintApi(JdbcTemplate db,com.aliator.cashregister.v8.V8Workflow workflow) { this.db = db;this.workflow=workflow; }
    record Staff(long tenant, String role) {}
    record Device(long id, long tenant) {}
    record DeviceInput(@NotBlank @Size(max=90) String name) {}
    record JobInput(@NotNull Long deviceId, @NotBlank String kind, Long referenceId) {}
    record LabelSettings(@Min(40) @Max(110) int widthMm, @Min(25) @Max(200) int heightMm,
        @Min(0) @Max(10) int gapMm, @Size(max=64) String header,
        @Size(max=100) String footer, @NotBlank String density, boolean showTable, @NotBlank String alignment) {}
    private static String labelText(String s,int max){
        if(s==null)return "";
        String value=s.strip().replaceAll("[\\r\\n\\t\\p{Cntrl}]"," ");
        if(value.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Text too long");
        return value;
    }
    @GetMapping("/staff/devices/{id}/settings")
    public Map<String,Object> getSettings(HttpServletRequest r,@PathVariable long id){
        Staff s=owner(r);
        var result=db.queryForList("""
          SELECT width_mm AS "widthMm",height_mm AS "heightMm",gap_mm AS "gapMm",
          label_header AS "header",label_footer AS "footer",density,show_table AS "showTable",text_align AS "alignment"
          FROM cr4_print_devices WHERE id=? AND restaurant_id=? AND enabled=true
          """,id,s.tenant);
        if(result.isEmpty())throw fail(HttpStatus.NOT_FOUND,"Printer not found");
        return result.get(0);
    }
    @PutMapping("/staff/devices/{id}/settings")
    @Transactional
    public Map<String,Object> saveSettings(HttpServletRequest r,@PathVariable long id,@Valid @RequestBody LabelSettings input){
        Staff s=owner(r);
        String density=input.density().toUpperCase(Locale.ROOT);
        if(!Set.of("COMPACT","NORMAL","LARGE").contains(density))throw fail(HttpStatus.BAD_REQUEST,"Invalid density");
        String alignment=input.alignment().toUpperCase(Locale.ROOT);
        if(!Set.of("LEFT","CENTER","RIGHT").contains(alignment))throw fail(HttpStatus.BAD_REQUEST,"Invalid text alignment");
        int n=db.update("""
          UPDATE cr4_print_devices SET width_mm=?,height_mm=?,gap_mm=?,label_header=?,label_footer=?,density=?,show_table=?,text_align=?
          WHERE id=? AND restaurant_id=? AND enabled=true
          """,input.widthMm(),input.heightMm(),input.gapMm(),labelText(input.header(),64),
          labelText(input.footer(),100),density,input.showTable(),alignment,id,s.tenant);
        if(n!=1)throw fail(HttpStatus.NOT_FOUND,"Printer not found");
        return Map.of("saved",true,"message","Affects newly queued jobs only");
    }

    private ResponseStatusException fail(HttpStatus code, String msg) { return new ResponseStatusException(code,msg); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private Staff staff(HttpServletRequest request) {
        String token="";
        if (request.getCookies()!=null) for (var c:request.getCookies()) if (c.getName().equals("cr2_staff")) token=c.getValue();
        if (token.length()<30) throw fail(HttpStatus.UNAUTHORIZED,"Staff sign-in required");
        var found=db.queryForList("""
            SELECT u.restaurant_id,u.role FROM cr2_staff_sessions s
            JOIN cr2_users u ON u.id=s.user_id
            WHERE s.token_hash=? AND s.expires_at>now()
            """, hash(token));
        if(found.isEmpty()) throw fail(HttpStatus.UNAUTHORIZED,"Session expired");
        return new Staff(((Number)found.get(0).get("restaurant_id")).longValue(),(String)found.get(0).get("role"));
    }
    private Staff owner(HttpServletRequest request) {
        Staff s=staff(request);
        if(!Set.of("OWNER","MANAGER").contains(s.role)) throw fail(HttpStatus.FORBIDDEN,"Manager only");
        return s;
    }
    private Device device(HttpServletRequest request) {
        String header=request.getHeader("Authorization");
        if(header==null || !header.startsWith("Bearer ") || header.length()>200)
            throw fail(HttpStatus.UNAUTHORIZED,"Printer token required");
        String token=header.substring(7);
        if(token.length()!=43 || !token.matches("[A-Za-z0-9_-]{43}")) throw fail(HttpStatus.UNAUTHORIZED,"Invalid printer token");
        var ds=db.queryForList("SELECT id,restaurant_id FROM cr4_print_devices WHERE token_hash=? AND enabled=true",hash(token));
        if(ds.isEmpty())throw fail(HttpStatus.UNAUTHORIZED,"Printer not authorized");
        return new Device(((Number)ds.get(0).get("id")).longValue(),((Number)ds.get(0).get("restaurant_id")).longValue());
    }
    @PostMapping("/staff/devices")
    @Transactional
    public Map<String,Object> createDevice(HttpServletRequest r,@Valid @RequestBody DeviceInput input) {
        Staff s=owner(r);
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String name=input.name().strip();
        long id=db.queryForObject("INSERT INTO cr4_print_devices(restaurant_id,name,token_hash) VALUES(?,?,?) RETURNING id",Long.class,s.tenant,name,hash(token));
        // This value is returned exactly once; never print it in logs.
        return Map.of("deviceId",id,"token",token,"name",name,"notice","Copy the token privately to the Raspberry Pi. It will not be displayed again.");
    }
    @GetMapping("/staff/devices")
    public List<Map<String,Object>> listDevices(HttpServletRequest r) {
        Staff s=staff(r);
        if("KITCHEN".equals(s.role()))throw fail(HttpStatus.FORBIDDEN,"Cashier access required");
        return db.queryForList("SELECT id,name,enabled,last_seen_at AS \"lastSeenAt\",created_at AS \"createdAt\" FROM cr4_print_devices WHERE restaurant_id=? ORDER BY id",s.tenant);
    }
    @PostMapping("/staff/devices/{id}/disable")
    public Map<String,Object> disable(HttpServletRequest r,@PathVariable long id) {
        Staff s=owner(r);
        int n=db.update("UPDATE cr4_print_devices SET enabled=false WHERE id=? AND restaurant_id=?",id,s.tenant);
        if(n==0)throw fail(HttpStatus.NOT_FOUND,"Device not found");
        return Map.of("disabled",true);
    }
    @GetMapping("/staff/jobs")
    public List<Map<String,Object>> listJobs(HttpServletRequest r) {
        Staff s=staff(r);
        if("KITCHEN".equals(s.role()))throw fail(HttpStatus.FORBIDDEN,"Cashier access required");
        return db.queryForList("""
            SELECT j.id,j.kind,j.reference_id AS "referenceId",j.status,j.created_at AS "createdAt",
              j.claimed_at AS "claimedAt",j.finished_at AS "finishedAt",d.name AS "device"
            FROM cr4_print_jobs j JOIN cr4_print_devices d ON d.id=j.device_id
            WHERE j.restaurant_id=? ORDER BY j.created_at DESC LIMIT 40
            """,s.tenant);
    }
    @GetMapping("/staff/orders")
    public List<Map<String,Object>> recentOrders(HttpServletRequest r) {
        Staff s=owner(r);
        return db.queryForList("""
            SELECT id,table_code AS "tableCode",total_yen AS "totalYen",status,demo,created_at AS "createdAt"
            FROM cr2_orders WHERE restaurant_id=? ORDER BY created_at DESC LIMIT 30
            """,s.tenant);
    }
    @GetMapping("/staff/payments")
    public List<Map<String,Object>> recentPayments(HttpServletRequest r) {
        Staff s=owner(r);
        return db.queryForList("""
            SELECT p.id,p.amount_yen AS "amountYen",p.method,t.code AS "tableCode",p.recorded_at AS "recordedAt"
            FROM cr3_payments p JOIN cr2_dining_sessions d ON d.id=p.dining_session_id
              JOIN cr2_tables t ON t.id=d.table_id
            WHERE p.restaurant_id=? ORDER BY p.recorded_at DESC LIMIT 30
            """,s.tenant);
    }
    private Properties makePayload(long tenant,String kind,Long referenceId) {
        Properties p=new Properties();
        p.setProperty("kind",kind.equals("TEST")?"ORDER":kind);
        if(kind.equals("TEST")) {
            p.setProperty("printFormat","JA_BITMAP");p.setProperty("restaurant","テスト印刷");
            p.setProperty("time",java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Tokyo")).toString());
            p.setProperty("itemCount","2");p.setProperty("qty1","1");p.setProperty("qty2","1");
            p.setProperty("number","PRINT-TEST");p.setProperty("table","TEST");
            p.setProperty("item1","JAVA CASHREGISTER");p.setProperty("item2","REMOTE BRIDGE OK");
            p.setProperty("note","印刷テスト");
            p.setProperty("number","印刷テスト");
            p.setProperty("item1","印刷動作確認");
            p.setProperty("item2","接続確認");
        } else if (kind.equals("ORDER")) {
            if(referenceId==null)throw fail(HttpStatus.BAD_REQUEST,"Order ID required");
            var o=db.queryForList("SELECT id,table_code FROM cr2_orders WHERE id=? AND restaurant_id=?",referenceId,tenant);
            if(o.isEmpty())throw fail(HttpStatus.NOT_FOUND,"Order not found in your restaurant");
            p.setProperty("number","ORDER-"+referenceId);
            p.setProperty("table",(String)o.get(0).get("table_code"));
            var lines=db.queryForList("SELECT name,qty,note FROM cr2_order_lines WHERE order_id=? ORDER BY id LIMIT 24",referenceId);
            int i=0;
            for(var line:lines){
                p.setProperty("item"+(++i),line.get("qty")+"x "+line.get("name"));
                String note=(String)line.get("note");
                if(note!=null && !note.isBlank() && i<29) p.setProperty("item"+(++i),"Note: "+note);
                if(i>=29) break;
            }
            p.setProperty("note","ORDER RECEIVED");
        } else if(kind.equals("RECEIPT")) {
            if(referenceId==null)throw fail(HttpStatus.BAD_REQUEST,"Payment ID required");
            var payments=db.queryForList("""
                SELECT p.id,p.amount_yen,d.id AS dining_id,t.code
                FROM cr3_payments p JOIN cr2_dining_sessions d ON d.id=p.dining_session_id
                JOIN cr2_tables t ON t.id=d.table_id
                WHERE p.id=? AND p.restaurant_id=?
                """,referenceId,tenant);
            if(payments.isEmpty())throw fail(HttpStatus.NOT_FOUND,"Payment not found in your restaurant");
            var m=payments.get(0);
            p.setProperty("number","PAID-"+referenceId);
            p.setProperty("table",(String)m.get("code"));
            p.setProperty("total",String.valueOf(m.get("amount_yen")));
            var lines=db.queryForList("""
                SELECT l.name,sum(l.qty) AS qty FROM cr2_order_lines l
                  JOIN cr2_orders o ON o.id=l.order_id
                WHERE o.restaurant_id=? AND o.dining_session_id=? AND o.demo=false AND o.status<>'CANCELLED'
                GROUP BY l.name ORDER BY l.name LIMIT 28
                """,tenant,m.get("dining_id"));
            int i=0;for(var line:lines) p.setProperty("item"+(++i),line.get("qty")+"x "+line.get("name"));
            p.setProperty("note","SIMULATED PAYMENT ONLY");
        } else throw fail(HttpStatus.BAD_REQUEST,"Invalid print kind");
        return p;
    }
    private String properties(Properties p) {
        try {StringWriter w=new StringWriter();p.store(w,"Aliator printer job");return w.toString();}
        catch (IOException ex) {throw new IllegalStateException(ex);}
    }
    @PostMapping("/staff/jobs")
    @Transactional
    public Map<String,Object> enqueue(HttpServletRequest r,@Valid @RequestBody JobInput input) {
        Staff s=staff(r);
        if("KITCHEN".equals(s.role()))throw fail(HttpStatus.FORBIDDEN,"Cashier access required");
        String kind=input.kind().strip().toUpperCase(Locale.ROOT);
        if(!Set.of("TEST","ORDER","RECEIPT").contains(kind))throw fail(HttpStatus.BAD_REQUEST,"Print kind must be TEST, ORDER or RECEIPT");
        Long existing=db.query("SELECT id FROM cr4_print_devices WHERE id=? AND restaurant_id=? AND enabled=true",
          rs->{if(rs.next())return rs.getLong(1);return null;},input.deviceId(),s.tenant);
        if(existing==null)throw fail(HttpStatus.NOT_FOUND,"Printer device not found");
        // ALIATOR_V8_MANUAL_JAPANESE: manual tickets use exactly the same layout as auto tickets.
        Properties payload=kind.equals("TEST")?makePayload(s.tenant(),kind,input.referenceId()):workflow.manualPayload(s.tenant(),kind,input.referenceId());
        // Snapshot printer settings at queue time; old jobs keep their original geometry.
        var profiles=db.queryForList("""
          SELECT width_mm,height_mm,gap_mm,label_header,label_footer,density,show_table,text_align
          FROM cr4_print_devices WHERE id=? AND restaurant_id=? AND enabled=true
          """,existing,s.tenant());
        if(profiles.isEmpty()) throw fail(HttpStatus.NOT_FOUND,"Printer not found");
        var profile=profiles.get(0);
        payload.setProperty("labelWidthMm",String.valueOf(profile.get("width_mm")));
        payload.setProperty("labelHeightMm",String.valueOf(profile.get("height_mm")));
        payload.setProperty("labelGapMm",String.valueOf(profile.get("gap_mm")));
        payload.setProperty("labelHeader",String.valueOf(profile.get("label_header")));
        payload.setProperty("labelFooter",String.valueOf(profile.get("label_footer")));
        payload.setProperty("labelDensity",String.valueOf(profile.get("density")));
        payload.setProperty("labelShowTable",String.valueOf(profile.get("show_table")));
        payload.setProperty("labelAlignment",String.valueOf(profile.get("text_align")));

        UUID id=UUID.randomUUID();payload.setProperty("remoteId",id.toString());
        db.update("INSERT INTO cr4_print_jobs(id,restaurant_id,device_id,kind,reference_id,content) VALUES(?::uuid,?,?,?,?,?)",
            id.toString(),s.tenant(),existing,kind,input.referenceId(),properties(payload));
        return Map.of("jobId",id.toString(),"status","PENDING","autoPrinting",false);
    }
    @GetMapping(value="/agent/next",produces="text/plain;charset=UTF-8")
    @Transactional
    public ResponseEntity<String> next(HttpServletRequest r) {
        Device d=device(r);
        db.update("UPDATE cr4_print_devices SET last_seen_at=now() WHERE id=?",d.id());
        db.update("""
            UPDATE cr4_print_devices v SET last_seen_at=now() FROM cr11_station_grants g
            WHERE g.station_device_id=? AND g.logical_device_id=v.id AND g.enabled=true AND v.enabled=true
        """,d.id());
        var list=db.queryForList("""
            SELECT j.id,j.content FROM cr4_print_jobs j
            WHERE j.status='PENDING' AND (
                (j.device_id=? AND j.restaurant_id=?) OR EXISTS (
                    SELECT 1 FROM cr11_station_grants g JOIN cr4_print_devices v ON v.id=g.logical_device_id
                    WHERE g.station_device_id=? AND g.logical_device_id=j.device_id
                      AND g.restaurant_id=j.restaurant_id AND g.enabled=true AND v.enabled=true
                )
            )
            ORDER BY j.created_at,j.id FOR UPDATE OF j SKIP LOCKED LIMIT 1
            """,d.id(),d.tenant(),d.id());
        if(list.isEmpty()) return ResponseEntity.noContent().build();
        var m=list.get(0);
        db.update("UPDATE cr4_print_jobs SET status='CLAIMED',claimed_at=now() WHERE id=?::uuid AND status='PENDING'",m.get("id").toString());
        return ResponseEntity.ok().contentType(new MediaType("text","plain",StandardCharsets.UTF_8)).body((String)m.get("content"));
    }
    @PostMapping("/agent/ack")
    @Transactional
    public Map<String,Object> ack(HttpServletRequest r,@RequestParam UUID jobId,@RequestParam String status) {
        Device d=device(r);
        String result=status.toUpperCase(Locale.ROOT);
        if(!Set.of("SENT","UNCERTAIN").contains(result))throw fail(HttpStatus.BAD_REQUEST,"Bad status");
        var rows=db.queryForList("""
            SELECT j.status FROM cr4_print_jobs j WHERE j.id=?::uuid AND (
                (j.device_id=? AND j.restaurant_id=?) OR EXISTS (
                    SELECT 1 FROM cr11_station_grants g JOIN cr4_print_devices v ON v.id=g.logical_device_id
                    WHERE g.station_device_id=? AND g.logical_device_id=j.device_id
                      AND g.restaurant_id=j.restaurant_id AND g.enabled=true AND v.enabled=true
                )
            ) FOR UPDATE OF j
            """,jobId.toString(),d.id(),d.tenant(),d.id());
        if(rows.isEmpty())throw fail(HttpStatus.NOT_FOUND,"Print job not found");
        String previous=(String)rows.get(0).get("status");
        if(previous.equals(result))return Map.of("ok",true,"alreadyAcknowledged",true);
        if(!previous.equals("CLAIMED"))throw fail(HttpStatus.CONFLICT,"Unexpected job state");
        db.update("UPDATE cr4_print_jobs SET status=?,finished_at=now() WHERE id=?::uuid",result,jobId.toString());
        return Map.of("ok",true,"status",result);
    }
}
