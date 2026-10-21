'use strict';
const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]);
const yen=n=>'¥'+Number(n||0).toLocaleString('ja-JP');
const LANGS=['ja','en','zh','ko','tr'];
const texts={
 ja:{menu:'メニュー',cart:'ご注文',history:'ご注文履歴',all:'すべて',add:'カートに入れる',order:'注文を送信',ingredients:'原材料（参考）',allergens:'アレルギー情報（要確認）',prep:'お料理について',notes:'備考・ご要望',quantity:'数量',required:'必須',optional:'任意',cancel:'閉じる',empty:'メニューから商品をお選びください',sent:'ご注文を送信しました',error:'処理に失敗しました',translation:'翻訳準備中：原文を表示しています',demo:'デモ用メニュー：原材料・アレルゲンは店舗で要確認'},
 en:{menu:'Menu',cart:'Your order',history:'Table orders',all:'All',add:'Add to order',order:'Send order',ingredients:'Ingredients (illustrative)',allergens:'Allergens (verify with restaurant)',prep:'Preparation',notes:'Special instructions',quantity:'Quantity',required:'Required',optional:'Optional',cancel:'Close',empty:'Choose something from the menu',sent:'Order sent to kitchen',error:'Request failed',translation:'Translation pending; showing source text',demo:'Demo menu: ingredients and allergens require confirmation'},
 zh:{menu:'菜单',cart:'您的订单',history:'本桌订单',all:'全部',add:'加入订单',order:'提交订单',ingredients:'参考食材',allergens:'过敏原（请向餐厅确认）',prep:'做法',notes:'备注',quantity:'数量',required:'必选',optional:'可选',cancel:'关闭',empty:'请选择菜品',sent:'订单已发送',error:'请求失败',translation:'翻译处理中，当前显示原文',demo:'演示菜单：配料和过敏原以餐厅实际信息为准'},
 ko:{menu:'메뉴',cart:'주문 내역',history:'테이블 주문',all:'전체',add:'주문에 담기',order:'주문 전송',ingredients:'재료 예시',allergens:'알레르기 정보(매장 확인)',prep:'조리 정보',notes:'요청 사항',quantity:'수량',required:'필수',optional:'선택',cancel:'닫기',empty:'메뉴를 선택해 주세요',sent:'주문이 전송되었습니다',error:'요청 실패',translation:'번역 준비 중: 원문 표시',demo:'데모 메뉴: 재료 및 알레르기 정보는 매장 확인이 필요합니다'},
 tr:{menu:'Menü',cart:'Siparişin',history:'Masa siparişleri',all:'Tümü',add:'Sepete ekle',order:'Siparişi gönder',ingredients:'Malzemeler (örnek)',allergens:'Alerjenler (restorandan doğrula)',prep:'Hazırlanışı',notes:'Özel istek',quantity:'Adet',required:'Zorunlu',optional:'İsteğe bağlı',cancel:'Kapat',empty:'Menüden ürün seç',sent:'Sipariş mutfağa gönderildi',error:'İşlem başarısız',translation:'Çeviri hazırlanıyor; orijinal metin gösteriliyor',demo:'Demo menü: malzeme ve alerjenleri restoran doğrulamalı'}
};
let lang=localStorage.getItem('cr9_lang')||'ja';
if(!LANGS.includes(lang))lang='ja';
let products=[],cart=new Map(),currentCategory='ALL',selected=null,selectedQty=1,processing=false,blocked=false,enabledLangs=['ja'];
const tr=k=>(texts[lang]||texts.ja)[k];
const pathApi=async(path,method='GET',data)=>{
 const r=await fetch(path,{method,credentials:'same-origin',cache:'no-store',headers:{'Content-Type':'application/json'},body:data===undefined?undefined:JSON.stringify(data)});
 const j=await r.json().catch(()=>({}));if(!r.ok)throw Error(j.message||j.error||`HTTP ${r.status}`);return j;
};
const name=p=>lang==='ja'?(p.nameJa||p.name):(p.translatedName||(lang==='en'?p.name:'')||p.nameJa||p.name);
const desc=p=>lang==='ja'?(p.descriptionJa||p.description):(p.translatedDescription||(lang==='en'?p.description:'')||p.descriptionJa||p.description);
const optLabel=g=>lang==='ja'?g.labelJa:(g.translatedLabel||(lang==='en'?g.labelEn:'')||g.labelJa);
const valLabel=v=>lang==='ja'?v.labelJa:(v.translatedLabel||(lang==='en'?v.labelEn:'')||v.labelJa);
function toast(msg){const e=$('#toast');e.textContent=msg;e.classList.add('show');clearTimeout(window.__toast);window.__toast=setTimeout(()=>e.classList.remove('show'),4800);}
function modalClose(){selected=null;$('#item-modal').classList.add('hide');document.body.classList.remove('modal-open');}
function radioName(group){return 'g'+group.id;}
function openItem(id){selected=products.find(p=>p.id===id);if(!selected)return;selectedQty=1;
 $('#item-title').textContent=name(selected);$('#item-description').textContent=desc(selected);$('#item-price').textContent=yen(selected.priceYen);
 $('#item-image').innerHTML=selected.imageUrl?`<img alt="${esc(name(selected))}" src="${esc(selected.imageUrl)}">`:`<span>${esc(selected.emoji||'🍽')}</span>`;
 $('#item-ingredients-title').textContent=tr('ingredients');$('#item-allergens-title').textContent=tr('allergens');$('#item-prep-title').textContent=tr('prep');
 $('#item-ingredients').textContent=(lang==='ja'?selected.ingredientsJa:selected.translatedIngredients||selected.ingredientsJa)||'—';
 $('#item-allergens').textContent=(lang==='ja'?selected.allergensJa:selected.translatedAllergens||selected.allergensJa)||tr('demo');
 $('#item-prep').textContent=(lang==='ja'?selected.preparationJa:selected.translatedPreparation||selected.preparationJa)||'—';
 $('#item-options').innerHTML=(selected.optionGroups||[]).map(g=>{
 const many=g.maxChoices>1;return `<fieldset class="v9-group" data-group="${g.id}"><legend>${esc(optLabel(g))} <span>${g.minChoices?tr('required'):tr('optional')} ${g.maxChoices>1?'(max '+g.maxChoices+')':''}</span></legend>`+
 g.values.map((v,i)=>`<label class="v9-choice"><input type="${many?'checkbox':'radio'}" name="${radioName(g)}" value="${v.id}" ${g.minChoices&&i===0&&!many?'checked':''}><span>${esc(valLabel(v))}</span><b>${v.deltaYen?`${v.deltaYen>0?'+':''}${yen(v.deltaYen)}`:'—'}</b></label>`).join('')+'</fieldset>';}).join('');
 $('#item-note').value='';$('#item-qty').textContent='1';$('#item-modal').classList.remove('hide');document.body.classList.add('modal-open');
 $$('#item-options input').forEach(el=>el.onchange=updateModalPrice);updateModalPrice();
 $('#modal-scroll').scrollTop=0;
}
function choices(){return $$('#item-options input:checked').map(i=>Number(i.value));}
function validOptions(){for(const g of(selected.optionGroups||[])){
 const n=choices().filter(id=>g.values.some(v=>v.id===id)).length;
 if(n<g.minChoices||n>g.maxChoices)return false;
 }return true;}
function unitPrice(){const picks=new Set(choices());return selected.priceYen+(selected.optionGroups||[]).flatMap(g=>g.values).filter(v=>picks.has(v.id)).reduce((sum,v)=>sum+v.deltaYen,0);}
function updateModalPrice(){if(!selected)return;const ok=validOptions(),price=unitPrice();$('#modal-add').disabled=!ok||price<0;
 $('#modal-add').textContent=`${tr('add')} · ${yen(price*selectedQty)}`;}
function renderProducts(){const items=products.filter(p=>currentCategory==='ALL'||p.category===currentCategory);
 $('#guest-products').innerHTML=items.map(p=>`<button class="v9-product" data-item="${p.id}" aria-label="${esc(name(p))}"><span class="v9-photo">${p.imageUrl?`<img src="${esc(p.imageUrl)}" alt="" loading="lazy" referrerpolicy="no-referrer" onerror="this.replaceWith(document.createTextNode('🍽'))">`:esc(p.emoji||'🍽')}</span><span class="v9-info"><b>${esc(name(p))}</b><small>${esc(desc(p))}</small><strong>${yen(p.priceYen)} →</strong></span></button>`).join('')||'<p class="muted">—</p>';
 $$('[data-item]').forEach(el=>el.onclick=()=>openItem(Number(el.dataset.item)));
 const cats=['ALL',...new Set(products.map(p=>p.category))];$('#guest-cats').innerHTML=cats.map(c=>`<button data-cat="${esc(c)}" class="${currentCategory===c?'active':''}">${c==='ALL'?tr('all'):esc(window.CR5?.category(c,lang)||c)}</button>`).join('');
 $$('[data-cat]').forEach(el=>el.onclick=()=>{currentCategory=el.dataset.cat;renderProducts();});
}
function renderCart(){let total=0,qty=0;const lines=[...cart.values()];
 $('#cart-lines').innerHTML=lines.length?lines.map((l,i)=>{
 const price=l.unitPrice*l.qty;total+=price;qty+=l.qty;
 return `<div class="cart-line"><div><strong>${esc(l.displayName)}</strong><small>${esc(l.optionLabels.join(' / '))}</small><small>${yen(l.unitPrice)} × ${l.qty}</small><button type="button" data-remove="${i}">−</button> <button type="button" data-increase="${i}">+</button></div><strong>${yen(price)}</strong></div>`;
 }).join(''):`<p class="muted">${tr('empty')}</p>`;
 $('#cart-count').textContent=qty+' · '+tr('quantity');$('#cart-total').textContent=yen(total);
 $('#send-order').disabled=$('#mobile-send').disabled=processing||blocked||qty===0||lines.length>20;
 $('#send-order').textContent=tr('order')+' →';$('#mobile-send').textContent=tr('order')+' · '+yen(total);
 $$('[data-remove]').forEach(b=>b.onclick=()=>{const l=lines[Number(b.dataset.remove)];if(l.qty<=1)cart.delete(l.key);else l.qty--;renderCart();});
 $$('[data-increase]').forEach(b=>b.onclick=()=>{const l=lines[Number(b.dataset.increase)];if(l.qty<15)l.qty++;renderCart();});
}
async function loadMenu(){const result=await pathApi('/api/v2/v9/guest/menu?lang='+encodeURIComponent(lang));
 products=result.items;enabledLangs=result.settings.menuLanguages.split(',');
 // Language can be enabled while translations are queued. Show Japanese until published.
 const pending=lang!=='ja' && products.some(p=>!p.translatedName || (p.optionGroups||[]).some(g=>!g.translatedLabel || g.values.some(v=>!v.translatedLabel)));
 if(pending)toast(tr('translation'));
 $('#locale').innerHTML=enabledLangs.map(l=>`<option value="${l}">${({ja:'日本語',en:'English',zh:'中文',ko:'한국어',tr:'Türkçe'})[l]||l}</option>`).join('');
 $('#locale').value=lang;$('#menu-heading').textContent=tr('menu');$('#cart-heading').textContent=tr('cart');$('#past-heading').textContent=tr('history');
 $('#guest-cover-title').textContent=tr('menu');$('#guest-cover-info').textContent=tr('demo');
 renderProducts();renderCart();}
async function loadOrders(){const orders=await pathApi('/api/v2/guest/orders');$('#my-orders').innerHTML=orders.length?orders.map(o=>`<div class="order"><b>#${o.id} · ${esc(o.status)}</b> ${yen(o.totalYen)}<p>${o.lines.map(l=>`${l.qty}× ${esc(l.name)} ${esc(l.note||'')}`).join(', ')}</p></div>`).join(''):'—';}
async function init(){try{
 const u=new URL(location.href);const q=u.searchParams.get('q');const t=q?await pathApi('/api/v2/guest/join','POST',{qr:q}):await pathApi('/api/v2/guest/current');
 if(q)history.replaceState({},'','/guest.html');$('#restaurant-title').textContent=t.restaurant;$('#table-title').textContent=t.tableCode;
 const menuSettings=await pathApi('/api/v2/v9/guest/settings');
 const allowed=menuSettings.menuLanguages.split(',');
 if(!allowed.includes(lang))lang=allowed.includes(menuSettings.sourceLanguage)?menuSettings.sourceLanguage:allowed[0];
 await loadMenu();
 await loadOrders();setInterval(()=>loadOrders().catch(()=>{}),7000);
 }catch(e){blocked=true;$('#guest-products').textContent='Session unavailable: '+e.message;toast(e.message);}}
$('#locale').onchange=async e=>{const next=e.target.value;lang=next;localStorage.setItem('cr9_lang',lang);try{await loadMenu()}catch(err){toast(err.message);}};
$('#item-close').onclick=modalClose;$('#item-modal').onclick=e=>{if(e.target.id==='item-modal')modalClose();};
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&selected)modalClose();});
$('#item-minus').onclick=()=>{selectedQty=Math.max(1,selectedQty-1);$('#item-qty').textContent=selectedQty;updateModalPrice();};
$('#item-plus').onclick=()=>{selectedQty=Math.min(15,selectedQty+1);$('#item-qty').textContent=selectedQty;updateModalPrice();};
$('#modal-add').onclick=()=>{if(!selected||!validOptions())return;
 const ids=choices().sort((a,b)=>a-b),notes=$('#item-note').value.trim().slice(0,80),unit=unitPrice();
 const key=selected.id+'|'+ids.join(',')+'|'+notes;
 const g=cart.get(key),actual=Math.min(15,(g?.qty||0)+selectedQty);
 const all=(selected.optionGroups||[]).flatMap(g=>g.values);
 cart.set(key,{key,menuItemId:selected.id,displayName:name(selected),unitPrice:unit,
   optionValueIds:ids,optionLabels:all.filter(v=>ids.includes(v.id)).map(valLabel),note:notes,qty:actual});
 modalClose();renderCart();toast(tr('add'));};
async function submit(){if(processing||blocked||cart.size===0||cart.size>20)return;
 processing=true;renderCart();const lines=[...cart.values()].map(l=>({menuItemId:l.menuItemId,quantity:l.qty,note:l.note,optionValueIds:l.optionValueIds}));
 const reqId=crypto.randomUUID();try{const o=await pathApi('/api/v2/guest/orders','POST',{requestId:reqId,items:lines});
 cart.clear();toast(`${tr('sent')} #${o.id}`);await loadOrders();}
 catch(e){if(/401|closed|expired/i.test(e.message)){blocked=true;}toast(e.message);}
 finally{processing=false;renderCart();}}
$('#send-order').onclick=submit;$('#mobile-send').onclick=submit;
init();
