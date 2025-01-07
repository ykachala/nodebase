package io.nodebase.auth;

public final class User {

    private String id;
    private String email;
    private String passwordHash;
    private String role;
    private String apiKey;
    private long createdAt;
    private long updatedAt;

    public User() {
    }

    public User(String id, String email, String passwordHash, String role,
                String apiKey, long createdAt, long updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.apiKey = apiKey;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
