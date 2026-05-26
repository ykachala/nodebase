package io.nodebase.admin;

import io.nodebase.config.ServerConfig;
import io.nodebase.database.DatabaseService;
import io.nodebase.security.RulesEngine;
import io.nodebase.storage.StorageService;
import io.nodebase.auth.UserRepository;
import io.nodebase.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DashboardHandler {

    private static final Logger log = LoggerFactory.getLogger(DashboardHandler.class);

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css",  "text/css; charset=utf-8",
            "js",   "application/javascript; charset=utf-8",
            "json", "application/json; charset=utf-8",
            "png",  "image/png",
            "svg",  "image/svg+xml",
            "ico",  "image/x-icon"
    );

    private final ServerConfig config;
    private final UserRepository userRepository;
    private final DatabaseService dbService;
    private final StorageService storageService;
    private final RulesEngine rulesEngine;

    /** token → expiryMs */
    private final ConcurrentHashMap<String, Long> sessions = new ConcurrentHashMap<>();

    public DashboardHandler(ServerConfig config, UserRepository userRepository,
                            DatabaseService dbService, StorageService storageService,
                            RulesEngine rulesEngine) {
        this.config = config;
        this.userRepository = userRepository;
        this.dbService = dbService;
        this.storageService = storageService;
        this.rulesEngine = rulesEngine;
    }

    public void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI();

        if (path.equals("/dashboard") || path.equals("/dashboard/")) {
            resp.sendRedirect("/dashboard/index.html");
            return;
        }

        if (path.startsWith("/dashboard/static/")) {
            handleStatic(path.substring("/dashboard/static/".length()), resp);
            return;
        }

        if (path.startsWith("/dashboard/api/")) {
            handleApi(req, resp);
            return;
        }

        if (path.equals("/dashboard/index.html")) {
            handleStatic("index.html", resp);
            return;
        }

        writeJson(resp, 404, Map.of("error", "not found"));
    }

    private void handleStatic(String relativePath, HttpServletResponse resp) throws IOException {
        if (relativePath.isEmpty() || relativePath.contains("..")) {
            writeJson(resp, 400, Map.of("error", "invalid path"));
            return;
        }

        String resourcePath = "admin/" + relativePath;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                resp.setStatus(404);
                return;
            }
            String ext = "";
            int dot = relativePath.lastIndexOf('.');
            if (dot >= 0) ext = relativePath.substring(dot + 1).toLowerCase();
            resp.setContentType(CONTENT_TYPES.getOrDefault(ext, "application/octet-stream"));
            resp.setStatus(200);
            try (OutputStream out = resp.getOutputStream()) {
                in.transferTo(out);
            }
        }
    }

    private void handleApi(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI();
        String method = req.getMethod();

        if ("POST".equals(method) && path.equals("/dashboard/api/login")) {
            login(req, resp);
            return;
        }

        if (!requireAuth(req, resp)) return;

        if ("GET".equals(method) && path.equals("/dashboard/api/stats")) {
            stats(resp);
        } else if ("GET".equals(method) && path.equals("/dashboard/api/config")) {
            getConfig(resp);
        } else if ("POST".equals(method) && path.equals("/dashboard/api/config")) {
            saveConfig(req, resp);
        } else if ("POST".equals(method) && path.equals("/dashboard/api/seed")) {
            seed(req, resp);
        } else if ("POST".equals(method) && path.equals("/dashboard/api/seed/demo")) {
            seedDemo(resp);
        } else if ("DELETE".equals(method) && path.startsWith("/dashboard/api/collections/")) {
            String name = path.substring("/dashboard/api/collections/".length());
            dropCollection(resp, name);
        } else {
            writeJson(resp, 404, Map.of("error", "not found"));
        }
    }

    /** Returns false and writes 401 if the session token is missing or expired. */
    private boolean requireAuth(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String token = req.getHeader("X-Dashboard-Token");
        if (token == null || token.isBlank()) {
            writeJson(resp, 401, Map.of("error", "missing X-Dashboard-Token"));
            return false;
        }
        Long expiry = sessions.get(token);
        if (expiry == null || System.currentTimeMillis() > expiry) {
            sessions.remove(token);
            writeJson(resp, 401, Map.of("error", "session expired or invalid"));
            return false;
        }
        return true;
    }

    private void login(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = JsonUtil.fromJson(req.getInputStream(), Map.class);
            String key = (String) body.get("masterKey");
            if (!config.getMasterKey().equals(key)) {
                writeJson(resp, 401, Map.of("error", "invalid master key"));
                return;
            }
            String token = UUID.randomUUID().toString();
            long expiresAt = System.currentTimeMillis() + 1_800_000L; // 30 minutes
            sessions.put(token, expiresAt);
            writeJson(resp, 200, Map.of("token", token, "expiresAt", expiresAt));
        } catch (Exception e) {
            writeJson(resp, 400, Map.of("error", "invalid request body"));
        }
    }

    private void stats(HttpServletResponse resp) throws IOException {
        long userCount = userRepository.findAll().size();
        var collections = dbService.collectionStats();
        long storageTotalBytes = storageService.totalSizeBytes();
        writeJson(resp, 200, Map.of(
                "users", userCount,
                "collections", collections,
                "storageTotalBytes", storageTotalBytes,
                "storageTotalMb", String.format("%.2f", storageTotalBytes / 1_048_576.0)
        ));
    }

    private void getConfig(HttpServletResponse resp) throws IOException {
        writeJson(resp, 200, Map.of(
                "port",               config.getPort(),
                "host",               config.getHost(),
                "cors.allowedOrigins",config.getCorsAllowedOrigins(),
                "maxUploadMb",        config.getMaxUploadMb(),
                "rateLimitPerIp",     config.getRateLimitPerIp(),
                "rateLimitPerKey",    config.getRateLimitPerKey()
        ));
    }

    @SuppressWarnings("unchecked")
    private void saveConfig(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Map<String, Object> body = JsonUtil.fromJson(req.getInputStream(), Map.class);
            Map<String, String> updates = new java.util.HashMap<>();
            body.forEach((k, v) -> updates.put(k, String.valueOf(v)));
            config.persist(updates);
            writeJson(resp, 200, Map.of("message", "config saved — restart to apply rate limit changes"));
        } catch (Exception e) {
            log.warn("Failed to save config: {}", e.getMessage());
            writeJson(resp, 400, Map.of("error", "failed to save config: " + e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private void seed(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Map<String, Object> body = JsonUtil.fromJson(req.getInputStream(), Map.class);
            String collection = (String) body.get("collection");
            Map<String, Object> template = (Map<String, Object>) body.getOrDefault("template", Map.of());
            int count = body.containsKey("count") ? ((Number) body.get("count")).intValue() : 10;

            if (collection == null || collection.isBlank()) {
                writeJson(resp, 400, Map.of("error", "collection is required"));
                return;
            }
            count = Math.min(count, 500);

            for (int i = 0; i < count; i++) {
                Map<String, Object> doc = interpolate(template, i);
                dbService.create(collection, doc, "admin-seed");
            }
            writeJson(resp, 200, Map.of("inserted", count, "collection", collection));
        } catch (Exception e) {
            writeJson(resp, 400, Map.of("error", "seed failed: " + e.getMessage()));
        }
    }

    private void seedDemo(HttpServletResponse resp) throws IOException {
        int total = 0;

        // 5 sample users
        for (int i = 1; i <= 5; i++) {
            dbService.create("users", Map.of(
                    "name",     "User " + i,
                    "email",    "user" + i + "@example.com",
                    "age",      20 + i,
                    "verified", i % 2 == 0
            ), "admin-seed");
            total++;
        }

        // 5 sample posts
        for (int i = 1; i <= 5; i++) {
            dbService.create("posts", Map.of(
                    "title",   "Sample Post " + i,
                    "content", "This is the body of post " + i + ". Edit me!",
                    "tags",    java.util.List.of("demo", "sample"),
                    "views",   i * 10
            ), "admin-seed");
            total++;
        }

        // 3 sample products
        for (int i = 1; i <= 3; i++) {
            dbService.create("products", Map.of(
                    "name",     "Product " + i,
                    "price",    9.99 * i,
                    "inStock",  true,
                    "category", "demo"
            ), "admin-seed");
            total++;
        }

        writeJson(resp, 200, Map.of(
                "inserted", total,
                "collections", java.util.List.of("users", "posts", "products")
        ));
    }

    private void dropCollection(HttpServletResponse resp, String name) throws IOException {
        if (name == null || name.isBlank()) {
            writeJson(resp, 400, Map.of("error", "collection name required"));
            return;
        }
        dbService.dropCollection(name);
        writeJson(resp, 200, Map.of("dropped", name));
    }

    /** Interpolates {{index}}, {{uuid}}, {{now}} placeholders in template string values. */
    private static Map<String, Object> interpolate(Map<String, Object> template, int index) {
        Map<String, Object> result = new java.util.HashMap<>();
        template.forEach((k, v) -> {
            if (v instanceof String s) {
                s = s.replace("{{index}}", String.valueOf(index))
                     .replace("{{uuid}}", UUID.randomUUID().toString())
                     .replace("{{now}}", String.valueOf(System.currentTimeMillis()));
                result.put(k, s);
            } else {
                result.put(k, v);
            }
        });
        return result;
    }

    private static void writeJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=utf-8");
        resp.getWriter().write(JsonUtil.toJson(body));
    }
}
