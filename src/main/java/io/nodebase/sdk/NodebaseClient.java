package io.nodebase.sdk;

import java.net.http.HttpClient;
import java.time.Duration;

public final class NodebaseClient {

    private final String baseUrl;
    private final HttpClient http;
    private final TokenHolder tokenHolder;
    private final NodebaseAuth auth;
    private final NodebaseDatabase database;
    private final NodebaseStorage storage;

    public NodebaseClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.tokenHolder = new TokenHolder();
        this.auth = new NodebaseAuth(this.baseUrl, http, tokenHolder);
        this.database = new NodebaseDatabase(this.baseUrl, http, tokenHolder);
        this.storage = new NodebaseStorage(this.baseUrl, http, tokenHolder);
    }

    public NodebaseClient(String baseUrl, String apiKey) {
        this(baseUrl);
        tokenHolder.setToken(apiKey);
    }

    public NodebaseAuth auth() { return auth; }
    public NodebaseDatabase database() { return database; }
    public NodebaseStorage storage() { return storage; }

    public void setToken(String token) { tokenHolder.setToken(token); }
}
