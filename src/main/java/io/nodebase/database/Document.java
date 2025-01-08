package io.nodebase.database;

import java.util.Map;

public final class Document {

    private String id;
    private String collection;
    private Map<String, Object> data;
    private String ownerId;
    private long createdAt;
    private long updatedAt;

    public Document() {
    }

    public Document(String id, String collection, Map<String, Object> data,
                    String ownerId, long createdAt, long updatedAt) {
        this.id = id;
        this.collection = collection;
        this.data = data;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCollection() { return collection; }
    public void setCollection(String collection) { this.collection = collection; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
