package io.nodebase.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;

public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private JsonUtil() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new RuntimeException("JSON serialize failed: " + e.getMessage(), e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new RuntimeException("JSON parse failed: " + e.getMessage(), e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new RuntimeException("JSON parse failed: " + e.getMessage(), e);
        }
    }

    public static <T> T fromJson(InputStream in, Class<T> type) {
        try {
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new RuntimeException("JSON parse failed: " + e.getMessage(), e);
        }
    }

    public static <T> T fromJson(InputStream in, TypeReference<T> type) {
        try {
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new RuntimeException("JSON parse failed: " + e.getMessage(), e);
        }
    }

    public static <T> T readValue(byte[] bytes, Class<T> type) {
        try {
            return MAPPER.readValue(bytes, type);
        } catch (IOException e) {
            throw new RuntimeException("JSON parse failed: " + e.getMessage(), e);
        }
    }
}
