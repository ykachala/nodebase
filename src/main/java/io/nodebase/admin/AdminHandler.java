package io.nodebase.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import io.nodebase.auth.User;
import io.nodebase.auth.UserRepository;
import io.nodebase.database.DatabaseService;
import io.nodebase.security.RulesEngine;
import io.nodebase.security.SecurityRule;
import io.nodebase.storage.StorageService;
import io.nodebase.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public final class AdminHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminHandler.class);

    private final UserRepository userRepository;
    private final DatabaseService dbService;
    private final StorageService storageService;
    private final RulesEngine rulesEngine;
    private final String dataDir;

    public AdminHandler(UserRepository userRepository, DatabaseService dbService,
                        StorageService storageService, RulesEngine rulesEngine, String dataDir) {
        this.userRepository = userRepository;
        this.dbService = dbService;
        this.storageService = storageService;
        this.rulesEngine = rulesEngine;
        this.dataDir = dataDir;
    }

    public void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String role = (String) req.getAttribute("nodebase.role");
        if (!"ADMIN".equals(role)) {
            writeJson(resp, 403, Map.of("error", "admin access required"));
            return;
        }

        String path = req.getRequestURI();
        String method = req.getMethod();

        if ("GET".equals(method) && path.equals("/admin/stats")) {
            stats(resp);
        } else if ("GET".equals(method) && path.equals("/admin/users")) {
            listUsers(resp);
        } else if ("DELETE".equals(method) && path.startsWith("/admin/users/")) {
            String userId = path.substring("/admin/users/".length());
            deleteUser(resp, userId);
        } else if ("GET".equals(method) && path.equals("/admin/collections")) {
            listCollections(resp);
        } else if ("DELETE".equals(method) && path.startsWith("/admin/collections/")) {
            String collection = path.substring("/admin/collections/".length());
            dropCollection(resp, collection);
        } else if ("GET".equals(method) && path.equals("/admin/rules")) {
            getRules(resp);
        } else if ("POST".equals(method) && path.equals("/admin/rules")) {
            setRules(req, resp);
        } else {
            writeJson(resp, 404, Map.of("error", "not found"));
        }
    }

    private void stats(HttpServletResponse resp) throws IOException {
        long userCount = userRepository.findAll().size();
        List<Map<String, Object>> collections = dbService.collectionStats();
        long storageTotalBytes = storageService.totalSizeBytes();
        writeJson(resp, 200, Map.of(
                "users", userCount,
                "collections", collections,
                "storageTotalBytes", storageTotalBytes
        ));
    }

    private void listUsers(HttpServletResponse resp) throws IOException {
        List<Map<String, Object>> users = userRepository.findAll().stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "email", u.getEmail(),
                        "role", u.getRole(),
                        "createdAt", u.getCreatedAt()
                ))
                .toList();
        writeJson(resp, 200, users);
    }

    private void deleteUser(HttpServletResponse resp, String userId) throws IOException {
        if (userRepository.findById(userId).isEmpty()) {
            writeJson(resp, 404, Map.of("error", "user not found"));
            return;
        }
        userRepository.deleteById(userId);
        writeJson(resp, 200, Map.of("deleted", userId));
    }

    private void listCollections(HttpServletResponse resp) throws IOException {
        writeJson(resp, 200, dbService.collectionStats());
    }

    private void dropCollection(HttpServletResponse resp, String collection) throws IOException {
        dbService.dropCollection(collection);
        writeJson(resp, 200, Map.of("dropped", collection));
    }

    private void getRules(HttpServletResponse resp) throws IOException {
        writeJson(resp, 200, rulesEngine.getRules());
    }

    private void setRules(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            List<SecurityRule> newRules = JsonUtil.fromJson(req.getInputStream(),
                    new TypeReference<List<SecurityRule>>() {});
            rulesEngine.replaceRules(newRules, Paths.get(dataDir).resolve("security-rules.json"));
            writeJson(resp, 200, Map.of("message", "rules updated", "count", newRules.size()));
        } catch (Exception e) {
            writeJson(resp, 400, Map.of("error", "invalid rules format: " + e.getMessage()));
        }
    }

    private static void writeJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=utf-8");
        resp.getWriter().write(JsonUtil.toJson(body));
    }
}
