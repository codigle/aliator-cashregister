package com.aliator.cashregister.v3;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(2)
public class V3Schema implements CommandLineRunner {
    private final JdbcTemplate db;
    public V3Schema(JdbcTemplate db) { this.db = db; }
    private static final String WIKI = "https://commons.wikimedia.org/wiki/Special:FilePath/";
    private static String photo(String name) {
        return WIKI + java.net.URLEncoder.encode(name,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20") + "?width=640";
    }
    @Override public void run(String... args) {
        String[] ddl = {
            "ALTER TABLE cr2_restaurants ADD COLUMN IF NOT EXISTS timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Tokyo'",
            "ALTER TABLE cr2_menu_items ADD COLUMN IF NOT EXISTS name_ja VARCHAR(140) NOT NULL DEFAULT ''",
            "ALTER TABLE cr2_menu_items ADD COLUMN IF NOT EXISTS description_ja VARCHAR(300) NOT NULL DEFAULT ''",
            "ALTER TABLE cr2_menu_items ADD COLUMN IF NOT EXISTS image_url VARCHAR(550) NOT NULL DEFAULT ''",
            "ALTER TABLE cr2_menu_items ADD COLUMN IF NOT EXISTS image_credit VARCHAR(250) NOT NULL DEFAULT ''",
            "ALTER TABLE cr2_orders ADD COLUMN IF NOT EXISTS preparing_at TIMESTAMPTZ",
            "ALTER TABLE cr2_orders ADD COLUMN IF NOT EXISTS ready_at TIMESTAMPTZ",
            "ALTER TABLE cr2_orders ADD COLUMN IF NOT EXISTS served_at TIMESTAMPTZ",
            "ALTER TABLE cr2_orders ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ",
            "CREATE TABLE IF NOT EXISTS cr3_payments (id BIGSERIAL PRIMARY KEY, restaurant_id BIGINT NOT NULL REFERENCES cr2_restaurants(id), dining_session_id BIGINT NOT NULL UNIQUE REFERENCES cr2_dining_sessions(id), cashier_user_id BIGINT NOT NULL REFERENCES cr2_users(id), method VARCHAR(12) NOT NULL CHECK (method IN ('CASH','CARD','OTHER')), amount_yen INTEGER NOT NULL CHECK (amount_yen > 0), tendered_yen INTEGER, change_yen INTEGER NOT NULL DEFAULT 0, recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(), reference VARCHAR(80) NOT NULL DEFAULT '')",
            "CREATE INDEX IF NOT EXISTS cr3_payments_tenant_date ON cr3_payments(restaurant_id,recorded_at DESC)",
            "CREATE INDEX IF NOT EXISTS cr2_orders_dining_id ON cr2_orders(dining_session_id)"
        };
        for(String sql:ddl) db.execute(sql);
        List<Long> ids = db.query("SELECT id FROM cr2_restaurants WHERE slug='aliator-demo'",(rs,i)->rs.getLong(1));
        if(ids.isEmpty()) return;
        long tenant = ids.get(0);
        // Seed only the demonstration tenant; never touch other restaurants' catalogs.
        // All prices are illustrative demo amounts, not a real restaurant's prices.
        Object[][] foods = {
            {"Starters","Edamame","枝豆",420,"Lightly salted soybeans","塩ゆでした枝豆","🫛","Edamame in Tokyo.jpg","Kelly Drewett · CC BY-SA 4.0"},
            {"Starters","Spicy Edamame","ピリ辛枝豆",480,"Soybeans with chili and garlic","唐辛子とにんにく風味の枝豆","🌶","Edamame in Tokyo.jpg","Kelly Drewett · CC BY-SA 4.0"},
            {"Starters","Tako Wasabi","たこわさび",490,"Octopus with wasabi","たことわさびの珍味","🐙","", ""},
            {"Starters","Chilled Tofu","冷奴",430,"Tofu with ginger and scallions","生姜とねぎを添えた冷奴","🥢","", ""},
            {"Starters","Cucumber with Miso","もろきゅう",450,"Fresh cucumber and miso dip","きゅうりと味噌","🥒","", ""},
            {"Starters","Japanese Pickles","漬物盛り合わせ",480,"Assorted seasonal pickles","季節の漬物盛り合わせ","🥒","", ""},
            {"Yakitori","Chicken Thigh Skewers","もも串",580,"Charcoal-style grilled chicken skewers","鶏もも焼き鳥（二本）","🍢","Yakitori.jpg","Tiia Monto · CC BY-SA 3.0"},
            {"Yakitori","Negima Skewers","ねぎま串",590,"Chicken and green onion skewers","鶏肉とねぎの串焼き（二本）","🍢","Yakitori.jpg","Tiia Monto · CC BY-SA 3.0"},
            {"Yakitori","Tsukune Skewers","つくね串",620,"Seasoned chicken meatball skewers","鶏つくね（二本）","🍢","Yakitori.jpg","Tiia Monto · CC BY-SA 3.0"},
            {"Yakitori","Chicken Skin Skewers","かわ串",480,"Crispy chicken skin","香ばしく焼いた鶏皮（二本）","🍢","Yakitori.jpg","Tiia Monto · CC BY-SA 3.0"},
            {"Yakitori","Shishito Skewers","ししとう串",420,"Grilled shishito peppers","ししとうの串焼き","🌶","Yakitori on grills.jpg","Soulfruit · CC BY 3.0"},
            {"Fried","Chicken Karaage","鶏の唐揚げ",780,"Juicy Japanese fried chicken","ジューシーな鶏の唐揚げ","🍗","Karaage.jpg","RightCowLeftCoast · CC BY-SA 4.0"},
            {"Fried","Takoyaki (6 pcs)","たこ焼き（6個）",650,"Osaka-style octopus balls","大阪名物のたこ焼き","🐙","Takoyaki in Osaka.jpg","ZhengZhou · CC BY-SA 4.0"},
            {"Fried","French Fries","フライドポテト",490,"Crispy potato fries","サクサクのポテト","🍟","", ""},
            {"Fried","Fried Squid","イカリング",690,"Crispy squid rings","サクサクのイカリング","🦑","", ""},
            {"Fried","Agedashi Tofu","揚げ出し豆腐",580,"Fried tofu with dashi broth","出汁香る揚げ出し豆腐","🥢","", ""},
            {"Seafood","Salmon Sashimi","サーモン刺身",980,"Sliced fresh salmon","サーモンのお刺身","🐟","Salmon sashimi detail.jpg", "KyotoKenshu · CC BY-SA 4.0"},
            {"Seafood","Tuna Sashimi","まぐろ刺身",1080,"Sliced tuna sashimi","まぐろのお刺身","🐟","", ""},
            {"Seafood","Assorted Sashimi","刺身盛り合わせ",1680,"Chef's sashimi selection","本日の刺身盛り合わせ","🍣","", ""},
            {"Seafood","Grilled Mackerel","さば塩焼き",780,"Salt-grilled mackerel","脂ののったさばの塩焼き","🐟","", ""},
            {"Seafood","Butter Corn","コーンバター",520,"Sweet corn sautéed with butter","香ばしいコーンバター","🌽","", ""},
            {"Hot Dishes","Osaka Okonomiyaki","大阪お好み焼き",980,"Savory cabbage pancake","大阪風のお好み焼き","🥘","Okonomiyaki osaka.jpg","Retinafunk · CC BY-SA"},
            {"Hot Dishes","Dashimaki Tamago","だし巻き玉子",680,"Japanese rolled omelet","出汁の効いた玉子焼き","🍳","", ""},
            {"Hot Dishes","Pork Ginger","豚の生姜焼き",880,"Pork slices with ginger sauce","豚肉の生姜焼き","🥩","", ""},
            {"Hot Dishes","Gyoza (6 pcs)","焼き餃子（6個）",590,"Pan-fried Japanese dumplings","パリッと焼いた餃子","🥟","", ""},
            {"Hot Dishes","Beef Tataki","牛たたき",1280,"Seared beef with ponzu","牛肉のたたき、ポン酢添え","🥩","", ""},
            {"Rice & Noodles","Yakisoba","焼きそば",830,"Stir-fried noodles with vegetables","野菜たっぷりの焼きそば","🍜","Yakisoba.jpg", "tab2_dawa · CC BY 2.0"},
            {"Rice & Noodles","Ochazuke","お茶漬け",580,"Rice in tea broth","さらっと食べるお茶漬け","🍚","", ""},
            {"Rice & Noodles","Onigiri (2 pcs)","おにぎり（2個）",480,"Two rice balls","おにぎり二個","🍙","", ""},
            {"Rice & Noodles","Chicken Donburi","鶏丼",950,"Chicken over steamed rice","鶏肉の丼もの","🍚","", ""},
            {"Beer","Draft Beer (Medium)","生ビール（中）",650,"Cold draft lager","冷えた生ビール","🍺","", ""},
            {"Beer","Bottled Beer","瓶ビール",780,"Japanese bottled beer","国産瓶ビール","🍺","", ""},
            {"Beer","Non-Alcoholic Beer","ノンアルコールビール",520,"Alcohol-free beer","ノンアルコールビール","🍺","", ""},
            {"Highball & Sour","Whisky Highball","ハイボール",590,"Whisky and soda","ウイスキーソーダ","🥃","", ""},
            {"Highball & Sour","Lemon Sour","レモンサワー",590,"Lemon shochu sour","爽快なレモンサワー","🍋","", ""},
            {"Highball & Sour","Yuzu Sour","ゆずサワー",620,"Yuzu citrus sour","ゆず風味のサワー","🍋","", ""},
            {"Highball & Sour","Umeshu Soda","梅酒ソーダ",620,"Plum wine soda","梅酒ソーダ割り","🍑","", ""},
            {"Sake & Shochu","Sake (Glass)","日本酒（グラス）",750,"Japanese rice sake","日本酒グラス","🍶","Japanese sake glass and flask.jpg", "Culture Japon · CC BY-SA 4.0"},
            {"Sake & Shochu","Shochu (Glass)","焼酎（グラス）",650,"Shochu on rocks or with water","焼酎水割り・ロック","🥃","", ""},
            {"Soft Drinks","Oolong Tea","ウーロン茶",360,"Chilled oolong tea","冷たいウーロン茶","🍵","", ""},
            {"Soft Drinks","Yuzu Soda","ゆずソーダ",480,"Refreshing yuzu soda","爽やかなゆずソーダ","🍋","", ""},
            {"Soft Drinks","Cola","コーラ",380,"Chilled cola","冷えたコーラ","🥤","", ""},
            {"Soft Drinks","Ginger Ale","ジンジャーエール",380,"Sparkling ginger ale","炭酸のジンジャーエール","🥤","", ""},
            {"Dessert","Matcha Ice Cream","抹茶アイス",490,"Matcha green tea ice cream","濃厚な抹茶アイス","🍨","", ""},
            {"Dessert","Mochi Trio","三色もち",530,"Three varieties of mochi","三種のおもち","🍡","Japanese dango.jpg", "Leeyj0401 · CC BY-SA 4.0"}
        };
        int added=0;
        for (int i=0; i<foods.length; i++) {
            var f=foods[i];
            int n=db.update("""
                INSERT INTO cr2_menu_items(restaurant_id,category,name,name_ja,description,description_ja,emoji,price_yen,active,sort_order,image_url,image_credit)
                SELECT ?,?,?,?,?,?,?,?,true,?,?,?
                WHERE NOT EXISTS(SELECT 1 FROM cr2_menu_items WHERE restaurant_id=? AND name=?)
                """,tenant,f[0],f[1],f[2],f[4],f[5],f[6],f[3],100+i,
                f[7].equals("")?"":photo((String)f[7]),f[8],tenant,f[1]);
            added+=n;
        }
        System.out.println("V3_IZAKAYA_SEED_ADDED="+added);
    }
}
