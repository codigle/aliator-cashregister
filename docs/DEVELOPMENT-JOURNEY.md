# Build Journal — Aliator CashRegister / 開発記録

This journal explains what was built, where each technology is used, the practical failures encountered, and how they were investigated. It is a **portfolio project narrative**, not a claim of production certification.

## 1. Java restaurant platform

**Java 21 + Spring Boot** were chosen for the backend: REST APIs, restaurant identity, authentication, menu/option management, table QR sessions, order status, simulated payments, reporting, and print jobs.

**PostgreSQL** maintains restaurant-scoped data, translation jobs and the print queue. **Docker Compose** runs the API/database on a VPS. **Nginx and HTTPS** expose the website while the app is bound to a local server port.

The web frontend uses **HTML, CSS and vanilla JavaScript** rather than a large client framework. Its screens cover restaurant ownership, kitchen, guest ordering, cashier, printers, reports and the menu studio.

## 2. QR customer ordering

A guest visits the menu through a table QR link, opens a product popup, chooses modifiers (ramen noodle firmness, portions, extras, sets; drink sizes), adds items, views the cart/history, and submits the order.

The database and server calculate the order state and price. The kitchen sees new orders immediately. Staff can mark preparation complete and optionally acknowledge service.

## 3. Multiple restaurants and accounts

Registration uses email verification OTP; existing accounts can recover access by email. Restaurant menu and sales data are tenant-scoped. Owners can configure which printer handles kitchen tickets and which one handles receipts.

## 4. Raspberry Pi hardware integration

On **Raspberry Pi 4**, a 7-inch touchscreen presents the web interface in a Chromium kiosk under Raspberry Pi OS/labwc. Two independent **Java 21** programs handle printing:

- **Print Bridge:** HTTPS polling, device authorization and local job persistence.
- **Print Agent:** USB/TSPL rendering of local jobs and spool status.

The **Zjiang ZJ-9220** was exercised through Linux `/dev/usb/lp0`. The Pi does not require the Spring Boot app itself to be installed locally.

## 5. The printing failures we solved / 印刷トラブル

- **Printer credentials:** switching the Pi token for each restaurant broke reliable access; physical-printer identity and tenant grants had to be separated.
- **Print Bridge:** the USB agent alone could print, but server jobs never arrived while the bridge service was stopped or failed.
- **Continuous-roll media:** a label GAP setting caused excessive paper feeding. The verified demo configuration became **58 mm / GAP 0**.
- **Japanese glyphs:** original text printing rendered Japanese as question marks. A Java bitmap renderer using CJK-capable fonts provided Japanese characters on TSPL.
- **Polarity:** first bitmap output was white-on-black; inverting the bitmap bits produced black text on white paper.
- **Variable content length:** fixed-size labels could cut off orders; text measurement, wrapping and dynamic-length output were implemented.
- **Delivery semantics:** a job ACK/`SENT` status confirms software handoff, **not** that the printer sensor verified the paper.

## 6. Translation and localization

Japanese is the menu source. Staff can choose enabled menu languages, and translated product/option content is built asynchronously. The translation sidecar is an **existing Codigle service** using DeepL/Google provider integration, not a third-party endpoint exposed to the browser.

Japanese and Korean display bugs were separated into two classes: language fallback/control bugs in the UI, and missing glyph/emoji fonts on the Raspberry Pi kiosk. Correct data in the database is distinct from a screen displaying the font correctly.

## 7. Demo payments and reporting

The cashier chooses a table and records simulated cash, card or QR checkout. A PayPay-style QR **simulation** is included only as a portfolio interaction. **No actual PayPay integration or money movement takes place.** Payments drive demo report aggregates and optional receipt jobs; tax compliance is not verified.

## 8. Packaging, safety and GitHub

The VPS source, Pi source and supporting deployment examples belong in separate directories. Runtime credentials, tokens, SMTP passwords, DB dumps, queue payloads and logs must never be committed.

Recommended demonstration: guest QR → order with modifiers → kitchen ticket → preparation → simulated checkout → Japanese receipt → reports → architecture diagram.

See [Raspberry Pi documentation](../raspberry-pi/README.md) and [media instructions](../media/README.md).
