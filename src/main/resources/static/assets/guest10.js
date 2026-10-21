'use strict';
(()=>{
 const b=document.getElementById('v10-mobile-history');
 const over=document.getElementById('v10-history-overlay');
 const body=document.getElementById('v10-history-content');
 const ttl=document.getElementById('v10-history-title');
 if(!b||!over||!body)return;
 const langs={ja:['注文履歴','閉じる'],en:['Order history','Close'],zh:['订单记录','关闭'],ko:['주문 내역','닫기'],tr:['Sipariş geçmişi','Kapat']};
 function update(){const locale=document.getElementById('locale')?.value||'ja';const l=langs[locale]||langs.ja;b.textContent='📋 '+l[0];ttl.textContent=l[0];}
 async function open(){
   update();b.disabled=true;
   try{
     if(typeof loadOrders==='function')await loadOrders();
     body.innerHTML=document.getElementById('my-orders')?.innerHTML||'<p>—</p>';
     over.classList.remove('hide');document.body.classList.add('v10-history-open');
     document.getElementById('v10-history-close').focus();
   }catch(e){body.textContent='History unavailable: '+e.message;over.classList.remove('hide');}
   finally{b.disabled=false;}
 }
 function close(){over.classList.add('hide');document.body.classList.remove('v10-history-open');b.focus();}
 b.addEventListener('click',open);
 document.getElementById('v10-history-close').addEventListener('click',close);
 document.getElementById('v10-history-back').addEventListener('click',close);
 over.addEventListener('click',e=>{if(e.target===over)close();});
 document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!over.classList.contains('hide'))close();});
 document.getElementById('locale')?.addEventListener('change',update);
 update();
})();
