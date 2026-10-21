package com.aliator.cashregister.v2;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.*;
import java.sql.Timestamp;

@Service
public class V2Orders {
    private final JdbcTemplate db;
    private final com.aliator.cashregister.v8.V8Workflow flow;
    private final com.aliator.cashregister.v9.V9MenuService optionService;
    V2Orders(JdbcTemplate db,com.aliator.cashregister.v8.V8Workflow flow,
             com.aliator.cashregister.v9.V9MenuService optionService) {
        this.db=db;this.flow=flow;this.optionService=optionService;
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND,message); }
    private static long n(Map<String,Object> m,String key) {return ((Number)m.get(key)).longValue();}

    @Transactional
    public Map<String,Object> join(String qr,HttpServletResponse response) {
        if(qr==null || qr.length()<30 || qr.length()>90) throw missing("QR code not valid.");
        var tables=db.queryForList("""
            SELECT t.id,t.restaurant_id,t.code,r.name FROM cr2_tables t
            JOIN cr2_restaurants r ON r.id=t.restaurant_id
            WHERE t.qr_token=? AND t.active=true FOR UPDATE OF t
            """,qr);
        if(tables.isEmpty()) throw missing("QR code has been replaced or revoked. Ask staff for a new QR.");
        var t=tables.get(0);long tableId=n(t,"id"),tenant=n(t,"restaurant_id");
        db.update("""
            INSERT INTO cr2_dining_sessions(restaurant_id,table_id,status) VALUES (?,?,'OPEN')
            ON CONFLICT (table_id) WHERE status='OPEN' DO NOTHING
            """,tenant,tableId);
        long diningId=db.queryForObject("SELECT id FROM cr2_dining_sessions WHERE table_id=? AND status='OPEN'",Long.class,tableId);
        String guestToken=V2Tokens.random();
        db.update("INSERT INTO cr2_guest_sessions(token_hash,dining_session_id,expires_at) VALUES (?,?,now()+interval '30 days')",
            V2Tokens.hash(guestToken),diningId);
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("cr2_guest",guestToken).httpOnly(true).secure(true)
            .sameSite("Lax").path("/api/v2").maxAge(Duration.ofDays(30)).build().toString());
        return Map.of("restaurant",(String)t.get("name"),"tableCode",(String)t.get("code"),"sessionId",diningId);
    }

    @Transactional
    public Map<String,Object> place(long tenant,long diningId,String tableCode,String reqId,List<V2Api.LineInput> lines) {
        UUID uuid;
        try {uuid=UUID.fromString(reqId);} catch(Exception e){throw bad("Invalid request ID.");}
        // Serialise placing/closing against the same session, also across multiple Java instances.
        var locked=db.queryForList("SELECT id FROM cr2_dining_sessions WHERE id=? AND restaurant_id=? AND status='OPEN' FOR UPDATE",diningId,tenant);
        if(locked.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"This table is already closed.");
        var prev=db.queryForList("SELECT id FROM cr2_orders WHERE dining_session_id=? AND request_id=?",diningId,uuid);
        if(!prev.isEmpty()) return view(n(prev.get(0),"id"),tenant);
        long id=create(tenant,diningId,tableCode,uuid,lines,false);
        return view(id,tenant);
    }

    @Transactional
    public Map<String,Object> placeDemo(long tenant,String tableCode,List<V2Api.LineInput> lines) {
        // Demo orders have NO dining session, and are excluded from actual turnover metrics.
        if(tableCode==null || !tableCode.matches("[A-Za-z0-9-]{1,30}"))throw bad("Invalid table code.");
        var tables=db.queryForList("SELECT id FROM cr2_tables WHERE restaurant_id=? AND code=?",tenant,tableCode);
        if(tables.isEmpty())throw missing("Table not found.");
        long id=create(tenant,null,tableCode,UUID.randomUUID(),lines,true);
        return view(id,tenant);
    }

    private long create(long tenant,Long diningId,String tableCode,UUID requestId,List<V2Api.LineInput> lines,boolean demo) {
        if(lines==null || lines.isEmpty() || lines.size()>20)throw bad("Choose between 1 and 20 items.");
        var entries=new ArrayList<Object[]>();long total=0;
        for(var line:lines) {
            if(line.menuItemId()==null || line.quantity()==null || line.quantity()<1 || line.quantity()>15)throw bad("Invalid quantity.");
            String note=line.note()==null?"":line.note().strip();
            if(note.length()>180)throw bad("Note too long.");
            var products=db.queryForList("SELECT id,name,price_yen FROM cr2_menu_items WHERE id=? AND restaurant_id=? AND active=true",
                line.menuItemId(),tenant);
            if(products.isEmpty())throw bad("One or more menu items are unavailable.");
            var p=products.get(0); int price=((Number)p.get("price_yen")).intValue();
            // ALIATOR_V9_VALIDATED_OPTIONS: compute menu extras on the SERVER, never trust client totals.
            var choices=line.optionValueIds()==null?java.util.List.<Long>of():line.optionValueIds();
            if(!demo || !choices.isEmpty()) {
                var resolved=optionService.resolve(tenant,line.menuItemId(),price,choices);
                price=resolved.unitPriceYen();
                if(!resolved.optionNote().isBlank()) note=resolved.optionNote()+(note.isBlank()?"":" | "+note);
                if(note.length()>180)throw bad("Customization note too long");
            }
            total+=(long)price*line.quantity();
            entries.add(new Object[]{line.menuItemId(),p.get("name"),line.quantity(),price,note});
        }
        if(total>200000 || total>Integer.MAX_VALUE)throw bad("Order exceeds demo limit.");
        long id=db.queryForObject("""
            INSERT INTO cr2_orders(restaurant_id,dining_session_id,table_code,request_id,status,demo,total_yen)
            VALUES (?,?,?,?,'NEW',?,?) RETURNING id
            """, Long.class,tenant,diningId,tableCode,requestId,demo,(int)total);
        for(Object[] line:entries)db.update("INSERT INTO cr2_order_lines(order_id,menu_item_id,name,qty,unit_price_yen,note) VALUES (?,?,?,?,?,?)",
            id,line[0],line[1],line[2],line[3],line[4]);
        // ALIATOR_V8_AUTOPRINT: append outbox job in the same transaction as a REAL order.
        if(!demo)flow.orderPlaced(tenant,id);
        return id;
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> orders(long tenant) {
        return db.queryForList("""
            SELECT id,table_code AS "tableCode",status,total_yen AS "totalYen",demo,created_at AS "createdAt"
            FROM cr2_orders WHERE restaurant_id=? ORDER BY created_at DESC LIMIT 200
            """,tenant).stream().map(m->withLines(m,tenant)).toList();
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> guestOrders(long tenant,long diningId) {
        return db.queryForList("""
            SELECT id,table_code AS "tableCode",status,total_yen AS "totalYen",demo,created_at AS "createdAt"
            FROM cr2_orders WHERE restaurant_id=? AND dining_session_id=? ORDER BY created_at DESC LIMIT 50
            """,tenant,diningId).stream().map(m->withLines(m,tenant)).toList();
    }
    private Map<String,Object> withLines(Map<String,Object> row,long tenant) {
        var copy=new LinkedHashMap<String,Object>(row);
        copy.put("lines",db.queryForList("""
            SELECT l.name,l.qty,l.unit_price_yen AS "unitPriceYen",l.note
            FROM cr2_order_lines l JOIN cr2_orders o ON o.id=l.order_id
            WHERE l.order_id=? AND o.restaurant_id=? ORDER BY l.id
            """,n(row,"id"),tenant));
        return copy;
    }
    private Map<String,Object> view(long id,long tenant) {
        var found=db.queryForList("""
            SELECT id,table_code AS "tableCode",status,total_yen AS "totalYen",demo,created_at AS "createdAt"
            FROM cr2_orders WHERE id=? AND restaurant_id=?
            """,id,tenant);
        if(found.isEmpty()) throw missing("Order not found.");
        return withLines(found.get(0),tenant);
    }
    @Transactional
    public Map<String,Object> changeStatus(long tenant,long orderId,String next) {
        String desired=next==null?"":next.toUpperCase(Locale.ROOT);
        var check=db.queryForList("SELECT dining_session_id FROM cr2_orders WHERE id=? AND restaurant_id=?",orderId,tenant);
        if(check.isEmpty())throw missing("Order not found.");
        Object parent=check.get(0).get("dining_session_id");
        if(parent!=null) {
            // Same session lock order as checkout, and prevent modifying a paid tab.
            var active=db.queryForList("SELECT id FROM cr2_dining_sessions WHERE id=? AND restaurant_id=? AND status='OPEN' FOR UPDATE",parent,tenant);
            if(active.isEmpty() && desired.equals("CANCELLED"))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"A paid order cannot be cancelled without a refund.");
        }
        var rows=db.queryForList("SELECT status FROM cr2_orders WHERE id=? AND restaurant_id=? FOR UPDATE",orderId,tenant);
        if(rows.isEmpty())throw missing("Order not found.");
        String previous=(String)rows.get(0).get("status");
        if(previous.equals(desired))return view(orderId,tenant);
        if(desired.equals("READY") && (previous.equals("NEW") || previous.equals("PREPARING"))
           && !flow.confirmDelivery(tenant)) {
            // When manual delivery confirmation is off, one "preparation complete" action is enough.
            db.update("UPDATE cr2_orders SET status='SERVED',updated_at=now(),preparing_at=COALESCE(preparing_at,now()),ready_at=COALESCE(ready_at,now()),served_at=COALESCE(served_at,now()) WHERE id=? AND restaurant_id=?",orderId,tenant);
            return view(orderId,tenant);
        }
        boolean valid=switch(previous) {
            case "NEW" -> desired.equals("PREPARING")||desired.equals("READY")||desired.equals("CANCELLED");
            case "PREPARING" -> desired.equals("READY")||desired.equals("CANCELLED");
            case "READY" -> desired.equals("SERVED");
            default -> false;
        };
        if(!valid)throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid order status transition.");
        String at=switch(desired) {
            case "PREPARING" -> "preparing_at";
            case "READY" -> "ready_at";
            case "SERVED" -> "served_at";
            case "CANCELLED" -> "cancelled_at";
            default -> "";
        };
        if(at.isBlank()) throw bad("Unsupported status.");
        db.update("UPDATE cr2_orders SET status=?,updated_at=now(),"+at+"=COALESCE("+at+",now()) WHERE id=? AND restaurant_id=?",desired,orderId,tenant);
        return view(orderId,tenant);
    }
    @Transactional
    public Map<String,Object> closePaid(long tenant,long tableId) {
        // Lock table to serialise manual closing and QR rotation.
        var t=db.queryForList("SELECT id FROM cr2_tables WHERE id=? AND restaurant_id=? FOR UPDATE",tableId,tenant);
        if(t.isEmpty())throw missing("Table not found.");
        var active=db.queryForList("SELECT id FROM cr2_dining_sessions WHERE table_id=? AND restaurant_id=? AND status='OPEN' FOR UPDATE",tableId,tenant);
        if(active.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"No open tab for this table.");
        long diningId=n(active.get(0),"id");
        // This action records a cashier's confirmation, NOT payment processing.
        db.update("UPDATE cr2_dining_sessions SET status='PAID',closed_at=now() WHERE id=? AND restaurant_id=?",diningId,tenant);
        db.update("DELETE FROM cr2_guest_sessions WHERE dining_session_id=?",diningId);
        db.update("UPDATE cr2_tables SET qr_token=? WHERE id=? AND restaurant_id=?",V2Tokens.random(),tableId,tenant);
        return Map.of("closed",true,"qrRotated",true,"sessionId",diningId);
    }
}
