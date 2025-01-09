package io.nodebase.realtime;

import io.nodebase.util.JsonUtil;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SubscriptionManager {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionManager.class);

    // channel -> set of sessionIds
    private final Map<String, Set<String>> channelSubs = new ConcurrentHashMap<>();
    // sessionId -> Session
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, Session session) {
        sessions.put(sessionId, session);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        channelSubs.values().forEach(subs -> subs.remove(sessionId));
    }

    public void subscribe(String sessionId, String channel) {
        channelSubs.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        log.debug("Session {} subscribed to {}", sessionId, channel);
    }

    public void unsubscribe(String sessionId, String channel) {
        Set<String> subs = channelSubs.get(channel);
        if (subs != null) subs.remove(sessionId);
    }

    public void broadcast(RealtimeEvent event) {
        String docChannel = "db/" + event.getCollection() + "/" + event.getDocumentId();
        String collectionChannel = "db/" + event.getCollection();

        String payload = JsonUtil.toJson(Map.of(
                "type", event.getType().name(),
                "collection", event.getCollection(),
                "documentId", event.getDocumentId(),
                "data", event.getData(),
                "timestamp", event.getTimestamp()
        ));

        sendToChannel(collectionChannel, payload);
        sendToChannel(docChannel, payload);
    }

    private void sendToChannel(String channel, String payload) {
        Set<String> subs = channelSubs.getOrDefault(channel, Collections.emptySet());
        for (String sessionId : subs) {
            Session session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    session.sendText(payload, null);
                } catch (Exception e) {
                    log.warn("Failed to send to session {}: {}", sessionId, e.getMessage());
                }
            }
        }
    }
}
