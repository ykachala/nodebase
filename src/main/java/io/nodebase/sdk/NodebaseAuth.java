package io.nodebase.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import io.nodebase.util.JsonUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public final class NodebaseAuth {

    private final String baseUrl;
    private final HttpClient http;
    private final TokenHolder tokenHolder;

    NodebaseAuth(String baseUrl, HttpClient http, TokenHolder tokenHolder) {
        this.baseUrl = baseUrl;
        this.http = http;
        this.tokenHolder = tokenHolder;
    }

    public String register(String email, String password) {
        Map<String, String> body = Map.of("email", email, "password", password);
        Map<String, Object> response = post("/auth/register", body);
        String token = (String) response.get("token");
        tokenHolder.setToken(token);
        return token;
    }

    public String login(String email, String password) {
        Map<String, String> body = Map.of("email", email, "password", password);
        Map<String, Object> response = post("/auth/login", body);
        String token = (String) response.get("token");
        tokenHolder.setToken(token);
        return token;
    }

    public String adminLogin(String masterKey) {
        Map<String, String> body = Map.of("masterKey", masterKey);
        Map<String, Object> response = post("/auth/admin/login", body);
        String token = (String) response.get("token");
        tokenHolder.setToken(token);
        return token;
    }

    public String generateApiKey() {
        Map<String, Object> response = postAuthenticated("/auth/apikey", Map.of());
        return (String) response.get("apiKey");
    }

    private Map<String, Object> post(String path, Object body) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.toJson(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = JsonUtil.fromJson(resp.body(), new TypeReference<>() {});
            if (resp.statusCode() >= 400) {
                throw new NodebaseException(resp.statusCode(), (String) result.get("error"));
            }
            return result;
        } catch (NodebaseException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("SDK request failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> postAuthenticated(String path, Object body) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + tokenHolder.getToken())
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.toJson(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = JsonUtil.fromJson(resp.body(), new TypeReference<>() {});
            if (resp.statusCode() >= 400) {
                throw new NodebaseException(resp.statusCode(), (String) result.get("error"));
            }
            return result;
        } catch (NodebaseException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("SDK request failed: " + e.getMessage(), e);
        }
    }
}
