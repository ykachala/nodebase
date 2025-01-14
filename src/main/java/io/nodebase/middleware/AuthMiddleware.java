package io.nodebase.middleware;

import io.jsonwebtoken.Claims;
import io.nodebase.auth.ApiKeyService;
import io.nodebase.auth.AuthService;
import io.nodebase.auth.User;
import io.nodebase.config.ServerConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

public final class AuthMiddleware {

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/auth/register", "/auth/login", "/health"
    );

    private final AuthService authService;
    private final ApiKeyService apiKeyService;
    private final String masterKey;

    public AuthMiddleware(AuthService authService, ApiKeyService apiKeyService, String masterKey) {
        this.authService = authService;
        this.apiKeyService = apiKeyService;
        this.masterKey = masterKey;
    }

    public boolean authenticate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI();
        if (isPublic(path)) return true;

        String bearer = extractBearer(req);
        if (bearer != null) {
            Optional<Claims> claims = authService.validateToken(bearer);
            if (claims.isPresent()) {
                req.setAttribute("nodebase.userId", claims.get().getSubject());
                req.setAttribute("nodebase.role", claims.get().get("role", String.class));
                return true;
            }
        }

        String apiKey = req.getHeader("x-api-key");
        if (apiKey != null) {
            Optional<User> user = apiKeyService.resolveKey(apiKey);
            if (user.isPresent()) {
                req.setAttribute("nodebase.userId", user.get().getId());
                req.setAttribute("nodebase.role", user.get().getRole());
                return true;
            }
        }

        resp.setStatus(401);
        resp.setContentType("application/json; charset=utf-8");
        resp.getWriter().write("{\"error\":\"authentication required\"}");
        return false;
    }

    private static boolean isPublic(String path) {
        if (path == null) return false;
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    private static String extractBearer(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).strip();
        }
        return null;
    }
}
