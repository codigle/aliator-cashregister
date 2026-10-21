'use strict';
/* V13 customer UI chrome localized separately from staff; never overwrites menu item translations. */
(()=>{
const q=s=>document.querySelector(s);
const i18n={
 ja:{title:'お席からのご注文',language:'表示言語',badge:'テーブル注文',cover:'お席からご注文',eyebrow:'お料理・お飲み物',currency:'デモ · 円',photos:'料理画像：',credits:'画像の出典',total:'合計',send:'注文を送信する →',note:'デモ：お会計はスタッフが処理します。',request:'ご要望（任意）',requestPlaceholder:'例：ねぎ抜き',back:'← メニューに戻る',history:'注文履歴',statuses:{NEW:'受付済み',PREPARING:'調理中',READY:'提供待ち',SERVED:'提供済み'}},
 en:{title:'Order from your table',language:'Language',badge:'Table menu',cover:'ORDER FROM YOUR TABLE',eyebrow:'IZAKAYA MENU',currency:'Demo · JPY',photos:'Food photography: ',credits:'sources & credits',total:'Total',send:'Send order to kitchen →',note:'Demo: staff will handle your payment.',request:'Special requests (optional)',requestPlaceholder:'e.g. No scallions',back:'← Back to menu',history:'Order history',statuses:{NEW:'Received',PREPARING:'Preparing',READY:'Ready',SERVED:'Served'}},
 zh:{title:'扫码点餐',language:'语言',badge:'桌面点餐',cover:'扫码点餐',eyebrow:'菜品和饮料',currency:'演示 · 日元',photos:'餐品图片：',credits:'图片来源',total:'合计',send:'提交订单 →',note:'演示：由工作人员处理付款。',request:'特别要求（可选）',requestPlaceholder:'例如：不要葱',back:'← 返回菜单',history:'订单记录',statuses:{NEW:'已接单',PREPARING:'制作中',READY:'待上菜',SERVED:'已上菜'}},
 ko:{title:'테이블 주문',language:'표시 언어',badge:'테이블 메뉴',cover:'테이블에서 주문',eyebrow:'음식 및 음료',currency:'데모 · 엔',photos:'음식 사진: ',credits:'이미지 출처',total:'합계',send:'주문 전송 →',note:'데모: 결제는 직원이 처리합니다.',request:'요청 사항 (선택)',requestPlaceholder:'예: 파 제외',back:'← 메뉴로 돌아가기',history:'주문 내역',statuses:{NEW:'접수됨',PREPARING:'조리 중',READY:'준비 완료',SERVED:'제공 완료'}},
 tr:{title:'Masadan sipariş',language:'Dil',badge:'Masa menüsü',cover:'MASADAN SİPARİŞ',eyebrow:'YEMEKLER VE İÇECEKLER',currency:'Demo · JPY',photos:'Yemek görselleri: ',credits:'görsel kaynakları',total:'Toplam',send:'Siparişi gönder →',note:'Demo: ödeme personel tarafından alınır.',request:'Özel istek (isteğe bağlı)',requestPlaceholder:'Örn. soğansız',back:'← Menüye dön',history:'Sipariş geçmişi',statuses:{NEW:'Alındı',PREPARING:'Hazırlanıyor',READY:'Hazır',SERVED:'Servis edildi'}}
};
function locale(){const l=q('#locale')?.value||'ja';return i18n[l]?l:'ja'}
const set=(sel,txt)=>{const el=q(sel);if(el)el.textContent=txt};
function apply(){const l=locale(),t=i18n[l];document.documentElement.lang=l==='zh'?'zh-CN':l;
 document.title=t.title+' · CashRegister';
 set('#language-label',t.language);set('#guest-cover-label',t.cover);set('#menu-eyebrow',t.eyebrow);
 const photo=q('.guest-main > .tiny.muted');if(photo){const a=photo.querySelector('a');
 if(a){photo.firstChild.textContent=t.photos+' ';a.textContent=t.credits;}}
 const total=q('.cart-bottom .split-heading span');if(total)total.textContent=t.total;
 const note=q('.cart-bottom > p.tiny');if(note)note.textContent=t.note;
 const special=q('label[for="item-note"]');if(special)special.textContent=t.request;
 const req=q('#item-note');if(req)req.placeholder=t.requestPlaceholder;
 const back=q('#v10-history-back');if(back)back.textContent=t.back;
 const ttl=q('#v10-history-title');if(ttl)ttl.textContent=t.history;
 // V9 updates this button with order total whenever the cart changes; only update the idle label.
 const send=q('#send-order');if(send&&!send.disabled)send.textContent=t.send;
 const statusRoot=q('#my-orders');if(statusRoot){const nodes=statusRoot.querySelectorAll('.order b');for(const el of nodes){
 const original=el.textContent;const match=/(#\d+\s*·\s*)(NEW|PREPARING|READY|SERVED|受付済み|調理中|提供待ち|提供済み|Received|Preparing|Ready|Served)$/.exec(original);
 if(match){
  const aliases={受付済み:'NEW',調理中:'PREPARING',提供待ち:'READY',提供済み:'SERVED',Received:'NEW',Preparing:'PREPARING',Ready:'READY',Served:'SERVED'};
  const status=aliases[match[2]]||match[2];
  const next=match[1]+(t.statuses[status]||status);
  if(original!==next)el.textContent=next;
 }
 }}
}
function boot(){q('#locale')?.addEventListener('change',apply);
 const orders=q('#my-orders');if(orders){let pending=false;new MutationObserver(()=>{
 if(pending)return;pending=true;queueMicrotask(()=>{pending=false;apply()});
 }).observe(orders,{subtree:true,childList:true});}
 apply();}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot,{once:true});else boot();
})();
