"use strict";
/* Aliator CashRegister five-language UI. English is the canonical source key. */
(()=>{
const catalog={
'Workspace':['ワークスペース','工作台','워크스페이스','Çalışma alanı'],
'Restaurant Workspace':['店舗管理','餐厅管理','매장 관리','Restoran yönetimi'],
'Owner Analytics':['オーナー分析','经营分析','매출 분석','İşletme analizi'],
'Cashier · POS':['レジ・POS','收银台','계산대','Kasa / POS'],
'Printer Station':['プリンター管理','打印机设置','프린터 설정','Yazıcı istasyonu'],
'Secure restaurant access':['店舗専用ログイン','商户安全登录','매장 보안 로그인','Güvenli restoran girişi'],
'Manage your restaurant.':['店舗を管理する','管理您的餐厅','매장 관리','Restoranınızı yönetin'],
'Email':['メール','电子邮箱','이메일','E-posta'], 'Password':['パスワード','密码','비밀번호','Şifre'],
'Sign in to workspace →':['ログイン →','登录 →','로그인 →','Panele giriş →'],
'Sign out':['ログアウト','退出登录','로그아웃','Çıkış yap'],
'Register a new restaurant · Email verification':['新しい店舗を登録・メール認証','注册新餐厅 · 邮箱验证','새 음식점 등록 · 이메일 인증','Yeni restoran kaydı · E-posta doğrulama'],
'Send verification code →':['認証コードを送信 →','发送验证码 →','인증 코드 전송 →','Doğrulama kodu gönder →'],
'Registration email':['登録メールアドレス','注册邮箱','등록 이메일','Kayıt e-postası'],
'Six-digit verification code':['6桁の認証コード','六位验证码','6자리 인증 코드','6 haneli doğrulama kodu'],
'Verify email & create restaurant →':['認証して店舗を作成 →','验证邮箱并创建餐厅 →','이메일 인증 후 매장 만들기 →','Doğrula ve restoranı oluştur →'],
'Edit details / resend code':['詳細を変更・再送信','修改信息 / 重新发送','정보 수정 / 코드 재전송','Bilgileri düzenle / kodu tekrar gönder'],

'Checking your restaurant session…':['ログイン状態を確認中…','正在验证登录状态…','로그인 확인 중…','Oturum doğrulanıyor…'],
'Overview':['概要','总览','개요','Genel bakış'],
'Menu management':['メニュー管理','菜单管理','메뉴 관리','Menü yönetimi'],
'Tables & QR':['テーブルとQR','餐桌和二维码','테이블 및 QR','Masalar ve QR'],
'Kitchen':['キッチン','后厨','주방','Mutfak'],
'System test':['システムテスト','系统测试','시스템 테스트','Sistem testi'],
'Cashier / POS':['レジ・POS','收银台','계산대','Kasa / POS'],
'Owner reports':['経営レポート','经营报表','매출 보고서','Patron raporları'],
'Printer settings':['印刷設定','打印设置','인쇄 설정','Yazıcı ayarları'],
'Edit menu':['メニューを編集','编辑菜单','메뉴 편집','Menüyü düzenle'],
'Orders':['注文','订单','주문','Siparişler'],
'Open tables':['利用中の席','在用餐桌','사용 중인 테이블','Açık masalar'],
'Kitchen active':['調理中の注文','厨房处理中','주방 진행 중','Mutfakta aktif'],
'Revenue reports':['売上レポート','营收报表','매출 보고서','Ciro raporları'],
'Add menu item':['メニューを追加','添加菜品','메뉴 추가','Ürün ekle'],
'Save menu item':['保存','保存菜品','메뉴 저장','Ürünü kaydet'],
'Cancel':['キャンセル','取消','취소','İptal'],
'Product name':['商品名','菜品名称','메뉴명','Ürün adı'],
'Category':['カテゴリ','分类','카테고리','Kategori'],
'Price (JPY)':['価格（円）','价格（日元）','가격 (엔)','Fiyat (JPY)'],
'Description':['説明','描述','설명','Açıklama'],
'Japanese name':['日本語名','日语名称','일본어 이름','Japonca adı'],
'Japanese description':['日本語の説明','日语描述','일본어 설명','Japonca açıklama'],
'Photo URL (Wikimedia Commons or Unsplash)':['画像URL','图片地址','이미지 URL','Fotoğraf bağlantısı'],
'Photo credit':['画像の出典','图片来源','사진 출처','Fotoğraf kaynağı'],
'Available for ordering':['注文可能','可供点餐','주문 가능','Siparişe açık'],
'Add table':['テーブルを追加','添加餐桌','테이블 추가','Masa ekle'],
'Table code':['席コード','桌号','테이블 코드','Masa kodu'],
'Display name':['表示名','显示名称','표시 이름','Görünen ad'],
'Refresh':['更新','刷新','새로고침','Yenile'],
'Hourly':['時間別','每小时','시간별','Saatlik'],
'Daily':['日別','每日','일별','Günlük'],
'Weekly':['週別','每周','주별','Haftalık'],
'Monthly':['月別','每月','월별','Aylık'],
'Yearly':['年別','每年','연도별','Yıllık'],
'Revenue':['売上','营收','매출','Ciro'],
'Order value':['注文金額','订单金额','주문 금액','Sipariş tutarı'],
'Owner revenue & operations':['売上・店舗運営レポート','营收与运营','매출 및 운영 분석','Ciro ve operasyon raporları'],
'Popular ordered items':['人気商品','热销菜品','인기 메뉴','Çok satan ürünler'],
'Performance over time':['期間別実績','时段表现','기간별 실적','Zamana göre performans'],
'Recorded revenue · paid only':['入金済み売上','已收款营收','결제 완료 매출','Tahsil edilmiş ciro'],
'Outstanding · open tables':['未収金額','未结账金额','미수금','Açık hesap tutarı'],
'Avg. kitchen prep':['平均調理時間','平均备餐时间','평균 조리 시간','Ortalama hazırlık süresi'],
'Kitchen performance':['調理パフォーマンス','厨房效率','주방 성과','Mutfak performansı'],
'How metrics work':['集計方法','统计说明','지표 안내','Raporların hesaplanması'],
'Close the table. Record the sale.':['会計してテーブルを閉じる','结账并关闭餐桌','결제 후 테이블 닫기','Ödeme al ve hesabı kapat'],
'Open guest checks':['会計待ち','待结账餐桌','결제 대기 테이블','Açık hesaplar'],
'Choose a table':['席を選択','选择餐桌','테이블 선택','Masa seç'],
'Amount due':['お会計金額','应付金额','결제 금액','Ödenecek tutar'],
'Payment method':['支払方法','付款方式','결제 방법','Ödeme yöntemi'],
'Cash received (JPY)':['お預かり金額（円）','实收现金（日元）','받은 현금 (엔)','Alınan nakit (JPY)'],
'Change due':['お釣り','找零','거스름돈','Para üstü'],
'Internal note / reference (optional)':['内部メモ','内部备注','내부 메모','İç not / referans'],
'Record payment & close table':['支払記録・会計完了','记录付款并结账','결제 기록 및 마감','Ödemeyi kaydet ve hesabı kapat'],
'Create printer access':['プリンターを登録','添加打印机','프린터 등록','Yazıcı erişimi oluştur'],
'Connect Raspberry Pi':['Raspberry Piに接続','连接树莓派','라즈베리파이 연결','Raspberry Pi bağlantısı'],
'Send a label':['ラベル印刷','打印标签','라벨 인쇄','Etiket gönder'],
'Device name':['プリンター名','设备名称','장치 이름','Cihaz adı'],
'Printer':['プリンター','打印机','프린터','Yazıcı'],
'Print type':['印刷種類','打印类型','인쇄 유형','Yazdırma türü'],
'Test label (safe pilot)':['テストラベル','测试标签','테스트 라벨','Test etiketi'],
'Kitchen order ticket':['厨房伝票','厨房订单','주방 주문표','Mutfak fişi'],
'Simulated payment receipt':['会計レシート（デモ）','模拟付款收据','모의 결제 영수증','Demo ödeme fişi'],
'Queue ONE label':['ラベルを1枚送信','发送1张标签','라벨 1장 전송','Tek etiket gönder'],
'Print job history':['印刷履歴','打印记录','인쇄 기록','Yazdırma geçmişi'],
'Label layout & paper':['用紙・ラベル設定','标签与纸张设置','라벨 및 용지 설정','Etiket tasarımı ve kağıt'],
'Save printer settings':['印刷設定を保存','保存打印设置','설정 저장','Yazıcı ayarlarını kaydet'],
'Label preview':['ラベルプレビュー','标签预览','라벨 미리보기','Etiket önizlemesi'],
'Width (mm)':['幅（mm）','宽度（毫米）','너비(mm)','Genişlik (mm)'],
'Height (mm)':['高さ（mm）','高度（毫米）','높이(mm)','Yükseklik (mm)'],
'Gap (mm)':['ラベル間隔（mm）','标签间距（毫米）','간격(mm)','Etiket aralığı (mm)'],
'Header':['見出し','标题','제목','Başlık'],
'Footer':['フッター','页脚','바닥글','Alt bilgi'],
'Text density':['文字間隔','文字密度','텍스트 간격','Yazı aralığı'],
'Compact':['コンパクト','紧凑','압축','Sıkışık'],
'Normal':['標準','标准','보통','Normal'],
'Large':['大きめ','较大','크게','Büyük'],
'Show table number':['席番号を表示','显示桌号','테이블 번호 표시','Masa numarasını göster'],
'Order from your table':['お席からのご注文','扫码点餐','테이블 주문','Masadan sipariş'],
'No orders yet.':['注文はまだありません','暂无订单','주문 내역 없음','Henüz sipariş yok'],
'No printers yet.':['プリンターが未登録です','尚未添加打印机','등록된 프린터 없음','Henüz yazıcı yok'],
'Label settings affect future jobs only.':['設定は今後の印刷ジョブにのみ適用されます。','设置仅影响之后的打印任务。','새 인쇄 작업부터 적용됩니다.','Ayarlar yalnızca yeni baskılara uygulanır.'],
'New orders':['新規注文','新订单','신규 주문','Yeni siparişler'],'Preparing':['調理中','制作中','조리 중','Hazırlanıyor'],'Ready to serve':['提供待ち','待上菜','서빙 대기','Servise hazır'],'Served':['提供済み','已上菜','서빙 완료','Servis edildi'],'No orders':['注文なし','无订单','주문 없음','Sipariş yok'],'Available':['注文可能','可点餐','주문 가능','Satışta'],'Hidden':['非表示','已隐藏','숨김','Gizli'],'Edit':['編集','编辑','편집','Düzenle'],'Language':['言語','语言','언어','Dil']
};
/* ALIATOR_V8_TRANSLATIONS */
Object.assign(catalog,{"Restaurant operations": ["店舗運営", "餐厅运营", "매장 운영", "Restoran operasyonları"], "Restaurant control center": ["店舗運営センター", "餐厅控制台", "매장 운영 센터", "Restoran kontrol merkezi"], "Workflow & automation ↗": ["運用・自動印刷 ↗", "流程与自动打印 ↗", "운영 및 자동 인쇄 ↗", "İş akışı ve otomasyon ↗"], "Every order, menu update and kitchen ticket stays inside your restaurant.": ["注文・メニュー・厨房伝票は店舗ごとに管理されます。", "订单、菜单和厨房小票按餐厅独立管理。", "주문·메뉴·주방 주문서는 매장별로 분리됩니다.", "Sipariş, menü ve mutfak fişleri restoran bazında ayrılır."], "Each restaurant has its own menu, tables, orders and kitchen workspace.": ["店舗ごとにメニュー・席・注文・厨房を個別管理します。", "每家餐厅拥有独立的菜单、桌台、订单和厨房后台。", "매장마다 독립된 메뉴·테이블·주문·주방 관리가 제공됩니다.", "Her restoranın menüsü, masaları, siparişleri ve mutfağı ayrıdır."], "Register a new restaurant (invite required)": ["新規店舗登録（招待制）", "注册新餐厅（需要邀请）", "새 매장 등록(초대 필요)", "Yeni restoran kaydı (davetiye gerekli)"], "Create restaurant": ["店舗を登録", "创建餐厅", "매장 만들기", "Restoran oluştur"], "The public showcase is a demo. No card payments are processed.": ["公開デモでは実際のカード決済は行われません。", "公开演示不处理真实银行卡支付。", "공개 데모에서는 실제 카드 결제를 처리하지 않습니다.", "Herkese açık demo gerçek kart ödemesi almaz."], "Orders refresh automatically every four seconds.": ["注文は4秒ごとに自動更新されます。", "订单每4秒自动刷新。", "주문은 4초마다 자동 갱신됩니다.", "Siparişler dört saniyede bir yenilenir."], "Refresh now": ["今すぐ更新", "立即刷新", "지금 새로고침", "Şimdi yenile"], "Kitchen display": ["キッチン画面", "厨房显示屏", "주방 화면", "Mutfak ekranı"], "Restaurant Workspace": ["店舗管理", "餐厅后台", "매장 관리", "Restoran paneli"], "Tables and QR codes": ["テーブル・QRコード", "桌台与二维码", "테이블 및 QR 코드", "Masalar ve QR kodları"], "Every table has its own current QR code. Closing a bill rotates the code.": ["各テーブル専用QRコードは会計完了時に更新されます。", "每桌二维码在结账完成后更新。", "각 테이블 QR 코드는 결제 후 갱신됩니다.", "Her masanın QR kodu vardır; hesap kapanınca yenilenir."], "Changes become available to guests immediately.": ["変更はすぐにお客様へ反映されます。", "修改将立即对顾客生效。", "변경 사항이 고객에게 바로 적용됩니다.", "Değişiklikler müşterilere hemen yansır."], "System test lab": ["システムテスト", "系统测试", "시스템 테스트", "Sistem testleri"], "Create test tickets without charging a customer or opening a guest session.": ["お客様の会計や利用セッションを作らずにテスト注文を作成できます。", "无需收费或开启顾客会话即可生成测试订单。", "고객 결제나 테이블 세션 없이 시험 주문을 만들 수 있습니다.", "Müşteri hesabı açmadan veya ödeme almadan test siparişi oluştur."], "Inject a test order": ["テスト注文を作成", "创建测试订单", "테스트 주문 생성", "Test siparişi oluştur"], "Send demo order to kitchen": ["デモ注文を厨房へ送信", "将演示订单发送到厨房", "데모 주문 주방으로 보내기", "Demo siparişini mutfağa gönder"], "Quantity": ["数量", "数量", "수량", "Adet"], "Table": ["テーブル", "桌台", "테이블", "Masa"], "Product": ["商品", "商品", "상품", "Ürün"], "Loading…": ["読み込み中…", "加载中…", "불러오는 중…", "Yükleniyor…"], "Select an open table on the left.": ["左側からご利用中のテーブルを選択してください。", "请从左侧选择一张正在使用的桌台。", "왼쪽에서 이용 중인 테이블을 선택하세요.", "Soldan açık bir masa seç."], "Back to cashier": ["レジに戻る", "返回收银台", "계산대로 돌아가기", "Kasaya dön"], "Print receipt preview": ["レシートプレビューを印刷", "打印收据预览", "영수증 미리보기 인쇄", "Fiş önizlemesini yazdır"], "Close without payment (only when no billable orders)": ["未精算注文がない場合のみ会計なしで閉じる", "仅无应付订单时无支付关闭", "청구 주문이 없을 때만 결제 없이 종료", "Ödeme olmadan kapat (yalnızca sipariş yoksa)"], "Cash / card / other are recorded for demonstration only. No real payment is collected.": ["現金・カード等はデモ記録であり実際の決済ではありません。", "现金、银行卡等仅作演示记录，不收取实际款项。", "현금·카드 등은 시연용으로만 기록하며 실제 결제는 없습니다.", "Nakit/kart işlemleri yalnızca demo amaçlı kaydedilir; gerçek ödeme yoktur."], "Print job history": ["印刷ジョブ履歴", "打印任务记录", "인쇄 작업 기록", "Yazdırma geçmişi"], "Undo edits": ["変更を元に戻す", "撤销修改", "변경 취소", "Değişiklikleri geri al"], "Text alignment": ["文字の配置", "文字对齐", "텍스트 정렬", "Yazı hizası"], "Physical output is not automatically verified. SENT means USB write completed, not that the label was visually inspected.": ["SENTはUSBへの書き込み完了を示し、実際の印字品質を保証しません。", "SENT仅表示USB写入完成，不保证打印质量。", "SENT는 USB 쓰기 완료이며 실제 인쇄 품질을 보장하지 않습니다.", "SENT, USB'ye yazıldığını gösterir; fiziksel baskı kalitesini garanti etmez."], "Each printer gets a private access token. Create once and enter it on the Pi. Never share the token publicly.": ["プリンターの認証トークンは非公開です。Piに一度だけ設定し、共有しないでください。", "打印机令牌为私密信息，只在树莓派配置，切勿公开。", "프린터 토큰은 비공개입니다. Pi에만 설정하고 외부에 공유하지 마세요.", "Yazıcı erişim anahtarı özeldir. Pi'ye gir; kimseyle paylaşma."], "One-time printer token": ["一度だけ表示する認証トークン", "一次性显示的打印机令牌", "한 번 표시되는 프린터 토큰", "Tek seferlik yazıcı anahtarı"], "Copy token": ["トークンをコピー", "复制令牌", "토큰 복사", "Anahtarı kopyala"], "Order / payment record": ["注文・会計履歴", "订单/付款记录", "주문/결제 기록", "Sipariş / ödeme kaydı"], "Choose a printer": ["プリンターを選択", "选择打印机", "프린터 선택", "Yazıcı seç"], "Device-specific settings · preview updates instantly": ["機器別設定・プレビューは即時更新", "设备专用设置·实时预览", "기기별 설정·미리보기 즉시 갱신", "Cihaz bazında ayarlar · canlı önizleme"], "Track completed payments, table turnover, cooking time and popular items.": ["会計・席回転・調理時間・人気商品を確認できます。", "查看完成付款、桌台周转、备餐时间和热销商品。", "결제·테이블 회전·조리 시간·인기 상품을 확인합니다.", "Ödemeleri, masa devirlerini, hazırlık sürelerini ve popüler ürünleri takip et."], "Paid checks": ["会計済み伝票", "已付款账单", "결제 완료 계산서", "Ödenmiş hesaplar"], "Placed orders · paid or unpaid": ["注文総数（未会計を含む）", "已下订单（含未付款）", "주문 건수(미결제 포함)", "Verilen siparişler (ödenmiş/ödenmemiş)"], "Avg. seating · paid visits": ["平均滞在時間", "平均就座时间", "평균 체류 시간", "Ortalama masa kullanım süresi"], "Completed tickets": ["調理完了伝票", "完成的小票", "완료 주문서", "Tamamlanan fişler"], "Test orders · excluded": ["デモ注文（集計対象外）", "演示订单（不计入）", "데모 주문(집계 제외)", "Test siparişleri (hariç)"], "No orders yet.": ["注文はまだありません。", "暂无订单。", "아직 주문이 없습니다.", "Henüz sipariş yok."], "Choose something from the menu.": ["メニューから商品を選んでください。", "请从菜单选择商品。", "메뉴에서 상품을 선택하세요.", "Menüden ürün seç."], "Your table's orders": ["このテーブルの注文", "本桌订单", "이 테이블의 주문", "Masanın siparişleri"], "Your order": ["ご注文内容", "您的订单", "내 주문", "Siparişin"], "Send order to kitchen →": ["注文をキッチンへ送信 →", "发送订单到厨房 →", "주방에 주문 보내기 →", "Siparişi mutfağa gönder →"], "Secure table session": ["安全なテーブルセッション", "安全餐桌会话", "안전한 테이블 세션", "Güvenli masa oturumu"], "Connecting to your table…": ["テーブルに接続中…", "正在连接餐桌…", "테이블 연결 중…", "Masaya bağlanılıyor…"], "Explore our dishes": ["料理を見る", "浏览菜品", "메뉴 살펴보기", "Yemeklere göz at"], "Demo · ¥ JPY": ["デモ · 日本円", "演示 · 日元", "데모 · 엔화", "Demo · Japon yeni"]});
const categories={
 'Starters':['おつまみ','前菜','전채','Başlangıçlar'],
 'Yakitori':['焼き鳥','日式烤串','야키토리','Yakitori'],
 'Fried':['揚げ物','炸物','튀김','Kızartmalar'],
 'Seafood':['刺身・魚介','海鲜','해산물','Deniz ürünleri'],
 'Hot Dishes':['一品料理','热菜','따뜻한 요리','Sıcak yemekler'],
 'Rice & Noodles':['ご飯・麺','米饭与面食','밥과 면','Pilav ve erişte'],
 'Beer':['ビール','啤酒','맥주','Bira'],
 'Highball & Sour':['ハイボール・サワー','高球与沙瓦','하이볼·사워','Highball ve Sour'],
 'Sake & Shochu':['日本酒・焼酎','清酒与烧酒','사케·소주','Sake ve Shochu'],
 'Soft Drinks':['ソフトドリンク','无酒精饮品','무알코올 음료','Alkolsüz içecekler'],
 'Mains':['メイン','主菜','메인','Ana yemekler'],'Sides':['サイド','小吃','사이드','Yan ürünler'],'Drinks':['飲み物','饮品','음료','İçecekler'],'Desserts':['デザート','甜点','디저트','Tatlılar'],
 'Dessert':['デザート','甜点','디저트','Tatlılar']
};
const locales=['ja','zh','ko','tr'];
const langKey='cr5_locale';
const locale=(x)=>locales.includes(x)?x:'en';
let active=(()=>{try{let x=localStorage.getItem(langKey)||localStorage.getItem('cr3_locale'); if(x)return locale(x);return locale((navigator.language||'en').split('-')[0]);}catch{return 'en'}})();
const remembered=new WeakMap();
function lookup(original){const idx=locales.indexOf(active);return idx<0?original:(catalog[original]?.[idx]||original)}
function applyNode(node){
 if(node.nodeType!==Node.TEXT_NODE || !node.parentElement || ['SCRIPT','STYLE','OPTION','TEXTAREA','INPUT','CODE','PRE'].includes(node.parentElement.tagName))return;
 const s=node.nodeValue;const trimmed=s.trim();if(!trimmed)return;
 let canonical=remembered.get(node);
 if(!canonical){ if(!catalog[trimmed])return;canonical=trimmed;remembered.set(node,canonical); }
 const mapped=lookup(canonical);const m=s.match(/^\s*/)?.[0]||'',e=s.match(/\s*$/)?.[0]||'';
 if(node.nodeValue!==m+mapped+e)node.nodeValue=m+mapped+e;
}
function apply(root=document.body){
 // Customer QR menu selects its own locale (cr9_lang). Never rewrite its DOM or html lang.
 if(document.querySelector('#locale') && document.querySelector('#guest-products'))return;
 if(!root)return;const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);let n;while(n=walker.nextNode())applyNode(n);
 document.documentElement.lang=active==='zh'?'zh-CN':active;
 document.querySelectorAll('[data-locale-picker]').forEach(x=>{if(x.value!==active)x.value=active});
}
let pending=false;function schedule(){if(pending)return;pending=true;queueMicrotask(()=>{pending=false;apply()});}
function setLocale(language,save=true){active=locale(language);if(save)try{localStorage.setItem(langKey,active)}catch{}apply();window.dispatchEvent(new CustomEvent('cr5:locale',{detail:{lang:active}}));}
function start(){
 if(!document.querySelector('#locale')&&!document.querySelector('[data-locale-picker]')){
 const header=document.querySelector('.header .actions, .header .right');if(header){
 let l=document.createElement('label');l.className='cr5-picker';l.innerHTML='<span aria-hidden="true">🌐</span><select aria-label="Language" data-locale-picker><option value="ja">日本語</option><option value="en">English</option><option value="zh">简体中文</option><option value="ko">한국어</option><option value="tr">Türkçe</option></select>';
 header.prepend(l);l.querySelector('select').addEventListener('change',e=>setLocale(e.target.value));
 }}
 // Customer menu locale is managed exclusively by guest9/guest13.
 const style=document.createElement('style');style.textContent='.cr5-picker{display:inline-flex;align-items:center;gap:5px;margin-right:8px}.cr5-picker select{width:auto;max-width:125px;padding:6px;border-radius:8px;border:1px solid #cbd5d1;background:transparent;color:inherit;font-size:12px}@media(max-width:700px){.cr5-picker select{max-width:92px}}';document.head.append(style);
 apply();new MutationObserver(schedule).observe(document.body,{subtree:true,childList:true});
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start);else start();
function requireSignIn(){if(document.getElementById('cr5-auth-expired'))return;const n=document.createElement('div');n.id='cr5-auth-expired';n.setAttribute('role','alert');n.style.cssText='position:fixed;z-index:9999;top:0;left:0;right:0;background:#142d23;color:white;padding:15px 20px;text-align:center;box-shadow:0 4px 15px #0003';const label=document.createElement('span');label.textContent=({'ja':'スタッフのログイン期限が切れました。','zh':'员工登录已过期。','ko':'직원 로그인 세션이 만료되었습니다.','tr':'Personel oturumu sona erdi.'})[active]||'Staff session expired.';const a=document.createElement('a');a.href='/portal.html';a.textContent=' → Sign in';a.style.cssText='margin-left:12px;color:white;text-decoration:underline;font-weight:bold';n.append(label,a);document.body.prepend(n);}
window.CR5={requireSignIn,getLocale:()=>active,setLocale,t:(str)=>lookup(str), category:(c,l=active)=>{const i=locales.indexOf(l);return i<0?c:categories[c]?.[i]||c;},productName:(p,l=active)=>p[{'ja':'nameJa','zh':'nameZh','ko':'nameKo','tr':'nameTr'}[l]]||p.name, productDescription:(p,l=active)=>p[{'ja':'descriptionJa','zh':'descriptionZh','ko':'descriptionKo','tr':'descriptionTr'}[l]]||p.description, translateOrderItem:(name,products,l=active)=>products.find(x=>x.name===name)?.[{'ja':'nameJa','zh':'nameZh','ko':'nameKo','tr':'nameTr'}[l]]||name, apply};
})();
