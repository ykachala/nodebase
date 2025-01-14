package io.nodebase.api;

import io.nodebase.auth.ApiKeyService;
import io.nodebase.auth.AuthService;
import io.nodebase.auth.JwtProvider;
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
    private final JwtProvider jwtProvider;
    private final String masterKey;

    public AuthHandler(AuthService authService, ApiKeyService apiKeyService,
                       JwtProvider jwtProvider, String masterKey) {
        this.authService = authService;
        this.apiKeyService = apiKeyService;
        this.jwtProvider = jwtProvider;
        this.masterKey = masterKey;
    }

    public void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI();
        String method = req.getMethod();

        if ("POST".equals(method) && path.equals("/auth/register")) {
            register(req, resp);
        } else if ("POST".equals(method) && path.equals("/auth/login")) {
            login(req, resp);
        } else if ("POST".equals(method) && path.equals("/auth/admin/login")) {
            adminLogin(req, resp);
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
        AuthService.AuthResult result = authService.register(
                body.getOrDefault("email", ""), body.getOrDefault("password", ""));
        if (result.success()) {
            writeJson(resp, 201, Map.of("token", result.token(), "userId", result.userId(), "role", result.role()));
        } else {
            writeJson(resp, 400, Map.of("error", result.error()));
        }
    }

    private void login(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, String> body = parseBody(req);
        AuthService.AuthResult result = authService.login(
                body.getOrDefault("email", ""), body.getOrDefault("password", ""));
        if (result.success()) {
            writeJson(resp, 200, Map.of("token", result.token(), "userId", result.userId(), "role", result.role()));
        } else {
            writeJson(resp, 401, Map.of("error", result.error()));
        }
    }

    private void adminLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, String> body = parseBody(req);
        String key = body.getOrDefault("masterKey", "");
        if (!masterKey.equals(key)) {
            writeJson(resp, 401, Map.of("error", "invalid master key"));
            return;
        }
        String token = jwtProvider.generate("admin", "ADMIN");
        writeJson(resp, 200, Map.of("token", token, "role", "ADMIN"));
    }

    private void generateApiKey(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userId = (String) req.getAttribute("nodebase.userId");
        if (userId == null) { writeJson(resp, 401, Map.of("error", "authentication required")); return; }
        writeJson(resp, 200, Map.of("apiKey", apiKeyService.generate(userId)));
    }

    private void revokeApiKey(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String userId = (String) req.getAttribute("nodebase.userId");
        if (userId == null) { writeJson(resp, 401, Map.of("error", "authentication required")); return; }
        apiKeyService.revoke(userId);
        writeJson(resp, 200, Map.of("message", "API key revoked"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseBody(HttpServletRequest req) {
        try { return JsonUtil.fromJson(req.getInputStream(), Map.class); }
        catch (Exception e) { return Map.of(); }
    }

    static void writeJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=utf-8");
        resp.getWriter().write(JsonUtil.toJson(body));
    }
}
