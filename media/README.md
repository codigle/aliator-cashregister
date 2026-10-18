# Demo Photos and Video / 写真・動画

The project demo video is published on YouTube. Hardware photographs will be added here after they are supplied and reviewed.

## Published demo video / 公開デモ動画

[![Aliator CashRegister project demo](https://img.youtube.com/vi/K00qFp7NA4k/hqdefault.jpg)](https://www.youtube.com/watch?v=K00qFp7NA4k)

- **English:** [Watch Aliator CashRegister on YouTube](https://www.youtube.com/watch?v=K00qFp7NA4k)
- **日本語:** [Aliator CashRegister 動作デモを見る](https://www.youtube.com/watch?v=K00qFp7NA4k)

Photos of the 7-inch Raspberry Pi display, ZJ-9220 printer and Japanese 58 mm receipt are planned. No photos are represented as already uploaded.

## Suggested layout

```text
media/
  README.md
  images/
    raspberry-pi-7inch-dashboard.jpg
    restaurant-qr-menu.jpg
    kitchen-screen.jpg
    receipt-japanese-58mm.jpg
    raspberry-pi-zj9220-setup.jpg
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

Once media is added, replace these placeholders with relative Markdown links, such as `![Raspberry Pi dashboard](images/raspberry-pi-7inch-dashboard.jpg)`.
