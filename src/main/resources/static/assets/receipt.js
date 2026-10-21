"use strict";
const id=new URLSearchParams(location.search).get("id");
const button=$("#print");button.addEventListener("click",()=>window.print());
if(!/^\d+$/.test(id||"")){ $("#receipt").textContent="Invalid ticket link"; }
else api("/api/orders/"+id,{staff:true}).then(o=>{
 const lines=o.lines.map(l=>`<tr><td>${l.quantity} × ${escapeHTML(l.itemName)}${l.note?`<p>${escapeHTML(l.note)}</p>`:""}</td><td>${yen(l.quantity*l.unitPriceYen)}</td></tr>`).join("");
 $("#receipt").innerHTML=`<div class="receipt-head"><h2 style="font-size:23px;margin:0 0 7px">ALIATOR KITCHEN</h2><strong>ORDER ${escapeHTML(o.code)}</strong><p style="margin:8px 0 0">TABLE ${escapeHTML(o.tableCode.slice(1))}</p><small>${new Date(o.createdAt).toLocaleString("en-GB")}</small></div><table class="receipt-table">${lines}</table><hr><div class="total-line" style="margin:0"><span>TOTAL</span><strong>${yen(o.totalYen)}</strong></div><p style="text-align:center;font-size:10px;margin:25px 0 0">DEMO TICKET · NO PAYMENT</p>`;button.disabled=false;
}).catch(err=>{$("#receipt").textContent="Unable to show ticket: "+err.message;});
