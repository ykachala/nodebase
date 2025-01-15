package io.nodebase.api;

import io.nodebase.admin.AdminHandler;
import io.nodebase.auth.ApiKeyService;
import io.nodebase.auth.AuthService;
import io.nodebase.auth.JwtProvider;
import io.nodebase.config.ServerConfig;
import io.nodebase.database.DatabaseService;
import io.nodebase.middleware.AuthMiddleware;
import io.nodebase.middleware.CorsFilter;
import io.nodebase.middleware.RateLimiter;
import io.nodebase.security.RulesEngine;
import io.nodebase.storage.StorageService;
import io.nodebase.util.JsonUtil;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.util.Map;

public final class Router extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(Router.class);

    private final AuthHandler authHandler;
    private final DatabaseHandler databaseHandler;
    private final StorageHandler storageHandler;
    private final AdminHandler adminHandler;
    private final HealthHandler healthHandler;
    private final AuthMiddleware authMiddleware;
    private final CorsFilter corsFilter;
    private final RateLimiter rateLimiter;

    public Router(ServerConfig config,
                  AuthService authService,
                  ApiKeyService apiKeyService,
                  JwtProvider jwtProvider,
                  AuthMiddleware authMiddleware,
                  DatabaseService dbService,
                  StorageService storageService,
                  RulesEngine rulesEngine,
                  AdminHandler adminHandler,
                  Connection dbConnection) {
        this.authHandler = new AuthHandler(authService, apiKeyService, jwtProvider, config.getMasterKey());
        this.databaseHandler = new DatabaseHandler(dbService, rulesEngine);
        this.storageHandler = new StorageHandler(storageService, rulesEngine);
        this.adminHandler = adminHandler;
        this.healthHandler = new HealthHandler(dbConnection);
        this.authMiddleware = authMiddleware;
        this.corsFilter = new CorsFilter(config.getCorsAllowedOrigins());
        this.rateLimiter = new RateLimiter();
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI();
        log.debug("{} {}", req.getMethod(), path);

        if (path == null) {
            writeJson(resp, 400, Map.of("error", "invalid request"));
            return;
        }

        if (!corsFilter.handle(req, resp)) return;
        if (!rateLimiter.allow(req, resp)) return;

        if (path.startsWith("/health")) {
            healthHandler.handle(req, resp);
            return;
        }

        if (path.startsWith("/auth")) {
            authHandler.handle(req, resp);
            return;
        }

        if (!authMiddleware.authenticate(req, resp)) return;

        if (path.equals("/")) {
            writeJson(resp, 200, Map.of("service", "nodebase", "version", "1.0", "status", "ok"));
            return;
        }

        if (path.startsWith("/db/")) {
            databaseHandler.handle(req, resp);
            return;
        }

        if (path.startsWith("/storage/") || path.equals("/storage")) {
            storageHandler.handle(req, resp);
            return;
        }

        if (path.startsWith("/admin")) {
            adminHandler.handle(req, resp);
            return;
        }

        writeJson(resp, 404, Map.of("error", "not found"));
    }

    static void writeJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=utf-8");
        resp.getWriter().write(JsonUtil.toJson(body));
    }
}
