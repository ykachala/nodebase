package io.nodebase;

import io.nodebase.api.Router;
import io.nodebase.config.ServerConfig;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NodebaseServer {

    private static final Logger log = LoggerFactory.getLogger(NodebaseServer.class);

    private final ServerConfig config;
    private final Server server;

    public NodebaseServer(ServerConfig config) {
        this.config = config;
        this.server = buildServer();
    }

    private Server buildServer() {
        Server srv = new Server();
        srv.setStopTimeout(30_000L);

        ServerConnector connector = new ServerConnector(srv);
        connector.setPort(config.getPort());
        connector.setHost(config.getHost());
        srv.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        Router router = new Router(config);
        context.addServlet(new ServletHolder(router), "/*");

        srv.setHandler(context);
        return srv;
    }

    public void start() throws Exception {
        server.start();
        log.info("================================================================");
        log.info(" Nodebase 1.0  listening on http://{}:{}", config.getHost(), config.getPort());
        log.info(" data dir: {}", config.getDataDir());
        log.info(" storage : {}", config.getStoragePath());
        log.info("================================================================");
    }

    public void stop() throws Exception {
        log.info("Stopping Nodebase ...");
        server.stop();
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
