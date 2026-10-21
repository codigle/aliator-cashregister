package com.aliator.cashregister.v9;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** CashRegister V13: translate a complete Japanese product and its choices as one publication.
 *  Codigle adapter contract: POST {text,source_language,target_language},
 *  Authorization: Bearer <server secret>; expects {translated_text:"..."}.
 *  An API URL and token must be configured and tested before a translation is considered completed.
 *  No payment, customer or receipt data is sent to this service.
 */
@Component
@EnableScheduling
public class V9TranslationWorker {
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public V9TranslationWorker(JdbcTemplate db,ObjectMapper mapper,PlatformTransactionManager tx){
        this.db=db;this.mapper=mapper;this.transaction=new TransactionTemplate(tx);
    }
    private record Field(String kind,long id,String source){}
    private record Output(String kind,long id,String translated){}
    private static String string(Object s){return s==null?"":s.toString().strip();}
    private static long number(Object n){return ((Number)n).longValue();}
    private static String env(String k){return Optional.ofNullable(System.getenv(k)).orElse("").strip();}
    private static URI endpoint(){
        URI u=URI.create(env("CODIGLE_TRANSLATION_URL"));
        boolean local="http".equalsIgnoreCase(u.getScheme()) && Set.of("127.0.0.1","localhost").contains(u.getHost());
        if(u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null||!(local||"https".equalsIgnoreCase(u.getScheme())))
            throw new IllegalArgumentException("Invalid translation endpoint");
        return u;
    }
    @Scheduled(fixedDelay=12000,initialDelay=30000)
    public void work(){
        if(env("CODIGLE_TRANSLATION_URL").isEmpty()||env("CODIGLE_TRANSLATION_TOKEN").isEmpty())return;
        final URI uri;
        try{uri=endpoint();}catch(Exception e){System.err.println("V13_TRANSLATOR_CONFIG=INVALID_URL");return;}
        var jobs=db.queryForList("""
          SELECT j.menu_item_id,j.language,m.name_ja,m.description_ja,
                 COALESCE(d.ingredients_ja,'') ingredients_ja,COALESCE(d.allergens_ja,'') allergens_ja,
                 COALESCE(d.preparation_ja,'') preparation_ja
          FROM cr9_translation_jobs j JOIN cr2_menu_items m ON m.id=j.menu_item_id
          JOIN cr9_menu_settings s ON s.restaurant_id=m.restaurant_id
          LEFT JOIN cr9_menu_details d ON d.menu_item_id=m.id
          WHERE j.state='PENDING' AND s.auto_translate=true AND s.source_language='ja'
            AND j.language<>s.source_language AND j.attempts<3
            AND position(j.language IN s.menu_languages)>0
          ORDER BY j.updated_at, j.menu_item_id LIMIT 1""");
        for(var menu:jobs){
            long item=number(menu.get("menu_item_id"));String lang=string(menu.get("language"));
            try{
                List<Field> fields=new ArrayList<>();
                for(String kind:List.of("name_ja","description_ja","ingredients_ja","allergens_ja","preparation_ja")){
                    String value=string(menu.get(kind));
                    if(kind.equals("name_ja") && value.isBlank())throw new IllegalStateException("JAPANESE_NAME_MISSING");
                    fields.add(new Field(kind,item,value));
                }
                var groups=db.queryForList("""
                  SELECT id,label_ja FROM cr9_option_groups WHERE menu_item_id=? AND active=true ORDER BY sort_order,id""",item);
                for(var g:groups){long gid=number(g.get("id"));fields.add(new Field("group",gid,string(g.get("label_ja"))));
                    var values=db.queryForList("""
                      SELECT id,label_ja FROM cr9_option_values WHERE group_id=? AND active=true ORDER BY sort_order,id""",gid);
                    for(var v:values)fields.add(new Field("value",number(v.get("id")),string(v.get("label_ja"))));
                }
                List<Output> outs=new ArrayList<>();
                Map<String,String> cache=new HashMap<>();
                for(var f:fields){
                    String translated=f.source().isBlank()?"":cache.computeIfAbsent(f.source(),s->{
                      try {return translate(uri,lang,s);} catch(Exception e){throw new IllegalStateException(e);} });
                    outs.add(new Output(f.kind(),f.id(),translated));
                }
                transaction.execute(status->{
                    Map<String,String> p=new HashMap<>();
                    for(var out:outs){
                        if(out.kind().equals("group"))db.update("""
                           INSERT INTO cr13_group_i18n(group_id,language,label) VALUES(?,?,?)
                           ON CONFLICT(group_id,language) DO UPDATE SET label=excluded.label""",out.id(),lang,out.translated());
                        else if(out.kind().equals("value"))db.update("""
                           INSERT INTO cr13_value_i18n(value_id,language,label) VALUES(?,?,?)
                           ON CONFLICT(value_id,language) DO UPDATE SET label=excluded.label""",out.id(),lang,out.translated());
                        else p.put(out.kind(),out.translated());
                    }
                    db.update("""
                       INSERT INTO cr9_translations(menu_item_id,language,name,description,ingredients,allergens,preparation)
                       VALUES(?,?,?,?,?,?,?) ON CONFLICT(menu_item_id,language) DO UPDATE SET
                       name=excluded.name, description=excluded.description,ingredients=excluded.ingredients,
                       allergens=excluded.allergens,preparation=excluded.preparation,translated_at=now()""",
                       item,lang,p.get("name_ja"),p.get("description_ja"),p.get("ingredients_ja"),p.get("allergens_ja"),p.get("preparation_ja"));
                    db.update("UPDATE cr9_translation_jobs SET state='DONE',attempts=0,error_code='',updated_at=now() WHERE menu_item_id=? AND language=?",item,lang);
                    return null;
                });
                System.out.println("V13_TRANSLATION_DONE=YES ITEM="+item+" LANGUAGE="+lang);
            }catch(Exception e){
                String code=e.getCause()!=null?e.getCause().getClass().getSimpleName():e.getClass().getSimpleName();
                db.update("UPDATE cr9_translation_jobs SET state='FAILED', attempts=attempts+1,error_code=?,updated_at=now() WHERE menu_item_id=? AND language=?",code,item,lang);
                System.err.println("V13_TRANSLATION_FAILED="+code+" ITEM="+item+" LANGUAGE="+lang);
            }
        }
    }
    private String translate(URI url,String language,String value)throws Exception{
        if(value.length()>750)throw new IllegalArgumentException("Source too long");
        String body=mapper.writeValueAsString(Map.of("text",value,"source_language","ja","target_language",language));
        HttpRequest req=HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(15))
           .header("Authorization","Bearer "+env("CODIGLE_TRANSLATION_TOKEN"))
           .header("Content-Type","application/json")
           .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
        HttpResponse<String> res=client.send(req,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(res.statusCode()!=200)throw new IllegalStateException("HTTP_"+res.statusCode());
        JsonNode root=mapper.readTree(res.body());
        JsonNode field=root.path("translated_text");
        if(!field.isTextual()||field.asText().isBlank()||field.asText().length()>900)
          throw new IllegalStateException("TRANSLATION_RESPONSE_MISMATCH");
        return field.asText().strip();
    }
}
