# Demo Photos and Video / 写真・動画

The project demo video and four photographs of the actual Raspberry Pi/USB printer setup are published in this repository.

## Published demo video / 公開デモ動画

[![Aliator CashRegister project demo](https://img.youtube.com/vi/K00qFp7NA4k/hqdefault.jpg)](https://www.youtube.com/watch?v=K00qFp7NA4k)

- **English:** [Watch Aliator CashRegister on YouTube](https://www.youtube.com/watch?v=K00qFp7NA4k)
- **日本語:** [Aliator CashRegister 動作デモを見る](https://www.youtube.com/watch?v=K00qFp7NA4k)

## Published hardware photographs / 実機写真

| Photo | English | 日本語 |
| --- | --- | --- |
| [Touch kiosk](images/raspberry-pi-dashboard-handheld.webp) | Raspberry Pi 4 and 7-inch touchscreen running the CashRegister dashboard | Raspberry Pi 4と7インチ画面の店舗ダッシュボード |
| [Japanese demo receipt](images/receipt-japanese-closeup.webp) | Real 58 mm paper with Japanese monochrome bitmap printing | 58 mm用紙に印刷された日本語デモレシート |
| [USB printer](images/zj9220-printer-receipt.webp) | ZJ-9220 with printed receipt emerging | USBプリンターから印刷されたレシート |
| [Workspace](images/raspberry-pi-workspace-screen.webp) | Japanese restaurant workspace displayed on the kiosk | キオスク上の日本語店舗管理画面 |

![USB printer producing a Japanese demo receipt](images/zj9220-printer-receipt.webp)

**Media notes:** Images were resized/compressed for GitHub and a close-up receipt shot was cropped to minimize unrelated background screen content. No original photo EXIF metadata is included. All printed payment details are demo values, not evidence of a real PayPay transfer.


## Folder layout

```text
media/
  README.md
  images/
    raspberry-pi-dashboard-handheld.webp
    receipt-japanese-closeup.webp
    raspberry-pi-workspace-screen.webp
    zj9220-printer-receipt.webp
    (more photos can be added)
  video/
    (upload an edited, reasonably compressed demo MP4)
```

Suggested GitHub README caption (English):

> **Aliator CashRegister hardware demonstration:** Raspberry Pi 4 with a 7-inch touch kiosk, Java-based HTTPS print bridge and a USB ZJ-9220 printer producing variable-length Japanese kitchen/order receipts.

日本語キャプション:

> **Aliator CashRegister 動作デモ:** Raspberry Pi 4 と 7インチタッチディスプレイを使った店舗用キオスク。Java製印刷ブリッジからUSBプリンターへ接続し、日本語の注文伝票を可変長で印刷します。

## Privacy checklist

- Blur email addresses, QR login/registration codes, printer tokens, order identifiers linked to real customers, and browser bookmarks/personal accounts.
- Don't film or upload real customer addresses or receipts containing personal information.
- Use only demo orders and simulated payment data.
- Photos can be added directly to `media/images/` through GitHub's **Add file → Upload files**.
- Larger videos are often better hosted on YouTube (unlisted/public) with a link in the README. Standard GitHub files have size limits; avoid uploading huge raw footage.

Additional photos can be referenced by relative Markdown links, e.g. `![Raspberry Pi dashboard](images/raspberry-pi-dashboard-handheld.webp)`.
