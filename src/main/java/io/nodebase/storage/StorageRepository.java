package io.nodebase.storage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class StorageRepository {

    private final Connection conn;

    public StorageRepository(Connection conn) {
        this.conn = conn;
        initSchema();
    }

    private void initSchema() {
        String[] stmts = {
                """
                CREATE TABLE IF NOT EXISTS storage_objects (
                    id           TEXT PRIMARY KEY,
                    bucket       TEXT NOT NULL,
                    path         TEXT NOT NULL,
                    filename     TEXT NOT NULL,
                    content_type TEXT NOT NULL,
                    size_bytes   INTEGER NOT NULL,
                    owner_id     TEXT,
                    created_at   INTEGER NOT NULL,
                    UNIQUE (bucket, path)
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_storage_bucket ON storage_objects(bucket)"
        };
        try (Statement st = conn.createStatement()) {
            for (String sql : stmts) st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init storage schema", e);
        }
    }

    public synchronized void insert(StorageObject obj) {
        String sql = """
                INSERT OR REPLACE INTO storage_objects
                (id, bucket, path, filename, content_type, size_bytes, owner_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, obj.getId());
            ps.setString(2, obj.getBucket());
            ps.setString(3, obj.getPath());
            ps.setString(4, obj.getFilename());
            ps.setString(5, obj.getContentType());
            ps.setLong(6, obj.getSizeBytes());
            ps.setString(7, obj.getOwnerId());
            ps.setLong(8, obj.getCreatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert storage object", e);
        }
    }

    public synchronized Optional<StorageObject> findByBucketAndPath(String bucket, String path) {
        String sql = "SELECT * FROM storage_objects WHERE bucket = ? AND path = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bucket);
            ps.setString(2, path);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find storage object", e);
        }
        return Optional.empty();
    }

    public synchronized List<StorageObject> listByBucket(String bucket) {
        String sql = "SELECT * FROM storage_objects WHERE bucket = ? ORDER BY path ASC";
        List<StorageObject> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bucket);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list storage objects", e);
        }
        return result;
    }

    public synchronized void delete(String bucket, String path) {
        String sql = "DELETE FROM storage_objects WHERE bucket = ? AND path = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bucket);
            ps.setString(2, path);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete storage object", e);
        }
    }

    public synchronized long totalSizeBytes() {
        String sql = "SELECT COALESCE(SUM(size_bytes), 0) FROM storage_objects";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to compute storage size", e);
        }
    }

    private static StorageObject map(ResultSet rs) throws SQLException {
        return new StorageObject(
                rs.getString("id"),
                rs.getString("bucket"),
                rs.getString("path"),
                rs.getString("filename"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getString("owner_id"),
                rs.getLong("created_at")
        );
    }
}
