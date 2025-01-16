package io.nodebase.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JwtProviderTest {

    private static final String SECRET = "test-secret-must-be-at-least-32-characters";
    private JwtProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtProvider(SECRET, 60_000);
    }

    @Test
    void constructor_throws_when_secret_too_short() {
        assertThrows(IllegalArgumentException.class, () -> new JwtProvider("tooshort", 60_000));
    }

    @Test
    void generate_and_validate_round_trip() {
        String token = provider.generate("user-123", "user");
        Optional<Claims> claims = provider.validate(token);
        assertTrue(claims.isPresent());
        assertEquals("user-123", claims.get().getSubject());
        assertEquals("user", claims.get().get("role", String.class));
    }

    @Test
    void validate_returns_empty_for_invalid_token() {
        assertTrue(provider.validate("not-a-valid-jwt").isEmpty());
    }

    @Test
    void validate_returns_empty_for_null_token() {
        assertTrue(provider.validate(null).isEmpty());
    }

    @Test
    void validate_returns_empty_for_expired_token() throws InterruptedException {
        JwtProvider shortLived = new JwtProvider(SECRET, 1);
        String token = shortLived.generate("user-123", "user");
        Thread.sleep(10);
        assertTrue(shortLived.validate(token).isEmpty());
    }

    @Test
    void extractUserId_returns_correct_subject() {
        String token = provider.generate("user-456", "admin");
        assertEquals(Optional.of("user-456"), provider.extractUserId(token));
    }

    @Test
    void extractRole_returns_correct_role() {
        String token = provider.generate("user-456", "admin");
        assertEquals(Optional.of("admin"), provider.extractRole(token));
    }

    @Test
    void extractUserId_returns_empty_for_bad_token() {
        assertTrue(provider.extractUserId("garbage").isEmpty());
    }
}
