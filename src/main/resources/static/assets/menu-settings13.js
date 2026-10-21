'use strict';
// JavaScript-only editor for existing /api/v2/v9/staff endpoints; no secrets in browser.
(()=>{
const $=s=>document.querySelector(s),$$=s=>[...document.querySelectorAll(s)];
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let products=[],selected=null,groups=[];
async function api(path,method='GET',data){
  const r=await fetch('/api/v2/v9/staff'+path,{method,credentials:'same-origin',cache:'no-store',
    headers:{'Content-Type':'application/json'},body:data===undefined?undefined:JSON.stringify(data)});
  const j=await r.json().catch(()=>({}));if(!r.ok)throw Error(j.message||j.error||`HTTP ${r.status}`);return j;
}
function notice(s,err=false){const el=$('#toast');el.textContent=s;el.classList.add('show');el.classList.toggle('error',err);clearTimeout(window.__ms13);window.__ms13=setTimeout(()=>el.classList.remove('show'),5000);}
const languageNames={ja:'日本語',en:'英語',zh:'中国語',ko:'韓国語',tr:'トルコ語'};
async function showStatus(){const s=await api('/menu-translation-status');const el=$('#translation-status');
  const rows=s.items.map(x=>`${languageNames[x.language]||x.language}：${({DONE:'翻訳完了',PENDING:'翻訳待ち',FAILED:'翻訳失敗'})[x.state]||x.state} ${x.count} 件`);
  const total=s.items.filter(x=>x.state==='PENDING').reduce((n,x)=>n+Number(x.count),0);
  el.textContent=(s.providerConfigured?'Codigle API：設定済み':'Codigle API：未設定（翻訳は開始されません）')+'\n'+
    (rows.join('\n')||'翻訳ジョブはありません。')+(total?'\n翻訳はバックグラウンドで順次実行されます。':'');
  el.classList.toggle('needs-setup',!s.providerConfigured);
}
async function boot(){try{const auth=await fetch('/api/v2/staff/me',{credentials:'same-origin'});if(!auth.ok){location.href='/portal.html';return;}
  const settings=await api('/menu-settings');$('#source-lang').value='ja';
  $$('#languages-select input').forEach(i=>i.checked=(settings.menuLanguages||'ja').split(',').includes(i.value));
  $('#auto-translate').checked=settings.autoTranslate;
  await loadProducts();await showStatus();setInterval(()=>showStatus().catch(()=>{}),12000);
}catch(e){notice('読み込みに失敗しました：'+e.message,true);}}
async function loadProducts(){products=await api('/menu-full');$('#product').innerHTML='<option value="">商品を選択</option>'+products.map(p=>
 `<option value="${Number(p.id)}">${esc(p.nameJa||p.name)} · ¥${Number(p.priceYen).toLocaleString('ja-JP')}</option>`).join('');}
$('#languages').onsubmit=async e=>{e.preventDefault();const selectedLangs=$$('#languages-select input:checked').map(x=>x.value);
 if(selectedLangs.some(x=>x!=='ja'))$('#auto-translate').checked=true;
 if(!selectedLangs.includes('ja'))selectedLangs.unshift('ja');
 try{await api('/menu-settings','PUT',{sourceLanguage:'ja',menuLanguages:selectedLangs,autoTranslate:$('#auto-translate').checked});
   await showStatus();notice('メニュー言語設定を保存しました。');
 }catch(err){notice('保存できません：'+err.message,true);}};
$('#refresh-translations').onclick=async()=>{if(!confirm('有効な言語について、全メニューとオプションの翻訳をもう一度実行しますか？ 外部APIの利用料金が発生する場合があります。'))return;
 try{const r=await api('/menu-translation-refresh','POST');await showStatus();notice(`${r.queued} 件を翻訳待ちに追加しました。`);}catch(e){notice(e.message,true);}};
$('#product').onchange=()=>{selected=products.find(p=>p.id===Number($('#product').value));$('#product-editor').classList.toggle('hide',!selected);if(!selected)return;
 $('#ingredients').value=selected.ingredientsJa||'';$('#allergens').value=selected.allergensJa||'';$('#preparation').value=selected.preparationJa||'';$('#spice').value=selected.spiceLevel||0;
 groups=(selected.optionGroups||[]).map(g=>({labelJa:g.labelJa,labelEn:g.labelEn,minChoices:g.minChoices,maxChoices:g.maxChoices,
    values:g.values.map(v=>({labelJa:v.labelJa,labelEn:v.labelEn,deltaYen:v.deltaYen}))}));renderGroups();};
$('#save-details').onclick=async()=>{if(!selected)return;try{await api('/menu/'+selected.id+'/details','PUT',
 {ingredientsJa:$('#ingredients').value,allergensJa:$('#allergens').value,preparationJa:$('#preparation').value,spiceLevel:Number($('#spice').value)});
 notice('商品詳細を保存し、翻訳を更新します。');await showStatus();}catch(e){notice(e.message,true);}};
function collect(){ $$('#option-groups > .v9-editor-group').forEach((el,i)=>{const g=groups[i];if(!g)return;
 g.labelJa=el.querySelector('[data-f=ja]').value;g.labelEn=g.labelJa;g.minChoices=Number(el.querySelector('[data-f=min]').value);
 g.maxChoices=Number(el.querySelector('[data-f=max]').value);g.values=[...el.querySelectorAll('.v9-editor-option')].map(v=>({
 labelJa:v.querySelector('[data-v=ja]').value,labelEn:v.querySelector('[data-v=ja]').value,deltaYen:Number(v.querySelector('[data-v=yen]').value)}));});}
function renderGroups(){const c=$('#option-groups');c.innerHTML=groups.map((g,i)=>
 `<div class="v9-editor-group"><div class="v9-flex"><b>選択グループ ${i+1}</b>
 <input data-f="ja" type="text" maxlength="90" aria-label="グループ名" placeholder="例：麺のかたさ" value="${esc(g.labelJa)}">
 <label>最小 <input data-f="min" type="number" min="0" max="8" value="${g.minChoices}" style="width:75px"></label>
 <label>最大 <input data-f="max" type="number" min="1" max="8" value="${g.maxChoices}" style="width:75px"></label>
 <button class="btn outline sm" type="button" data-delete-group="${i}">グループ削除</button></div>
 <div>${g.values.map((v,j)=>`<div class="v9-editor-option cr13-option"><input data-v="ja" maxlength="100" aria-label="選択肢名" placeholder="例：大盛り" value="${esc(v.labelJa)}"><input data-v="yen" type="number" min="-2000" max="10000" aria-label="追加料金・円" value="${v.deltaYen}"><button class="btn outline sm" type="button" data-delval="${i}:${j}">削除</button></div>`).join('')}</div>
 <button class="btn outline sm" type="button" data-addval="${i}">＋ 選択肢を追加</button></div>`).join('');
 $$('[data-delete-group]').forEach(b=>b.onclick=()=>{collect();groups.splice(Number(b.dataset.deleteGroup),1);renderGroups();});
 $$('[data-addval]').forEach(b=>b.onclick=()=>{collect();groups[Number(b.dataset.addval)].values.push({labelJa:'',labelEn:'',deltaYen:0});renderGroups();});
 $$('[data-delval]').forEach(b=>b.onclick=()=>{collect();const [i,j]=b.dataset.delval.split(':').map(Number);groups[i].values.splice(j,1);renderGroups();});}
$('#new-group').onclick=()=>{collect();groups.push({labelJa:'',labelEn:'',minChoices:0,maxChoices:1,values:[{labelJa:'',labelEn:'',deltaYen:0}]});renderGroups();};
$('#save-options').onclick=async()=>{if(!selected)return;collect();try{
 await api('/menu/'+selected.id+'/options','PUT',groups);notice('選択肢を保存し、翻訳を更新します。');
 await loadProducts();$('#product').value=String(selected.id);$('#product').dispatchEvent(new Event('change'));await showStatus();
 }catch(e){notice('保存できません：'+e.message,true);}};
boot();
})();
