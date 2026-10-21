package com.aliator.cashregister.v9;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Server-computed option prices. Untrusted browser prices are never accepted. */
@Service
public class V9MenuService {
    private final JdbcTemplate db;
    public V9MenuService(JdbcTemplate db){this.db=db;}
    private static long lng(Object n){return ((Number)n).longValue();}
    private static ResponseStatusException bad(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
    public record Resolved(int unitPriceYen,String optionNote) {}
    public Resolved resolve(long tenant,long itemId,int basePrice,List<Long> selected) {
        List<Long> ids=selected==null?List.of():selected;
        if(ids.size()>16||ids.stream().anyMatch(Objects::isNull)||new HashSet<>(ids).size()!=ids.size())throw bad("Invalid menu options");
        var groups=db.queryForList("""
  SELECT id,min_choices,max_choices FROM cr9_option_groups
             WHERE menu_item_id=? AND active=true ORDER BY sort_order,id"""
  ,itemId);
        Map<Long,Integer> chosen=new HashMap<>();int delta=0;List<String> labels=new ArrayList<>();
        for(Long id:ids){
            var values=db.queryForList("""
  SELECT v.group_id,v.delta_yen,v.label_ja
                 FROM cr9_option_values v JOIN cr9_option_groups g ON g.id=v.group_id
                 JOIN cr2_menu_items m ON m.id=g.menu_item_id
                 WHERE v.id=? AND v.active=true AND g.active=true AND m.id=? AND m.restaurant_id=? AND m.active=true"""
  ,id,itemId,tenant);
            if(values.size()!=1)throw bad("An option is unavailable for this product");
            var v=values.get(0);long group=lng(v.get("group_id"));chosen.merge(group,1,Integer::sum);
            delta+=((Number)v.get("delta_yen")).intValue();labels.add((String)v.get("label_ja"));
        }
        for(var g:groups){long id=lng(g.get("id"));int n=chosen.getOrDefault(id,0);
            if(n<((Number)g.get("min_choices")).intValue()||n>((Number)g.get("max_choices")).intValue())
                throw bad("Please complete all required product choices");}
        long total=(long)basePrice+delta;
        if(total<0||total>1_000_000)throw bad("Invalid option price");
        String note=String.join(" / ",labels);
        if(note.length()>155)throw bad("Too many option notes");
        return new Resolved((int)total,note);
    }
    public List<Map<String,Object>> products(long tenant,String lang) {
        String language=normalize(lang);
        var rows=db.queryForList("""
  SELECT m.id,m.category,m.name,m.description,m.name_ja AS "nameJa",
                m.description_ja AS "descriptionJa",m.emoji,m.price_yen AS "priceYen",m.image_url AS "imageUrl",
                m.image_credit AS "imageCredit", COALESCE(t.name,'') AS "translatedName",
                COALESCE(t.description,'') AS "translatedDescription", COALESCE(t.ingredients,'') AS "translatedIngredients",
                COALESCE(t.allergens,'') AS "translatedAllergens",
                COALESCE(t.preparation,'') AS "translatedPreparation",
                COALESCE(d.ingredients_ja,'') AS "ingredientsJa",COALESCE(d.allergens_ja,'') AS "allergensJa",
                COALESCE(d.preparation_ja,'') AS "preparationJa",COALESCE(d.spice_level,0) AS "spiceLevel"
                FROM cr2_menu_items m LEFT JOIN cr9_menu_details d ON d.menu_item_id=m.id
                LEFT JOIN cr9_translations t ON t.menu_item_id=m.id AND t.language=?
                   AND ( ?='ja' OR EXISTS (SELECT 1 FROM cr9_translation_jobs tj
                         WHERE tj.menu_item_id=m.id AND tj.language=? AND tj.state='DONE'))
                WHERE m.restaurant_id=? AND m.active=true ORDER BY m.sort_order,m.id"""
  ,language,language,language,tenant);
        for(var m:rows){
            long id=lng(m.get("id"));
            var gs=db.queryForList("""
  SELECT g.id,g.label_ja AS "labelJa",g.label_en AS "labelEn",
                 COALESCE(gi.label,'') AS "translatedLabel",
                 g.min_choices AS "minChoices",g.max_choices AS "maxChoices"
          FROM cr9_option_groups g
          LEFT JOIN cr13_group_i18n gi ON gi.group_id=g.id AND gi.language=?
             AND EXISTS(SELECT 1 FROM cr9_translation_jobs tj WHERE tj.menu_item_id=? AND tj.language=? AND tj.state='DONE')
          WHERE g.menu_item_id=? AND g.active=true ORDER BY g.sort_order,g.id"""
  ,language,id,language,id);
            for(var g:gs)g.put("values",db.queryForList("""
  SELECT v.id,v.label_ja AS "labelJa",v.label_en AS "labelEn",
                 COALESCE(vi.label,'') AS "translatedLabel",v.delta_yen AS "deltaYen"
          FROM cr9_option_values v LEFT JOIN cr13_value_i18n vi ON vi.value_id=v.id AND vi.language=?
             AND EXISTS(SELECT 1 FROM cr9_translation_jobs tj WHERE tj.menu_item_id=? AND tj.language=? AND tj.state='DONE')
          WHERE v.group_id=? AND v.active=true ORDER BY v.sort_order,v.id"""
  ,language,id,language,lng(g.get("id"))));
            m.put("optionGroups",gs);
        }
        return rows;
    }
    public static String normalize(String s){String l=s==null?"ja":s.trim().toLowerCase(Locale.ROOT);
        if(!Set.of("ja","en","zh","ko","tr").contains(l))throw bad("Unsupported language");return l;}
    public Map<String,Object> settings(long tenant){
        var a=db.queryForList("SELECT source_language AS \"sourceLanguage\",menu_languages AS \"menuLanguages\",auto_translate AS \"autoTranslate\" FROM cr9_menu_settings WHERE restaurant_id=?",tenant);
        return a.isEmpty()?Map.of("sourceLanguage","ja","menuLanguages","ja","autoTranslate",false):a.get(0);
    }
    @Transactional
    public Map<String,Object> configure(long tenant,String source,List<String> languages,boolean auto){
        String src=normalize(source);
        if(!"ja".equals(src))throw bad("This Japanese-first menu currently requires Japanese source text");
        if(languages==null||languages.isEmpty()||languages.size()>5)throw bad("Choose 1–5 menu languages");
        LinkedHashSet<String> all=new LinkedHashSet<>();all.add(src);
        for(String x:languages)all.add(normalize(x));
        String enabled=String.join(",",all);
        var before=settings(tenant);boolean mustTranslate=auto||all.stream().anyMatch(x->!"ja".equals(x));
        db.update("""
  INSERT INTO cr9_menu_settings(restaurant_id,source_language,menu_languages,auto_translate)
          VALUES(?,?,?,?) ON CONFLICT(restaurant_id) DO UPDATE SET source_language=excluded.source_language,
          menu_languages=excluded.menu_languages,auto_translate=excluded.auto_translate,updated_at=now()"""
  ,tenant,src,enabled,mustTranslate);
        if(mustTranslate && (!Boolean.TRUE.equals(before.get("autoTranslate")) || !enabled.equals(before.get("menuLanguages"))))
            requeue(tenant,src,all);
        return settings(tenant);
    }
    @Transactional
    public int requeue(long tenant,String src,Set<String> enabled){
        int n=0;
        for(String l:enabled){if(l.equals(src))continue;
            n+=db.update("""
  INSERT INTO cr9_translation_jobs(menu_item_id,language,state,attempts,error_code)
              SELECT id,?,'PENDING',0,'' FROM cr2_menu_items WHERE restaurant_id=?
              ON CONFLICT(menu_item_id,language) DO UPDATE SET state='PENDING',attempts=0,error_code='',updated_at=now()"""
  ,l,tenant);
        }return n;
    }
    @Transactional
    public void markChanged(long tenant,long itemId){
        var cfg=settings(tenant);
        String src=(String)cfg.get("sourceLanguage");
        // Edited source is authoritative; never leave an obsolete source-language translation.
        db.update("DELETE FROM cr9_translations WHERE menu_item_id=? AND language=?",itemId,src);
        if(!Boolean.TRUE.equals(cfg.get("autoTranslate")))return;
        for(String l:((String)cfg.get("menuLanguages")).split(","))if(!l.equals(src))
            db.update("""
  INSERT INTO cr9_translation_jobs(menu_item_id,language,state,attempts,error_code)
               VALUES(?,?,'PENDING',0,'') ON CONFLICT(menu_item_id,language) DO UPDATE SET
               state='PENDING',attempts=0,error_code='',updated_at=now()"""
  ,itemId,l);
    }
    public Map<String,Object> translationStatus(long tenant){
        var rows=db.queryForList("""
  SELECT j.language,j.state,count(*) AS count FROM cr9_translation_jobs j
          JOIN cr2_menu_items m ON m.id=j.menu_item_id WHERE m.restaurant_id=? GROUP BY j.language,j.state
          ORDER BY j.language,j.state"""
  ,tenant);
        return Map.of("items",rows,"providerConfigured",
          System.getenv("CODIGLE_TRANSLATION_URL")!=null && !System.getenv("CODIGLE_TRANSLATION_URL").isBlank()
          && System.getenv("CODIGLE_TRANSLATION_TOKEN")!=null && !System.getenv("CODIGLE_TRANSLATION_TOKEN").isBlank());
    }
}
