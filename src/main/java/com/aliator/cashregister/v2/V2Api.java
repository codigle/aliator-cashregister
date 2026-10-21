package com.aliator.cashregister.v2;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.aliator.cashregister.v3.V3Operations;

@RestController
@RequestMapping("/api/v2")
public class V2Api {
    record Login(@Email String email, @NotBlank String password) {}
    record Register(@NotBlank String restaurantName, @NotBlank String slug,
        @Email String email, @NotBlank String password, @NotBlank String inviteCode) {}
    record MenuInput(@NotBlank String category, @NotBlank String name, String description,
        String emoji, @NotNull @Min(0) @Max(1000000) Integer priceYen, boolean active,
        String nameJa, String descriptionJa, String nameZh, String descriptionZh, String nameKo, String descriptionKo, String nameTr, String descriptionTr, String imageUrl, String imageCredit) {}
    record TableInput(@NotBlank String code, @NotBlank String label) {}
    record JoinRequest(@NotBlank String qr) {}
    record LineInput(@NotNull Long menuItemId, @NotNull @Min(1) @Max(15) Integer quantity,
        @Size(max=180) String note, @Size(max=16) List<Long> optionValueIds) {}
    record OrderInput(@NotBlank String requestId, @NotEmpty @Size(max=20) List<@Valid LineInput> items) {}
    record DemoInput(@NotBlank String tableCode, @NotEmpty @Size(max=20) List<@Valid LineInput> items) {}
    record StatusInput(@NotBlank String status) {}
    record Staff(long id, long tenant, String email, String role, String restaurant) {}
    record Guest(long diningId, long tenant, long tableId, String tableCode, String restaurant) {}

    private final com.aliator.cashregister.v9.V9MenuService v9Menu;
    private final JdbcTemplate db;
    private final V2Orders orderService;
    private final V3Operations v3;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(11);
    private final String ownerInvite;
    private final String publicUrl;
    private final Map<String, long[]> loginAttempts = new ConcurrentHashMap<>();

    public V2Api(JdbcTemplate db, V2Orders orderService, V3Operations v3, @Value("${app.owner-invite-code:}") String ownerInvite,
                 @Value("${app.public-url:https://cashregister.aliator.com}") String publicUrl,
                 com.aliator.cashregister.v9.V9MenuService v9Menu) {
        this.db = db; this.orderService = orderService; this.v3=v3; this.ownerInvite = ownerInvite; this.publicUrl = publicUrl; this.v9Menu=v9Menu;
    }
    private static ResponseStatusException err(HttpStatus status, String msg) { return new ResponseStatusException(status, msg); }
    private static String cookie(HttpServletRequest req, String name) {
        if (req.getCookies() == null) return "";
        for (var c : req.getCookies()) if (name.equals(c.getName())) return c.getValue();
        return "";
    }
    private static void setCookie(HttpServletResponse resp, String name, String value, Duration age) {
        resp.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).httpOnly(true).secure(true)
            .sameSite("Lax").path("/api/v2").maxAge(age).build().toString());
    }
    private Staff staff(HttpServletRequest req) {
        String token = cookie(req, "cr2_staff");
        if (token.length() < 30) throw err(HttpStatus.UNAUTHORIZED, "Please sign in as restaurant staff.");
        var rows = db.queryForList("""
            SELECT u.id AS user_id, u.restaurant_id, u.email, u.role, r.name
            FROM cr2_staff_sessions s JOIN cr2_users u ON u.id=s.user_id
            JOIN cr2_restaurants r ON r.id=u.restaurant_id
            WHERE s.token_hash=? AND s.expires_at>now()
            """, V2Tokens.hash(token));
        if (rows.isEmpty()) throw err(HttpStatus.UNAUTHORIZED, "Staff session expired.");
        var a=rows.get(0);
        return new Staff(num(a,"user_id"),num(a,"restaurant_id"),(String)a.get("email"),(String)a.get("role"),(String)a.get("name"));
    }
    private static long num(Map<String,Object> m,String key) { return ((Number)m.get(key)).longValue(); }
    private Guest guest(HttpServletRequest req) {
        String token = cookie(req,"cr2_guest");
        if (token.length() < 30) throw err(HttpStatus.UNAUTHORIZED,"Scan the current table QR code.");
        var rows = db.queryForList("""
            SELECT d.id AS dining_id,d.restaurant_id,d.table_id,t.code,r.name
            FROM cr2_guest_sessions g JOIN cr2_dining_sessions d ON d.id=g.dining_session_id
            JOIN cr2_tables t ON t.id=d.table_id JOIN cr2_restaurants r ON r.id=d.restaurant_id
            WHERE g.token_hash=? AND g.expires_at>now() AND d.status='OPEN'
            """, V2Tokens.hash(token));
        if (rows.isEmpty()) throw err(HttpStatus.UNAUTHORIZED,"The table session is closed or expired. Scan a current QR.");
        var m=rows.get(0);
        return new Guest(num(m,"dining_id"),num(m,"restaurant_id"),num(m,"table_id"),(String)m.get("code"),(String)m.get("name"));
    }
    private static void manager(Staff s) {
        if (!s.role().equals("OWNER") && !s.role().equals("MANAGER"))
            throw err(HttpStatus.FORBIDDEN,"Manager permission required.");
    }
    private static String clean(String value, int max) {
        String s = value==null?"":value.strip();
        if (s.isEmpty() || s.length()>max) throw err(HttpStatus.BAD_REQUEST,"Invalid field length.");
        return s;
    }
    private void slowDownBadLogin(String key) {
        long now=System.currentTimeMillis();
        long[] a=loginAttempts.computeIfAbsent(key,k->new long[]{0,now});
        synchronized(a) {
            if (now-a[1]>600_000) {a[0]=0;a[1]=now;}
            if (++a[0]>8) throw err(HttpStatus.TOO_MANY_REQUESTS,"Too many attempts. Try later.");
        }
    }
    @PostMapping("/auth/login")
    public Map<String,Object> login(@Valid @RequestBody Login input, HttpServletRequest req, HttpServletResponse resp) {
        String email=clean(input.email(),254).toLowerCase(Locale.ROOT);
        // Deliberately limit by remote address and email; reverse-proxy must remain localhost-only.
        String key=req.getRemoteAddr()+"|"+email;
        slowDownBadLogin(key);
        var users=db.queryForList("SELECT id,password_hash FROM cr2_users WHERE email=?",email);
        if (users.isEmpty() || !bcrypt.matches(input.password(), (String)users.get(0).get("password_hash")))
            throw err(HttpStatus.UNAUTHORIZED,"Invalid email or password.");
        loginAttempts.remove(key);
        long id=num(users.get(0),"id");
        String token=V2Tokens.random();
        db.update("INSERT INTO cr2_staff_sessions(token_hash,user_id,expires_at) VALUES (?,?,now() + interval '12 hours')",V2Tokens.hash(token),id);
        setCookie(resp,"cr2_staff",token,Duration.ofHours(12));
        return Map.of("ok",true);
    }
    @PostMapping("/auth/logout")
    public Map<String,Object> logout(HttpServletRequest req,HttpServletResponse resp) {
        String token=cookie(req,"cr2_staff");
        if (!token.isEmpty()) db.update("DELETE FROM cr2_staff_sessions WHERE token_hash=?",V2Tokens.hash(token));
        setCookie(resp,"cr2_staff","",Duration.ZERO);return Map.of("ok",true);
    }
    // Legacy unverified registration path is disabled. Only email-verified registration is accepted.
    @PostMapping("/auth/register")
    public Map<String,Object> disabledLegacyRegistration(@Valid @RequestBody Register input) {
        throw err(HttpStatus.GONE, "Use email verification to create a restaurant.");
    }
    private static String photoUrl(String value) {
        String url = value==null?"":value.strip();
        if(url.isEmpty())return "";
        if(url.length()>550 || !(url.startsWith("https://commons.wikimedia.org/wiki/Special:FilePath/") ||
              url.startsWith("https://images.unsplash.com/")))
            throw err(HttpStatus.BAD_REQUEST,"Photo URL must be a trusted HTTPS image source.");
        return url;
    }
    private static String optional(String s,int max) {
        if(s==null)return "";
        s=s.strip();if(s.length()>max)throw err(HttpStatus.BAD_REQUEST,"Field too long.");return s;
    }
    @GetMapping("/staff/me")
    public Map<String,Object> me(HttpServletRequest req) {
        Staff s=staff(req);
        return Map.of("restaurant",s.restaurant(),"email",s.email(),"role",s.role(),"restaurantId",s.tenant());
    }
    @GetMapping("/staff/menu")
    public List<Map<String,Object>> menu(HttpServletRequest req) {
        Staff s=staff(req);
        return db.queryForList("SELECT id,category,name,description,emoji,price_yen AS \"priceYen\",active,sort_order AS \"sortOrder\", name_ja AS \"nameJa\", description_ja AS \"descriptionJa\", name_zh AS \"nameZh\", description_zh AS \"descriptionZh\",name_ko AS \"nameKo\", description_ko AS \"descriptionKo\", name_tr AS \"nameTr\", description_tr AS \"descriptionTr\", image_url AS \"imageUrl\", image_credit AS \"imageCredit\" FROM cr2_menu_items WHERE restaurant_id=? ORDER BY sort_order,id",s.tenant());
    }
    @PostMapping("/staff/menu")
    @Transactional
    public Map<String,Object> createMenu(HttpServletRequest req,@Valid @RequestBody MenuInput m) {
        Staff s=staff(req); manager(s);
        long id=db.queryForObject("""
            INSERT INTO cr2_menu_items(restaurant_id,category,name,description,emoji,price_yen,active,sort_order,name_ja,description_ja,name_zh,description_zh,name_ko,description_ko,name_tr,description_tr,image_url,image_credit)
            VALUES (?,?,?,?,?,?,?,COALESCE((SELECT max(sort_order)+1 FROM cr2_menu_items WHERE restaurant_id=?),1),?,?,?,?,?,?,?,?,?,?) RETURNING id
            """, Long.class,s.tenant(),clean(m.category(),80),clean(m.name(),140),m.description()==null?"":m.description().strip(),
            m.emoji()==null?"🍽":clean(m.emoji(),24),m.priceYen(),m.active(),s.tenant(),
            optional(m.nameJa(),140),optional(m.descriptionJa(),300),optional(m.nameZh(),140),optional(m.descriptionZh(),300),optional(m.nameKo(),140),optional(m.descriptionKo(),300),optional(m.nameTr(),140),optional(m.descriptionTr(),300),photoUrl(m.imageUrl()),optional(m.imageCredit(),250));
        v9Menu.markChanged(s.tenant(),id);
        db.update("""
              INSERT INTO cr9_translation_jobs(menu_item_id,language,state,attempts,error_code)
              SELECT ?, l.lang, 'PENDING',0,'' FROM cr9_menu_settings cfg,
                regexp_split_to_table(cfg.menu_languages, ',') AS l(lang)
              WHERE cfg.restaurant_id=? AND cfg.auto_translate=true AND l.lang<>cfg.source_language
              ON CONFLICT(menu_item_id,language) DO UPDATE SET state='PENDING',attempts=0,error_code='',updated_at=now()""",id,s.tenant());
        return Map.of("id",id);
    }
    @PutMapping("/staff/menu/{id}")
    public Map<String,Object> updateMenu(HttpServletRequest req,@PathVariable long id,@Valid @RequestBody MenuInput m) {
        Staff s=staff(req);manager(s);
        int n=db.update("UPDATE cr2_menu_items SET category=?,name=?,description=?,emoji=?,price_yen=?,active=?,name_ja=?,description_ja=?,name_zh=?,description_zh=?,name_ko=?,description_ko=?,name_tr=?,description_tr=?,image_url=?,image_credit=? WHERE id=? AND restaurant_id=?",
            clean(m.category(),80),clean(m.name(),140),m.description()==null?"":m.description().strip(),
            m.emoji()==null?"🍽":clean(m.emoji(),24),m.priceYen(),m.active(),
            optional(m.nameJa(),140),optional(m.descriptionJa(),300),optional(m.nameZh(),140),optional(m.descriptionZh(),300),optional(m.nameKo(),140),optional(m.descriptionKo(),300),optional(m.nameTr(),140),optional(m.descriptionTr(),300),photoUrl(m.imageUrl()),optional(m.imageCredit(),250),id,s.tenant());
        if(n==0)throw err(HttpStatus.NOT_FOUND,"Menu item not found.");v9Menu.markChanged(s.tenant(),id);
        db.update("""
              INSERT INTO cr9_translation_jobs(menu_item_id,language,state,attempts,error_code)
              SELECT ?, l.lang, 'PENDING',0,'' FROM cr9_menu_settings cfg,
                regexp_split_to_table(cfg.menu_languages, ',') AS l(lang)
              WHERE cfg.restaurant_id=? AND cfg.auto_translate=true AND l.lang<>cfg.source_language
              ON CONFLICT(menu_item_id,language) DO UPDATE SET state='PENDING',attempts=0,error_code='',updated_at=now()""",id,s.tenant());
        return Map.of("updated",true);
    }
    @GetMapping("/staff/tables")
    public List<Map<String,Object>> tables(HttpServletRequest req) {
        Staff s=staff(req);
        return db.queryForList("SELECT t.id,t.code,t.label,t.active,t.qr_token AS \"qrToken\",d.id AS \"openSessionId\" FROM cr2_tables t LEFT JOIN cr2_dining_sessions d ON d.table_id=t.id AND d.status='OPEN' WHERE t.restaurant_id=? ORDER BY t.code",s.tenant());
    }
    @PostMapping("/staff/tables")
    public Map<String,Object> addTable(HttpServletRequest req,@Valid @RequestBody TableInput t) {
        Staff s=staff(req);manager(s);
        String code=clean(t.code(),30).toUpperCase(Locale.ROOT);
        if(!code.matches("[A-Z0-9-]{1,30}"))throw err(HttpStatus.BAD_REQUEST,"Invalid table code.");
        if(!db.queryForList("SELECT id FROM cr2_tables WHERE restaurant_id=? AND code=?",s.tenant(),code).isEmpty())
            throw err(HttpStatus.CONFLICT,"Table code already exists.");
        long id=db.queryForObject("INSERT INTO cr2_tables(restaurant_id,code,label,qr_token) VALUES (?,?,?,?) RETURNING id",
            Long.class,s.tenant(),code,clean(t.label(),80),V2Tokens.random());
        return Map.of("id",id);
    }
    @PostMapping("/staff/tables/{id}/rotate")
    public Map<String,Object> rotate(HttpServletRequest req,@PathVariable long id) {
        Staff s=staff(req);manager(s);
        if(!db.queryForList("SELECT id FROM cr2_dining_sessions WHERE table_id=? AND restaurant_id=? AND status='OPEN'",id,s.tenant()).isEmpty())
            throw err(HttpStatus.CONFLICT,"Close the active dining session before rotating the QR.");
        int n=db.update("UPDATE cr2_tables SET qr_token=? WHERE id=? AND restaurant_id=?",V2Tokens.random(),id,s.tenant());
        if(n==0)throw err(HttpStatus.NOT_FOUND,"Table not found.");return Map.of("rotated",true);
    }
    @GetMapping(value="/staff/tables/{id}/qr.png",produces=MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(HttpServletRequest req,@PathVariable long id) throws Exception {
        Staff s=staff(req);
        var rows=db.queryForList("SELECT qr_token FROM cr2_tables WHERE id=? AND restaurant_id=?",id,s.tenant());
        if(rows.isEmpty())throw err(HttpStatus.NOT_FOUND,"Table not found.");
        String q=(String)rows.get(0).get("qr_token");
        String url=publicUrl+"/guest.html?q="+q;
        BitMatrix bits=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,320,320);
        var img=new java.awt.image.BufferedImage(320,320,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<320;y++)for(int x=0;x<320;x++)img.setRGB(x,y,bits.get(x,y)?0xff111111:0xffffffff);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(img,"png",bytes);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Content-Disposition","inline; filename=table-qr.png").body(bytes.toByteArray());
    }
    @GetMapping("/staff/orders")
    public List<Map<String,Object>> orders(HttpServletRequest req) { return orderService.orders(staff(req).tenant()); }
    @PostMapping("/staff/orders/{id}/status")
    public Map<String,Object> status(HttpServletRequest req,@PathVariable long id,@Valid @RequestBody StatusInput input) {
        return orderService.changeStatus(staff(req).tenant(),id,input.status());
    }
    @PostMapping("/staff/tables/{id}/close-paid")
    public Map<String,Object> close(HttpServletRequest req,@PathVariable long id) {
        Staff s=staff(req); if(s.role().equals("KITCHEN"))throw err(HttpStatus.FORBIDDEN,"Cashier role required.");
        throw err(HttpStatus.GONE,"Use Cashier / POS to record simulated payment and close the tab.");
    }
    @PostMapping("/staff/demo/orders")
    public Map<String,Object> demo(HttpServletRequest req,@Valid @RequestBody DemoInput input) {
        Staff s=staff(req);manager(s);return orderService.placeDemo(s.tenant(),input.tableCode(),input.items());
    }
    @GetMapping("/staff/dashboard")
    public Map<String,Object> dashboard(HttpServletRequest req) {
        Staff s=staff(req);long tenant=s.tenant();
        return Map.of("orders",db.queryForObject("SELECT count(*) FROM cr2_orders WHERE restaurant_id=? AND NOT demo",Long.class,tenant),
            "openTables",db.queryForObject("SELECT count(*) FROM cr2_dining_sessions WHERE restaurant_id=? AND status='OPEN'",Long.class,tenant),
            "liveOrders",db.queryForObject("SELECT count(*) FROM cr2_orders WHERE restaurant_id=? AND status IN ('NEW','PREPARING','READY')",Long.class,tenant),
            "grossOrderYen",db.queryForObject("SELECT COALESCE(sum(total_yen),0) FROM cr2_orders WHERE restaurant_id=? AND NOT demo AND status<>'CANCELLED'",Long.class,tenant),
            "paidRevenueYen",db.queryForObject("SELECT COALESCE(sum(amount_yen),0) FROM cr3_payments WHERE restaurant_id=?",Long.class,tenant));
    }
    @PostMapping("/guest/join")
    public Map<String,Object> join(@Valid @RequestBody JoinRequest r,HttpServletResponse resp) {
        return orderService.join(r.qr(),resp);
    }
    @GetMapping("/guest/current")
    public Map<String,Object> current(HttpServletRequest req) {
        Guest g=guest(req);
        return Map.of("restaurant",g.restaurant(),"tableCode",g.tableCode(),"sessionId",g.diningId());
    }
    @GetMapping("/guest/menu")
    public List<Map<String,Object>> guestMenu(HttpServletRequest req) {
        Guest g=guest(req);
        return db.queryForList("SELECT id,category,name,description,emoji,price_yen AS \"priceYen\",name_ja AS \"nameJa\",description_ja AS \"descriptionJa\",name_zh AS \"nameZh\",description_zh AS \"descriptionZh\",name_ko AS \"nameKo\",description_ko AS \"descriptionKo\",name_tr AS \"nameTr\",description_tr AS \"descriptionTr\",image_url AS \"imageUrl\",image_credit AS \"imageCredit\" FROM cr2_menu_items WHERE restaurant_id=? AND active=true ORDER BY sort_order,id",g.tenant());
    }
    @PostMapping("/guest/orders")
    public Map<String,Object> guestOrder(HttpServletRequest req,@Valid @RequestBody OrderInput input) {
        Guest g=guest(req);return orderService.place(g.tenant(),g.diningId(),g.tableCode(),input.requestId(),input.items());
    }
    @GetMapping("/guest/orders")
    public List<Map<String,Object>> guestOrders(HttpServletRequest req) {
        Guest g=guest(req);return orderService.guestOrders(g.tenant(),g.diningId());
    }
    @GetMapping("/staff/checks")
    public List<Map<String,Object>> checks(HttpServletRequest req) {
        Staff s=staff(req); if(s.role().equals("KITCHEN"))throw err(HttpStatus.FORBIDDEN,"Cashier permission required.");
        return v3.openChecks(s.tenant());
    }
    @GetMapping("/staff/checks/{sessionId}/orders")
    public List<Map<String,Object>> checkOrders(HttpServletRequest req,@PathVariable long sessionId) {
        Staff s=staff(req);if(s.role().equals("KITCHEN"))throw err(HttpStatus.FORBIDDEN,"Cashier permission required.");
        return v3.checkOrders(s.tenant(),sessionId);
    }
    @PostMapping("/staff/tables/{id}/checkout")
    public Map<String,Object> checkout(HttpServletRequest req,@PathVariable long id,
            @RequestBody V3Operations.CheckoutInput input) {
        Staff s=staff(req);if(s.role().equals("KITCHEN"))throw err(HttpStatus.FORBIDDEN,"Cashier permission required.");
        return v3.checkout(s.tenant(),s.id(),id,input);
    }
    @PostMapping("/staff/tables/{id}/close-empty")
    public Map<String,Object> closeEmpty(HttpServletRequest req,@PathVariable long id) {
        Staff s=staff(req);if(s.role().equals("KITCHEN"))throw err(HttpStatus.FORBIDDEN,"Cashier permission required.");
        return v3.closeEmpty(s.tenant(),id);
    }
    @GetMapping("/staff/payments/{id}/receipt")
    public Map<String,Object> receipt(HttpServletRequest req,@PathVariable long id) {
        Staff s=staff(req); if(s.role().equals("KITCHEN"))throw err(HttpStatus.FORBIDDEN,"Cashier permission required.");
        return v3.receipt(s.tenant(),id);
    }
    @GetMapping("/staff/reports")
    public Map<String,Object> reports(HttpServletRequest req,@RequestParam(defaultValue="day") String period) {
        Staff s=staff(req);manager(s);
        return v3.reports(s.tenant(),period);
    }

}
