'use strict';
const $=s=>document.querySelector(s);
const params=new URLSearchParams(location.search),token=params.get('t')||'';
const yen=n=>'¥'+Number(n||0).toLocaleString('ja-JP');
async function api(suffix,method='GET',body){let r=await fetch('/api/v2/v9/public/demo-pay/'+encodeURIComponent(token)+suffix,{method,headers:{'Content-Type':'application/json'},cache:'no-store',body:body?JSON.stringify(body):undefined});let j=await r.json().catch(()=>({}));if(!r.ok)throw Error(j.message||j.error||`HTTP ${r.status}`);return j;}
async function load(){try{let p=await api('');$('#restaurant').textContent=p.restaurant;$('#table').textContent=p.tableCode;$('#amount').textContent=yen(p.amountYen);
 if(p.status==='AWAITING_CASHIER'){$('#status').textContent='Sent to cashier. Waiting for cashier confirmation (demo only).';$('#submit').disabled=true;}
 else if(p.status==='CONFIRMED'){$('#status').textContent='SIMULATED payment confirmed by cashier. No money transferred.';$('#submit').disabled=true;}
 else if(p.status!=='OPEN'){$('#status').textContent='This demo QR is no longer active: '+p.status;$('#submit').disabled=true;}
 else $('#status').textContent='Enter the amount shown and submit a demo request.';
 }catch(e){$('#status').textContent=e.message;$('#submit').disabled=true;}}
$('#form').onsubmit=async e=>{e.preventDefault();$('#submit').disabled=true;try{
 const result=await api('/submit','POST',{amountYen:Number($('#input').value)});
 if(!result.accepted){$('#status').textContent='Incorrect amount. Attempts left: '+result.remainingAttempts;$('#submit').disabled=result.remainingAttempts<=0;return;}
 $('#status').textContent='Submitted for CASHIER APPROVAL. No actual payment was taken.';
 }catch(e){$('#status').textContent=e.message;$('#submit').disabled=false;}};
load();setInterval(load,3500);
