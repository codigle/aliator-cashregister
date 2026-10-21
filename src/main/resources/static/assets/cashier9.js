'use strict';
(()=>{
 const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]);
 const yen=x=>'¥'+Number(x||0).toLocaleString('ja-JP');
 let current=null,poll=null;
 async function api(path,method='GET',data){const r=await fetch('/api/v2/v9'+path,{method,credentials:'same-origin',cache:'no-store',headers:{'Content-Type':'application/json'},body:data?JSON.stringify(data):undefined});const j=await r.json().catch(()=>({}));if(!r.ok)throw Error(j.message||j.error||`HTTP ${r.status}`);return j;}
 const modal=document.createElement('div');modal.id='v9-pay-modal';modal.className='v9-backdrop hide';document.body.append(modal);
 function close(){clearInterval(poll);current=null;modal.classList.add('hide');}
 async function refresh(){if(!current)return;try{const p=await api('/staff/demo-pay/'+current.id);
 const state=document.getElementById('v9-pay-status');state.textContent=p.status==='AWAITING_CASHIER'?'Customer submitted demo amount; cashier approval required.':p.status==='OPEN'?'Waiting for customer to scan and enter amount.':p.status;
 const b=document.getElementById('v9-pay-approve');b.disabled=p.status!=='AWAITING_CASHIER';
 if(p.status==='CONFIRMED'||p.status==='REPLACED'||p.status==='REJECTED'){clearInterval(poll);}
 }catch(e){document.getElementById('v9-pay-status').textContent=e.message;}}
 async function open(){if(!window.v9CashierGetSelected())return alert('Select an open table first');
 try{current=await api('/staff/demo-pay','POST',{tableId:window.v9CashierGetSelected().tableId});
 modal.innerHTML=`<section class="v9-dialog" role="dialog" aria-modal="true" aria-label="Demo QR payment"><div class="v9-dialog-head"><h2>QR payment · PayPay-style DEMO</h2><button class="v9-close" id="v9-pay-close">×</button></div><div class="v9-dialog-scroll" style="text-align:center"><p class="v9-demo-banner">SIMULATION ONLY — No real PayPay transaction</p><h2>${yen(current.amountYen)}</h2><img style="width:250px;max-width:85%;margin:auto" alt="Scan demo payment QR" src="/api/v2/v9/staff/demo-pay/${current.id}/qr?url=${encodeURIComponent(current.url)}"><p>Customer scans with their phone, enters the same amount and submits.</p><p><a href="${esc(current.url)}" target="_blank" rel="noopener">Open customer demo on this device</a></p><p id="v9-pay-status" role="status">Waiting for customer…</p></div><div class="v9-dialog-footer"><button class="btn primary full" id="v9-pay-approve" disabled>Cashier: confirm DEMO payment and close table</button></div></section>`;
 modal.classList.remove('hide');document.getElementById('v9-pay-close').onclick=close;
 document.getElementById('v9-pay-approve').onclick=async()=>{if(!confirm('Confirm simulated QR payment? This is NOT real PayPay settlement.'))return;
 const b=document.getElementById('v9-pay-approve');b.disabled=true;
 try{const done=await api('/staff/demo-pay/'+current.id+'/confirm','POST');
   const receipt=await fetch('/api/v2/staff/payments/'+done.paymentId+'/receipt',{credentials:'same-origin'}).then(r=>r.json());
   receipt.method='QR payment (simulated)';close();await window.v9CashierShowReceipt(receipt);
  }catch(e){alert(e.message);b.disabled=false;}}
 await refresh();poll=setInterval(refresh,2000);
 }catch(e){alert(e.message);}}
 document.getElementById('v9-pay-open').onclick=open;
 modal.onclick=e=>{if(e.target===modal)close();};
 document.addEventListener('keydown',e=>{if(e.key==='Escape')close();});
})();
