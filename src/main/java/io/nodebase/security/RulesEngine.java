package io.nodebase.security;

import com.fasterxml.jackson.core.type.TypeReference;
import io.nodebase.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class RulesEngine {

    private static final Logger log = LoggerFactory.getLogger(RulesEngine.class);

    private final Path externalRulesFile;
    private final AtomicReference<List<SecurityRule>> rules = new AtomicReference<>(Collections.emptyList());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rules-reload");
        t.setDaemon(true);
        return t;
    });

    public RulesEngine(String dataDir) {
        this.externalRulesFile = Paths.get(dataDir).resolve("security-rules.json");
        loadRules();
        scheduler.scheduleAtFixedRate(this::loadRules, 30, 30, TimeUnit.SECONDS);
    }

    public void loadRules() {
        try {
            List<SecurityRule> loaded;
            if (Files.isRegularFile(externalRulesFile)) {
                try (InputStream in = Files.newInputStream(externalRulesFile)) {
                    loaded = JsonUtil.fromJson(in, new TypeReference<>() {});
                }
                log.debug("Loaded security rules from {}", externalRulesFile);
            } else {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("security-rules.json")) {
                    loaded = in != null ? JsonUtil.fromJson(in, new TypeReference<>() {}) : Collections.emptyList();
                }
                log.debug("Loaded security rules from classpath defaults");
            }
            rules.set(loaded);
        } catch (IOException e) {
            log.error("Failed to load security rules: {}", e.getMessage());
        }
    }

    public void replaceRules(List<SecurityRule> newRules, Path targetFile) throws IOException {
        Files.createDirectories(targetFile.getParent());
        Files.writeString(targetFile, JsonUtil.toJson(newRules));
        rules.set(newRules);
        log.info("Security rules replaced ({} rules)", newRules.size());
    }

    public boolean evaluate(String resource, SecurityRule.Operation operation,
                            String userId, boolean isOwner, String role) {
        List<SecurityRule> current = rules.get();
        for (SecurityRule rule : current) {
            if (!rule.isEnabled()) continue;
            if (!matchesResource(rule.getResource(), resource)) continue;
            if (rule.getOperation() != SecurityRule.Operation.ALL && rule.getOperation() != operation) continue;
            return checkCondition(rule.getCondition(), userId, isOwner, role);
        }
        // Default deny
        return false;
    }

    public List<SecurityRule> getRules() {
        return Collections.unmodifiableList(rules.get());
    }

    private static boolean matchesResource(String pattern, String resource) {
        if (pattern.equals(resource)) return true;
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return resource.startsWith(prefix);
        }
        return false;
    }

    private static boolean checkCondition(SecurityRule.Condition condition,
                                          String userId, boolean isOwner, String role) {
        return switch (condition) {
            case PUBLIC        -> true;
            case AUTHENTICATED -> userId != null;
            case OWNER         -> isOwner || "ADMIN".equals(role);
            case ADMIN         -> "ADMIN".equals(role);
        };
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
