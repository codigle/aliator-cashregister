package com.aliator.cashregister.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderThrottle {
    private record Window(long started, int count) {}
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public void check(String key) {
        long now = Instant.now().getEpochSecond();
        if (windows.size() > 5000) windows.entrySet().removeIf(e -> now - e.getValue().started > 120);
        Window window = windows.compute(key, (k, prev) -> {
            if (prev == null || now - prev.started >= 60) return new Window(now, 1);
            return new Window(prev.started, prev.count + 1);
        });
        if (window.count > 12) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Demo limit reached. Please try again in one minute.");
    }
}
