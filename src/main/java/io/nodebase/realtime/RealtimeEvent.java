package io.nodebase.realtime;

import java.util.Map;

public final class RealtimeEvent {

    public enum Type { CREATED, UPDATED, DELETED }

    private final Type type;
    private final String collection;
    private final String documentId;
    private final Map<String, Object> data;
    private final long timestamp;

    public RealtimeEvent(Type type, String collection, String documentId, Map<String, Object> data) {
        this.type = type;
        this.collection = collection;
        this.documentId = documentId;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public Type getType() { return type; }
    public String getCollection() { return collection; }
    public String getDocumentId() { return documentId; }
    public Map<String, Object> getData() { return data; }
    public long getTimestamp() { return timestamp; }
}
