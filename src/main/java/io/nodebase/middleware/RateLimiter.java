package io.nodebase.middleware;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {

    private static final int IP_LIMIT_PER_MINUTE = 100;
    private static final int KEY_LIMIT_PER_MINUTE = 1000;
    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public boolean allow(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String apiKey = req.getHeader("x-api-key");
        String identifier = (apiKey != null && !apiKey.isBlank()) ? "key:" + apiKey : "ip:" + getClientIp(req);
        int limit = (apiKey != null && !apiKey.isBlank()) ? KEY_LIMIT_PER_MINUTE : IP_LIMIT_PER_MINUTE;

        if (!checkWindow(identifier, limit)) {
            long retryAfter = WINDOW_MS / 1000;
            resp.setStatus(429);
            resp.setHeader("Retry-After", String.valueOf(retryAfter));
            resp.setContentType("application/json; charset=utf-8");
            resp.getWriter().write("{\"error\":\"rate limit exceeded\",\"retryAfter\":" + retryAfter + "}");
            return false;
        }
        return true;
    }

    private synchronized boolean checkWindow(String key, int limit) {
        long now = System.currentTimeMillis();
        Deque<Long> hits = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!hits.isEmpty() && hits.peekFirst() < now - WINDOW_MS) {
            hits.pollFirst();
        }
        if (hits.size() >= limit) return false;
        hits.addLast(now);
        return true;
    }

    private static String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return req.getRemoteAddr();
    }
}
