package io.nodebase.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import io.nodebase.util.JsonUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public final class NodebaseStorage {

    private final String baseUrl;
    private final HttpClient http;
    private final TokenHolder tokenHolder;

    NodebaseStorage(String baseUrl, HttpClient http, TokenHolder tokenHolder) {
        this.baseUrl = baseUrl;
        this.http = http;
        this.tokenHolder = tokenHolder;
    }

    public Map<String, Object> upload(String bucket, String path, byte[] data, String contentType) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/storage/" + bucket + "/" + path))
                    .header("Authorization", "Bearer " + tokenHolder.getToken())
                    .header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(data))
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
            throw new RuntimeException("SDK upload failed: " + e.getMessage(), e);
        }
    }

    public byte[] download(String bucket, String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/storage/" + bucket + "/" + path))
                    .header("Authorization", "Bearer " + tokenHolder.getToken())
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 400) {
                throw new NodebaseException(resp.statusCode(), "download failed");
            }
            return resp.body();
        } catch (NodebaseException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("SDK download failed: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> delete(String bucket, String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/storage/" + bucket + "/" + path))
                    .header("Authorization", "Bearer " + tokenHolder.getToken())
                    .DELETE()
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
            throw new RuntimeException("SDK delete failed: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> list(String bucket) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/storage/" + bucket))
                    .header("Authorization", "Bearer " + tokenHolder.getToken())
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new NodebaseException(resp.statusCode(), "list failed");
            }
            return JsonUtil.fromJson(resp.body(), new TypeReference<>() {});
        } catch (NodebaseException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("SDK list failed: " + e.getMessage(), e);
        }
    }
}
