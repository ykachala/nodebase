package io.nodebase.auth;

import io.nodebase.util.CryptoUtil;

import java.util.Optional;

public final class ApiKeyService {

    private static final String PREFIX = "nb_";
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;

    public ApiKeyService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String generate(String userId) {
        String apiKey = PREFIX + CryptoUtil.randomToken(TOKEN_BYTES);
        userRepository.updateApiKey(userId, apiKey);
        return apiKey;
    }

    public String rotate(String userId) {
        return generate(userId);
    }

    public void revoke(String userId) {
        userRepository.updateApiKey(userId, null);
    }

    public Optional<User> resolveKey(String apiKey) {
        if (apiKey == null || !apiKey.startsWith(PREFIX)) return Optional.empty();
        return userRepository.findByApiKey(apiKey);
    }
}
