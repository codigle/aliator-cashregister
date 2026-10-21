package com.aliator.cashregister.v2;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class V2RateLimit extends OncePerRequestFilter {
    private record Window(long since, int count) {}
    private final ConcurrentHashMap<String, Window> buckets = new ConcurrentHashMap<>();
    @Override protected boolean shouldNotFilter(HttpServletRequest req) {
        return !req.getMethod().equals("POST") || !(
            req.getRequestURI().equals("/api/v2/guest/join") ||
            req.getRequestURI().equals("/api/v2/guest/orders") ||
            req.getRequestURI().equals("/api/v2/auth/login") ||
            req.getRequestURI().equals("/api/v2/auth/register"));
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
        throws IOException, ServletException {
        // Nginx sets X-Real-IP; Java's port is loopback-bound, not directly public.
        String ip=req.getHeader("X-Real-IP");
        if(ip==null || !ip.matches("[0-9a-fA-F:.]{3,50}"))ip=req.getRemoteAddr();
        String path=req.getRequestURI();
        String key=ip+"|"+path;
        int max=path.endsWith("/join")?30:path.endsWith("/orders")?15:path.endsWith("/register")?5:15;
        long now=System.currentTimeMillis();
        if(buckets.size()>20000)buckets.clear();
        Window updated=buckets.compute(key,(k,w)->w==null || now-w.since()>60000 ? new Window(now,1) : new Window(w.since(),w.count()+1));
        if(updated.count()>max){resp.setStatus(429);resp.setContentType("application/json");resp.getWriter().write("{\"error\":\"Rate limit exceeded. Try in a minute.\"}");return;}
        chain.doFilter(req,resp);
    }
}
