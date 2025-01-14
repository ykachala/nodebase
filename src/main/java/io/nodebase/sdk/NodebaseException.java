package io.nodebase.sdk;

public final class NodebaseException extends RuntimeException {

    private final int statusCode;

    public NodebaseException(int statusCode, String message) {
        super("[HTTP " + statusCode + "] " + message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
