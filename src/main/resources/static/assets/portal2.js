"use strict";
const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"})[c]);
const yen=n=>"¥"+Number(n||0).toLocaleString("ja-JP");
let allProducts=[],allTables=[],allOrders=[],user=null,panel="overview",interval;
let toastTimer;function toast(msg){const e=$("#toast");e.textContent=msg;e.classList.add("show");clearTimeout(toastTimer);toastTimer=setTimeout(()=>e.classList.remove("show"),4300);}
async function api(path, opts={}){
 const response=await fetch("/api/v2"+path,{credentials:"same-origin",cache:"no-store",method:opts.method||"GET",headers:{"Content-Type":"application/json"},body:opts.data?JSON.stringify(opts.data):undefined});
 let data;try{data=await response.json()}catch{data={}}
 if(!response.ok){const error=new Error(data.error||`HTTP ${response.status}`);error.status=response.status;throw error;}
 return data;
}
function showLogin(){ $("#auth-checking")?.classList.add("hide"); $("#auth").classList.remove("hide");$("#workspace").classList.add("hide");$("#logout").classList.add("hide"); $("#current-restaurant").textContent=""; clearInterval(interval); }
function showApp(me){$("#auth-checking")?.classList.add("hide");user=me;$("#auth").classList.add("hide");$("#workspace").classList.remove("hide");$("#logout").classList.remove("hide");$("#current-restaurant").textContent=me.restaurant;$("#welcome").textContent=me.restaurant;switchPanel(location.hash.slice(1)||"overview");clearInterval(interval);interval=setInterval(()=>{if(panel==="kitchen")loadOrders().catch(console.warn);else if(panel==="overview")loadDashboard().catch(console.warn)},4000);}
async function start(){try{showApp(await api("/staff/me"))}catch(e){
 if(e.status===401)showLogin();
 else {$("#auth-checking").textContent="Connection issue. Please retry.";let b=document.createElement('button');b.type='button';b.className='btn outline';b.textContent='Retry';b.onclick=()=>{b.remove();start()};$("#auth-checking").append(' ',b);}
}}
$("#login-form").addEventListener("submit",async e=>{e.preventDefault();try{await api("/auth/login",{method:"POST",data:{email:$("#email").value,password:$("#password").value}});$("#password").value="";showApp(await api("/staff/me"));toast("Signed in");}catch(err){toast(err.message)}});
// OTP is never interpreted as an active restaurant account before successful /verify.
const pendingKey="aliator-otp-pending-v61";
const registrationFeedback=$("#registration-feedback");
const verificationError=$("#verification-error");
const verificationDialog=$("#verification-modal");
let registrationBusy=false;
function pendingRead(){try{const p=JSON.parse(sessionStorage.getItem(pendingKey)||"null");if(p&&p.email&&p.expiresAt>Date.now())return p;}catch{}return null;}
function pendingSave(email){try{sessionStorage.setItem(pendingKey,JSON.stringify({email,expiresAt:Date.now()+600000}));}catch{}}
function pendingClear(){try{sessionStorage.removeItem(pendingKey);}catch{}}
function registrationError(message){registrationFeedback.textContent=message;toast(message);}
function verificationOpen(email){
 const value=String(email||"").trim().toLowerCase();
 if(!value){registrationError("Enter your email address first.");return;}
 $("#v-email").value=value;
 $("#pending-email").textContent="Enter the 6-digit code sent to "+value+". Your restaurant account is not created until the code is confirmed.";
 verificationError.textContent="";
 if(!verificationDialog.open)verificationDialog.showModal();
 $("#v-code").focus();
}
async function requestRegistrationCode(){
 if(registrationBusy)return;
 registrationBusy=true;
 const btn=$("#send-verification"),resend=$("#resend-verification");btn.disabled=true;resend.disabled=true;
 const email=$("#r-email").value.trim().toLowerCase();
 try{
  const data={restaurantName:$("#r-name").value,slug:$("#r-slug").value,email,password:$("#r-password").value};
  await api("/auth/register/start",{method:"POST",data});
  pendingSave(email);
  registrationFeedback.textContent="";
  $("#r-password").value="";
  verificationOpen(email);
  toast("Verification code sent. Check your inbox.");
 }catch(err){
  const old=pendingRead();
  if(err.status===429 && old && old.email===email){
   verificationOpen(email);
   verificationError.textContent="Too many requests. Use the code already sent or wait before resending.";
  }else{
   const msg=err.message||"Could not send a verification code.";
   if(verificationDialog.open)verificationError.textContent=msg;
   registrationError(msg);
  }
 }finally{btn.disabled=false;resend.disabled=false;registrationBusy=false;}
}
$("#register-form").addEventListener("submit",async e=>{e.preventDefault();await requestRegistrationCode();});
$("#verify-form").addEventListener("submit",async e=>{
 e.preventDefault();const btn=$("#confirm-verification");btn.disabled=true;verificationError.textContent="";
 try{
  const dest=$("#v-email").value.trim().toLowerCase();
  await api("/auth/register/verify",{method:"POST",data:{email:dest,code:$("#v-code").value.trim()}});
  pendingClear();$("#email").value=dest;$("#v-code").value="";$("#r-password").value="";
  verificationDialog.close();
  toast("Email confirmed and restaurant created. Sign in now.");
  registrationFeedback.textContent="Restaurant created. You can sign in above.";
  document.getElementById("login-form").scrollIntoView({behavior:"smooth",block:"center"});
 }catch(err){verificationError.textContent=err.message||"Verification failed. Please try again.";}
 finally{btn.disabled=false;}
});
$("#close-verification").addEventListener("click",()=>verificationDialog.close());
$("#edit-registration").addEventListener("click",()=>{
 $("#r-email").value=$("#v-email").value;
 verificationDialog.close();
 document.querySelector("#register-form").closest("details").open=true;
 $("#r-email").focus();
});
$("#resend-verification").addEventListener("click",()=>{
 // Passwords are never persisted client-side; require the owner to re-enter the password to resend.
 $("#r-email").value=$("#v-email").value;
 verificationDialog.close();
 $("#register-form").closest("details").open=true;
 registrationFeedback.textContent="Enter your password and press Send verification code to request a new one. Wait 60 seconds after the previous email.";
 $("#r-password").focus();
});
$("#resume-verification").addEventListener("click",()=>{
 const p=pendingRead();
 verificationOpen(p?.email||$("#r-email").value);
});
const lastPending=pendingRead();
if(lastPending){$("#r-email").value=lastPending.email;}
$("#logout").addEventListener("click",async()=>{try{await api("/auth/logout",{method:"POST"})}finally{showLogin()}});
const panes=["overview","menu","tables","kitchen","demo"];
function switchPanel(name){if(!panes.includes(name))name="overview";panel=name;$$('.pane').forEach(el=>el.classList.toggle('hide',el.id!==name));$$('#tabs [data-panel]').forEach(el=>el.classList.toggle('active',el.dataset.panel===name));location.hash=name; if(name==="overview")loadDashboard().catch(err=>toast(err.message));if(name==="menu"||name==="demo")loadMenu().catch(err=>toast(err.message));if(name==="tables"||name==="demo")loadTables().catch(err=>toast(err.message));if(name==="kitchen")loadOrders().catch(err=>toast(err.message));}
$$('[data-panel]').forEach(e=>e.addEventListener('click',()=>switchPanel(e.dataset.panel)));
$$('[data-show]').forEach(e=>e.addEventListener('click',()=>switchPanel(e.dataset.show)));
async function loadDashboard(){const d=await api('/staff/dashboard');$("#m-orders").textContent=d.orders;$("#m-tables").textContent=d.openTables;$("#m-live").textContent=d.liveOrders;$("#m-sales").textContent=yen(d.paidRevenueYen);}
async function loadMenu(){allProducts=await api('/staff/menu');$("#menu-list").innerHTML=allProducts.length?allProducts.map(p=>`<article class="item-card"><div class="item-title"><span class="emoji">${p.imageUrl?`<img loading="lazy" class="portal-food-photo" alt="${esc(p.name)}" src="${esc(p.imageUrl)}" onerror="this.remove();this.parentElement.querySelector('span').hidden=false"><span hidden>${esc(p.emoji)}</span>` : esc(p.emoji)}</span><div><h3>${esc(window.CR5?.productName(p)||p.name)}</h3><span class="tiny muted">${esc(window.CR5?.category(p.category,window.CR5?.getLocale?.()||'ja')||p.category)} · ${p.active?'Available':'Hidden'}</span></div></div><p>${esc(window.CR5?.getLocale?.()==='ja'?(p.descriptionJa||p.description):(p.description||p.descriptionJa))}</p><footer><strong>${yen(p.priceYen)}</strong><button class="btn outline sm" data-edit="${p.id}">Edit →</button></footer></article>`).join(''):'<p>No products yet. Add your first menu item.</p>';
 $$('[data-edit]').forEach(el=>el.addEventListener('click',()=>openEditor(allProducts.find(p=>p.id===Number(el.dataset.edit)))));
 renderDemoProducts();}
function openEditor(p){$("#menu-editor").classList.remove('hide');$("#editor-title").textContent=p?'Edit menu item':'Add menu item';$("#product-id").value=p?.id||'';$("#product-name").value=p?.nameJa||p?.name||'';$("#product-category").value=p?.category||'Mains';$("#product-price").value=p?.priceYen||0;$("#product-emoji").value=p?.emoji||'🍽';$("#product-desc").value=p?.descriptionJa||p?.description||'';$("#product-active").checked=p?.active??true;$("#product-name-ja").value=p?.nameJa||''; for(const k of ["zh","ko","tr"]){$("#product-name-"+k).value=p?.["name"+k[0].toUpperCase()+k.slice(1)]||"";$("#product-desc-"+k).value=p?.["description"+k[0].toUpperCase()+k.slice(1)]||"";}$("#product-desc-ja").value=p?.descriptionJa||'';$("#product-image").value=p?.imageUrl||'';$("#product-credit").value=p?.imageCredit||'';$("#menu-editor").scrollIntoView({behavior:'smooth',block:'center'});}
$("#add-product").addEventListener('click',()=>openEditor(null));$("#cancel-product").addEventListener('click',()=>$("#menu-editor").classList.add('hide'));
$("#menu-form").addEventListener('submit',async e=>{e.preventDefault();const id=$("#product-id").value;const editing=allProducts.find(x=>String(x.id)===String(id));const data={name:editing?.name||$("#product-name").value,category:$("#product-category").value,description:editing?.description||$("#product-desc").value,priceYen:Number($("#product-price").value),emoji:$("#product-emoji").value,active:$("#product-active").checked,nameJa:$("#product-name").value,descriptionJa:$("#product-desc").value, nameZh:$("#product-name-zh").value, descriptionZh:$("#product-desc-zh").value, nameKo:$("#product-name-ko").value,descriptionKo:$("#product-desc-ko").value,nameTr:$("#product-name-tr").value,descriptionTr:$("#product-desc-tr").value,imageUrl:$("#product-image").value,imageCredit:$("#product-credit").value};try{await api('/staff/menu'+(id?'/'+id:''),{method:id?'PUT':'POST',data});$("#menu-editor").classList.add('hide');await loadMenu();toast('Menu updated.');}catch(err){toast(err.message)}});
async function loadTables(){allTables=await api('/staff/tables');$("#table-list").innerHTML=allTables.map(t=>`<article class="table-card"><header><h3>${esc(t.label)}</h3><span class="chip">${esc(t.code)}</span></header><img alt="QR for ${esc(t.label)}" class="qr" src="/api/v2/staff/tables/${t.id}/qr.png?rev=${encodeURIComponent(t.qrToken)}"><p>${t.openSessionId?'● Active guest session':'○ Available'}</p><footer><a class="btn outline sm" href="/guest.html?q=${encodeURIComponent(t.qrToken)}" target="_blank" rel="noopener noreferrer">Preview as guest</a><button class="btn outline sm" data-print="${t.id}">Print QR</button><button class="btn outline sm" data-rotate="${t.id}">Rotate QR</button>${t.openSessionId?`<a class="btn primary sm" href="/cashier.html?table=${t.id}">Open Cashier / POS</a>`:''}</footer></article>`).join('')||'<p class="muted">No tables yet.</p>';
 $$('[data-rotate]').forEach(el=>el.onclick=()=>rotateTable(Number(el.dataset.rotate)));

 $$('[data-print]').forEach(el=>el.onclick=()=>printQr(Number(el.dataset.print)));
 renderDemoTables();}
function printQr(id){const t=allTables.find(x=>x.id===id);if(!t)return;const w=window.open('','_blank');if(!w){toast('Allow popups to print the QR');return;}w.document.write(`<!doctype html><title>Table ${esc(t.code)}</title><div style="text-align:center;font-family:Arial;padding:25px"><h1>${esc(user.restaurant)}</h1><h2>${esc(t.label)}</h2><img width="330" height="330" src="/api/v2/staff/tables/${t.id}/qr.png?rev=${encodeURIComponent(t.qrToken)}"><p>Scan to order</p></div>`);w.document.close();w.onload=()=>w.print();}
async function rotateTable(id){if(!confirm('Rotate this QR? Any previously printed QR will stop working.'))return;try{await api(`/staff/tables/${id}/rotate`,{method:'POST'});await loadTables();toast('QR rotated. Reprint it.')}catch(err){toast(err.message)}}
$("#show-table-form").onclick=()=>$("#table-form").classList.toggle('hide');
$("#table-form").addEventListener('submit',async e=>{e.preventDefault();try{await api('/staff/tables',{method:'POST',data:{code:$("#table-code").value,label:$("#table-label").value}});$("#table-form").reset();$("#table-form").classList.add('hide');await loadTables();toast('Table added')}catch(err){toast(err.message)}});
async function loadOrders(){
 if(!allProducts.length)allProducts=await api('/staff/menu');
 allOrders=await api('/staff/orders');
 const config=await flow8Settings();
 const lang=window.CR5?.getLocale()||'ja';
 const txt={
  ja:{new:'新規注文',ready:'提供待ち',done:'完了',finish:'調理完了 →',served:'提供済みにする →',none:'注文なし',demo:'デモ注文'},
  en:{new:'New orders',ready:'Ready to serve',done:'Completed',finish:'Preparation complete →',served:'Mark served →',none:'No orders',demo:'DEMO'},
  zh:{new:'新订单',ready:'待上菜',done:'已完成',finish:'备餐完成 →',served:'确认已上菜 →',none:'暂无订单',demo:'演示'},
  ko:{new:'신규 주문',ready:'서빙 대기',done:'완료',finish:'조리 완료 →',served:'서빙 완료 →',none:'주문 없음',demo:'데모'},
  tr:{new:'Yeni siparişler',ready:'Servis bekliyor',done:'Tamamlandı',finish:'Hazırlık tamamlandı →',served:'Servis edildi →',none:'Sipariş yok',demo:'DEMO'}
 }[lang]||null;
 const labels=txt||{new:'New orders',ready:'Ready to serve',done:'Completed',finish:'Preparation complete →',served:'Mark served →',none:'No orders',demo:'DEMO'};
 const grouped=[{state:'NEW',label:labels.new,orders:allOrders.filter(o=>o.status==='NEW'||o.status==='PREPARING'||(!config.confirmDelivery&&o.status==='READY'))},
    ...(config.confirmDelivery?[{state:'READY',label:labels.ready,orders:allOrders.filter(o=>o.status==='READY')}]:[]),
    {state:'SERVED',label:labels.done,orders:allOrders.filter(o=>o.status==='SERVED')}];
 $('#kitchen-board').innerHTML=grouped.map(group=>`<article class="lane"><h3>${group.label} · ${group.orders.length}</h3>${group.orders.slice(0,30).map(o=>`<div class="ticket"><div class="meta"><span>#${o.id} · ${esc(o.tableCode)}</span><small>${new Date(o.createdAt).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'})}</small></div><p class="tiny">${o.demo?`<span class="status">${labels.demo}</span>`:''}<span class="status">${esc(o.status)}</span></p><ul>${o.lines.map(l=>`<li>${Number(l.qty)}× ${esc(window.CR5?.translateOrderItem(l.name,allProducts)||l.name)} ${l.note?`<em>${esc(l.note)}</em>`:''}</li>`).join('')}</ul><strong>${yen(o.totalYen)}</strong>${(o.status==='NEW'||o.status==='PREPARING'||o.status==='READY')?`<p><button class="btn primary sm full" data-progress="${o.id}" data-next="${o.status==='READY'?'SERVED':'READY'}">${o.status==='READY'?labels.served:labels.finish}</button></p>`:''}</div>`).join('')||`<p class="tiny muted">${labels.none}</p>`}</article>`).join('');
 $$('[data-progress]').forEach(el=>el.onclick=async()=>{el.disabled=true;try{await api(`/staff/orders/${el.dataset.progress}/status`,{method:'POST',data:{status:el.dataset.next}});await loadOrders()}catch(err){toast(err.message);el.disabled=false}});
}
let flow8Cache={confirmDelivery:false},flow8At=0;
async function flow8Settings(){
 if(Date.now()-flow8At>15000){try{flow8Cache=await api('/staff/workflow/settings');flow8At=Date.now()}catch(e){if(e.status!==403)console.warn('workflow settings',e)}}
 return flow8Cache;
}
$("#refresh-kitchen").onclick=()=>loadOrders().catch(err=>toast(err.message));
function renderDemoProducts(){$("#demo-product").innerHTML=allProducts.filter(p=>p.active).map(p=>`<option value="${p.id}">${esc(window.CR5?.productName(p)||p.name)} · ${yen(p.priceYen)}</option>`).join('');}
function renderDemoTables(){$("#demo-table").innerHTML=allTables.map(t=>`<option value="${esc(t.code)}">${esc(t.label)} (${esc(t.code)})</option>`).join('');}
$("#demo-form").addEventListener('submit',async e=>{e.preventDefault();try{await api('/staff/demo/orders',{method:'POST',data:{tableCode:$("#demo-table").value,items:[{menuItemId:Number($("#demo-product").value),quantity:Number($("#demo-quantity").value),note:'Test order'}]}});toast('Test order sent to the kitchen');switchPanel('kitchen');}catch(err){toast(err.message)}});
window.addEventListener('hashchange',()=>{if(user&&location.hash.slice(1)!==panel)switchPanel(location.hash.slice(1))});
start();

window.addEventListener('cr5:locale',()=>{if(user){if(panel==='menu'||panel==='demo')loadMenu().catch(console.warn);if(panel==='kitchen')loadOrders().catch(console.warn);}});

// === ALIATOR_PASSWORD_RESET_V7 ===
(() => {
  const resetModal = document.getElementById('password-reset-modal');
  if (!resetModal) return;
  const byId = (id) => document.getElementById(id);
  const notice = byId('reset-feedback');
  const startForm = byId('password-reset-start-form');
  const confirmForm = byId('password-reset-confirm-form');
  const emailInput = byId('reset-email');
  const codeInput = byId('reset-code');
  const newPasswordInput = byId('reset-new-password');
  let busy = false;

  const feedback = (message, error = false) => {
    notice.textContent = message;
    notice.style.color = error ? '#b3261e' : '#20613b';
  };
  const step = (which) => {
    startForm.hidden = which !== 1;
    confirmForm.hidden = which !== 2;
  };
  const open = () => {
    feedback('');
    step(1);
    codeInput.value = '';
    newPasswordInput.value = '';
    emailInput.value = (byId('email').value || byId('r-email').value || '').trim();
    if (!resetModal.open) resetModal.showModal();
    emailInput.focus();
  };
  byId('open-password-reset').addEventListener('click', open);
  byId('close-password-reset').addEventListener('click', () => resetModal.close());
  byId('reset-request-another').addEventListener('click', () => {
    feedback('You can request a fresh code after 60 seconds.');
    step(1);
  });
  async function post(path, body) {
    const response = await fetch('/api/v2/auth/password-reset/' + path, {
      method: 'POST', credentials: 'same-origin', cache: 'no-store',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(body)
    });
    let data = {};
    try {data = await response.json();} catch (_) {}
    if (!response.ok) {
      const reason = String(data.detail || data.message || data.error || `HTTP ${response.status}`);
      throw new Error(reason);
    }
    return data;
  }
  startForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy) return;
    const email = emailInput.value.trim().toLowerCase();
    if (!email || !emailInput.checkValidity()) return;
    busy = true;
    const button = byId('send-reset-code'); button.disabled = true;
    feedback('Sending your recovery code…');
    try {
      await post('start', {email});
      step(2);
      feedback('If an account exists for this address, a six-digit code will arrive. Check your spam folder.');
      codeInput.focus();
    } catch (e) {feedback(e.message || 'Could not request a code.', true);}
    finally {button.disabled = false; busy = false;}
  });
  confirmForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy || !confirmForm.checkValidity()) return;
    const button = byId('confirm-reset');
    busy = true; button.disabled = true;
    feedback('Updating password…');
    try {
      await post('confirm', {
        email: emailInput.value.trim().toLowerCase(),
        code: codeInput.value.trim(),
        newPassword: newPasswordInput.value
      });
      byId('email').value = emailInput.value.trim().toLowerCase();
      byId('password').value = '';
      codeInput.value = ''; newPasswordInput.value = '';
      resetModal.close();
      const out = byId('registration-feedback');
      if (out) out.textContent = 'Password updated. Sign in with your new password.';
      byId('password').focus();
    } catch (e) {feedback(e.message || 'Reset failed.', true);}
    finally {button.disabled = false; busy = false;}
  });
  resetModal.addEventListener('close', () => {
    newPasswordInput.value = '';
    codeInput.value = '';
  });
})();
