'use strict';
(()=>{
 const $=s=>document.querySelector(s);
 const E=s=>String(s??'');
 let busy=false,activeId=null,poll=null,queuedId=null;
 let printerList=[],workflow=null;
 function status(t,bad=false){const el=$('#v10-print-status');if(el){el.textContent=t;el.classList.toggle('v10-error',bad);}}
 async function request(url,method='GET',data){
   const res=await fetch(url,{method,credentials:'same-origin',cache:'no-store',headers:{'Content-Type':'application/json'},body:data===undefined?undefined:JSON.stringify(data)});
   const j=await res.json().catch(()=>({}));if(!res.ok)throw Error(j.detail||j.message||j.error||`HTTP ${res.status}`);return j;
 }
 window.v10SyncTables=(arr,selected)=>{
   const picker=$('#v10-table-select');const keep=selected?.tableId||'';
   picker.replaceChildren(new Option('Select an open table / テーブルを選択',''),...arr.map(c=>new Option(`${c.tableLabel} · ${E(c.tableCode)} — ¥${Number(c.amountYen).toLocaleString('ja-JP')}`,String(c.tableId))));
   picker.value=String(keep);
   if(!selected){$('#v10-selected-label').textContent='No table selected / テーブル未選択';$('#v10-selected-detail').textContent=`${arr.length} open bills · choose one to proceed`;
     if($('#checkout-panel').classList.contains('hide')===false && arr.length===0)$('#checkout-panel').classList.add('hide');}
 };
 window.v10OnTableSelected=(c)=>{
   $('#v10-selected-label').textContent=`${c.tableLabel} · ${c.tableCode}`;
   $('#v10-selected-detail').textContent=`${c.orderCount} orders · Amount due ¥${Number(c.amountYen).toLocaleString('ja-JP')}`;
   $('#v10-table-select').value=String(c.tableId);
   $('#v10-selected-label').closest('.v10-selection').classList.add('v10-selected');
   if(matchMedia('(max-width:840px)').matches)$('#check-title').scrollIntoView({behavior:'smooth',block:'start'});
 };
 $('#v10-table-select').addEventListener('change',ev=>{const n=Number(ev.target.value);if(n&&typeof select==='function')select(n).catch(e=>status(e.message,true));});
 async function loadPrinters(){
   const p=await Promise.all([request('/api/v2/print/staff/devices'),request('/api/v2/staff/workflow/settings')]);
   printerList=p[0].filter(x=>x.enabled);workflow=p[1];
   const el=$('#v10-receipt-printer');const earlier=el.value;
   el.replaceChildren(new Option('Choose linked printer / プリンターを選択',''),...printerList.map(x=>new Option(`${x.name}${x.lastSeenAt?' · connected before':''}`,String(x.id))));
   const recommended=workflow.receiptDeviceId;
   const selectedId=printerList.some(x=>String(x.id)===earlier)?earlier:
     printerList.some(x=>x.id===recommended)?String(recommended):printerList.length===1?String(printerList[0].id):'';
   el.value=selectedId;
   if(!printerList.length)status('No printer linked to this restaurant. Pair this Pi in Printer settings first.',true);
   return selectedId;
 }
 async function printJobs(){return request('/api/v2/print/staff/jobs');}
 const latestReceiptJob=(arr,id)=>arr.find(j=>j.kind==='RECEIPT'&&Number(j.referenceId)===Number(id));
 function showJob(j){if(!j)return;
   const msg={PENDING:'Queued on server; waiting for Raspberry Pi.',CLAIMED:'Raspberry Pi claimed the job; waiting for acknowledgment.',SENT:'Print agent acknowledged the job as sent to printer.',UNCERTAIN:'Print delivery uncertain. Check printer and paper before trying again.'};
   status(`${msg[j.status]||j.status} · ${j.device||'printer'}`,j.status==='UNCERTAIN');
 }
 function startPolling(jobId){clearInterval(poll);let tries=0;poll=setInterval(async()=>{
   if(++tries>25){clearInterval(poll);return;}
   try{const jobs=await printJobs();const job=jobs.find(j=>String(j.id)===String(jobId));if(job){showJob(job);if(['SENT','UNCERTAIN'].includes(job.status))clearInterval(poll);}}
   catch(e){clearInterval(poll);status('Print status unavailable: '+e.message,true);}
 },2000);}
 window.v10ReceiptReady=async receipt=>{
   clearInterval(poll);activeId=Number(receipt?.id);queuedId=null;
   if(!activeId){status('No payment ID in receipt — printing disabled.',true);$('#print').disabled=true;return;}
   $('#print').disabled=false;$('#print').textContent='Send receipt to Raspberry Pi';
   try{
     await loadPrinters();const jobs=await printJobs();const existing=latestReceiptJob(jobs,activeId);
     if(existing){queuedId=existing.id;showJob(existing);if(['PENDING','CLAIMED'].includes(existing.status))startPolling(existing.id);
       $('#print').textContent='Print another copy (reprint)';}
     else if(printerList.length)status('Ready: receipt will be sent to the selected Raspberry Pi printer.');
   }catch(e){status('Printer setup failed: '+e.message,true);}
 };
 // A slow browser can finish the original cashier boot before this script runs.
 // Rehydrate from the already-loaded table list as well as listening to future updates.
 const syncExisting=()=>{try{if(typeof checks!=='undefined'&&Array.isArray(checks))window.v10SyncTables(checks,typeof selected==='undefined'?null:selected);}catch(e){console.warn('Table selector sync',e);}};
 syncExisting();setTimeout(syncExisting,100);
 const checkContainer=$('#checks');if(checkContainer){let updateScheduled=false;
   new MutationObserver(()=>{if(updateScheduled)return;updateScheduled=true;queueMicrotask(()=>{updateScheduled=false;syncExisting();});})
     .observe(checkContainer,{childList:true});}
 window.v10PrintReceipt=async receipt=>{
   if(busy||!receipt?.id)return;
   const id=Number(receipt.id),device=Number($('#v10-receipt-printer').value);
   if(!device)return status('Choose a printer. If none is listed, link your Pi to this restaurant in Printer settings.',true);
   if(!printerList.some(p=>Number(p.id)===device))return status('Selected printer does not belong to this restaurant.',true);
   if(id===activeId&&queuedId&&!confirm('This receipt already has a print job. Send a second copy?'))return;
   busy=true;$('#print').disabled=true;status('Sending print job to server…');
   try{
     const out=await request('/api/v2/print/staff/jobs','POST',{deviceId:device,kind:'RECEIPT',referenceId:id});
     queuedId=out.jobId;activeId=id;status('Queued for Raspberry Pi · '+out.status);$('#print').textContent='Print another copy (reprint)';startPolling(out.jobId);
   }catch(e){status('Cannot queue receipt: '+e.message,true);}
   finally{busy=false;$('#print').disabled=false;}
 };
})();
