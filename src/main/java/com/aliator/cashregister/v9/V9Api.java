package com.aliator.cashregister.v9;

import com.aliator.cashregister.v3.V3Operations;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/** V9 API: menu customization and cashier-approved, simulated QR payment. */
@RestController
@RequestMapping("/api/v2/v9")
public class V9Api {
    private final JdbcTemplate db;
    private final V9MenuService menu;
    private final V3Operations payments;
    private final String publicUrl;
    private final SecureRandom random=new SecureRandom();
    public V9Api(JdbcTemplate db,V9MenuService menu,V3Operations payments,
        @Value("${app.public-url:https://cashregister.aliator.com}") String publicUrl) {
        this.db=db;this.menu=menu;this.payments=payments;this.publicUrl=publicUrl.replaceAll("/+$","");
    }
    private static ResponseStatusException error(HttpStatus s,String m){return new ResponseStatusException(s,m);}
    private static long number(Object o){return ((Number)o).longValue();}
    private static String hash(String token){try{
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }catch(Exception e){throw new IllegalStateException(e);}}
    record Staff(long id,long tenant,String role) {}
    private static String cookie(HttpServletRequest req,String name){
        if(req.getCookies()!=null)for(var c:req.getCookies())if(name.equals(c.getName()))return c.getValue();return "";
    }
    private Staff staff(HttpServletRequest req,boolean manager){
        String token=cookie(req,"cr2_staff");
        if(!token.matches("[A-Za-z0-9_-]{30,200}"))throw error(HttpStatus.UNAUTHORIZED,"Staff login required");
        var rows=db.queryForList("""
  SELECT u.id,u.restaurant_id,u.role FROM cr2_staff_sessions s
          JOIN cr2_users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>now()"""
  ,hash(token));
        if(rows.isEmpty())throw error(HttpStatus.UNAUTHORIZED,"Staff session expired");
        var r=rows.get(0);String role=(String)r.get("role");
        if(manager && !Set.of("OWNER","MANAGER").contains(role))throw error(HttpStatus.FORBIDDEN,"Manager role required");
        if(!manager && "KITCHEN".equals(role))throw error(HttpStatus.FORBIDDEN,"Cashier role required");
        return new Staff(number(r.get("id")),number(r.get("restaurant_id")),role);
    }
    private long guestTenant(HttpServletRequest req){
        String token=cookie(req,"cr2_guest");
        if(!token.matches("[A-Za-z0-9_-]{30,200}"))throw error(HttpStatus.UNAUTHORIZED,"Scan active table QR");
        var rows=db.queryForList("""
  SELECT d.restaurant_id FROM cr2_guest_sessions g
          JOIN cr2_dining_sessions d ON d.id=g.dining_session_id
          WHERE g.token_hash=? AND g.expires_at>now() AND d.status='OPEN'"""
  ,hash(token));
        if(rows.isEmpty())throw error(HttpStatus.UNAUTHORIZED,"Table expired");
        return number(rows.get(0).get("restaurant_id"));
    }
    private static String limited(String value,int max){String s=value==null?"":value.strip();
        if(s.length()>max)throw error(HttpStatus.BAD_REQUEST,"Text exceeds maximum length");return s;}
    record SettingInput(String sourceLanguage,List<String> menuLanguages,boolean autoTranslate){}
    @GetMapping("/guest/settings")
    public Map<String,Object> guestSettings(HttpServletRequest req){return menu.settings(guestTenant(req));}
    @GetMapping("/guest/menu")
    public Map<String,Object> guestMenu(HttpServletRequest req,@RequestParam(defaultValue="ja")String lang){
        long tenant=guestTenant(req);String l=V9MenuService.normalize(lang);var config=menu.settings(tenant);
        if(!Arrays.asList(((String)config.get("menuLanguages")).split(",")).contains(l))
            throw error(HttpStatus.BAD_REQUEST,"Restaurant has not enabled this language");
        return Map.of("settings",config,"items",menu.products(tenant,l));
    }
    @GetMapping("/staff/menu-full")
    public List<Map<String,Object>> staffFullMenu(HttpServletRequest req){
        Staff s=staff(req,true);return menu.products(s.tenant(),"ja");
    }
    @GetMapping("/staff/menu-settings")
    public Map<String,Object> menuSettings(HttpServletRequest req){Staff s=staff(req,true);return menu.settings(s.tenant());}
    @PutMapping("/staff/menu-settings")
    public Map<String,Object> configure(HttpServletRequest req,@RequestBody SettingInput in){
        Staff s=staff(req,true);return menu.configure(s.tenant(),in.sourceLanguage(),in.menuLanguages(),in.autoTranslate());
    }
    @GetMapping("/staff/menu-translation-status")
    public Map<String,Object> translationStatus(HttpServletRequest req){Staff s=staff(req,true);return menu.translationStatus(s.tenant());}
    @PostMapping("/staff/menu-translation-refresh")
    public Map<String,Object> refreshTranslations(HttpServletRequest req){Staff s=staff(req,true);
        var c=menu.settings(s.tenant());if(!Boolean.TRUE.equals(c.get("autoTranslate")))
            throw error(HttpStatus.BAD_REQUEST,"Enable automatic translation first");
        Set<String> languages=new HashSet<>(Arrays.asList(((String)c.get("menuLanguages")).split(",")));
        return Map.of("queued",menu.requeue(s.tenant(),(String)c.get("sourceLanguage"),languages));
    }
    record DetailInput(String ingredientsJa,String allergensJa,String preparationJa,Integer spiceLevel){}
    @PutMapping("/staff/menu/{itemId}/details") @Transactional
    public Map<String,Object> saveDetails(HttpServletRequest req,@PathVariable long itemId,@RequestBody DetailInput input){
        Staff s=staff(req,true);requireItem(s.tenant(),itemId);
        if(input.spiceLevel()==null||input.spiceLevel()<0||input.spiceLevel()>5)
            throw error(HttpStatus.BAD_REQUEST,"Invalid spice level");
        db.update("""
  INSERT INTO cr9_menu_details(menu_item_id,ingredients_ja,allergens_ja,preparation_ja,spice_level)
           VALUES(?,?,?,?,?) ON CONFLICT(menu_item_id) DO UPDATE SET ingredients_ja=excluded.ingredients_ja,
           allergens_ja=excluded.allergens_ja,preparation_ja=excluded.preparation_ja,spice_level=excluded.spice_level"""
  ,
           itemId,limited(input.ingredientsJa(),600),limited(input.allergensJa(),300),limited(input.preparationJa(),600),input.spiceLevel());
        menu.markChanged(s.tenant(),itemId);return Map.of("saved",true);
    }
    public record ValueInput(String labelJa,String labelEn,Integer deltaYen){}
    public record GroupInput(String labelJa,String labelEn,Integer minChoices,Integer maxChoices,List<ValueInput> values){}
    @PutMapping("/staff/menu/{itemId}/options") @Transactional
    public Map<String,Object> saveOptions(HttpServletRequest req,@PathVariable long itemId,@RequestBody List<GroupInput> groups){
        Staff s=staff(req,true);requireItem(s.tenant(),itemId);
        if(groups==null||groups.size()>10)throw error(HttpStatus.BAD_REQUEST,"Too many option groups");
        db.update("DELETE FROM cr9_option_groups WHERE menu_item_id=?",itemId);
        int i=0;
        for(var g:groups){
            if(g.minChoices()==null||g.maxChoices()==null||g.minChoices()<0||g.maxChoices()<1||g.maxChoices()>8||g.minChoices()>g.maxChoices()
              ||g.values()==null||g.values().isEmpty()||g.values().size()>16||g.minChoices()>g.values().size())throw error(HttpStatus.BAD_REQUEST,"Invalid option group");
            long group=db.queryForObject("""
  INSERT INTO cr9_option_groups(menu_item_id,label_ja,label_en,min_choices,max_choices,sort_order)
               VALUES(?,?,?,?,?,?) RETURNING id"""
  ,Long.class,itemId,limited(g.labelJa(),90),limited(g.labelEn(),90),g.minChoices(),g.maxChoices(),i++);
            int j=0;for(var v:g.values()){
                if(v.deltaYen()==null||v.deltaYen()<-2000||v.deltaYen()>10000)throw error(HttpStatus.BAD_REQUEST,"Invalid option price");
                db.update("""
  INSERT INTO cr9_option_values(group_id,label_ja,label_en,delta_yen,sort_order)
                    VALUES(?,?,?,?,?)"""
  ,group,limited(v.labelJa(),100),limited(v.labelEn(),100),v.deltaYen(),j++);
            }
        }
        menu.markChanged(s.tenant(),itemId);return Map.of("saved",true,"groups",groups.size());
    }
    private void requireItem(long tenant,long id){Integer n=db.queryForObject("SELECT count(*) FROM cr2_menu_items WHERE id=? AND restaurant_id=?",Integer.class,id,tenant);
        if(n==null||n!=1)throw error(HttpStatus.NOT_FOUND,"Menu product not found");}

    record StartPay(long tableId){}
    record PayAmount(Integer amountYen){}
    private String token(){byte[] raw=new byte[32];random.nextBytes(raw);return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);}
    @PostMapping("/staff/demo-pay") @Transactional
    public Map<String,Object> startPay(HttpServletRequest req,@RequestBody StartPay in){
        Staff s=staff(req,false);long table=in.tableId();
        var rows=db.queryForList("""
  SELECT d.id,r.name,t.code FROM cr2_tables t
          JOIN cr2_dining_sessions d ON d.table_id=t.id
          JOIN cr2_restaurants r ON r.id=d.restaurant_id
          WHERE t.id=? AND t.restaurant_id=? AND d.status='OPEN' FOR UPDATE OF t"""
  ,table,s.tenant());
        if(rows.size()!=1)throw error(HttpStatus.CONFLICT,"Open table not found");
        long session=number(rows.get(0).get("id"));
        long rawTotal=db.queryForObject("""
  SELECT COALESCE(sum(total_yen),0) FROM cr2_orders
          WHERE restaurant_id=? AND dining_session_id=? AND demo=false AND status<>'CANCELLED'"""
  ,Long.class,s.tenant(),session);
        if(rawTotal>2000000000L)throw error(HttpStatus.BAD_REQUEST,"Bill too large");
        int total=(int)rawTotal;
        if(total<=0)throw error(HttpStatus.CONFLICT,"No bill to pay");
        db.update("""
  UPDATE cr9_demo_pay SET status='REPLACED',updated_at=now()
          WHERE restaurant_id=? AND dining_session_id=? AND status IN ('OPEN','AWAITING_CASHIER')"""
  ,s.tenant(),session);
        String raw=token();UUID id=UUID.randomUUID();
        db.update("""
  INSERT INTO cr9_demo_pay(id,restaurant_id,table_id,dining_session_id,token_hash,amount_yen,expires_at)
          VALUES(?::uuid,?,?,?,?,?,now()+interval '5 minutes')"""
  ,id.toString(),s.tenant(),table,session,hash(raw),total);
        return Map.of("id",id.toString(),"url",publicUrl+"/demo-pay.html?t="+raw,
          "amountYen",total,"status","OPEN","expiresInSeconds",300,"demo",true);
    }
    private static String checkToken(String t){if(t==null||!t.matches("[A-Za-z0-9_-]{35,100}"))
        throw error(HttpStatus.NOT_FOUND,"Invalid demo QR");return hash(t);}
    private Map<String,Object> publicStatus(String t,boolean lock){
        String sql="""
  SELECT p.id,p.amount_yen AS "amountYen",p.status,p.expires_at AS "expiresAt",
         t.code AS "tableCode",r.name AS "restaurant" FROM cr9_demo_pay p
         JOIN cr2_tables t ON t.id=p.table_id JOIN cr2_restaurants r ON r.id=p.restaurant_id
         WHERE p.token_hash=?"""
  +(lock?" FOR UPDATE OF p":"");
        var rows=db.queryForList(sql,checkToken(t));
        if(rows.isEmpty())throw error(HttpStatus.NOT_FOUND,"Demo QR not found");
        return rows.get(0);
    }
    @GetMapping("/public/demo-pay/{token}")
    public Map<String,Object> demoStatus(@PathVariable String token){Map<String,Object> m=publicStatus(token,false);
        return Map.of("amountYen",m.get("amountYen"),"restaurant",m.get("restaurant"),"tableCode",m.get("tableCode"),
          "status",m.get("status"),"expiresAt",m.get("expiresAt"),"demo",true);
    }
    @PostMapping("/public/demo-pay/{token}/submit") @Transactional
    public Map<String,Object> submit(@PathVariable String token,@RequestBody PayAmount in){
        var m=publicStatus(token,true);String state=(String)m.get("status");
        if(!"OPEN".equals(state))throw error(HttpStatus.CONFLICT,"QR is not awaiting submission");
        if(((java.sql.Timestamp)m.get("expiresAt")).toInstant().isBefore(Instant.now()))
            throw error(HttpStatus.GONE,"Demo QR expired");
        long amount=number(m.get("amountYen"));UUID id=(UUID)m.get("id");
        if(in.amountYen()==null||in.amountYen()!=amount){
            db.update("UPDATE cr9_demo_pay SET attempts=attempts+1 WHERE id=?",id);
            Integer tries=db.queryForObject("SELECT attempts FROM cr9_demo_pay WHERE id=?",Integer.class,id);
            if(tries>=4)db.update("UPDATE cr9_demo_pay SET status='REJECTED' WHERE id=?",id);
            return Map.of("accepted",false,"reason","Amount does not match","remainingAttempts",Math.max(0,4-tries));
        }
        db.update("UPDATE cr9_demo_pay SET status='AWAITING_CASHIER',updated_at=now() WHERE id=?",id);
        return Map.of("accepted",true,"paid",false,"status","AWAITING_CASHIER","message","Demo submitted; cashier must confirm");
    }
    @GetMapping("/staff/demo-pay/{id}")
    public Map<String,Object> cashierStatus(HttpServletRequest req,@PathVariable UUID id){Staff s=staff(req,false);
        var rows=db.queryForList("""
  SELECT id,amount_yen AS "amountYen",status,expires_at AS "expiresAt",
          payment_id AS "paymentId" FROM cr9_demo_pay WHERE id=? AND restaurant_id=?"""
  ,id,s.tenant());
        if(rows.isEmpty())throw error(HttpStatus.NOT_FOUND,"QR not found");return rows.get(0);
    }
    @GetMapping(value="/staff/demo-pay/{id}/qr",produces=MediaType.IMAGE_PNG_VALUE)
    public byte[] qr(HttpServletRequest req,@PathVariable UUID id,@RequestParam String url)throws Exception{
        Staff s=staff(req,false);
        String prefix=publicUrl+"/demo-pay.html?t=";
        if(url.length()>300||!url.startsWith(prefix))
            throw error(HttpStatus.NOT_FOUND,"QR not found");
        String tokenValue=url.substring(prefix.length());
        if(!tokenValue.matches("[A-Za-z0-9_-]{35,100}"))
            throw error(HttpStatus.NOT_FOUND,"QR not found");
        Integer count=db.queryForObject(
            "SELECT count(*) FROM cr9_demo_pay WHERE id=? AND restaurant_id=? AND token_hash=?",
            Integer.class,id,s.tenant(),hash(tokenValue));
        if(count!=1)throw error(HttpStatus.NOT_FOUND,"QR not found");
        BitMatrix bits=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,300,300);
        BufferedImage img=new BufferedImage(300,300,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<300;y++)for(int x=0;x<300;x++)img.setRGB(x,y,bits.get(x,y)?0x111111:0xffffff);
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(img,"PNG",out);return out.toByteArray();
    }
    @PostMapping("/staff/demo-pay/{id}/confirm") @Transactional
    public Map<String,Object> confirm(HttpServletRequest req,@PathVariable UUID id){Staff s=staff(req,false);
        // Lock order matches QR creation and V3 checkout: table first, then payment row.
        var located=db.queryForList("SELECT table_id FROM cr9_demo_pay WHERE id=? AND restaurant_id=?",id,s.tenant());
        if(located.size()!=1)throw error(HttpStatus.NOT_FOUND,"Demo QR not found");
        db.queryForList("SELECT id FROM cr2_tables WHERE id=? AND restaurant_id=? FOR UPDATE",
                number(located.get(0).get("table_id")),s.tenant());
        var rows=db.queryForList("""
  SELECT id,table_id,dining_session_id,amount_yen,status,expires_at
          FROM cr9_demo_pay WHERE id=? AND restaurant_id=? FOR UPDATE"""
  ,id,s.tenant());
        if(rows.size()!=1)throw error(HttpStatus.NOT_FOUND,"Demo QR not found");var p=rows.get(0);
        if(!"AWAITING_CASHIER".equals(p.get("status")))throw error(HttpStatus.CONFLICT,"Customer has not submitted demo amount");
        if(((java.sql.Timestamp)p.get("expires_at")).toInstant().isBefore(Instant.now()))throw error(HttpStatus.GONE,"Demo QR expired");
        // The existing V3 checkout locks the session, creates ONE simulated payment,
        // closes guest tokens and invokes the V8 automatic receipt outbox in one transaction.
        var result=payments.checkout(s.tenant(),s.id(),number(p.get("table_id")),
           new V3Operations.CheckoutInput("OTHER",null,"PAYPAY-DEMO-"+id.toString().substring(0,8)));
        if(number(result.get("amountYen"))!=number(p.get("amount_yen")))
            throw error(HttpStatus.CONFLICT,"Bill changed; please start a new QR payment");
        db.update("""
  UPDATE cr9_demo_pay SET status='CONFIRMED',cashier_id=?,payment_id=?,updated_at=now()
           WHERE id=? AND restaurant_id=?"""
  ,s.id(),result.get("paymentId"),id,s.tenant());
        return Map.of("confirmed",true,"simulated",true,"paymentId",result.get("paymentId"));
    }
}
