package io.nodebase.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public final class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    private final Connection conn;

    public UserRepository(Connection conn) {
        this.conn = conn;
        initSchema();
    }

    private void initSchema() {
        String sql = """
                CREATE TABLE IF NOT EXISTS users (
                    id          TEXT PRIMARY KEY,
                    email       TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    role        TEXT NOT NULL DEFAULT 'USER',
                    api_key     TEXT,
                    created_at  INTEGER NOT NULL,
                    updated_at  INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_users_email   ON users(email);
                CREATE INDEX IF NOT EXISTS idx_users_api_key ON users(api_key);
                """;
        try (Statement st = conn.createStatement()) {
            for (String stmt : sql.split(";")) {
                String trimmed = stmt.trim();
                if (!trimmed.isEmpty()) st.execute(trimmed);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init users schema", e);
        }
    }

    public synchronized void save(User user) {
        String sql = """
                INSERT INTO users (id, email, password_hash, role, api_key, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole());
            ps.setString(5, user.getApiKey());
            ps.setLong(6, user.getCreatedAt());
            ps.setLong(7, user.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save user", e);
        }
    }

    public synchronized Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user by email", e);
        }
        return Optional.empty();
    }

    public synchronized Optional<User> findById(String id) {
        String sql = "SELECT * FROM users WHERE id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user by id", e);
        }
        return Optional.empty();
    }

    public synchronized Optional<User> findByApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        String sql = "SELECT * FROM users WHERE api_key = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, apiKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user by api_key", e);
        }
        return Optional.empty();
    }

    public synchronized void updateApiKey(String userId, String apiKey) {
        String sql = "UPDATE users SET api_key = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, apiKey);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update api_key", e);
        }
    }

    public synchronized void deleteById(String id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    public synchronized java.util.List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        java.util.List<User> users = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) users.add(map(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list users", e);
        }
        return users;
    }

    private static User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("role"),
                rs.getString("api_key"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
