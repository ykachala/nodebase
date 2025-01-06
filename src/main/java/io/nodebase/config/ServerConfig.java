package io.nodebase.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class ServerConfig {

    private static final Logger log = LoggerFactory.getLogger(ServerConfig.class);

    private final Properties props;

    private ServerConfig(Properties props) {
        this.props = props;
    }

    /** Load configuration from disk + classpath + system properties. */
    public static ServerConfig load() {
        Properties defaults = new Properties();
        try (InputStream in = ServerConfig.class.getClassLoader()
                .getResourceAsStream("nodebase.properties")) {
            if (in != null) {
                defaults.load(in);
                log.info("Loaded default nodebase.properties from classpath");
            } else {
                log.warn("No nodebase.properties on classpath; using hard-coded defaults");
            }
        } catch (IOException e) {
            log.warn("Failed reading nodebase.properties from classpath: {}", e.getMessage());
        }

        // External overrides
        Path externalPath = resolveExternalPath();
        if (externalPath != null && Files.isRegularFile(externalPath)) {
            try (InputStream in = Files.newInputStream(externalPath)) {
                Properties external = new Properties();
                external.load(in);
                defaults.putAll(external);
                log.info("Loaded external config: {}", externalPath);
            } catch (IOException e) {
                log.warn("Failed reading {}: {}", externalPath, e.getMessage());
            }
        }

        // Allow system property overrides: -Dnodebase.port=9090
        for (String key : defaults.stringPropertyNames()) {
            String sysKey = "nodebase." + key;
            String sysVal = System.getProperty(sysKey);
            if (sysVal != null) {
                defaults.setProperty(key, sysVal);
            }
        }

        return new ServerConfig(defaults);
    }

    private static Path resolveExternalPath() {
        String configured = System.getProperty("nodebase.config");
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured);
        }
        Path cwd = Paths.get("nodebase.properties");
        if (Files.isRegularFile(cwd)) {
            return cwd;
        }
        return null;
    }

    /* ----- typed accessors ----- */

    public int getPort() {
        return parseInt("port", 8080);
    }

    public String getHost() {
        return props.getProperty("host", "0.0.0.0");
    }

    public String getDataDir() {
        return props.getProperty("dataDir", "./data");
    }

    public String getJwtSecret() {
        return props.getProperty("jwtSecret",
                "change-me-please-this-is-the-default-secret-please-override");
    }

    public String getMasterKey() {
        return props.getProperty("masterKey", "change-me-master-key");
    }

    public String getStoragePath() {
        return props.getProperty("storagePath", "./storage");
    }

    public int getMaxUploadMb() {
        return parseInt("maxUploadMb", 50);
    }

    public String getCorsAllowedOrigins() {
        return props.getProperty("cors.allowedOrigins", "*");
    }

    public long getJwtExpiryMillis() {
        return parseLong("jwtExpiryMillis", 24L * 60L * 60L * 1000L);
    }

    public String getProperty(String key, String fallback) {
        return props.getProperty(key, fallback);
    }

    private int parseInt(String key, int fallback) {
        String v = props.getProperty(key);
        if (v == null) return fallback;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            log.warn("Bad integer for {}: {} — using default {}", key, v, fallback);
            return fallback;
        }
    }

    private long parseLong(String key, long fallback) {
        String v = props.getProperty(key);
        if (v == null) return fallback;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            log.warn("Bad long for {}: {} — using default {}", key, v, fallback);
            return fallback;
        }
    }
}
