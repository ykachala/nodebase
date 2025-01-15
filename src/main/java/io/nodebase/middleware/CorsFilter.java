package io.nodebase.middleware;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public final class CorsFilter {

    private final String allowedOrigins;

    public CorsFilter(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins != null && !allowedOrigins.isBlank() ? allowedOrigins : "*";
    }

    public boolean handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String origin = req.getHeader("Origin");
        String allowed = resolveOrigin(origin);

        resp.setHeader("Access-Control-Allow-Origin", allowed);
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, x-api-key");
        resp.setHeader("Access-Control-Max-Age", "3600");

        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            resp.setStatus(204);
            return false;
        }
        return true;
    }

    private String resolveOrigin(String requestOrigin) {
        if ("*".equals(allowedOrigins)) return "*";
        if (requestOrigin == null) return allowedOrigins.split(",")[0].strip();
        for (String allowed : allowedOrigins.split(",")) {
            if (allowed.strip().equals(requestOrigin)) return requestOrigin;
        }
        return allowedOrigins.split(",")[0].strip();
    }
}
