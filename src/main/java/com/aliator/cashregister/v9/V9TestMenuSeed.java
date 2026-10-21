package com.aliator.cashregister.v9;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Opt-in seed for isolated TEST tenant. No production tenants or original item prices are rewritten. */
@Component
@Order(1050)
public class V9TestMenuSeed implements CommandLineRunner {
    private final JdbcTemplate db;
    public V9TestMenuSeed(JdbcTemplate db){this.db=db;}
    private long add(long item,String ja,String en,int min,int max,String[][] choices){
        long gid=db.queryForObject("INSERT INTO cr9_option_groups(menu_item_id,label_ja,label_en,min_choices,max_choices) VALUES(?,?,?,?,?) RETURNING id",
          Long.class,item,ja,en,min,max);
        int order=0;for(String[] c:choices)db.update("INSERT INTO cr9_option_values(group_id,label_ja,label_en,delta_yen,sort_order) VALUES(?,?,?,?,?)",
          gid,c[0],c[1],Integer.parseInt(c[2]),order++);return gid;
    }
    private static String[][] a(String... vals){String[][] r=new String[vals.length/3][3];for(int i=0;i<r.length;i++)r[i]=new String[]{vals[3*i],vals[3*i+1],vals[3*i+2]};return r;}
    @Override @Transactional public void run(String... args){
        // Feature must be opted in on the VPS. Startup never silently changes an arbitrary TEST shop.
        if(!"true".equalsIgnoreCase(System.getenv("CASHREGISTER_V9_SEED_TEST"))){System.out.println("V9_TEST_MENU_SEED=SKIPPED");return;}
        String seedEmail = Optional.ofNullable(System.getenv("CASHREGISTER_TEST_SEED_EMAIL"))
            .orElse("").strip().toLowerCase(Locale.ROOT);
        if(seedEmail.isBlank() || !seedEmail.contains("@")) {
            System.out.println("V9_TEST_MENU_SEED=TARGET_EMAIL_NOT_CONFIGURED");
            return;
        }
        var tenants=db.queryForList("""
  SELECT DISTINCT r.id,r.name FROM cr2_users u
          JOIN cr2_restaurants r ON r.id=u.restaurant_id WHERE lower(u.email)=?""",
          seedEmail
  );
        if(tenants.size()!=1||!"TEST".equalsIgnoreCase((String)tenants.get(0).get("name"))) {
            System.out.println("V9_TEST_MENU_SEED=TARGET_NOT_MATCHED");return;
        }
        long tenant=((Number)tenants.get(0).get("id")).longValue();
        String[][] ramens={
          {"Shoyu Ramen","醤油ラーメン","Chicken-and-soy broth, wheat noodles, chashu, scallions, nori","鶏ガラと醤油のスープ、中華麺、チャーシュー、ねぎ、のり","1080"},
          {"Shio Ramen","塩ラーメン","Clear salt-seasoned broth, wheat noodles, chicken and scallions","塩だれの澄んだスープ、中華麺、鶏肉、ねぎ","1050"},
          {"Miso Ramen","味噌ラーメン","Rich miso broth, wheat noodles, corn, bean sprouts, chashu","味噌スープ、中華麺、コーン、もやし、チャーシュー","1180"},
          {"Spicy Miso Ramen","辛味噌ラーメン","Hot miso broth, wheat noodles, sesame, egg and vegetables","辛味噌スープ、中華麺、ごま、卵、野菜","1250"},
          {"Tonkotsu Ramen","豚骨ラーメン","Pork-bone broth, wheat noodles, chashu, scallions, kikurage","豚骨スープ、中華麺、チャーシュー、ねぎ、きくらげ","1280"}
        };
        for(String[] r:ramens)db.update("""
  INSERT INTO cr2_menu_items(restaurant_id,category,name,name_ja,description,description_ja,
           emoji,price_yen,active,sort_order) SELECT ?,'Rice & Noodles',?,?,?,?,'🍜',?,true,280
           WHERE NOT EXISTS (SELECT 1 FROM cr2_menu_items WHERE restaurant_id=? AND name=?)"""
  ,
           tenant,r[0],r[1],r[2],r[3],Integer.parseInt(r[4]),tenant,r[0]);
        var items=db.queryForList("""
  SELECT id,category,name,name_ja,description,description_ja
           FROM cr2_menu_items WHERE restaurant_id=? ORDER BY id"""
  ,tenant);
        int enriched=0;
        for(var p:items){
            long id=((Number)p.get("id")).longValue();String cat=(String)p.get("category");String name=(String)p.get("name");
            boolean ramen=name.toLowerCase(Locale.ROOT).contains("ramen");
            String ingredient=ingredient(name,cat);String allergy="原材料・アレルギー情報は店舗スタッフに要確認（デモ用の参考情報）";
            String prep=(String)p.get("description_ja");if(prep==null||prep.isBlank())prep="調理方法・具材についてはスタッフにご確認ください。";
            db.update("""
  INSERT INTO cr9_menu_details(menu_item_id,ingredients_ja,allergens_ja,preparation_ja,spice_level)
              VALUES(?,?,?,?,?) ON CONFLICT(menu_item_id) DO NOTHING"""
  ,id,ingredient,allergy,prep,
              name.toLowerCase(Locale.ROOT).contains("spicy")?3:0);
            db.update("""
  INSERT INTO cr9_translations(menu_item_id,language,name,description,ingredients,allergens)
              VALUES(?,'en',?,?,?,?) ON CONFLICT(menu_item_id,language) DO NOTHING"""
  ,
              id,name,nonNull(p.get("description")),"Illustrative ingredients; please verify with restaurant.","Ingredient/allergen list is illustrative. Please confirm with the restaurant.");
            if(!nonNull(p.get("name_ja")).isEmpty())db.update("""
  INSERT INTO cr9_translations(menu_item_id,language,name,description,ingredients,allergens)
              VALUES(?,'ja',?,?,?,?) ON CONFLICT(menu_item_id,language) DO NOTHING"""
  ,
              id,p.get("name_ja"),nonNull(p.get("description_ja")),ingredient,allergy);
            Integer groups=db.queryForObject("SELECT count(*) FROM cr9_option_groups WHERE menu_item_id=?",Integer.class,id);
            if(groups!=null && groups>0)continue;
            if(ramen){
                add(id,"麺のかたさ","Noodle firmness",1,1,a("かため","Firm","0","ふつう","Normal","0","やわらかめ","Soft","0"));
                add(id,"麺の量","Noodle portion",1,1,a("普通","Regular","0","大盛り","Large","180"));
                add(id,"スープの濃さ","Broth richness",1,1,a("あっさり","Light","0","ふつう","Normal","0","こってり","Rich","0"));
                add(id,"追加トッピング","Extra toppings",0,5,a("味玉","Marinated egg","160","青ねぎ","Extra scallions","90","玉ねぎ","Onion","80","チャーシュー","Chashu pork","260","のり","Nori seaweed","80","替え玉","Extra noodles","200"));
                add(id,"セット","Meal set",0,1,a("セットなし","No set","0","餃子3個セット","3 gyoza set","320","ミニチャーハンセット","Mini fried rice set","380"));
            }else if(Set.of("Beer","Highball & Sour","Sake & Shochu","Soft Drinks").contains(cat)){
                if(!name.contains("Bottled"))add(id,"サイズ","Drink size",1,1,a("S","Small","-80","M","Medium","0","L","Large","180"));
                if(cat.equals("Soft Drinks"))add(id,"氷","Ice",1,1,a("氷あり","With ice","0","氷なし","No ice","0"));
                if(cat.equals("Highball & Sour"))add(id,"濃さ","Strength",1,1,a("通常","Regular","0","濃いめ","Strong","180"));
            }else if(cat.equals("Rice & Noodles")||cat.equals("Hot Dishes")){
                add(id,"量","Portion",1,1,a("普通","Regular","0","大盛り","Large","180"));
                add(id,"追加","Add-ons",0,3,a("温泉玉子","Soft-boiled egg","160","ねぎ","Scallions","90","ご飯セット","Rice set","180"));
            }else if(cat.equals("Yakitori")){
                add(id,"味付け","Seasoning",1,1,a("塩","Salt","0","たれ","Sweet soy glaze","0"));
                add(id,"追加","Extras",0,2,a("七味","Chili flakes","0","マヨネーズ","Mayonnaise","60"));
            }else if(cat.equals("Fried")){
                add(id,"ソース","Sauce",0,2,a("マヨネーズ","Mayonnaise","60","レモン","Lemon","0","ピリ辛ソース","Spicy sauce","60"));
            }else if(cat.equals("Seafood")){
                add(id,"追加","Side additions",0,2,a("わさび追加","Extra wasabi","0","ご飯セット","Rice set","180"));
            }else if(cat.equals("Dessert")){
                add(id,"トッピング","Toppings",0,2,a("黒みつ","Kuromitsu syrup","80","あんこ","Sweet red bean","100"));
            }
            enriched++;
        }
        db.update("""
  INSERT INTO cr9_menu_settings(restaurant_id,source_language,menu_languages,auto_translate)
          VALUES(?,'ja','ja,en,zh,ko,tr',false) ON CONFLICT(restaurant_id) DO NOTHING"""
  ,tenant);
        System.out.println("V9_TEST_MENU_TENANT="+tenant+" V9_PRODUCTS="+items.size()+" V9_PRODUCTS_OPTIONS_CREATED="+enriched);
    }
    private static String nonNull(Object o){return o==null?"":o.toString();}
    private static String ingredient(String name,String category){
        if(name.toLowerCase(Locale.ROOT).contains("ramen"))return "参考例：中華麺（小麦）、スープ、ねぎ、具材。具体的な材料は店舗に要確認";
        if(name.contains("Gyoza"))return "参考例：小麦粉の皮、肉、キャベツ、にら、にんにく";
        if(name.contains("Karaage"))return "参考例：鶏肉、醤油、生姜、にんにく、衣";
        if(name.contains("Yakisoba"))return "参考例：小麦麺、キャベツ、野菜、ソース";
        if(name.contains("Onigiri"))return "参考例：米、塩、具材（種類は要確認）";
        if(name.contains("Takoyaki"))return "参考例：小麦粉、たこ、だし、ソース";
        if(name.contains("Matcha"))return "参考例：抹茶、乳製品（商品による）";
        if(category.equals("Yakitori"))return "参考例：鶏肉または野菜、塩またはたれ";
        if(category.equals("Seafood"))return "参考例：魚介類、薬味、醤油";
        if(category.equals("Beer")||category.equals("Highball & Sour")||category.equals("Sake & Shochu"))return "飲料原材料・アルコールの有無は商品ラベルに従い要確認";
        if(category.equals("Soft Drinks"))return "清涼飲料。商品ごとの原材料表示をご確認ください";
        if(category.equals("Dessert"))return "甘味・乳成分・小麦・大豆等の使用有無は店舗に要確認";
        return "参考レシピです。実際の原材料・アレルゲンは店舗に要確認";
    }
}
