package io.nodebase.api;

import io.nodebase.config.ServerConfig;
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

    public Router(ServerConfig config) {
        this.config = config;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI();
        log.debug("{} {}", req.getMethod(), path);

        if (path == null || path.equals("/") || path.equals("/health")) {
            writeJson(resp, HttpServletResponse.SC_OK, Map.of(
                    "service", "nodebase",
                    "version", "1.0",
                    "status", "ok"
            ));
            return;
        }

        writeJson(resp, HttpServletResponse.SC_NOT_FOUND, Map.of(
                "error", "no handler registered for " + path
        ));
    }

    private static void writeJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=utf-8");
        resp.getWriter().write(JsonUtil.toJson(body));
    }
}
