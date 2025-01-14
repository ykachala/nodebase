package io.nodebase.sdk;

import java.util.concurrent.atomic.AtomicReference;

final class TokenHolder {

    private final AtomicReference<String> token = new AtomicReference<>();

    void setToken(String t) { token.set(t); }

    String getToken() { return token.get(); }
}
