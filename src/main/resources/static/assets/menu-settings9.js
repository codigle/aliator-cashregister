'use strict';
const $=s=>document.querySelector(s),$$=s=>[...document.querySelectorAll(s)];
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]);
let products=[],current=null,groups=[];
async function api(path,method='GET',data){const r=await fetch('/api/v2/v9/staff'+path,{method,credentials:'same-origin',cache:'no-store',headers:{'Content-Type':'application/json'},body:data?JSON.stringify(data):undefined});const j=await r.json().catch(()=>({}));if(!r.ok)throw Error(j.message||j.error||`HTTP ${r.status}`);return j;}
function toast(s){$('#toast').textContent=s;$('#toast').classList.add('show');setTimeout(()=>$('#toast').classList.remove('show'),4000);}
async function status(){let s=await api('/menu-translation-status');$('#translation-status').textContent=(s.providerConfigured?'Codigle adapter URL configured':'Codigle adapter NOT configured; automatic translations paused')+'\n'+s.items.map(x=>`${x.language} · ${x.state}: ${x.count}`).join('\n');}
async function boot(){try{const me=await fetch('/api/v2/staff/me',{credentials:'same-origin'});if(!me.ok){location.href='/portal.html';return;}
 const settings=await api('/menu-settings');$('#source-lang').value=settings.sourceLanguage;
 $$('#languages-select input').forEach(x=>x.checked=settings.menuLanguages.split(',').includes(x.value));$('#auto-translate').checked=settings.autoTranslate;
 products=await api('/menu-full');$('#product').innerHTML='<option value="">Select a product</option>'+products.map(p=>`<option value="${p.id}">${esc(p.nameJa||p.name)} · ¥${p.priceYen}</option>`).join('');await status();
 }catch(e){toast(e.message)}}
$('#languages').onsubmit=async e=>{e.preventDefault();const sourceLanguage=$('#source-lang').value;
 const menuLanguages=$$('#languages-select input:checked').map(x=>x.value);if(!menuLanguages.includes(sourceLanguage))menuLanguages.push(sourceLanguage);
 try{await api('/menu-settings','PUT',{sourceLanguage,menuLanguages,autoTranslate:$('#auto-translate').checked});await status();toast('Language configuration saved');}catch(e){toast(e.message)}};
$('#refresh-translations').onclick=async()=>{if(!confirm('Queue translation of all menu items in the selected languages? Translation costs may apply.'))return;try{const r=await api('/menu-translation-refresh','POST');await status();toast(r.queued+' translation jobs queued');}catch(e){toast(e.message)}};
$('#product').onchange=()=>{current=products.find(p=>p.id===Number($('#product').value));$('#product-editor').classList.toggle('hide',!current);if(!current)return;
 $('#ingredients').value=current.ingredientsJa||'';$('#allergens').value=current.allergensJa||'';$('#preparation').value=current.preparationJa||'';$('#spice').value=current.spiceLevel||0;
 groups=(current.optionGroups||[]).map(g=>({labelJa:g.labelJa,labelEn:g.labelEn,minChoices:g.minChoices,maxChoices:g.maxChoices,values:g.values.map(v=>({labelJa:v.labelJa,labelEn:v.labelEn,deltaYen:v.deltaYen}))}));renderGroups();};
$('#save-details').onclick=async()=>{if(!current)return;try{await api('/menu/'+current.id+'/details','PUT',{ingredientsJa:$('#ingredients').value,allergensJa:$('#allergens').value,preparationJa:$('#preparation').value,spiceLevel:Number($('#spice').value)});toast('Product details saved');}catch(e){toast(e.message)}};
function collect(){ $$('#option-groups > .v9-editor-group').forEach((el,i)=>{if(!groups[i])return;
 groups[i].labelJa=el.querySelector('[data-f=ja]').value;groups[i].labelEn=el.querySelector('[data-f=en]').value;
 groups[i].minChoices=Number(el.querySelector('[data-f=min]').value);groups[i].maxChoices=Number(el.querySelector('[data-f=max]').value);
 groups[i].values=[...el.querySelectorAll('.v9-editor-option')].map(v=>({labelJa:v.querySelector('[data-v=ja]').value,labelEn:v.querySelector('[data-v=en]').value,deltaYen:Number(v.querySelector('[data-v=yen]').value)}));});}
function renderGroups(){const c=$('#option-groups');c.innerHTML=groups.map((g,i)=>`<div class="v9-editor-group"><div class="v9-flex"><b>Group ${i+1}</b><input data-f="ja" type="text" maxlength="90" placeholder="日本語見出し" value="${esc(g.labelJa)}"><input data-f="en" type="text" maxlength="90" placeholder="English group" value="${esc(g.labelEn)}"><label>Minimum <input data-f="min" type="number" min="0" max="8" value="${g.minChoices}" style="width:65px"></label><label>Maximum <input data-f="max" type="number" min="1" max="8" value="${g.maxChoices}" style="width:65px"></label><button class="btn outline sm" data-delete-group="${i}">Remove group</button></div><div>${g.values.map((v,j)=>`<div class="v9-editor-option"><input data-v="ja" maxlength="100" placeholder="日本語選択" value="${esc(v.labelJa)}"><input data-v="en" maxlength="100" placeholder="English option" value="${esc(v.labelEn)}"><input data-v="yen" type="number" min="-2000" max="10000" value="${v.deltaYen}" title="Price difference in JPY"><button class="btn outline sm" data-delval="${i}:${j}">×</button></div>`).join('')}</div><button class="btn outline sm" data-addval="${i}">+ Add choice</button></div>`).join('');
 $$('[data-delete-group]').forEach(b=>b.onclick=()=>{collect();groups.splice(Number(b.dataset.deleteGroup),1);renderGroups()});
 $$('[data-addval]').forEach(b=>b.onclick=()=>{collect();groups[Number(b.dataset.addval)].values.push({labelJa:'',labelEn:'',deltaYen:0});renderGroups()});
 $$('[data-delval]').forEach(b=>b.onclick=()=>{collect();const [i,j]=b.dataset.delval.split(':').map(Number);groups[i].values.splice(j,1);renderGroups()});}
$('#new-group').onclick=()=>{collect();groups.push({labelJa:'',labelEn:'',minChoices:0,maxChoices:1,values:[{labelJa:'',labelEn:'',deltaYen:0}]});renderGroups();};
$('#save-options').onclick=async()=>{if(!current)return;collect();try{await api('/menu/'+current.id+'/options','PUT',groups);toast('Options saved. Reload page to fetch new option IDs.');}catch(e){toast(e.message)}};
boot();
