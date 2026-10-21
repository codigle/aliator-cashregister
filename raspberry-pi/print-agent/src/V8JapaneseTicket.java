import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * Aliator CashRegister v15. TSPL raster printing on verified 58mm continuous paper.
 * One physical slip is built from one or more exact-height TSPL pages. No GAP search.
 * Paper consumption depends on measured glyph widths, wrapped lines and footer space.
 */
public final class V8JapaneseTicket {
    private V8JapaneseTicket() {}
    private static final int DOTS_PER_MM = 8; // supported 203-dpi TSPL units
    private static final int PAD_X = 18;
    private static final int PAD_TOP = 16;
    private static final int PAD_BOTTOM = 24;
    private static final int MIN_PAGE_MM = 25;
    private static final int MAX_PAGE_MM = 150;
    private static final int MAX_PAGES = 12;
    private static final int MAX_LINES = 360;

    static Properties demo() {
        Properties p = new Properties();
        p.setProperty("kind", "ORDER");
        p.setProperty("printFormat", "JA_BITMAP");
        p.setProperty("restaurant", "ALIATOR 居酒屋");
        p.setProperty("number", "注文番号 TEST-1001");
        p.setProperty("table", "3");
        p.setProperty("time", "2026/10/18 17:00");
        p.setProperty("itemCount", "2");
        p.setProperty("item1", "特製とんこつラーメン");
        p.setProperty("qty1", "1");
        p.setProperty("itemNote1", "麺かため・追加味玉・ねぎ多め");
        p.setProperty("item2", "焼き餃子（6個）");
        p.setProperty("qty2", "2");
        p.setProperty("labelWidthMm", "58");
        p.setProperty("labelGapMm", "0");
        return p;
    }

    static String clean(String source) {
        if (source == null) return "";
        StringBuilder b = new StringBuilder();
        source.codePoints().limit(1500).forEach(cp -> {
            if (Character.isISOControl(cp)) {
                if (cp == '\n' || cp == '\r' || cp == '\t') b.append(' ');
            } else b.appendCodePoint(cp);
        });
        return b.toString().strip();
    }

    static int number(Properties p, String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(p.getProperty(key, ""));
            if (value < min || value > max) throw new IllegalArgumentException(key + " out of range");
            return value;
        } catch (NumberFormatException ignored) { return fallback; }
    }

    static List<String> lines(Properties p) {
        String kind = p.getProperty("kind", "");
        if (!Set.of("ORDER", "RECEIPT").contains(kind)) throw new IllegalArgumentException("Invalid ticket kind");
        List<String> result = new ArrayList<>();
        result.add(clean(p.getProperty("restaurant", "ALIATOR 店舗")));
        result.add(kind.equals("ORDER") ? "キッチン注文伝票" : "お会計レシート（デモ）");
        String number = clean(p.getProperty("number", ""));
        if (!number.isBlank()) result.add(number);
        String time = clean(p.getProperty("time", ""));
        if (!time.isBlank()) result.add("日時: " + time);
        if (!"false".equalsIgnoreCase(p.getProperty("labelShowTable", "true")))
            result.add("テーブル: " + clean(p.getProperty("table", "")));
        result.add("--------------------------------");
        int count = number(p, "itemCount", 0, 0, 100);
        if (count == 0) {
            for (int i = 1; i <= 100; i++) {
                String item = clean(p.getProperty("item"+i, ""));
                if (!item.isBlank()) result.add(item);
            }
        } else {
            for (int i = 1; i <= count; i++) {
                String name = clean(p.getProperty("item"+i, ""));
                if (name.isEmpty()) continue;
                int qty = number(p, "qty"+i, 1, 1, 999);
                result.add(qty + "点 " + name);
                String note = clean(p.getProperty("itemNote"+i, ""));
                if (kind.equals("ORDER") && !note.isBlank()) result.add("  備考: " + note);
                if (kind.equals("RECEIPT")) {
                    String unit = clean(p.getProperty("unitPrice"+i, ""));
                    String sub = clean(p.getProperty("linePrice"+i, ""));
                    if (!unit.isBlank() || !sub.isBlank()) result.add("  単価 ¥" + unit + "  小計 ¥" + sub);
                }
            }
        }
        String note = clean(p.getProperty("note", ""));
        if (!note.isBlank() && kind.equals("ORDER")) result.add("備考: " + note);
        result.add("--------------------------------");
        if (kind.equals("RECEIPT")) {
            result.add("合計 ¥" + clean(p.getProperty("total", "0")));
            result.add("お支払い: " + clean(p.getProperty("method", "")));
            String tendered = clean(p.getProperty("tendered", ""));
            if (!tendered.isBlank()) {
                result.add("お預かり ¥" + tendered);
                result.add("お釣り ¥" + clean(p.getProperty("change", "")));
            }
            result.add("税率内訳: 未設定");
            result.add("適格請求書ではありません");
        } else result.add("ご注文ありがとうございます");
        result.add("【デモ】実決済・税処理なし");
        String footer = clean(p.getProperty("labelFooter", ""));
        if (!footer.isBlank()) result.add(footer);
        return result;
    }

    private static Font font(int size, boolean bold) {
        Font f = new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size);
        if (f.canDisplayUpTo("特製とんこつラーメン・注文・領収書・餃子") != -1)
            throw new IllegalStateException("JAPANESE_FONT_MISSING: install fonts-noto-cjk on Raspberry Pi");
        return f;
    }

    private record PrintedLine(String text, boolean heading) {}
    static final class Rendered {
        final byte[] bytes;
        final List<Integer> pageHeightsMm;
        final int actualLines;
        Rendered(byte[] bytes, List<Integer> heights, int lines) {
            this.bytes = bytes;
            this.pageHeightsMm = List.copyOf(heights);
            this.actualLines = lines;
        }
        int lengthMm() { return pageHeightsMm.stream().mapToInt(Integer::intValue).sum(); }
    }

    static Rendered render(Properties p) throws IOException {
        // Physical media is owned by the Pi, not by stale printer records in the VPS.
        // These exact settings have been verified with the customer's continuous roll.
        if (AliatorPrintAgent.W != 58 || AliatorPrintAgent.GAP != 0)
            throw new IOException("CONTINUOUS_MEDIA_MISMATCH: Pi must be 58mm, GAP=0");
        final int widthPx = 58 * DOTS_PER_MM;
        final int availablePx = widthPx - 2 * PAD_X;
        String density = p.getProperty("labelDensity", "NORMAL");
        int size = switch (density) { case "COMPACT" -> 18; case "LARGE" -> 24; default -> 21; };
        Font bodyFont = font(size, false);
        Font headingFont = font(size + 4, true);
        int lineHeight = switch (density) { case "COMPACT" -> 29; case "LARGE" -> 39; default -> 34; };
        BufferedImage metricCanvas = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D measure = metricCanvas.createGraphics();
        FontMetrics bodyMetrics = measure.getFontMetrics(bodyFont);
        FontMetrics titleMetrics = measure.getFontMetrics(headingFont);
        int maxGlyphHeight = Math.max(bodyMetrics.getAscent()+bodyMetrics.getDescent(),
                                     titleMetrics.getAscent()+titleMetrics.getDescent());
        lineHeight = Math.max(lineHeight, maxGlyphHeight+4);
        ArrayList<PrintedLine> wrapped = new ArrayList<>();
        for (String raw : lines(p)) {
            boolean heading = raw.contains("注文伝票") || raw.contains("レシート");
            FontMetrics metrics = heading ? titleMetrics : bodyMetrics;
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < raw.length();) {
                int cp = raw.codePointAt(i);
                String unit = new String(Character.toChars(cp));
                String next = line.toString()+unit;
                if (!line.isEmpty() && metrics.stringWidth(next) > availablePx) {
                    wrapped.add(new PrintedLine(line.toString(), heading));
                    line.setLength(0);
                }
                if (metrics.stringWidth(unit)>availablePx)
                    throw new IOException("GLYPH_TOO_WIDE");
                line.append(unit);
                i += Character.charCount(cp);
            }
            wrapped.add(new PrintedLine(line.toString(), heading));
            if (wrapped.size() > MAX_LINES) throw new IOException("TICKET_TOO_LONG_FOR_PRINTER");
        }
        measure.dispose();

        // Plan exact-height pages from rendered line count. For long tickets,
        // split only BETWEEN glyph rows (never clip or silently discard items).
        final int maxPageDots = MAX_PAGE_MM * DOTS_PER_MM;
        final int minPageDots = MIN_PAGE_MM * DOTS_PER_MM;
        int maxLinesPerPage = Math.max(1, (maxPageDots-PAD_TOP-PAD_BOTTOM-maxGlyphHeight)/lineHeight);
        int pages = (wrapped.size() + maxLinesPerPage - 1) / maxLinesPerPage;
        if (pages > MAX_PAGES) throw new IOException("TICKET_TOO_MANY_PAGES");
        ByteArrayOutputStream command = new ByteArrayOutputStream();
        ArrayList<Integer> heights = new ArrayList<>();
        for (int page=0; page<pages; page++) {
            int from = page*maxLinesPerPage;
            int to = Math.min(wrapped.size(), from+maxLinesPerPage);
            int count = to - from;
            int usedDots = PAD_TOP + count*lineHeight + PAD_BOTTOM;
            int pageMm = Math.max(MIN_PAGE_MM, (usedDots + DOTS_PER_MM-1)/DOTS_PER_MM);
            int pageDots = pageMm*DOTS_PER_MM;
            if (pageMm > MAX_PAGE_MM) throw new IOException("TICKET_PAGE_OVERFLOW");
            heights.add(pageMm);
            BufferedImage image = new BufferedImage(widthPx,pageDots,BufferedImage.TYPE_BYTE_BINARY);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE); g.fillRect(0,0,widthPx,pageDots);
            g.setColor(Color.BLACK);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            int baseline = PAD_TOP + maxGlyphHeight;
            for (int i=from; i<to; i++) {
                PrintedLine line = wrapped.get(i);
                g.setFont(line.heading ? headingFont : bodyFont);
                FontMetrics fm = g.getFontMetrics();
                int textWidth = fm.stringWidth(line.text);
                String alignment = p.getProperty("labelAlignment", "LEFT");
                int x = switch (alignment) {
                    case "CENTER" -> Math.max(PAD_X, (widthPx-textWidth)/2);
                    case "RIGHT" -> Math.max(PAD_X, widthPx-PAD_X-textWidth);
                    default -> PAD_X;
                };
                if (textWidth > availablePx) throw new IOException("LINE_TOO_WIDE_AFTER_WRAP");
                g.drawString(line.text, x,baseline);
                baseline += lineHeight;
            }
            g.dispose();
            int rowBytes = (widthPx+7)/8;
            // Standard TSPL BITMAP polarity: 0 prints black, 1 leaves paper white.
            // v15 used the opposite bit polarity, producing white text on a black roll.
            byte[] bitmap = new byte[rowBytes*pageDots];
            Arrays.fill(bitmap, (byte) 0xFF); // white paper, including top/bottom margins
            for (int y=0;y<pageDots;y++) for (int x=0;x<widthPx;x++) {
                if ((image.getRGB(x,y)&0x00ffffff)!=0x00ffffff)
                    bitmap[y*rowBytes+x/8] &= (byte)~(0x80>>(x%8)); // black glyph dot
            }
            String header = "SIZE 58 mm,"+pageMm+" mm\r\nGAP 0 mm,0 mm\r\nDIRECTION 1\r\nCLS\r\nBITMAP 0,0,"+rowBytes+","+pageDots+",0,";
            command.write(header.getBytes(StandardCharsets.US_ASCII));
            command.write(bitmap);
            command.write("\r\nPRINT 1,1\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        return new Rendered(command.toByteArray(),heights,wrapped.size());
    }

    static String previewText(Properties p) {return String.join("\n",lines(p))+"\n";}
    static byte[] tspl(Properties p) throws IOException {return render(p).bytes;}
}
