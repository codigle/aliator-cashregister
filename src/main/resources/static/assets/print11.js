'use strict';
(() => {
  const el = id => document.getElementById(id);
  const output = (message, error=false) => {
    el('v11-msg').textContent = message;
    el('v11-msg').style.color = error ? '#c43b3b' : 'inherit';
  };
  const esc = s => String(s??'').replace(/[&<>"']/g,c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  async function request(path, method='GET', payload) {
    const r = await fetch('/api/v2/print/station/'+path,{
      credentials:'same-origin',cache:'no-store',method,
      headers:{'Content-Type':'application/json'},
      body:payload===undefined ? undefined : JSON.stringify(payload)
    });
    let data={};try{data=await r.json();}catch{}
    if(!r.ok)throw new Error(data.detail||data.message||data.error||`HTTP ${r.status}`);
    return data;
  }
  async function refresh(){
    try{
      const [owned,links] = await Promise.all([request('owned'),request('links')]);
      const old=el('v11-owned').value;
      el('v11-owned').innerHTML=owned.filter(x=>x.enabled).map(x=>`<option value="${Number(x.id)}">${esc(x.name)} (#${Number(x.id)} · ${Number(x.sharedWith)} links)</option>`).join('') || '<option value="">No owned station. Link from another restaurant, or create a printer above.</option>';
      if([...el('v11-owned').options].some(o=>o.value===old))el('v11-owned').value=old;
      el('v11-linked').innerHTML=links.filter(x=>x.enabled).map(x=>`<div class="device-row"><span>${esc(x.name)} (#${Number(x.deviceId)})</span><span>Shared printer · ${x.lastSeenAt?'Connected':'Not yet connected'}</span><button class="btn outline sm" type="button" data-unlink="${Number(x.deviceId)}">Unlink</button></div>`).join('') || '<p class="muted">No shared printer on this restaurant yet.</p>';
      el('v11-linked').querySelectorAll('[data-unlink]').forEach(button => button.onclick=async()=>{
        if(!confirm('Disconnect this restaurant from the shared physical printer? Queued jobs will not automatically move.'))return;
        try{await request('revoke','POST',{logicalDeviceId:Number(button.dataset.unlink)});output('Printer disconnected from this restaurant.');await refresh();window.location.reload();}
        catch(e){output(e.message,true);}
      });
    }catch(e){output('Printer sharing: '+e.message,true);}
  }
  el('v11-invite').onclick=async()=>{
    const deviceId=Number(el('v11-owned').value);
    if(!deviceId){output('First select an existing physical station.',true);return;}
    try{
      const data=await request('invite','POST',{deviceId});
      el('v11-code').value=data.inviteCode;
      el('v11-code-box').classList.remove('hide');
      output('Invitation created. Valid for 10 minutes and one use only. Do not post it publicly.');
    }catch(e){output(e.message,true);}
  };
  el('v11-copy').onclick=async()=>{
    try{await navigator.clipboard.writeText(el('v11-code').value);output('Invitation copied; open other restaurant owner session to join.');}
    catch(e){output('Copy invitation manually from the box.');}
  };
  el('v11-join').onclick=async()=>{
    const inviteCode=el('v11-join-code').value.trim();
    if(!/^[A-Za-z0-9_-]{43}$/.test(inviteCode)){output('Paste the complete 43-character invitation.',true);return;}
    try{
      const d=await request('join','POST',{inviteCode});
      el('v11-join-code').value='';
      output('Linked successfully as printer #'+Number(d.deviceId)+'. Select it under Workflow Settings, then test one label. Raspberry Pi token stays unchanged.');
      await refresh();
      if(typeof window.refresh==='function')await window.refresh();
    }catch(e){output(e.message,true);}
  };
  refresh();
})();
