package io.nodebase;

import io.nodebase.admin.AdminHandler;
import io.nodebase.api.Router;
import io.nodebase.auth.ApiKeyService;
import io.nodebase.auth.AuthService;
import io.nodebase.auth.JwtProvider;
import io.nodebase.auth.UserRepository;
import io.nodebase.config.ServerConfig;
import io.nodebase.database.DatabaseService;
import io.nodebase.database.DocumentRepository;
import io.nodebase.middleware.AuthMiddleware;
import io.nodebase.realtime.RealtimeEvent;
import io.nodebase.realtime.RealtimeWebSocketServlet;
import io.nodebase.realtime.SubscriptionManager;
import io.nodebase.security.RulesEngine;
import io.nodebase.storage.StorageRepository;
import io.nodebase.storage.StorageService;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public final class NodebaseServer {

    private static final Logger log = LoggerFactory.getLogger(NodebaseServer.class);

    private final ServerConfig config;
    private final Server server;
    private Connection dbConnection;
    private RulesEngine rulesEngine;

    public NodebaseServer(ServerConfig config) throws Exception {
        this.config = config;
        this.dbConnection = openDatabase();
        this.server = buildServer();
    }

    private Connection openDatabase() throws Exception {
        Files.createDirectories(Paths.get(config.getDataDir()));
        String url = "jdbc:sqlite:" + Paths.get(config.getDataDir()).resolve("nodebase.db");
        Connection conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
        }
        return conn;
    }

    private Server buildServer() throws Exception {
        JwtProvider jwtProvider = new JwtProvider(config.getJwtSecret(), config.getJwtExpiryMillis());
        UserRepository userRepo = new UserRepository(dbConnection);
        AuthService authService = new AuthService(userRepo, jwtProvider);
        ApiKeyService apiKeyService = new ApiKeyService(userRepo);
        AuthMiddleware authMiddleware = new AuthMiddleware(authService, apiKeyService, config.getMasterKey());

        DocumentRepository docRepo = new DocumentRepository(dbConnection);
        DatabaseService dbService = new DatabaseService(docRepo);

        SubscriptionManager subscriptionManager = new SubscriptionManager();
        dbService.setEventEmitter(evt -> subscriptionManager.broadcast(new RealtimeEvent(
                RealtimeEvent.Type.valueOf(evt.type().name()),
                evt.collection(),
                evt.documentId(),
                evt.data()
        )));

        StorageRepository storageRepo = new StorageRepository(dbConnection);
        Files.createDirectories(Paths.get(config.getStoragePath()));
        StorageService storageService = new StorageService(storageRepo, config.getStoragePath(), config.getMaxUploadMb());

        rulesEngine = new RulesEngine(config.getDataDir());
        AdminHandler adminHandler = new AdminHandler(userRepo, dbService, storageService, rulesEngine, config.getDataDir());

        Router router = new Router(config, authService, apiKeyService, jwtProvider,
                authMiddleware, dbService, storageService, rulesEngine, adminHandler);

        Server srv = new Server();
        srv.setStopTimeout(30_000L);

        ServerConnector connector = new ServerConnector(srv);
        connector.setPort(config.getPort());
        connector.setHost(config.getHost());
        srv.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(new RealtimeWebSocketServlet(subscriptionManager, authService)), "/realtime");
        context.addServlet(new ServletHolder(router), "/*");

        srv.setHandler(context);
        return srv;
    }

    public void start() throws Exception {
        server.start();
        log.info("================================================================");
        log.info(" Nodebase 1.0  listening on http://{}:{}", config.getHost(), config.getPort());
        log.info(" data dir : {}", config.getDataDir());
        log.info(" storage  : {}", config.getStoragePath());
        log.info("================================================================");
    }

    public void stop() throws Exception {
        log.info("Stopping Nodebase ...");
        if (rulesEngine != null) rulesEngine.shutdown();
        server.stop();
        if (dbConnection != null && !dbConnection.isClosed()) {
            dbConnection.close();
        }
        log.info("Stopped.");
    }

    public void join() throws InterruptedException {
        server.join();
    }

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.load();
        NodebaseServer app = new NodebaseServer(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                app.stop();
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            }
        }, "nodebase-shutdown"));

        app.start();
        app.join();
    }
}
