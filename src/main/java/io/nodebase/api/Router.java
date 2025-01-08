package io.nodebase.api;

import io.nodebase.auth.ApiKeyService;
import io.nodebase.auth.AuthService;
import io.nodebase.config.ServerConfig;
import io.nodebase.database.DatabaseService;
import io.nodebase.middleware.AuthMiddleware;
import io.nodebase.util.JsonUtil;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public final class Router extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(Router.class);

    private final ServerConfig config;
    private final AuthHandler authHandler;
    private final DatabaseHandler databaseHandler;
    private final AuthMiddleware authMiddleware;

    public Router(ServerConfig config,
                  AuthService authService,
                  ApiKeyService apiKeyService,
                  AuthMiddleware authMiddleware,
                  DatabaseService dbService) {
        this.config = config;
        this.authHandler = new AuthHandler(authService, apiKeyService);
        this.databaseHandler = new DatabaseHandler(dbService);
        this.authMiddleware = authMiddleware;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI();
        log.debug("{} {}", req.getMethod(), path);

        if (path == null) {
            writeJson(resp, 400, Map.of("error", "invalid request"));
            return;
        }

        if (path.startsWith("/auth")) {
            authHandler.handle(req, resp);
            return;
        }

        if (!authMiddleware.authenticate(req, resp)) return;

        if (path.equals("/") || path.equals("/health")) {
            writeJson(resp, 200, Map.of("service", "nodebase", "version", "1.0", "status", "ok"));
            return;
        }

        if (path.startsWith("/db/")) {
            databaseHandler.handle(req, resp);
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
