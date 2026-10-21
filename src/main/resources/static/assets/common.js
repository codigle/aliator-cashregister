"use strict";
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const yen = value => new Intl.NumberFormat("ja-JP", {style:"currency",currency:"JPY",maximumFractionDigits:0}).format(value);
const escapeHTML = value => String(value ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"})[c]);
const timeLabel = stamp => new Date(stamp).toLocaleTimeString("en-GB",{hour:"2-digit",minute:"2-digit"});
const elapsed = stamp => {const mins = Math.max(0, Math.floor((Date.now()-new Date(stamp).getTime())/60000)); return mins === 0 ? "just now" : mins + " min ago";};
const staff = () => sessionStorage.getItem("aliator_staff_token") || "";
const login = key => sessionStorage.setItem("aliator_staff_token",key);
const logout = () => {sessionStorage.removeItem("aliator_staff_token"); location.reload();};
async function api(path, options={}) {
 const headers = {"Accept":"application/json", ...options.headers};
 if (options.body) headers["Content-Type"]="application/json";
 if (options.staff) headers["X-Staff-Token"]=staff();
 const response = await fetch(path,{...options,headers,cache:"no-store"});
 let payload;try{payload=await response.json();}catch{payload={};}
 if(!response.ok){const error=new Error(payload.error || "Request failed ("+response.status+")");error.status=response.status;throw error;}
 return payload;
}
let toastTimer;
function toast(message){const el=$("#toast");if(!el)return;el.textContent=message;el.classList.add("show");clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove("show"),4200);}
function initStaffGate(ready){const gate=$("#staff-gate"),panel=$("#staff-form"),content=$("#kitchen-content") || $("#admin-content"),error=$("#login-error");
 const open=()=>{gate.classList.add("hidden");content.classList.remove("hidden");ready();};
 const verify=async()=>{await api("/api/orders?status=ACTIVE",{staff:true});};
 if(staff())verify().then(open).catch(()=>{sessionStorage.removeItem("aliator_staff_token");});
 panel.addEventListener("submit",async e=>{e.preventDefault();login($("#staff-pin").value.trim());error.textContent="";try{await verify();open();}catch(err){sessionStorage.removeItem("aliator_staff_token");error.textContent="Invalid code or server unavailable.";}});
 $("#logout")?.addEventListener("click",logout);
}
