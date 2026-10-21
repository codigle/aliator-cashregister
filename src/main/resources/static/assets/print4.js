'use strict';
const $=s=>document.querySelector(s);
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const apiBase='/api/v2/print/staff';let devices=[],orders=[],payments=[];
async function api(path,method='GET',data){const r=await fetch(apiBase+path,{method,credentials:'same-origin',cache:'no-store',headers:{'Content-Type':'application/json'},body:data===undefined?undefined:JSON.stringify(data)});let obj;try{obj=await r.json()}catch{obj={}}if(!r.ok)throw Error(obj.detail||obj.error||`HTTP ${r.status}`);return obj;}
function show(msg,bad=false){$('#message').style.color=bad?'#c43b3b':'inherit';$('#message').textContent=msg;}
async function refresh(){try{const [ds,js,os,ps]=await Promise.all([api('/devices'),api('/jobs'),api('/orders'),api('/payments')]);devices=ds;orders=os;payments=ps;$('#devices').innerHTML=ds.map(d=>`<div class="device-row"><span>${esc(d.name)} #${d.id}</span><span>${d.enabled?'Enabled':'Disabled'} · ${d.lastSeenAt?'Last connected '+esc(new Date(d.lastSeenAt).toLocaleString()):'Not connected'}</span></div>`).join('')||'<p class="muted">No printers yet.</p>';let old=$('#device-select').value;$('#device-select').innerHTML=ds.filter(x=>x.enabled).map(x=>`<option value="${x.id}">${esc(x.name)} (#${x.id})</option>`).join('');if(ds.some(x=>String(x.id)===old))$('#device-select').value=old;$('#job-count').textContent=js.length+' recent jobs';$('#jobs').innerHTML=js.map(j=>`<tr><td>${esc(new Date(j.createdAt).toLocaleString())}</td><td>${esc(j.device)}</td><td>${esc(j.kind)}</td><td>${j.referenceId??'—'}</td><td><span class="status-pill">${esc(j.status)}</span></td></tr>`).join('')||'<tr><td colspan="5">No jobs yet</td></tr>';renderRef(); updateSettingsDevices();}catch(e){if(String(e).includes('401')){window.CR5?.requireSignIn();show('Staff session expired. Sign in to continue.',true);return}show(e.message,true)}}
function renderRef(){const kind=$('#job-kind').value;$('#reference-wrap').classList.toggle('hide',kind==='TEST');if(kind==='TEST')return;const a=kind==='ORDER'?orders:payments;$('#reference-select').innerHTML=a.map(x=>`<option value="${x.id}">#${x.id} · ${esc(x.tableCode)} · ¥${x.totalYen??x.amountYen??0}</option>`).join('')||'<option value="">No records available</option>'}
$('#refresh').onclick=refresh;$('#job-kind').onchange=renderRef;
$('#device-form').onsubmit=async e=>{e.preventDefault();if(!confirm('Create a new private printer credential?'))return;try{const d=await api('/devices','POST',{name:$('#device-name').value});$('#token').value=d.token;$('#secret').classList.remove('hide');show('Printer registered. Copy its one-time token into the Pi setup.');await refresh()}catch(err){show(err.message,true)}};
$('#copy-token').onclick=async()=>{try{await navigator.clipboard.writeText($('#token').value);show('Token copied. Store it securely on Pi; do not paste it into chat.')}catch{show('Select and copy the token manually.')}};
$('#job-form').onsubmit=async e=>{e.preventDefault();const deviceId=Number($('#device-select').value),kind=$('#job-kind').value,referenceId=kind==='TEST'?null:Number($('#reference-select').value);if(!deviceId||kind!=='TEST'&&!referenceId)return show('Select a printer and valid record.',true);if(!confirm(`Queue exactly ONE ${kind} label on the real printer?`))return;try{const job=await api('/jobs','POST',{deviceId,kind,referenceId});show('Queued: '+job.jobId+'. Watch status and physical printer.');await refresh()}catch(err){show(err.message,true)}};
refresh();setInterval(refresh,12000);


/* V5 per-device thermal paper/layout settings. No printing occurs until manually queued. */
let activeProfileId=null, savedProfile=null,settingsDirty=false;
const profileFields={widthMm:'#label-width',heightMm:'#label-height',gapMm:'#label-gap',header:'#label-header',footer:'#label-footer',density:'#label-density',showTable:'#label-show-table',alignment:'#label-alignment'};
function settingsDeviceId(){return Number($('#settings-device').value||0)}
function updateSettingsDevices(){
 const s=$('#settings-device'),selected=s.value;
 s.innerHTML=devices.filter(d=>d.enabled).map(d=>`<option value="${d.id}">${esc(d.name)}</option>`).join('');
 if(devices.some(d=>String(d.id)===selected&&d.enabled))s.value=selected;
 else if(devices.some(d=>String(d.id)===$('#device-select').value&&d.enabled))s.value=$('#device-select').value;
 const id=settingsDeviceId();
 if(id && id!==activeProfileId)loadSettings().catch(e=>show(e.message,true));
 else if(!id){activeProfileId=null;$('#settings-state').textContent='No active printer';}
}
function fillSettings(profile){for(const [k,selector] of Object.entries(profileFields)){const el=$(selector);if(k==='showTable')el.checked=Boolean(profile[k]);else el.value=profile[k]??'';}settingsDirty=false;renderPreview();}
function readSettings(){let p={};for(const [k,selector] of Object.entries(profileFields)){const el=$(selector);p[k]=k==='showTable'?el.checked:['widthMm','heightMm','gapMm'].includes(k)?Number(el.value):el.value;}return p;}
async function loadSettings(){const id=settingsDeviceId();if(!id)return;activeProfileId=id;const profile=await api(`/devices/${id}/settings`);if(settingsDeviceId()!==id)return;savedProfile=profile;fillSettings(profile);$('#settings-state').textContent='Saved settings';}
function renderPreview(){const p=readSettings(),w=Math.max(40,Math.min(110,p.widthMm||100)),h=Math.max(25,Math.min(200,p.heightMm||150));
 const scale=Math.min(3.05,260/w),paper=$('#label-preview');paper.style.width=Math.round(w*scale)+'px';paper.style.minHeight=Math.round(h*scale)+'px';paper.className='label-preview density-'+p.density;paper.style.textAlign=(p.alignment||'LEFT').toLowerCase();
 $('#preview-dimensions').textContent=w+' × '+h+' mm · gap '+p.gapMm+' mm';
 $('#preview-head').textContent=p.header||'';$('#preview-foot').textContent=p.footer||'';$('#preview-table').hidden=!p.showTable;
 const kind=$('#job-kind').value,ref=$('#reference-select').value;
 $('#preview-order').textContent=kind==='TEST'?'TEST LABEL':(kind==='RECEIPT'?'PAID #':'ORDER #')+(ref||'—');
 $('#preview-items').textContent=kind==='TEST'?'2x Yakitori\n1x Karaage\n1x Edamame':kind==='RECEIPT'?'Payment receipt · sample layout':'Kitchen ticket · sample layout';
 $('#preview-items').style.whiteSpace='pre-line';
}
$('#settings-device').addEventListener('change',()=>{activeProfileId=null;loadSettings().catch(e=>show(e.message,true));});
for(const selector of Object.values(profileFields)){$(selector).addEventListener('input',()=>{settingsDirty=true;$('#settings-state').textContent='Unsaved changes';renderPreview();});}
$('#restore-label-settings').onclick=()=>{if(savedProfile)fillSettings(savedProfile)};
$('#job-kind').addEventListener('change',renderPreview);$('#reference-select').addEventListener('change',renderPreview);
$('#label-form').onsubmit=async e=>{
 e.preventDefault();const id=settingsDeviceId(),p=readSettings();
 if(!id)return show('Select a printer first.',true);
 if(p.widthMm<40||p.widthMm>110||p.heightMm<25||p.heightMm>200||p.gapMm<0||p.gapMm>10)return show('Invalid paper dimensions.',true);
 try{await api(`/devices/${id}/settings`,'PUT',p);savedProfile=p;settingsDirty=false;$('#settings-state').textContent='Saved settings';show('Printer settings saved. Queue a single TEST label to verify physical output.');}
 catch(e){show(e.message,true)}
};
renderPreview();
