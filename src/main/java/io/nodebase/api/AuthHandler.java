package io.nodebase.api;

import io.nodebase.auth.ApiKeyService;
import io.nodebase.auth.AuthService;
import io.nodebase.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public final class AuthHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);

    private final AuthService authService;
    private final ApiKeyService apiKeyService;

    public AuthHandler(AuthService authService, ApiKeyService apiKeyService) {
        this.authService = authService;
        this.apiKeyService = apiKeyService;
    }

    public void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI();
        String method = req.getMethod();

        if ("POST".equals(method) && path.equals("/auth/register")) {
            register(req, resp);
        } else if ("POST".equals(method) && path.equals("/auth/login")) {
            login(req, resp);
        } else if ("POST".equals(method) && path.equals("/auth/apikey")) {
            generateApiKey(req, resp);
        } else if ("DELETE".equals(method) && path.equals("/auth/apikey")) {
            revokeApiKey(req, resp);
        } else {
            writeJson(resp, 404, Map.of("error", "not found"));
        }
    }

    private void register(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, String> body = parseBody(req);
        String email = body.getOrDefault("email", "");
        String password = body.getOrDefault("password", "");
        AuthService.AuthResult result = authService.register(email, password);
        if (result.success()) {
            writeJson(resp, 201, Map.of(
                    "token", result.token(),
                    "userId", result.userId(),
                    "role", result.role()
            ));
        } else {
            writeJson(resp, 400, Map.of("error", result.error()));
        }
    }

    private void login(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, String> body = parseBody(req);
        String email = body.getOrDefault("email", "");
        String password = body.getOrDefault("password", "");
        AuthService.AuthResult result = authService.login(email, password);
        if (result.success()) {
            writeJson(resp, 200, Map.of(
                    "token", result.token(),
                    "userId", result.userId(),
                    "role", result.role()
            ));
        } else {
            writeJson(resp, 401, Map.of("error", result.error()));
        }
    }

    private void generateApiKey(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userId = (String) req.getAttribute("nodebase.userId");
        if (userId == null) {
            writeJson(resp, 401, Map.of("error", "authentication required"));
            return;
        }
        String apiKey = apiKeyService.generate(userId);
        writeJson(resp, 200, Map.of("apiKey", apiKey));
    }

    private void revokeApiKey(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userId = (String) req.getAttribute("nodebase.userId");
        if (userId == null) {
            writeJson(resp, 401, Map.of("error", "authentication required"));
            return;
        }
        apiKeyService.revoke(userId);
        writeJson(resp, 200, Map.of("message", "API key revoked"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseBody(HttpServletRequest req) {
        try {
            return JsonUtil.fromJson(req.getInputStream(), Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    static void writeJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=utf-8");
        resp.getWriter().write(JsonUtil.toJson(body));
    }
}
