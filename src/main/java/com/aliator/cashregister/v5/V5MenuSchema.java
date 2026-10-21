package com.aliator.cashregister.v5;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(5)
public class V5MenuSchema implements CommandLineRunner {
 private final JdbcTemplate db;
 public V5MenuSchema(JdbcTemplate db) { this.db=db; }
 @Override public void run(String... args) {
  for(String lang:new String[]{"zh","ko","tr"}){
   db.execute("ALTER TABLE cr2_menu_items ADD COLUMN IF NOT EXISTS name_"+lang+" VARCHAR(140) NOT NULL DEFAULT ''");
   db.execute("ALTER TABLE cr2_menu_items ADD COLUMN IF NOT EXISTS description_"+lang+" VARCHAR(300) NOT NULL DEFAULT ''");
  }
  // Seed translations only for demo catalog, never overwrite merchant-edited values.
  Object[][] demo = {
   {"Signature Ramen","招牌拉面","시그니처 라멘","Özel Ramen"},
   {"Chicken Katsu Curry","炸鸡排咖喱","치킨 카츠 카레","Tavuk Katsu Köri"},
   {"Teriyaki Donburi","照烧鸡肉饭","데리야키 덮밥","Teriyaki Donburi"},
   {"Gyoza (6 pcs)","煎饺（6个）","교자 (6개)","Gyoza (6 adet)"},
   {"Karaage","日式炸鸡","가라아게","Japon Usulü Kızarmış Tavuk"},
   {"Matcha Latte","抹茶拿铁","말차 라테","Matcha Latte"},
   {"Edamame","毛豆","에다마메","Edamame"},
   {"Spicy Edamame","辣味毛豆","매콤한 에다마메","Acılı Edamame"},
   {"Tako Wasabi","芥末章鱼","타코와사비","Wasabili Ahtapot"},
   {"Chilled Tofu","冷豆腐","히야얏코","Soğuk Tofu"},
   {"Cucumber with Miso","味噌黄瓜","미소 오이","Misolu Salatalık"},
   {"Japanese Pickles","日式腌菜拼盘","일본식 절임 모둠","Japon Turşu Tabağı"},
   {"Chicken Thigh Skewers","鸡腿肉串","닭다리살 꼬치","Tavuk But Şiş"},
   {"Negima Skewers","鸡葱串","네기마 꼬치","Soğanlı Tavuk Şiş"},
   {"Tsukune Skewers","鸡肉丸串","츠쿠네 꼬치","Tavuk Köfte Şiş"},
   {"Chicken Skin Skewers","鸡皮串","닭껍질 꼬치","Tavuk Derisi Şiş"},
   {"Shishito Skewers","烤青椒串","시시토 꼬치","Biber Şiş"},
   {"Chicken Karaage","日式炸鸡","가라아게","Japon Usulü Kızarmış Tavuk"},
   {"Takoyaki (6 pcs)","章鱼烧（6个）","타코야키 (6개)","Takoyaki (6 adet)"},
   {"French Fries","炸薯条","감자튀김","Patates Kızartması"},
   {"Fried Squid","炸鱿鱼圈","오징어 링 튀김","Kalamar Tava"},
   {"Agedashi Tofu","炸豆腐佐高汤","아게다시 두부","Dashi Soslu Kızarmış Tofu"},
   {"Salmon Sashimi","三文鱼刺身","연어 사시미","Somon Sashimi"},
   {"Tuna Sashimi","金枪鱼刺身","참치 사시미","Ton Balığı Sashimi"},
   {"Assorted Sashimi","刺身拼盘","모둠 사시미","Karışık Sashimi"},
   {"Grilled Mackerel","盐烤青花鱼","고등어 소금구이","Izgara Uskumru"},
   {"Butter Corn","黄油玉米","버터 옥수수","Tereyağlı Mısır"},
   {"Osaka Okonomiyaki","大阪御好烧","오사카 오코노미야키","Osaka Okonomiyaki"},
   {"Dashimaki Tamago","日式高汤玉子烧","다시마키 타마고","Dashi Omlet"},
   {"Pork Ginger","姜汁猪肉","돼지고기 생강구이","Zencefilli Domuz Eti"},
   {"Gyoza (6 pcs)","煎饺（6个）","군만두 (6개)","Gyoza (6 adet)"},
   {"Beef Tataki","炙烤牛肉","소고기 타타키","Dana Tataki"},
   {"Yakisoba","日式炒面","야키소바","Yakisoba"},
   {"Ochazuke","茶泡饭","오차즈케","Ochazuke"},
   {"Onigiri (2 pcs)","饭团（2个）","주먹밥 (2개)","Onigiri (2 adet)"},
   {"Chicken Donburi","鸡肉盖饭","닭고기 덮밥","Tavuklu Donburi"},
   {"Draft Beer (Medium)","生啤（中杯）","생맥주 (중)","Fıçı Bira (Orta)"},
   {"Bottled Beer","瓶装啤酒","병맥주","Şişe Bira"},
   {"Non-Alcoholic Beer","无酒精啤酒","무알코올 맥주","Alkolsüz Bira"},
   {"Whisky Highball","威士忌高球","위스키 하이볼","Viski Highball"},
   {"Lemon Sour","柠檬沙瓦","레몬 사워","Limon Sour"},
   {"Yuzu Sour","柚子沙瓦","유자 사워","Yuzu Sour"},
   {"Umeshu Soda","梅酒苏打","매실주 소다","Umeshu Soda"},
   {"Sake (Glass)","日本清酒（杯）","사케 (잔)","Sake (Kadeh)"},
   {"Shochu (Glass)","烧酒（杯）","쇼추 (잔)","Shochu (Kadeh)"},
   {"Oolong Tea","乌龙茶","우롱차","Oolong Çayı"},
   {"Yuzu Soda","柚子汽水","유자 소다","Yuzu Soda"},
   {"Cola","可乐","콜라","Kola"},
   {"Ginger Ale","姜汁汽水","진저에일","Ginger Ale"},
   {"Matcha Ice Cream","抹茶冰淇淋","말차 아이스크림","Matcha Dondurma"},
   {"Mochi Trio","三色麻糬","삼색 모찌","Üçlü Mochi"}
  };
  for(Object[] row:demo) db.update("""
    UPDATE cr2_menu_items AS m SET
      name_zh=CASE WHEN m.name_zh='' THEN ? ELSE m.name_zh END,
      name_ko=CASE WHEN m.name_ko='' THEN ? ELSE m.name_ko END,
      name_tr=CASE WHEN m.name_tr='' THEN ? ELSE m.name_tr END
    FROM cr2_restaurants r
    WHERE m.restaurant_id=r.id AND r.slug='aliator-demo' AND m.name=?
    """,row[1],row[2],row[3],row[0]);
 }
}
