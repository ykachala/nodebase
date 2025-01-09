package io.nodebase.realtime;

import com.fasterxml.jackson.core.type.TypeReference;
import io.nodebase.auth.AuthService;
import io.nodebase.util.JsonUtil;
import io.jsonwebtoken.Claims;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@WebSocket
public final class RealtimeWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);

    private final SubscriptionManager subscriptionManager;
    private final AuthService authService;

    private Session session;
    private String sessionId;
    private String userId;

    public RealtimeWebSocketHandler(SubscriptionManager subscriptionManager, AuthService authService) {
        this.subscriptionManager = subscriptionManager;
        this.authService = authService;
    }

    @OnWebSocketOpen
    public void onOpen(Session session) {
        String token = extractToken(session);
        Optional<Claims> claims = authService.validateToken(token != null ? token : "");
        if (claims.isEmpty()) {
            session.close(4001, "authentication required");
            return;
        }
        this.session = session;
        this.sessionId = UUID.randomUUID().toString();
        this.userId = claims.get().getSubject();
        subscriptionManager.registerSession(sessionId, session);
        log.debug("WebSocket opened session={} user={}", sessionId, userId);
        send(Map.of("type", "connected", "sessionId", sessionId));
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        if (sessionId != null) {
            subscriptionManager.removeSession(sessionId);
            log.debug("WebSocket closed session={}", sessionId);
        }
    }

    @OnWebSocketError
    public void onError(Session session, Throwable cause) {
        log.warn("WebSocket error session={}: {}", sessionId, cause.getMessage());
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String text) {
        if (sessionId == null) return;
        try {
            Map<String, Object> msg = JsonUtil.fromJson(text, new TypeReference<>() {});
            String type = (String) msg.get("type");
            String channel = (String) msg.get("channel");

            switch (type != null ? type : "") {
                case "subscribe" -> {
                    if (channel != null && !channel.isBlank()) {
                        subscriptionManager.subscribe(sessionId, channel);
                        send(Map.of("type", "subscribed", "channel", channel));
                    }
                }
                case "unsubscribe" -> {
                    if (channel != null) {
                        subscriptionManager.unsubscribe(sessionId, channel);
                        send(Map.of("type", "unsubscribed", "channel", channel));
                    }
                }
                case "ping" -> send(Map.of("type", "pong"));
                default -> send(Map.of("type", "error", "message", "unknown message type"));
            }
        } catch (Exception e) {
            send(Map.of("type", "error", "message", "invalid message format"));
        }
    }

    private void send(Object payload) {
        if (session != null && session.isOpen()) {
            session.sendText(JsonUtil.toJson(payload), Callback.NOOP);
        }
    }

    private static String extractToken(Session session) {
        String query = session.getUpgradeRequest().getQueryString();
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("token=")) return part.substring(6);
            }
        }
        String auth = session.getUpgradeRequest().getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7).strip();
        return null;
    }
}
