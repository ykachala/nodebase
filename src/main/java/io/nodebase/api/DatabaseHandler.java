package io.nodebase.api;

import com.fasterxml.jackson.core.type.TypeReference;
import io.nodebase.database.DatabaseService;
import io.nodebase.database.Document;
import io.nodebase.database.QueryFilter;
import io.nodebase.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DatabaseHandler {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHandler.class);

    private final DatabaseService dbService;

    public DatabaseHandler(DatabaseService dbService) {
        this.dbService = dbService;
    }

    public void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // /db/{collection}[/{id}]
        String path = req.getRequestURI();
        String[] parts = path.replaceFirst("^/db/?", "").split("/", 2);
        String collection = parts.length > 0 ? parts[0] : "";
        String docId = parts.length > 1 ? parts[1] : null;

        if (collection.isBlank()) {
            AuthHandler.writeJson(resp, 400, Map.of("error", "collection name required"));
            return;
        }

        String userId = (String) req.getAttribute("nodebase.userId");
        String role = (String) req.getAttribute("nodebase.role");
        String method = req.getMethod();

        try {
            if (docId == null || docId.isBlank()) {
                handleCollection(req, resp, method, collection, userId, role);
            } else {
                handleDocument(req, resp, method, collection, docId, userId, role);
            }
        } catch (DatabaseService.AccessDeniedException e) {
            AuthHandler.writeJson(resp, 403, Map.of("error", e.getMessage()));
        }
    }

    private void handleCollection(HttpServletRequest req, HttpServletResponse resp,
                                   String method, String collection,
                                   String userId, String role) throws IOException {
        if ("GET".equals(method)) {
            QueryFilter filter = QueryFilter.from(req);
            List<Document> docs = dbService.list(collection, filter);
            AuthHandler.writeJson(resp, 200, docs);
        } else if ("POST".equals(method)) {
            Map<String, Object> body = parseBody(req);
            Document doc = dbService.create(collection, body, userId);
            AuthHandler.writeJson(resp, 201, doc);
        } else {
            AuthHandler.writeJson(resp, 405, Map.of("error", "method not allowed"));
        }
    }

    private void handleDocument(HttpServletRequest req, HttpServletResponse resp,
                                 String method, String collection, String docId,
                                 String userId, String role) throws IOException {
        switch (method) {
            case "GET" -> {
                Optional<Document> doc = dbService.get(docId);
                if (doc.isEmpty()) {
                    AuthHandler.writeJson(resp, 404, Map.of("error", "document not found"));
                } else {
                    AuthHandler.writeJson(resp, 200, doc.get());
                }
            }
            case "PUT" -> {
                Map<String, Object> body = parseBody(req);
                Optional<Document> updated = dbService.replace(docId, body, userId, role);
                if (updated.isEmpty()) {
                    AuthHandler.writeJson(resp, 404, Map.of("error", "document not found"));
                } else {
                    AuthHandler.writeJson(resp, 200, updated.get());
                }
            }
            case "PATCH" -> {
                Map<String, Object> patch = parseBody(req);
                Optional<Document> patched = dbService.patch(docId, patch, userId, role);
                if (patched.isEmpty()) {
                    AuthHandler.writeJson(resp, 404, Map.of("error", "document not found"));
                } else {
                    AuthHandler.writeJson(resp, 200, patched.get());
                }
            }
            case "DELETE" -> {
                boolean deleted = dbService.delete(docId, userId, role);
                if (deleted) {
                    AuthHandler.writeJson(resp, 200, Map.of("deleted", docId));
                } else {
                    AuthHandler.writeJson(resp, 404, Map.of("error", "document not found"));
                }
            }
            default -> AuthHandler.writeJson(resp, 405, Map.of("error", "method not allowed"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseBody(HttpServletRequest req) {
        try {
            return JsonUtil.fromJson(req.getInputStream(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
