package io.nodebase.api;

import io.nodebase.storage.StorageObject;
import io.nodebase.storage.StorageService;
import io.nodebase.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StorageHandler {

    private static final Logger log = LoggerFactory.getLogger(StorageHandler.class);

    private final StorageService storageService;

    public StorageHandler(StorageService storageService) {
        this.storageService = storageService;
    }

    public void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // /storage/{bucket}[/{*path}]
        String rawPath = req.getRequestURI().replaceFirst("^/storage/?", "");
        int slash = rawPath.indexOf('/');
        String bucket = slash >= 0 ? rawPath.substring(0, slash) : rawPath;
        String filePath = slash >= 0 ? rawPath.substring(slash + 1) : null;

        if (bucket.isBlank()) {
            AuthHandler.writeJson(resp, 400, Map.of("error", "bucket name required"));
            return;
        }

        String userId = (String) req.getAttribute("nodebase.userId");
        String role = (String) req.getAttribute("nodebase.role");
        String method = req.getMethod();

        try {
            if (filePath == null || filePath.isBlank()) {
                if ("GET".equals(method)) {
                    List<StorageObject> objects = storageService.list(bucket);
                    AuthHandler.writeJson(resp, 200, objects);
                } else {
                    AuthHandler.writeJson(resp, 405, Map.of("error", "method not allowed"));
                }
                return;
            }

            switch (method) {
                case "POST", "PUT" -> upload(req, resp, bucket, filePath, userId, role);
                case "GET" -> download(req, resp, bucket, filePath);
                case "DELETE" -> delete(req, resp, bucket, filePath, userId, role);
                default -> AuthHandler.writeJson(resp, 405, Map.of("error", "method not allowed"));
            }
        } catch (StorageService.StorageException e) {
            int status = e.getMessage().contains("not authorized") ? 403 :
                         e.getMessage().contains("not found") ? 404 : 400;
            AuthHandler.writeJson(resp, status, Map.of("error", e.getMessage()));
        }
    }

    private void upload(HttpServletRequest req, HttpServletResponse resp,
                        String bucket, String path, String userId, String role) throws IOException {
        String contentType = req.getContentType();
        long contentLength = req.getContentLengthLong();
        StorageObject obj = storageService.store(bucket, path, req.getInputStream(),
                contentType, contentLength, userId);
        AuthHandler.writeJson(resp, 201, obj);
    }

    private void download(HttpServletRequest req, HttpServletResponse resp,
                          String bucket, String path) throws IOException {
        Optional<StorageObject> meta = storageService.getMeta(bucket, path);
        if (meta.isEmpty()) {
            AuthHandler.writeJson(resp, 404, Map.of("error", "not found"));
            return;
        }
        resp.setStatus(200);
        resp.setContentType(meta.get().getContentType());
        resp.setHeader("Content-Disposition", "inline; filename=\"" + meta.get().getFilename() + "\"");
        storageService.streamFile(bucket, path, resp.getOutputStream());
    }

    private void delete(HttpServletRequest req, HttpServletResponse resp,
                        String bucket, String path, String userId, String role) throws IOException {
        boolean deleted = storageService.delete(bucket, path, userId, role);
        if (deleted) {
            AuthHandler.writeJson(resp, 200, Map.of("deleted", path));
        } else {
            AuthHandler.writeJson(resp, 404, Map.of("error", "not found"));
        }
    }
}
