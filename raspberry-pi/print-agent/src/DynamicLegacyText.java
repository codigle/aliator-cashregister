import java.nio.charset.StandardCharsets;
import java.util.*;

/** Backward-compatible ASCII TSPL jobs, automatically sized for 58mm continuous roll. */
final class DynamicLegacyText {
    private record Row(String data, String font) {}
    private static final int WIDTH_DOTS=58*8;
    private static final int MAX_HEIGHT_MM=150;
    private static final int MIN_HEIGHT_MM=25;
    private static final int MARGIN_X=18;
    private static final int TOP_DOTS=15;
    private static final int BOTTOM_DOTS=26;
    private DynamicLegacyText() {}

    private static String cleaned(String s) { return AliatorPrintAgent.sanitize(s,400); }

    static String render(Properties p) {
        if (AliatorPrintAgent.W!=58 || AliatorPrintAgent.GAP!=0)
            throw new IllegalArgumentException("CONTINUOUS_MEDIA_MISMATCH: require 58mm / GAP 0");
        String kind=p.getProperty("kind", "");
        if (!Set.of("ORDER","RECEIPT").contains(kind)) throw new IllegalArgumentException("Unknown kind");
        List<Row> lines=new ArrayList<>();
        String head=cleaned(p.getProperty("labelHeader",AliatorPrintAgent.APP));
        if (!head.isBlank()) appendWrapped(lines,head,"3");
        appendWrapped(lines,kind+" #"+cleaned(p.getProperty("number","")),"2");
        if (!"false".equalsIgnoreCase(p.getProperty("labelShowTable","true")))
            appendWrapped(lines,"TABLE " + cleaned(p.getProperty("table","")),"2");
        for (int i=1; i<=100; i++) {
            String item=p.getProperty("item"+i);
            if(item!=null)appendWrapped(lines,cleaned(item),"2");
        }
        String note=cleaned(p.getProperty("note",""));
        if(!note.isBlank())appendWrapped(lines,"NOTE: "+note,"2");
        if(kind.equals("RECEIPT")){
            appendWrapped(lines,"TOTAL YEN "+cleaned(p.getProperty("total","")),"2");
            appendWrapped(lines,"SIMULATED PAYMENT","2");
        }
        String footer=cleaned(p.getProperty("labelFooter",""));
        if(!footer.isBlank())appendWrapped(lines,footer,"2");
        if(lines.isEmpty()||lines.size()>180)throw new IllegalArgumentException("LEGACY_TICKET_TOO_LONG");
        int maxLines=(MAX_HEIGHT_MM*8-TOP_DOTS-BOTTOM_DOTS)/32;
        int pages=(lines.size()+maxLines-1)/maxLines;
        if (pages>12) throw new IllegalArgumentException("LEGACY_TICKET_TOO_MANY_PAGES");
        StringBuilder out=new StringBuilder();
        for(int page=0;page<pages;page++) {
            int start=page*maxLines;
            int end=Math.min(lines.size(),start+maxLines);
            int heightDots=TOP_DOTS+(end-start)*32+BOTTOM_DOTS;
            int mm=Math.max(MIN_HEIGHT_MM,(heightDots+7)/8);
            out.append("SIZE 58 mm,").append(mm).append(" mm\r\n")
               .append("GAP 0 mm,0 mm\r\nDIRECTION 1\r\nCLS\r\n");
            int y=TOP_DOTS;
            for(int i=start;i<end;i++) {
                Row row=lines.get(i);
                out.append("TEXT 18,").append(y).append(",\"").append(row.font)
                    .append("\",0,1,1,\"").append(row.data).append("\"\r\n");
                y+=32;
            }
            out.append("PRINT 1,1\r\n");
        }
        return out.toString();
    }

    private static void appendWrapped(List<Row> result,String value,String font) {
        int chars=(WIDTH_DOTS - 2*MARGIN_X)/(font.equals("3")?16:12);
        value=value.replace('"','\'');
        if(value.isBlank()){result.add(new Row("",font));return;}
        for(int start=0;start<value.length();start+=chars){
            result.add(new Row(value.substring(start,Math.min(value.length(),start+chars)),font));
        }
    }
}
