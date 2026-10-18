# Raspberry Pi 4 Integration / Raspberry Pi 4 連携

This section documents the **hardware and edge software** used with the Aliator CashRegister Java portfolio project.

**Status:** The Pi printer source was developed and tested separately from the Spring Boot server. The final Pi source snapshot is being prepared for publication; do not treat this document alone as a deployable installer.

## Hardware / 使用機器

| Device / デバイス | Purpose / 用途 |
| --- | --- |
| Raspberry Pi 4 | On-premises touch kiosk and local print gateway |
| 7-inch touchscreen | Chromium kiosk interface |
| Zjiang ZJ-9220 (USB; reported by Linux as `0416:5011 POS Label Printer`) | TSPL print device |
| 58 mm continuous receipt roll | Demo kitchen tickets and checkout slips |
| Raspberry Pi OS + labwc/Wayland | Touchscreen desktop session |
| Java 21 | Separate Print Bridge and Print Agent programs |

The operating-system printer device was `/dev/usb/lp0` in the tested setup. Device paths and printer modes vary between installations.

## Software architecture / ソフトウェア構成

```text
                [CashRegister on VPS]
               [Java Spring Boot REST]
               [PostgreSQL print jobs]
                         |
                  HTTPS + bearer token
                  polling / job ACK
                         |
                  [Pi Print Bridge]
                     Java 21
                         |
                    local spool
       inbox / processing / sent / uncertain
                         |
                  [Pi Print Agent]
                     Java 21
                         |
                 TSPL bitmap / USB
                         |
                 [ZJ-9220 printer]
```

The Pi also displays the CashRegister frontend through Chromium in kiosk/app mode; kiosk and printer are **independent** processes.

### Java Print Bridge

- Connects outbound via HTTPS to the CashRegister print-agent endpoints.
- Authenticates using a **private physical-device token**, stored on the Pi outside the repository.
- Polls print jobs, saves them to a local spool and acknowledges completed or uncertain delivery.
- Does not write to USB directly.
- Uses the same physical token when authorized restaurants share one printer via server-side grants.

### Java Print Agent

- Consumes local jobs in its own spool and sends TSPL to the USB printer.
- Prints Japanese Unicode by rendering text with a CJK-capable font into monochrome bitmaps.
- Calculates height from wrapped text and content rather than using one fixed receipt length.
- Uses one or more correctly sized bitmap pages when the calculated ticket is long.
- Separates sent and uncertain local jobs to reduce accidental duplicate prints.
- **Important:** A USB write or server `SENT` acknowledgment does **not** guarantee that a physical paper ticket was successfully delivered.

### 7-inch touchscreen kiosk

- Raspberry Pi OS, labwc compositor, Chromium fullscreen app mode.
- Finger scrolling originally selected text: labwc touch **mouse emulation** was disabled and compositor reloaded.
- `fonts-noto-cjk` enabled Japanese/Korean glyphs; color emoji may also require `fonts-noto-color-emoji`.
- The kiosk startup URL points to the CashRegister login/restaurant dashboard, not an arbitrary browser new-tab page.
- A responsive dashboard uses larger tile-style touch targets at 800 × 480 and scales for phone/desktop.

## Verified media/print settings / 動作確認済み

- **Width:** 58 mm
- **Continuous roll:** 0 mm GAP
- **Japanese:** bitmap-rendered glyphs
- **Ticket length:** computed from text wrapping and item count
- **Background:** white; text: black

The prototype initially used 100 × 150 mm labels with a 2 mm gap. Using GAP=2 with a continuous roll caused the printer to advance while looking for a label gap. Setting GAP=0 fixed feeding on the tested machine. A bitmap bit-polarity correction fixed inverted white-on-black tickets.

**Do not assume these settings work for other printer models or media.** Always test the printer locally before enabling unattended printing.

## Multi-restaurant physical printer sharing

The prototype had one physical Pi but multiple restaurant records. Creating independent credentials for each tenant was a dead end. The working setup uses:

- **One physical printer identity**, associated with the Pi's local token.
- **Per-restaurant logical printer grants**, authorized on the server.
- Separate tenant print queues and workflow settings (kitchen and receipt printing).
- Old queued jobs are preserved instead of blindly migrated/replayed when links change.

No physical printer token is included in this repository.

## Safety and operations

1. Keep authentication tokens, SMTP secrets, `/etc/aliator-*.env`, queues and live logs outside Git.
2. Use dedicated service identities and restricted systemd files.
3. Verify the local USB print agent before enabling the server-polling bridge.
4. Inspect pending/uncertain jobs before restarting the bridge to avoid unexpected reprints.
5. Send only one short physical ticket on the first test.
6. Do not treat payment-demo receipts as fiscal or Qualified Invoice documents.

## What the video will demonstrate / 動画の内容

Login on the 7-inch kiosk → open restaurant dashboard → customer QR/table order → kitchen receives a Japanese ticket → cashier records a **demo** payment → 58 mm Japanese receipt → restaurant reports.

Photographs and video links will be added in [../media/README.md](../media/README.md).
