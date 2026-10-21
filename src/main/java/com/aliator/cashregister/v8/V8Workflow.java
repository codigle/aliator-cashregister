package com.aliator.cashregister.v8;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Order(900)
class V8WorkflowSchema implements CommandLineRunner {
    private final JdbcTemplate db;
    V8WorkflowSchema(JdbcTemplate db) { this.db=db; }
    @Override public void run(String... args) {
        db.execute("""
            CREATE TABLE IF NOT EXISTS cr8_workflow_settings (
                restaurant_id BIGINT PRIMARY KEY REFERENCES cr2_restaurants(id),
                auto_kitchen BOOLEAN NOT NULL DEFAULT FALSE,
                auto_receipt BOOLEAN NOT NULL DEFAULT FALSE,
                confirm_delivery BOOLEAN NOT NULL DEFAULT FALSE,
                kitchen_device_id BIGINT REFERENCES cr4_print_devices(id),
                receipt_device_id BIGINT REFERENCES cr4_print_devices(id),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
            )
        """);
        db.execute("ALTER TABLE cr4_print_jobs ADD COLUMN IF NOT EXISTS origin VARCHAR(12) NOT NULL DEFAULT 'MANUAL'");
        db.execute("CREATE UNIQUE INDEX IF NOT EXISTS cr8_unique_auto_jobs ON cr4_print_jobs(restaurant_id,kind,reference_id) WHERE origin='AUTO' AND reference_id IS NOT NULL");
    }
}

/** Tenant-scoped configuration, atomic transactional outbox and Japanese-only job snapshots. */
@Service
public class V8Workflow {
    private final JdbcTemplate db;
    public V8Workflow(JdbcTemplate db) { this.db=db; }

    public record Settings(boolean autoKitchen,boolean autoReceipt,boolean confirmDelivery,
                           Long kitchenDeviceId,Long receiptDeviceId) {}
    private static long number(Object v) { return ((Number)v).longValue(); }
    private static ResponseStatusException fail(HttpStatus code,String msg) { return new ResponseStatusException(code,msg); }

    public Settings settings(long tenant) {
        var list=db.queryForList("""
            SELECT auto_kitchen,auto_receipt,confirm_delivery,kitchen_device_id,receipt_device_id
            FROM cr8_workflow_settings WHERE restaurant_id=?
        """,tenant);
        if(list.isEmpty()) return new Settings(false,false,false,null,null);
        var m=list.get(0);
        return new Settings((Boolean)m.get("auto_kitchen"),(Boolean)m.get("auto_receipt"),
            (Boolean)m.get("confirm_delivery"),
            m.get("kitchen_device_id")==null?null:number(m.get("kitchen_device_id")),
            m.get("receipt_device_id")==null?null:number(m.get("receipt_device_id")));
    }
    public boolean confirmDelivery(long tenant) {return settings(tenant).confirmDelivery();}

    @Transactional
    public Settings save(long tenant,Settings s) {
        if(s==null)throw fail(HttpStatus.BAD_REQUEST,"Missing workflow settings");
        validateDevice(tenant,s.kitchenDeviceId());
        validateDevice(tenant,s.receiptDeviceId());
        if(s.autoKitchen() && s.kitchenDeviceId()==null)
            throw fail(HttpStatus.BAD_REQUEST,"Select a kitchen printer before enabling automatic kitchen tickets");
        if(s.autoReceipt() && s.receiptDeviceId()==null)
            throw fail(HttpStatus.BAD_REQUEST,"Select a receipt printer before enabling automatic payment tickets");
        db.update("""
            INSERT INTO cr8_workflow_settings(restaurant_id,auto_kitchen,auto_receipt,confirm_delivery,kitchen_device_id,receipt_device_id)
            VALUES(?,?,?,?,?,?) ON CONFLICT(restaurant_id) DO UPDATE SET
              auto_kitchen=excluded.auto_kitchen,auto_receipt=excluded.auto_receipt,
              confirm_delivery=excluded.confirm_delivery,kitchen_device_id=excluded.kitchen_device_id,
              receipt_device_id=excluded.receipt_device_id,updated_at=now()
        """,tenant,s.autoKitchen(),s.autoReceipt(),s.confirmDelivery(),s.kitchenDeviceId(),s.receiptDeviceId());
        return settings(tenant);
    }
    private void validateDevice(long tenant, Long id) {
        if(id==null)return;
        long count=db.queryForObject("SELECT count(*) FROM cr4_print_devices WHERE id=? AND restaurant_id=? AND enabled=true",Long.class,id,tenant);
        if(count!=1)throw fail(HttpStatus.BAD_REQUEST,"Selected printer is not active in this restaurant");
    }

    /** Called inside the original order transaction. No duplicate or phantom ticket on rollback. */
    @Transactional
    public void orderPlaced(long tenant,long orderId) {
        Settings s=settings(tenant);
        if(s.autoKitchen() && s.kitchenDeviceId()!=null) queue(tenant,s.kitchenDeviceId(),"ORDER",orderId);
    }
    /** Called inside checkout's transaction before returning the simulated payment. */
    @Transactional
    public void paymentRecorded(long tenant,long paymentId) {
        Settings s=settings(tenant);
        if(s.autoReceipt() && s.receiptDeviceId()!=null) queue(tenant,s.receiptDeviceId(),"RECEIPT",paymentId);
    }
    private static String propClean(String s,int max) {
        if(s==null)return "";
        s=s.replaceAll("[\\p{Cntrl}\\r\\n\\t]"," ").strip();
        return s.length()>max?s.substring(0,max):s;
    }
    private static String y(Object n){return String.format(Locale.JAPAN,"%,d",number(n));}
    private static String jst(Object ts){
        if(ts instanceof java.sql.Timestamp t)return DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm",Locale.JAPANESE)
            .withZone(ZoneId.of("Asia/Tokyo")).format(t.toInstant());
        return DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm",Locale.JAPANESE)
            .withZone(ZoneId.of("Asia/Tokyo")).format(Instant.now());
    }
    public Properties manualPayload(long tenant,String kind,Long id) {
        if(id==null)throw fail(HttpStatus.BAD_REQUEST,"Print reference required");
        return payload(tenant,kind,id);
    }
    private Properties payload(long tenant,String kind,long id) {
        var p=new Properties();p.setProperty("kind",kind);p.setProperty("printFormat","JA_BITMAP");
        p.setProperty("note","デモシステム／実際の決済は行われていません");
        if(kind.equals("ORDER")) {
            var rows=db.queryForList("""
                SELECT o.id,o.table_code,o.created_at,r.name AS restaurant
                FROM cr2_orders o JOIN cr2_restaurants r ON r.id=o.restaurant_id
                WHERE o.restaurant_id=? AND o.id=?
            """,tenant,id);
            if(rows.isEmpty())throw fail(HttpStatus.NOT_FOUND,"Order not found");
            var m=rows.get(0);p.setProperty("number","注文番号 "+id);
            p.setProperty("restaurant",propClean((String)m.get("restaurant"),90));
            p.setProperty("table",propClean((String)m.get("table_code"),40));
            p.setProperty("time",jst(m.get("created_at")));
            var lines=db.queryForList("""
                SELECT COALESCE(NULLIF(mi.name_ja,''),l.name) AS name,l.qty,l.note
                FROM cr2_order_lines l JOIN cr2_orders o ON o.id=l.order_id
                LEFT JOIN cr2_menu_items mi ON mi.id=l.menu_item_id AND mi.restaurant_id=o.restaurant_id
                WHERE o.restaurant_id=? AND o.id=? ORDER BY l.id LIMIT 50
            """,tenant,id);
            p.setProperty("itemCount",String.valueOf(lines.size()));
            int i=0;
            for(var l:lines){i++;p.setProperty("item"+i,propClean((String)l.get("name"),150));
                p.setProperty("qty"+i,String.valueOf(l.get("qty")));
                p.setProperty("itemNote"+i,propClean((String)l.get("note"),180));}
        } else if(kind.equals("RECEIPT")) {
            var rows=db.queryForList("""
                SELECT p.id,p.amount_yen,p.tendered_yen,p.change_yen,p.method,p.reference,p.recorded_at,
                  t.code AS table_code,r.name AS restaurant
                FROM cr3_payments p JOIN cr2_dining_sessions d ON d.id=p.dining_session_id
                JOIN cr2_tables t ON t.id=d.table_id JOIN cr2_restaurants r ON r.id=p.restaurant_id
                WHERE p.restaurant_id=? AND p.id=?
            """,tenant,id);
            if(rows.isEmpty())throw fail(HttpStatus.NOT_FOUND,"Payment not found");
            var m=rows.get(0);p.setProperty("number","会計番号 "+id);
            p.setProperty("restaurant",propClean((String)m.get("restaurant"),90));
            p.setProperty("table",propClean((String)m.get("table_code"),40));
            p.setProperty("time",jst(m.get("recorded_at")));
            p.setProperty("total",y(m.get("amount_yen")));
            p.setProperty("method",String.valueOf(m.get("reference")).startsWith("PAYPAY-DEMO-")
                ? "QR決済（模擬）" : switch((String)m.get("method")){
                    case "CASH"->"現金";case "CARD"->"カード（模擬）";default->"その他（模擬）";});
            p.setProperty("tendered",m.get("tendered_yen")==null?"":y(m.get("tendered_yen")));
            p.setProperty("change",y(m.get("change_yen")));
            var lines=db.queryForList("""
                SELECT COALESCE(NULLIF(mi.name_ja,''),l.name) AS name,l.qty,l.unit_price_yen
                FROM cr2_order_lines l JOIN cr2_orders o ON o.id=l.order_id
                JOIN cr3_payments p ON p.dining_session_id=o.dining_session_id
                LEFT JOIN cr2_menu_items mi ON mi.id=l.menu_item_id AND mi.restaurant_id=o.restaurant_id
                WHERE p.restaurant_id=? AND p.id=? AND o.demo=false AND o.status<>'CANCELLED'
                ORDER BY o.id,l.id LIMIT 100
            """,tenant,id);
            p.setProperty("itemCount",String.valueOf(lines.size()));
            int i=0;
            for(var l:lines){i++;p.setProperty("item"+i,propClean((String)l.get("name"),150));
                p.setProperty("qty"+i,String.valueOf(l.get("qty")));
                p.setProperty("unitPrice"+i,y(l.get("unit_price_yen")));
                p.setProperty("linePrice"+i,y(number(l.get("unit_price_yen"))*number(l.get("qty"))));}
        } else throw fail(HttpStatus.BAD_REQUEST,"Unsupported print type");
        return p;
    }
    private void queue(long tenant,long deviceId,String kind,long id) {
        // FK/tenant check prevents cross-restaurant jobs and avoids queueing to disabled devices.
        var printers=db.queryForList("""
            SELECT width_mm,height_mm,gap_mm,label_header,label_footer,density,show_table,text_align
            FROM cr4_print_devices WHERE id=? AND restaurant_id=? AND enabled=true
        """,deviceId,tenant);
        if(printers.isEmpty())return; // User disabled a printer after saving workflow.
        Properties p=payload(tenant,kind,id);
        Map<String,Object> m=printers.get(0);
        p.setProperty("labelWidthMm",String.valueOf(m.get("width_mm")));
        p.setProperty("labelHeightMm",String.valueOf(m.get("height_mm")));
        p.setProperty("labelGapMm",String.valueOf(m.get("gap_mm")));
        p.setProperty("labelHeader",propClean((String)m.get("label_header"),64));
        p.setProperty("labelFooter",propClean((String)m.get("label_footer"),100));
        p.setProperty("labelDensity",String.valueOf(m.get("density")));
        p.setProperty("labelShowTable",String.valueOf(m.get("show_table")));
        p.setProperty("labelAlignment",String.valueOf(m.get("text_align")));
        UUID uuid=UUID.randomUUID();p.setProperty("remoteId",uuid.toString());
        var out=new StringWriter();try{p.store(out,"Aliator V8 Japanese bitmap thermal ticket");}
        catch(java.io.IOException ex){throw new IllegalStateException(ex);}
        String content=out.toString();
        if(content.length()>=20000)throw fail(HttpStatus.UNPROCESSABLE_ENTITY,"Ticket too large for printer queue");
        db.update("""
            INSERT INTO cr4_print_jobs(id,restaurant_id,device_id,kind,reference_id,content,origin)
            VALUES(?::uuid,?,?,?,?,?,'AUTO')
            ON CONFLICT DO NOTHING
        """,uuid.toString(),tenant,deviceId,kind,id,content);
    }
}

@RestController
@RequestMapping("/api/v2/staff/workflow")
class V8WorkflowApi {
    private final JdbcTemplate db;
    private final V8Workflow workflow;
    V8WorkflowApi(JdbcTemplate db,V8Workflow workflow){this.db=db;this.workflow=workflow;}
    private long staffTenant(HttpServletRequest req,boolean managerOnly){
        String token="";
        if(req.getCookies()!=null)for(var c:req.getCookies())if("cr2_staff".equals(c.getName()))token=c.getValue();
        if(!token.matches("[A-Za-z0-9_-]{30,200}"))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in required");
        String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
        var rows=db.queryForList("""
            SELECT u.restaurant_id,u.role FROM cr2_staff_sessions s JOIN cr2_users u ON u.id=s.user_id
            WHERE s.token_hash=? AND s.expires_at>now()
        """,hash);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Session expired");
        if(managerOnly && !Set.of("OWNER","MANAGER").contains(rows.get(0).get("role")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Owner/manager required");
        return ((Number)rows.get(0).get("restaurant_id")).longValue();
    }
    @GetMapping("/settings")
    public V8Workflow.Settings get(HttpServletRequest req){return workflow.settings(staffTenant(req,false));}
    @PutMapping("/settings")
    public V8Workflow.Settings put(HttpServletRequest req,@RequestBody V8Workflow.Settings input){return workflow.save(staffTenant(req,true),input);}
}
