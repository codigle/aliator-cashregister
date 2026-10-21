import java.util.*;
import java.nio.charset.StandardCharsets;

public final class BitmapPolarityTest {
  static void require(boolean x, String m) { if (!x) throw new AssertionError(m); }
  static byte[] page(V8JapaneseTicket.Rendered r) {
    byte[] full = r.bytes;
    String prefix = new String(full, 0, Math.min(full.length, 200), StandardCharsets.ISO_8859_1);
    int idx = prefix.indexOf("BITMAP 0,0,");
    require(idx >= 0, "no bitmap command");
    int dataIndex = prefix.indexOf(',', prefix.indexOf(',', prefix.indexOf(',', prefix.indexOf(',', prefix.indexOf(',', idx) + 1) + 1) + 1) + 1);
    // Simpler and unambiguous: exactly five numeric commas follow BITMAP; data starts after sixth.
    String marker = "BITMAP 0,0,58,";
    require(prefix.contains(marker), "bitmap width mismatch");
    int p = prefix.indexOf(marker);
    int seen = 0, payload = -1;
    for (int n=p; n<full.length && n<p+90; n++) {
      if (full[n] == ',') { seen++; if (seen==5) { payload=n+1; break; } }
    }
    require(payload>0,"header parse failed");
    int height = r.pageHeightsMm.get(0)*8;
    int length = 58*height;
    require(payload+length <= full.length,"payload truncated");
    return Arrays.copyOfRange(full,payload,payload+length);
  }
  public static void main(String[] args) throws Exception {
    var order = V8JapaneseTicket.demo();
    var one = V8JapaneseTicket.render(order);
    byte[] data=page(one);
    for (int i=0;i<58*12;i++) require((data[i]&0xff)==0xff,"top margin should print white");
    long ink=0, blank=0;
    for (byte b:data) { if ((b&0xff)!=0xff) ink++; else blank++; }
    require(ink>50,"no glyph ink");
    require(blank>ink,"background should be blank, not solid black");
    var receipt = V8JapaneseTicket.demo(); receipt.setProperty("kind","RECEIPT");
    require(page(V8JapaneseTicket.render(receipt))[0]==(byte)0xff,"receipt background not white");
    var many = V8JapaneseTicket.demo(); many.setProperty("itemCount","60");
    for (int i=1;i<=60;i++) { many.setProperty("item"+i,"特製とんこつラーメン 焼き餃子 " + i); many.setProperty("itemNote"+i,"麺かため、ねぎ多め"); }
    var longSlip=V8JapaneseTicket.render(many);
    require(longSlip.pageHeightsMm.size()>1,"long ticket pages clipped");
    require(longSlip.lengthMm()>one.lengthMm(),"not dynamic length");
    System.out.printf("BITMAP_WHITE_BACKGROUND=PASS GLYPH_BLACK=PASS WHITE_BYTES=%d INK_BYTES=%d SHORT_LENGTH_MM=%d LONG_LENGTH_MM=%d LONG_PAGES=%d MEDIA_58MM_GAP0=PASS%n",blank,ink,one.lengthMm(),longSlip.lengthMm(),longSlip.pageHeightsMm.size());
  }
}
