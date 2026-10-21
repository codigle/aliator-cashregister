import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static java.nio.file.StandardCopyOption.*;

/**
 * Aliator Print Agent — Java 21, no third-party dependencies.
 * Local USB TSPL label output and local durable spool, NO inbound network port.
 */
public final class AliatorPrintAgent {
    static final String APP = "ALIATOR CASHREGISTER";
    static final Path ROOT = Path.of(System.getenv().getOrDefault("ALIATOR_SPOOL", "/var/lib/aliator-print-agent"));
    static final Path DEVICE = Path.of(System.getenv().getOrDefault("ALIATOR_USB_DEVICE", "/dev/usb/lp0"));
    static final int W = dimension("ALIATOR_LABEL_WIDTH_MM", 60, 40, 110);
    static final int H = dimension("ALIATOR_LABEL_HEIGHT_MM", 50, 25, 200);
    static final int GAP = dimension("ALIATOR_LABEL_GAP_MM", 2, 0, 10);

    static int dimension(String key, int dflt, int min, int max) {
        String raw = System.getenv(key);
        int n;
        try { n = raw == null ? dflt : Integer.parseInt(raw); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(key + " must be an integer", e); }
        if (n < min || n > max) throw new IllegalArgumentException(key + " must be " + min + ".." + max);
        return n;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !Set.of("test", "daemon", "preview", "queue-test", "preview-japanese", "test-bitmap", "preview-dynamic", "print-dynamic-test", "queue-japanese-test").contains(args[0])) {
            System.err.println("Usage: java -jar aliator-print-agent.jar test|preview|daemon|queue-test");
            System.exit(2);
        }
        switch (args[0]) {
            case "test" -> {
                send(testJob());
                System.out.println("USB_WRITE_OK=YES; physical output must be visually confirmed");
            }
            case "preview" -> System.out.println(escapeDebug(DynamicLegacyText.render(testJob())));
            case "preview-japanese" -> System.out.println(V8JapaneseTicket.previewText(V8JapaneseTicket.demo()));
            case "test-bitmap" -> { byte[] bytes=V8JapaneseTicket.tspl(V8JapaneseTicket.demo());
                if(bytes.length<1000)throw new IllegalStateException("Missing bitmap data");
                Properties receipt=V8JapaneseTicket.demo();
                receipt.setProperty("kind","RECEIPT");receipt.setProperty("total","2,300");
                receipt.setProperty("method","現金");receipt.setProperty("tendered","3,000");
                receipt.setProperty("change","700");receipt.setProperty("unitPrice1","400");
                receipt.setProperty("linePrice1","800");
                byte[] other=V8JapaneseTicket.tspl(receipt);
                if(other.length<1000)throw new IllegalStateException("Receipt bitmap missing");
                System.out.println("TSPL_BITMAP=PASS ORDER_BYTES="+bytes.length+" RECEIPT_BYTES="+other.length); }
            case "preview-dynamic" -> {
                var demo = V8JapaneseTicket.demo();
                var result = V8JapaneseTicket.render(demo);
                System.out.println("FORMAT=JAPANESE_BITMAP");
                System.out.println("PAGES="+result.pageHeightsMm.size());
                System.out.println("HEIGHTS_MM="+result.pageHeightsMm);
                System.out.println("TOTAL_LENGTH_MM="+result.lengthMm());
                System.out.println("WRAPPED_LINES="+result.actualLines);
                System.out.println("WIDTH_MM=58 GAP_MM=0");
            }
            case "print-dynamic-test" -> {
                var ticket = V8JapaneseTicket.demo();
                send(ticket);
                System.out.println("USB_WRITE_OK=YES PHYSICAL_PRINT=VISUAL_CHECK_REQUIRED");
            }
            case "queue-japanese-test" -> {
                // Exactly one local, non-remote Japanese test job; no server/order mutation.
                var demo = V8JapaneseTicket.demo();
                V8JapaneseTicket.render(demo); // reject missing fonts BEFORE writing spool
                String id = UUID.randomUUID().toString();
                writeJob(id, demo);
                System.out.println("QUEUED_ONE_JAPANESE_TEST_JOB=" + id);
            }
            case "queue-test" -> {
                String id = UUID.randomUUID().toString();
                writeJob(id, testJob());
                System.out.println("QUEUED_JOB_ID=" + id);
            }
            case "daemon" -> daemon();
            default -> throw new IllegalArgumentException("Unknown command");
        }
    }

    static Properties testJob() {
        Properties p = new Properties();
        p.setProperty("kind", "ORDER");
        p.setProperty("number", "TEST-1001");
        p.setProperty("table", "T01");
        p.setProperty("item1", "2x Yakitori");
        p.setProperty("item2", "1x Karaage");
        p.setProperty("item3", "1x Edamame");
        p.setProperty("note", "JAVA USB PRINT TEST");
        return p;
    }

    static String sanitize(String input, int length) {
        if (input == null) return "";
        String cleaned = input.replaceAll("[\\r\\n\\t\\u0000-\\u001f]", " ").trim();
        // Device built-in TSPL fonts typically lack Japanese glyphs; no raw commands from user input.
        cleaned = cleaned.replaceAll("[^\\x20-\\x7E]", "?");
        cleaned = cleaned.replace('"', '\'').replace('\\', '/');
        return cleaned.length() > length ? cleaned.substring(0, length) : cleaned;
    }

    static String render(Properties props) {
        String kind = props.getProperty("kind", "");
        if (!Set.of("ORDER", "RECEIPT").contains(kind)) throw new IllegalArgumentException("Unknown job kind");
        int widthMm = jobDimension(props,"labelWidthMm",W,40,110);
        int heightMm = jobDimension(props,"labelHeightMm",H,25,200);
        int gapMm = jobDimension(props,"labelGapMm",GAP,0,10);
        int widthDots = widthMm * 8;
        int heightDots = heightMm * 8;
        int maxChars = Math.max(8, (widthDots - 40) / 12);
        StringBuilder s = new StringBuilder();
        s.append("SIZE ").append(widthMm).append(" mm,").append(heightMm).append(" mm\r\n")
          .append("GAP ").append(gapMm).append(" mm,0 mm\r\n")
          .append("DIRECTION 1\r\nCLS\r\n");
        int y = 15;
        String alignment = props.getProperty("labelAlignment","LEFT");
        if(!Set.of("LEFT","CENTER","RIGHT").contains(alignment))alignment="LEFT";
        final String align=alignment;
        String density = props.getProperty("labelDensity","NORMAL");
        int extra = switch(density) { case "COMPACT" -> -5; case "LARGE" -> 8; default -> 0; };
        String header=sanitize(props.getProperty("labelHeader",APP),64);
        if (!header.isEmpty())y = line(s, y, "3",header,maxChars,heightDots,extra,align,widthDots);
        y += 5;
        y = line(s, y, "2", kind + " #" + sanitize(props.getProperty("number"), 16), maxChars, heightDots,extra,align,widthDots);
        if(!props.getProperty("labelShowTable","true").equalsIgnoreCase("false"))
           y = line(s, y, "2", "TABLE " + sanitize(props.getProperty("table"), 12), maxChars, heightDots,extra,align,widthDots);
        y += 7;
        for (int i=1; i<=30; i++) {
            String item = props.getProperty("item" + i);
            if (item == null) continue;
            y = line(s, y, "2", sanitize(item, 80), maxChars, heightDots,extra,align,widthDots);
        }
        String note = sanitize(props.getProperty("note"), 100);
        if (!note.isBlank()) {y+=7; y = line(s,y,"2",note,maxChars,heightDots,extra,align,widthDots);}
        if (kind.equals("RECEIPT")) {
            String total = sanitize(props.getProperty("total"), 18);
            y+=5;
            if (!total.isBlank()) y = line(s,y,"2","TOTAL YEN " + total,maxChars,heightDots,extra,align,widthDots);
            y = line(s,y,"2","SIMULATED PAYMENT",maxChars,heightDots,extra,align,widthDots);
        }
        String footer=sanitize(props.getProperty("labelFooter",""),100);
        if(!footer.isEmpty()){ y+=4;line(s,y,"2",footer,maxChars,heightDots,extra,align,widthDots); }
        s.append("PRINT 1,1\r\n");
        return s.toString();
    }

    static int jobDimension(Properties props,String key,int dflt,int min,int max){
        try{int n=Integer.parseInt(props.getProperty(key,String.valueOf(dflt)));return Math.max(min,Math.min(max,n));}
        catch(NumberFormatException e){return dflt;}
    }
    static int line(StringBuilder s,int y,String font,String data,int maxChars,int heightDots){
       return line(s,y,font,data,maxChars,heightDots,0,"LEFT",W*8);
    }
    static int line(StringBuilder s, int y, String font, String data, int maxChars, int heightDots, int extra,String align,int paperWidth) {
        int step = Math.max(19,(font.equals("3") ? 31 : 27)+extra);
        if (y + step > heightDots - 12) throw new IllegalArgumentException("LABEL_TOO_SHORT: increase height or reduce items");
        String value = sanitize(data, maxChars);
        int textPixels=value.length()*(font.equals("3")?16:12);
        int x=switch(align){case "CENTER" -> Math.max(18,(paperWidth-textPixels)/2);case "RIGHT" -> Math.max(18,paperWidth-18-textPixels);default -> 18;};
        s.append("TEXT ").append(x).append(",").append(y).append(",\"").append(font)
         .append("\",0,1,1,\"").append(value).append("\"\r\n");
        return y + step;
    }

    static String escapeDebug(String s) {return s.replace("\r", "\\r").replace("\n", "\\n\n");}

    static void send(Properties job) throws IOException {
        byte[] bytes = "JA_BITMAP".equals(job.getProperty("printFormat"))
            ? V8JapaneseTicket.tspl(job) : DynamicLegacyText.render(job).getBytes(StandardCharsets.US_ASCII);
        if (!Files.exists(DEVICE)) throw new IOException("Printer not found: " + DEVICE);
        Object rawMode = Files.getAttribute(DEVICE, "unix:mode");
        int mode = ((Number)rawMode).intValue();
        if ((mode & 0170000) != 0020000) throw new IOException("Refusing to write to non-character-device " + DEVICE);
        Path lockFile = ROOT.resolve(".device.lock");
        Files.createDirectories(ROOT);
        try (FileChannel lockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock exclusive = lockChannel.lock()) {
            try (OutputStream out = Files.newOutputStream(DEVICE, StandardOpenOption.WRITE)) {
                out.write(bytes);
                out.flush();
            }
        }
    }

    static void writeJob(String id, Properties job) throws IOException {
        if (!id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid job ID");
        Path inbox = ROOT.resolve("inbox");
        Files.createDirectories(inbox);
        Path temp = Files.createTempFile(inbox, ".job-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                job.store(writer, "Aliator local test print job");
            }
            Files.move(temp, inbox.resolve(id + ".job"), ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temp); }
    }

    static void daemon() throws Exception {
        for (String dir : List.of("inbox", "processing", "sent", "uncertain")) Files.createDirectories(ROOT.resolve(dir));
        // Previously claimed jobs might have printed before shutdown; never retry them silently.
        try (var stream=Files.list(ROOT.resolve("processing"))) {
            for (Path stale:stream.filter(f->f.getFileName().toString().endsWith(".job")).toList()) {
                Files.move(stale,ROOT.resolve("uncertain").resolve(stale.getFileName()),REPLACE_EXISTING);
                System.err.println("UNCERTAIN_AFTER_RESTART="+stale.getFileName());
            }
        }
        System.out.println("PRINT_AGENT_READY=YES device="+DEVICE+" dimensions="+W+"x"+H+"mm gap="+GAP+"mm");
        while (true) {
            List<Path> pending;
            try (var stream=Files.list(ROOT.resolve("inbox"))) {
                pending=stream.filter(f->f.getFileName().toString().matches("[0-9a-f-]{36}\\.job"))
                    .sorted().limit(10).toList();
            }
            for (Path p: pending) {
                Path inProgress = ROOT.resolve("processing").resolve(p.getFileName());
                try { Files.move(p,inProgress,ATOMIC_MOVE); }
                catch (NoSuchFileException e) {continue;}
                try {
                    Properties job=new Properties();
                    try (var reader=Files.newBufferedReader(inProgress, StandardCharsets.UTF_8)) {job.load(reader);}
                    send(job);
                    Files.move(inProgress,ROOT.resolve("sent").resolve(p.getFileName()),ATOMIC_MOVE);
                    System.out.println("USB_WRITE_OK job="+p.getFileName()+" at="+Instant.now());
                } catch (Exception e) {
                    // A USB write can fail after partial bytes; consider the job uncertain, not retryable automatically.
                    System.err.println("PRINT_UNCERTAIN="+p.getFileName()+" cause="+e.getMessage());
                    Files.move(inProgress,ROOT.resolve("uncertain").resolve(p.getFileName()),REPLACE_EXISTING);
                }
            }
            TimeUnit.SECONDS.sleep(2);
        }
    }
}
