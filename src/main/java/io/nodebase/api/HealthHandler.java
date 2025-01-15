package io.nodebase.api;

import io.nodebase.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

public final class HealthHandler {

    private static final long START_TIME = System.currentTimeMillis();

    private final Connection dbConnection;

    public HealthHandler(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    public void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getRequestURI();
        if (path.equals("/health/ready")) {
            ready(resp);
        } else {
            liveness(resp);
        }
    }

    private void liveness(HttpServletResponse resp) throws IOException {
        long uptime = (System.currentTimeMillis() - START_TIME) / 1000;
        resp.setStatus(200);
        resp.setContentType("application/json; charset=utf-8");
        resp.getWriter().write(JsonUtil.toJson(Map.of(
                "status", "ok",
                "version", "1.0",
                "uptime", uptime
        )));
    }

    private void ready(HttpServletResponse resp) throws IOException {
        boolean dbOk = checkDatabase();
        int status = dbOk ? 200 : 503;
        resp.setStatus(status);
        resp.setContentType("application/json; charset=utf-8");
        resp.getWriter().write(JsonUtil.toJson(Map.of(
                "status", dbOk ? "ready" : "unavailable",
                "database", dbOk ? "ok" : "error"
        )));
    }

    private boolean checkDatabase() {
        try (Statement st = dbConnection.createStatement()) {
            st.execute("SELECT 1");
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
