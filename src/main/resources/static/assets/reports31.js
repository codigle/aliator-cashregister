"use strict";
const $=s=>document.querySelector(s);
const $$=s=>[...document.querySelectorAll(s)];
const yen=n=>'¥'+Number(n||0).toLocaleString('ja-JP');
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]);
const integer=n=>Number(n||0).toLocaleString('ja-JP');
let period='day', metric='revenue', report=null;
async function api(path){
  const r=await fetch('/api/v2/staff/'+path,{credentials:'same-origin',cache:'no-store'});
  let j={};try{j=await r.json()}catch{}
  if(!r.ok)throw new Error('HTTP '+r.status+': '+(j.error||j.message||'Request failed'));
  return j;
}
function minutes(n){return n===null||n===undefined||Number(n)===0?'—':Number(n).toLocaleString(undefined,{maximumFractionDigits:1})+' min';}
function hint(msg,kind='info'){const n=$('#report-hint');n.textContent=msg;n.dataset.kind=kind;}
function label(x){
  if(period==='year')return x.slice(0,4);
  if(period==='month')return x.slice(0,7);
  if(period==='day'||period==='week')return x.slice(5,10);
  return x.slice(11,16);
}
function renderTopItems(el,arr,empty){
  const max=Math.max(1,...arr.map(x=>Number(x.quantity)));
  $(el).innerHTML=arr.length?arr.map((x,i)=>`<div class="top-item"><div class="split-heading"><span>${i+1}. ${esc(x.name)}</span><b>${integer(x.quantity)} pcs</b></div><div class="meter"><span style="width:${100*Number(x.quantity)/max}%"></span></div><small class="muted">${yen(x.grossYen)}</small></div>`).join(''):`<p class="muted">${esc(empty)}</p>`;
}
function draw(){
  if(!report)return;
  const series=report.series||[];
  const data=series.map(x=>Number(x[metric]||0));
  const max=Math.max(1,...data);
  const any=data.some(x=>x>0);
  const format=metric==='orders'?integer:yen;
  const chart=$('#chart');
  chart.classList.toggle('report-empty',!any);
  chart.innerHTML=series.map(x=>{
    const val=Number(x[metric]||0);
    const h=val>0?Math.max(4,100*val/max):0;
    return `<div class="bar-col" title="${esc(x.label)}: ${format(val)}"><strong>${format(val)}</strong><div class="bar-rail"><span style="height:${h}%"></span></div><small>${esc(label(x.label))}</small></div>`;
  }).join('');
  const emptyNote=document.createElement('p');
  emptyNote.className='report-empty-note';
  if(!any){
    emptyNote.textContent=metric==='revenue'?'No checkout recorded in this period yet. The orders chart may already have activity.':'No customer orders recorded in this period yet. Test orders do not count.';
    chart.appendChild(emptyNote);
  }
  $('#points tbody').innerHTML=series.map(x=>`<tr><td>${esc(x.label)}</td><td>${yen(x.revenue)}</td><td>${integer(x.checks)}</td><td>${integer(x.orders)}</td><td>${yen(x.orderedYen)}</td></tr>`).join('')||'<tr><td colspan="5">No reporting periods available</td></tr>';
}
function paint(d){
  $('#revenue').textContent=yen(d.totals.revenueYen);
  $('#checks').textContent=integer(d.totals.paidChecks);
  $('#seating').textContent=minutes(d.totals.avgSeatMinutes);
  $('#prep').textContent=minutes(d.kitchen.avgPrepMinutes);
  $('#placed').textContent=integer(d.activity.placedOrders);
  $('#outstanding').textContent=yen(d.openChecks.outstandingYen);
  $('#open-tables').textContent=integer(d.openChecks.openTables);
  $('#test-orders').textContent=integer(d.activity.demoOrders);
  $('#kitchen-minutes').textContent=minutes(d.kitchen.avgKitchenMinutes);
  $('#prep-minutes').textContent=minutes(d.kitchen.avgPrepMinutes);
  $('#completed').textContent=integer(d.kitchen.completedOrders);
  $('#tz').textContent=d.timezone;
  $('#period-info').textContent='Revenue is paid only; order value includes unpaid orders · '+({hour:'last 24 hours',day:'last 30 days',week:'last 12 weeks',month:'last 12 months',year:'last 5 years'})[period];
  $('#activity-note').textContent='Cancelled orders: '+integer(d.activity.cancelledOrders)+'. Placed order value: '+yen(d.activity.placedValueYen)+'. Open balance is current, regardless of reporting period.';
  const hasPayments=Number(d.totals.paidChecks)>0, hasOrders=Number(d.activity.placedOrders)>0, hasTests=Number(d.activity.demoOrders)>0;
  if(!hasPayments&&hasOrders){metric='orders';hint('Customer orders found. No checkouts recorded yet, so revenue remains ¥0. Showing order activity instead.','info');}
  else if(!hasPayments&&hasTests&&!hasOrders){metric='orders';hint('Only system-test orders exist. These are excluded from business revenue. Try a QR customer order, then check out in the Cashier.','info');}
  else if(!hasPayments&&!hasOrders){metric='orders';hint('No customer orders or recorded payments in this period. Try the QR menu to generate your first order.','info');}
  else {hint('Reporting live activity. Recorded revenue counts simulated cashier checkouts only.','success');}
  $$('#report-mode [data-metric]').forEach(x=>x.classList.toggle('active',x.dataset.metric===metric));
  renderTopItems('#top-ordered',d.topOrdered||[],'No customer orders yet.');
  renderTopItems('#top-items',d.topProducts||[],'No paid menu items yet.');
  draw();
}
async function load(){
  try{hint('Loading analytics…');const d=await api('reports?period='+encodeURIComponent(period));
    if(!Array.isArray(d.series)||!d.totals||!d.kitchen||!d.activity||!d.openChecks||!Array.isArray(d.topOrdered))throw new Error('Unexpected analytics response. Ensure backend v3.1 deployed.');
    report=d;paint(d);
  }catch(e){
    if(e.message.includes('401')){window.CR5?.requireSignIn();return;}
    hint('Unable to load reports: '+e.message,'error');
    const toast=$('#toast');toast.textContent=e.message;toast.classList.add('show');
  }
}
$$('[data-period]').forEach(b=>b.onclick=()=>{period=b.dataset.period;metric='revenue';$$('[data-period]').forEach(x=>x.classList.toggle('active',x===b));load();});
$$('[data-metric]').forEach(b=>b.onclick=()=>{metric=b.dataset.metric;$$('[data-metric]').forEach(x=>x.classList.toggle('active',x===b));draw();});
$('#refresh').onclick=load;
load();
