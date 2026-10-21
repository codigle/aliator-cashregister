package com.aliator.cashregister.v2;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;

@Component
public class V2OriginGuard extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v2/");
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
        throws ServletException, IOException {
        resp.setHeader("Cache-Control", "no-store");
        // Browser same-origin check for state-changing requests; CLI clients may send no Origin.
        boolean changing = req.getMethod().equals("POST") || req.getMethod().equals("PUT") ||
                req.getMethod().equals("PATCH") || req.getMethod().equals("DELETE");
        String origin = changing ? req.getHeader("Origin") : null;
        if (origin != null) {
            try {
                URI u=URI.create(origin);
                if (!"https".equalsIgnoreCase(u.getScheme()) ||
                    !req.getServerName().equalsIgnoreCase(u.getHost()) || u.getPort()!=-1) {
                    resp.sendError(403,"Cross-origin request rejected");return;
                }
            } catch(IllegalArgumentException e){resp.sendError(403,"Invalid Origin");return;}
        }
        chain.doFilter(req,resp);
    }
}
