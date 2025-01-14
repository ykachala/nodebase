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

public final class NodebaseDatabase {

    private final String baseUrl;
    private final HttpClient http;
    private final TokenHolder tokenHolder;

    NodebaseDatabase(String baseUrl, HttpClient http, TokenHolder tokenHolder) {
        this.baseUrl = baseUrl;
        this.http = http;
        this.tokenHolder = tokenHolder;
    }

    public CollectionRef collection(String name) {
        return new CollectionRef(name);
    }

    public final class CollectionRef {

        private final String name;

        private CollectionRef(String name) {
            this.name = name;
        }

        public Map<String, Object> add(Map<String, Object> data) {
            return request("POST", "/db/" + name, data, new TypeReference<>() {});
        }

        public Map<String, Object> get(String id) {
            return request("GET", "/db/" + name + "/" + id, null, new TypeReference<>() {});
        }

        public List<Map<String, Object>> list() {
            return request("GET", "/db/" + name, null, new TypeReference<>() {});
        }

        public Map<String, Object> update(String id, Map<String, Object> patch) {
            return request("PATCH", "/db/" + name + "/" + id, patch, new TypeReference<>() {});
        }

        public Map<String, Object> replace(String id, Map<String, Object> data) {
            return request("PUT", "/db/" + name + "/" + id, data, new TypeReference<>() {});
        }

        public Map<String, Object> delete(String id) {
            return request("DELETE", "/db/" + name + "/" + id, null, new TypeReference<>() {});
        }
    }

    private <T> T request(String method, String path, Object body, TypeReference<T> type) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + tokenHolder.getToken());

            String bodyJson = body != null ? JsonUtil.toJson(body) : "";
            builder.method(method, body != null
                    ? HttpRequest.BodyPublishers.ofString(bodyJson)
                    : HttpRequest.BodyPublishers.noBody());

            HttpResponse<String> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                Map<String, Object> err = JsonUtil.fromJson(resp.body(), new TypeReference<>() {});
                throw new NodebaseException(resp.statusCode(), (String) err.get("error"));
            }
            return JsonUtil.fromJson(resp.body(), type);
        } catch (NodebaseException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("SDK request failed: " + e.getMessage(), e);
        }
    }
}
