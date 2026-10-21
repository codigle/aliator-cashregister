import java.util.*;
import java.io.*;

public final class DynamicTicketTest {
    static void ok(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Properties small=V8JapaneseTicket.demo();
        V8JapaneseTicket.Rendered a=V8JapaneseTicket.render(small);
        ok(a.pageHeightsMm.size()==1,"small one page");
        ok(a.lengthMm()>=25 && a.lengthMm()<=150,"small length");
        String raw=new String(a.bytes,0,Math.min(95,a.bytes.length),java.nio.charset.StandardCharsets.US_ASCII);
        ok(raw.startsWith("SIZE 58 mm,"+a.pageHeightsMm.get(0)+" mm\r\nGAP 0 mm,0 mm\r\n"),"media header");
        Properties shortReceipt=V8JapaneseTicket.demo();
        shortReceipt.setProperty("itemCount","1");
        shortReceipt.setProperty("itemNote1","");
        for(int i=2;i<30;i++)shortReceipt.remove("item"+i);
        int heightShort=V8JapaneseTicket.render(shortReceipt).lengthMm();
        Properties longTicket=V8JapaneseTicket.demo();
        longTicket.setProperty("itemCount","45");
        for(int i=1;i<=45;i++){
            longTicket.setProperty("item"+i,"特製とんこつラーメンと焼き餃子（6個）・ねぎ多め・麺かため "+i);
            longTicket.setProperty("itemNote"+i,"追加味玉 つけ麺の硬さは普通、スープ少なめ");
        }
        var c=V8JapaneseTicket.render(longTicket);
        ok(c.lengthMm()>heightShort,"longer content requires more paper");
        ok(c.pageHeightsMm.size()>1,"long ticket splits into pages");
        ok(c.actualLines>45,"wrapped lines present");
        for(int h:c.pageHeightsMm)ok(h>=25 && h<=150,"each page within firmware bounds");
        Properties receipt=V8JapaneseTicket.demo();
        receipt.setProperty("kind","RECEIPT");
        receipt.setProperty("total","4,300");
        receipt.setProperty("method","現金");
        receipt.setProperty("tendered","5,000");
        receipt.setProperty("change","700");
        ok(V8JapaneseTicket.previewText(receipt).contains("お釣り"),"receipt Japanese");
        String legacy=DynamicLegacyText.render(AliatorPrintAgent.testJob());
        ok(legacy.startsWith("SIZE 58 mm,"),"legacy dynamic");
        ok(legacy.contains("GAP 0 mm,0 mm"),"legacy gap0");
        System.out.printf("SHORT_MM=%d LONG_MM=%d LONG_PAGES=%d WRAPPED_LINES=%d LEGACY_PREVIEW=PASS JAPANESE=PASS%n",
            heightShort,c.lengthMm(),c.pageHeightsMm.size(),c.actualLines);
    }
}
