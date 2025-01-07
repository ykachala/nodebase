package io.nodebase.auth;

import io.jsonwebtoken.Claims;
import io.nodebase.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

public final class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public AuthService(UserRepository userRepository, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
    }

    public AuthResult register(String email, String password) {
        if (email == null || !email.contains("@")) {
            return AuthResult.failure("Invalid email address");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return AuthResult.failure("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return AuthResult.failure("Email already registered");
        }

        long now = System.currentTimeMillis();
        User user = new User(
                UUID.randomUUID().toString(),
                email.toLowerCase().strip(),
                CryptoUtil.hashPassword(password),
                "USER",
                null,
                now,
                now
        );
        userRepository.save(user);
        log.info("Registered user {}", user.getId());
        String token = jwtProvider.generate(user.getId(), user.getRole());
        return AuthResult.success(token, user.getId(), user.getRole());
    }

    public AuthResult login(String email, String password) {
        if (email == null || password == null) {
            return AuthResult.failure("Email and password are required");
        }
        Optional<User> found = userRepository.findByEmail(email.toLowerCase().strip());
        if (found.isEmpty()) {
            return AuthResult.failure("Invalid credentials");
        }
        User user = found.get();
        if (!CryptoUtil.verifyPassword(password, user.getPasswordHash())) {
            return AuthResult.failure("Invalid credentials");
        }
        String token = jwtProvider.generate(user.getId(), user.getRole());
        return AuthResult.success(token, user.getId(), user.getRole());
    }

    public Optional<Claims> validateToken(String token) {
        return jwtProvider.validate(token);
    }

    public record AuthResult(boolean success, String token, String userId, String role, String error) {
        static AuthResult success(String token, String userId, String role) {
            return new AuthResult(true, token, userId, role, null);
        }

        static AuthResult failure(String error) {
            return new AuthResult(false, null, null, null, error);
        }
    }
}
