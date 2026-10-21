import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static java.nio.file.StandardCopyOption.*;

/**
 * Independent Java 21 HTTPS bridge: SERVER (claimed job) -> LOCAL SPOOL -> existing USB print agent.
 * Never writes directly to USB; never retries a physically attempted label automatically.
 */
public final class AliatorPrintBridge {
    static final Path ROOT=Path.of(System.getenv().getOrDefault("ALIATOR_SPOOL","/var/lib/aliator-print-agent"));
    static final String BASE=System.getenv().getOrDefault("ALIATOR_PRINT_SERVER","https://cashregister.aliator.com");
    static final String TOKEN=System.getenv().getOrDefault("ALIATOR_PRINT_TOKEN","");
    static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();

    private static HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(BASE+path))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization","Bearer "+TOKEN)
                .header("User-Agent","AliatorJavaPrintBridge/1.0");
    }
    private static void validate() {
        URI u=URI.create(BASE);
        if(!"https".equals(u.getScheme()) || !"cashregister.aliator.com".equals(u.getHost()) || u.getPort()!=-1 || u.getRawQuery()!=null || !"".equals(u.getRawPath()==null?"":u.getRawPath()))
            throw new IllegalArgumentException("Unexpected print server URL; HTTPS cashregister.aliator.com required");
        if(!TOKEN.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("ALIATOR_PRINT_TOKEN is missing or malformed");
        for(String name:List.of("inbox","processing","sent","uncertain")) if(!Files.isDirectory(ROOT.resolve(name)))
            throw new IllegalArgumentException("Local print agent spool is missing: "+name);
    }
    private static boolean seen(String id) {
        for(String folder:List.of("inbox","processing","sent","uncertain"))
            if(Files.exists(ROOT.resolve(folder).resolve(id+".job")))return true;
        return false;
    }
    private static void getNext() throws Exception {
        HttpResponse<String> response=HTTP.send(request("/api/v2/print/agent/next").GET().build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(response.statusCode()==204)return;
        if(response.statusCode()!=200)throw new IOException("GET next HTTP="+response.statusCode());
        if(response.body().length()>20000)throw new IOException("Server print payload exceeded safe limit");
        Properties p=new Properties();p.load(new StringReader(response.body()));
        String id=p.getProperty("remoteId","");
        if(!id.matches("[0-9a-f-]{36}") || !Set.of("ORDER","RECEIPT").contains(p.getProperty("kind","")))
            throw new IOException("Unrecognized job data from server");
        if(seen(id))return; // Idempotent local spool: do not reprint a received job.
        Path inbox=ROOT.resolve("inbox");
        Path tmp=Files.createTempFile(inbox,".remote-",".tmp");
        try {
            try(var out=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)){p.store(out,"Remote Java print bridge job");}
            Files.move(tmp,inbox.resolve(id+".job"),ATOMIC_MOVE);
            System.out.println("REMOTE_JOB_QUEUED="+id);
        } finally {Files.deleteIfExists(tmp);}
    }
    private static void ackFolder(String dir,String status) throws Exception {
        Path folder=ROOT.resolve(dir);
        try(var stream=Files.list(folder)) {
            for(Path job:stream.filter(p->p.getFileName().toString().matches("[0-9a-f-]{36}\\.job")).limit(100).toList()) {
                Path ackFlag=folder.resolve(job.getFileName().toString()+".acked");
                if(Files.exists(ackFlag))continue;
                Properties p=new Properties();
                try(var in=Files.newBufferedReader(job,StandardCharsets.UTF_8)){p.load(in);}
                String id=p.getProperty("remoteId","");
                if(!id.matches("[0-9a-f-]{36}"))continue; // v1 local test jobs
                String form="jobId="+URLEncoder.encode(id,StandardCharsets.UTF_8)+"&status="+status;
                HttpResponse<String> resp=HTTP.send(request("/api/v2/print/agent/ack")
                    .header("Content-Type","application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.ofString());
                if(resp.statusCode()!=200)throw new IOException("ACK HTTP="+resp.statusCode()+" job="+id);
                try {Files.createFile(ackFlag);} catch(FileAlreadyExistsException ignored) {}
                System.out.println("REMOTE_JOB_ACK="+id+" status="+status);
            }
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=0)throw new IllegalArgumentException("Run without arguments as aliator-print-bridge systemd service");
        validate();
        System.out.println("PRINT_BRIDGE_READY=YES server="+BASE);
        long lastLog=0;
        while(true) {
            try {
                ackFolder("sent","SENT");
                ackFolder("uncertain","UNCERTAIN");
                getNext();
            } catch(Exception e) {
                if(System.currentTimeMillis()-lastLog>15000) {
                    System.err.println("BRIDGE_WAITING="+e.getClass().getSimpleName()+" message="+e.getMessage());
                    lastLog=System.currentTimeMillis();
                }
            }
            TimeUnit.SECONDS.sleep(4);
        }
    }
}
