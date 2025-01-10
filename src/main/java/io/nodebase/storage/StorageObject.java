package io.nodebase.storage;

public final class StorageObject {

    private String id;
    private String bucket;
    private String path;
    private String filename;
    private String contentType;
    private long sizeBytes;
    private String ownerId;
    private long createdAt;

    public StorageObject() {
    }

    public StorageObject(String id, String bucket, String path, String filename,
                         String contentType, long sizeBytes, String ownerId, long createdAt) {
        this.id = id;
        this.bucket = bucket;
        this.path = path;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
