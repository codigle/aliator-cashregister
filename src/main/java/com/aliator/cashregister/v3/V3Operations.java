package com.aliator.cashregister.v3;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.*;

@Service
public class V3Operations {
    private final JdbcTemplate db;
    private final com.aliator.cashregister.v8.V8Workflow flow;
    public V3Operations(JdbcTemplate db,com.aliator.cashregister.v8.V8Workflow flow) { this.db=db;this.flow=flow; }
    public record CheckoutInput(String method, Integer tenderedYen, String reference) {}
    private static long num(Map<String,Object> m,String k){return ((Number)m.get(k)).longValue();}
    private static ResponseStatusException conflict(String msg){return new ResponseStatusException(HttpStatus.CONFLICT,msg);}

    @Transactional(readOnly=true)
    public List<Map<String,Object>> openChecks(long tenant) {
        return db.queryForList("""
            SELECT d.id AS "sessionId",t.id AS "tableId",t.code AS "tableCode",t.label AS "tableLabel",
                   d.opened_at AS "openedAt",COALESCE(sum(o.total_yen) FILTER(WHERE o.demo=false AND o.status<>'CANCELLED'),0) AS "amountYen",
                   count(o.id) FILTER(WHERE o.demo=false AND o.status<>'CANCELLED') AS "orderCount"
            FROM cr2_dining_sessions d JOIN cr2_tables t ON t.id=d.table_id
            LEFT JOIN cr2_orders o ON o.dining_session_id=d.id
            WHERE d.restaurant_id=? AND d.status='OPEN'
            GROUP BY d.id,t.id,t.code,t.label,d.opened_at ORDER BY d.opened_at
            """,tenant);
    }


    @Transactional(readOnly=true)
    public List<Map<String,Object>> checkOrders(long tenant,long session) {
        long count=db.queryForObject("SELECT count(*) FROM cr2_dining_sessions WHERE id=? AND restaurant_id=? AND status='OPEN'",Long.class,session,tenant);
        if(count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Open session not found.");
        return db.queryForList("""
            SELECT o.id,o.status,o.total_yen AS "totalYen",o.created_at AS "createdAt",
              l.name,l.qty,l.unit_price_yen AS "unitPriceYen",l.note
            FROM cr2_orders o JOIN cr2_order_lines l ON l.order_id=o.id
            WHERE o.restaurant_id=? AND o.dining_session_id=? AND o.demo=false AND o.status<>'CANCELLED'
            ORDER BY o.id,l.id
            """,tenant,session);
    }
    @Transactional
    public Map<String,Object> checkout(long tenant,long cashierId,long tableId,CheckoutInput input) {
        if(input==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Provide payment method.");
        String method=input.method()==null?"":input.method().trim().toUpperCase(Locale.ROOT);
        if(!Set.of("CASH","CARD","OTHER").contains(method))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use CASH, CARD, or OTHER.");
        String reference=input.reference()==null?"":input.reference().strip();
        if(reference.length()>80)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reference exceeds 80 characters.");
        // DB locks protect concurrent cashiers / guests posting new orders.
        var t=db.queryForList("SELECT id FROM cr2_tables WHERE id=? AND restaurant_id=? FOR UPDATE",tableId,tenant);
        if(t.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Table not found.");
        var d=db.queryForList("SELECT id,opened_at FROM cr2_dining_sessions WHERE table_id=? AND restaurant_id=? AND status='OPEN' FOR UPDATE",tableId,tenant);
        if(d.isEmpty())throw conflict("No active dining session. It may already have been closed.");
        long session=num(d.get(0),"id");
        long sum=db.queryForObject("""
            SELECT COALESCE(sum(total_yen),0) FROM cr2_orders
            WHERE restaurant_id=? AND dining_session_id=? AND demo=false AND status<>'CANCELLED'
            """,Long.class,tenant,session);
        if(sum<=0) throw conflict("No billable orders. Use Close empty table to finish an unpaid visit.");
        if(sum>2_000_000_000)throw conflict("Bill exceeds supported demo value.");
        int amount=(int)sum;
        Integer tendered=null;
        int change=0;
        if(method.equals("CASH")) {
            tendered=input.tenderedYen();
            if(tendered==null || tendered<amount || tendered>2_000_000_000)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cash tendered must be at least the bill amount.");
            change=tendered-amount;
        }
        long id=db.queryForObject("""
            INSERT INTO cr3_payments(restaurant_id,dining_session_id,cashier_user_id,method,amount_yen,tendered_yen,change_yen,reference)
            VALUES(?,?,?,?,?,?,?,?) RETURNING id
            """,Long.class,tenant,session,cashierId,method,amount,tendered,change,reference);
        db.update("UPDATE cr2_dining_sessions SET status='PAID',closed_at=now() WHERE id=? AND restaurant_id=?",session,tenant);
        db.update("DELETE FROM cr2_guest_sessions WHERE dining_session_id=?",session);
        db.update("UPDATE cr2_tables SET qr_token=? WHERE id=? AND restaurant_id=?",random(),tableId,tenant);
        // ALIATOR_V8_AUTORECEIPT: queue after checkout state is recorded, in same transaction.
        flow.paymentRecorded(tenant,id);
        return Map.of("paymentId",id,"amountYen",amount,"changeYen",change,"method",method,"sessionId",session,"closed",true,"qrRotated",true,"simulated",true);
    }

    @Transactional
    public Map<String,Object> closeEmpty(long tenant,long tableId) {
        var tables=db.queryForList("SELECT id FROM cr2_tables WHERE id=? AND restaurant_id=? FOR UPDATE",tableId,tenant);
        if(tables.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Table not found.");
        var sessions=db.queryForList("SELECT id FROM cr2_dining_sessions WHERE restaurant_id=? AND table_id=? AND status='OPEN' FOR UPDATE",tenant,tableId);
        if(sessions.isEmpty())throw conflict("No open dining session.");
        long sid=num(sessions.get(0),"id");
        long count=db.queryForObject("SELECT count(*) FROM cr2_orders WHERE restaurant_id=? AND dining_session_id=? AND demo=false AND status<>'CANCELLED'",Long.class,tenant,sid);
        if(count>0)throw conflict("This session contains billable orders; record payment at the cashier.");
        db.update("UPDATE cr2_dining_sessions SET status='CLOSED',closed_at=now() WHERE id=?",sid);
        db.update("DELETE FROM cr2_guest_sessions WHERE dining_session_id=?",sid);
        db.update("UPDATE cr2_tables SET qr_token=? WHERE id=? AND restaurant_id=?",random(),tableId,tenant);
        return Map.of("closed",true,"sessionId",sid,"qrRotated",true);
    }

    private static String random(){
        byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> receipt(long tenant,long paymentId) {
        var p=db.queryForList("""
            SELECT p.id,p.method,p.amount_yen AS "amountYen",p.tendered_yen AS "tenderedYen",
              p.change_yen AS "changeYen",p.recorded_at AS "recordedAt",p.reference,
              t.code AS "tableCode",d.opened_at AS "openedAt",d.closed_at AS "closedAt",r.name AS "restaurant"
            FROM cr3_payments p JOIN cr2_dining_sessions d ON d.id=p.dining_session_id
              JOIN cr2_tables t ON t.id=d.table_id JOIN cr2_restaurants r ON r.id=p.restaurant_id
            WHERE p.restaurant_id=? AND p.id=?
            """,tenant,paymentId);
        if(p.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Payment not found.");
        Map<String,Object> result=new LinkedHashMap<>(p.get(0));
        result.put("items",db.queryForList("""
            SELECT l.name,sum(l.qty) AS qty,l.unit_price_yen AS "unitPriceYen"
            FROM cr2_order_lines l JOIN cr2_orders o ON o.id=l.order_id
            JOIN cr3_payments p ON p.dining_session_id=o.dining_session_id
            WHERE p.id=? AND p.restaurant_id=? AND o.status<>'CANCELLED' AND o.demo=false
            GROUP BY l.name,l.unit_price_yen ORDER BY l.name
            """,paymentId,tenant));
        result.put("simulated",true);
        return result;
    }

    @Transactional(readOnly=true)
    public Map<String,Object> reports(long tenant,String period) {
        String p = period == null ? "day" : period.toLowerCase(Locale.ROOT);
        // These SQL fragments are selected from trusted literals only.
        String bucket,window,offset,step;
        int expectedPoints;
        switch (p) {
            case "hour" -> {bucket="hour";window="24 hours";offset="23 hours";step="1 hour";expectedPoints=24;}
            case "day" -> {bucket="day";window="30 days";offset="29 days";step="1 day";expectedPoints=30;}
            case "week" -> {bucket="week";window="12 weeks";offset="11 weeks";step="1 week";expectedPoints=12;}
            case "month" -> {bucket="month";window="12 months";offset="11 months";step="1 month";expectedPoints=12;}
            case "year" -> {bucket="year";window="5 years";offset="4 years";step="1 year";expectedPoints=5;}
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Period must be hour/day/week/month/year.");
        }
        String zone = db.queryForObject("SELECT timezone FROM cr2_restaurants WHERE id=?",String.class,tenant);
        try { ZoneId.of(zone); } catch (Exception e) { zone="Asia/Tokyo"; }

        // Use generate_series so a brand-new restaurant still has a complete chart.
        // Payments and orders remain separate: open orders are NEVER counted as revenue.
        String chartSql = """
            WITH slices AS (
              SELECT generate_series(
                date_trunc('%s',now() AT TIME ZONE ?) - interval '%s',
                date_trunc('%s',now() AT TIME ZONE ?), interval '%s') AS bucket
            ), payments AS (
              SELECT date_trunc('%s',recorded_at AT TIME ZONE ?) AS bucket,
                     sum(amount_yen) AS revenue, count(*) AS checks
              FROM cr3_payments WHERE restaurant_id=? AND recorded_at >= now()-interval '%s'
              GROUP BY 1
            ), placed AS (
              SELECT date_trunc('%s',created_at AT TIME ZONE ?) AS bucket,
                     count(*) AS orders,sum(total_yen) AS ordered_yen
              FROM cr2_orders
              WHERE restaurant_id=? AND demo=false AND status<>'CANCELLED'
                AND created_at >= now()-interval '%s'
              GROUP BY 1
            )
            SELECT to_char(s.bucket,'YYYY-MM-DD HH24:MI') AS label,
                   COALESCE(pm.revenue,0) AS revenue,
                   COALESCE(pm.checks,0) AS checks,
                   COALESCE(o.orders,0) AS orders,
                   COALESCE(o.ordered_yen,0) AS "orderedYen"
            FROM slices s LEFT JOIN payments pm ON pm.bucket=s.bucket
                          LEFT JOIN placed o ON o.bucket=s.bucket
            ORDER BY s.bucket
            """.formatted(bucket,offset,bucket,step,bucket,window,bucket,window);
        List<Map<String,Object>> series=db.queryForList(chartSql,zone,zone,zone,tenant,zone,tenant);
        if(series.size()!=expectedPoints) {
            throw new IllegalStateException("Analytics generated unexpected bucket count.");
        }
        String timeframe=" AND p.recorded_at >= now()-interval '"+window+"'";
        Map<String,Object> totals=db.queryForMap("""
            SELECT count(*) AS "paidChecks",COALESCE(sum(p.amount_yen),0) AS "revenueYen",
              COALESCE(round(avg(extract(epoch from (p.recorded_at-d.opened_at))/60)::numeric,1),0) AS "avgSeatMinutes"
            FROM cr3_payments p JOIN cr2_dining_sessions d ON d.id=p.dining_session_id
            WHERE p.restaurant_id=?
            """+timeframe,tenant);
        Map<String,Object> kitchen=db.queryForMap("""
            SELECT COALESCE(round(avg(extract(epoch from (o.ready_at-o.preparing_at))/60)::numeric,1),0) AS "avgPrepMinutes",
                   COALESCE(round(avg(extract(epoch from (o.ready_at-o.created_at))/60)::numeric,1),0) AS "avgKitchenMinutes",
                   count(*) AS "completedOrders"
            FROM cr2_orders o
            WHERE o.restaurant_id=? AND o.demo=false AND o.ready_at IS NOT NULL AND o.preparing_at IS NOT NULL
              AND o.ready_at>=now()-interval '%s'
            """.formatted(window),tenant);
        List<Map<String,Object>> top=db.queryForList("""
            SELECT l.name,sum(l.qty) AS quantity,sum(l.qty*l.unit_price_yen) AS "grossYen"
            FROM cr3_payments p JOIN cr2_orders o ON o.dining_session_id=p.dining_session_id
            JOIN cr2_order_lines l ON l.order_id=o.id
            WHERE p.restaurant_id=? AND p.recorded_at>=now()-interval '%s'
              AND o.demo=false AND o.status<>'CANCELLED'
            GROUP BY l.name ORDER BY quantity DESC,l.name LIMIT 8
            """.formatted(window),tenant);
        Map<String,Object> activity=db.queryForMap("""
            SELECT count(*) FILTER(WHERE demo=false AND status<>'CANCELLED') AS "placedOrders",
                   COALESCE(sum(total_yen) FILTER(WHERE demo=false AND status<>'CANCELLED'),0) AS "placedValueYen",
                   count(*) FILTER(WHERE demo=true) AS "demoOrders",
                   count(*) FILTER(WHERE demo=false AND status='CANCELLED') AS "cancelledOrders"
            FROM cr2_orders
            WHERE restaurant_id=? AND created_at >= now()-interval '%s'
            """.formatted(window),tenant);
        Map<String,Object> open=db.queryForMap("""
            SELECT count(*) AS "openTables",COALESCE(sum(bill),0) AS "outstandingYen"
            FROM (
               SELECT d.id, COALESCE(sum(o.total_yen) FILTER
                 (WHERE o.demo=false AND o.status<>'CANCELLED'),0) AS bill
               FROM cr2_dining_sessions d
               LEFT JOIN cr2_orders o ON o.dining_session_id=d.id AND o.restaurant_id=d.restaurant_id
               WHERE d.restaurant_id=? AND d.status='OPEN'
               GROUP BY d.id
            ) checks
            """,tenant);
        List<Map<String,Object>> topOrdered=db.queryForList("""
            SELECT l.name,sum(l.qty) AS quantity,sum(l.qty*l.unit_price_yen) AS "grossYen"
            FROM cr2_orders o JOIN cr2_order_lines l ON l.order_id=o.id
            WHERE o.restaurant_id=? AND o.created_at >= now()-interval '%s'
              AND o.demo=false AND o.status<>'CANCELLED'
            GROUP BY l.name ORDER BY quantity DESC,l.name LIMIT 8
            """.formatted(window),tenant);
        List<Map<String,Object>> orderStatuses=db.queryForList("""
            SELECT status,count(*) AS quantity
            FROM cr2_orders
            WHERE restaurant_id=? AND created_at >= now()-interval '%s' AND demo=false
            GROUP BY status ORDER BY status
            """.formatted(window),tenant);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("period",p);
        result.put("timezone",zone);
        result.put("currency","JPY");
        result.put("series",series);
        result.put("totals",totals);
        result.put("kitchen",kitchen);
        result.put("topProducts",top);
        result.put("activity",activity);
        result.put("openChecks",open);
        result.put("topOrdered",topOrdered);
        result.put("orderStatuses",orderStatuses);
        result.put("salesDefinition","Recorded simulated checkouts only; excludes unpaid orders and demo test tickets.");
        return result;
    }
}
