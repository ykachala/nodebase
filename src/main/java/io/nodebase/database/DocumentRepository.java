package io.nodebase.database;

import com.fasterxml.jackson.core.type.TypeReference;
import io.nodebase.util.JsonUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DocumentRepository {

    private final Connection conn;

    public DocumentRepository(Connection conn) {
        this.conn = conn;
        initSchema();
    }

    private void initSchema() {
        String[] stmts = {
                """
                CREATE TABLE IF NOT EXISTS documents (
                    id          TEXT PRIMARY KEY,
                    collection  TEXT NOT NULL,
                    data        TEXT NOT NULL,
                    owner_id    TEXT,
                    created_at  INTEGER NOT NULL,
                    updated_at  INTEGER NOT NULL
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_docs_collection ON documents(collection)",
                "CREATE INDEX IF NOT EXISTS idx_docs_owner     ON documents(owner_id)"
        };
        try (Statement st = conn.createStatement()) {
            for (String sql : stmts) st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init documents schema", e);
        }
    }

    public synchronized void insert(Document doc) {
        String sql = """
                INSERT INTO documents (id, collection, data, owner_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, doc.getId());
            ps.setString(2, doc.getCollection());
            ps.setString(3, JsonUtil.toJson(doc.getData()));
            ps.setString(4, doc.getOwnerId());
            ps.setLong(5, doc.getCreatedAt());
            ps.setLong(6, doc.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert document", e);
        }
    }

    public synchronized Optional<Document> findById(String id) {
        String sql = "SELECT * FROM documents WHERE id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find document", e);
        }
        return Optional.empty();
    }

    public synchronized List<Document> findByCollection(String collection, QueryFilter filter) {
        StringBuilder sb = new StringBuilder("SELECT * FROM documents WHERE collection = ?");
        List<Object> params = new ArrayList<>();
        params.add(collection);

        filter.appendWhere(sb, params);

        String order = filter.getOrderBy() != null ? sanitizeColumn(filter.getOrderBy()) : "created_at";
        String dir = "desc".equalsIgnoreCase(filter.getOrder()) ? "DESC" : "ASC";
        sb.append(" ORDER BY ").append(order).append(" ").append(dir);

        int limit = filter.getLimit() > 0 ? filter.getLimit() : 100;
        int offset = Math.max(0, filter.getOffset());
        sb.append(" LIMIT ").append(limit).append(" OFFSET ").append(offset);

        List<Document> docs = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) docs.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list documents", e);
        }
        return docs;
    }

    public synchronized void update(String id, Map<String, Object> data, long updatedAt) {
        String sql = "UPDATE documents SET data = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, JsonUtil.toJson(data));
            ps.setLong(2, updatedAt);
            ps.setString(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update document", e);
        }
    }

    public synchronized void delete(String id) {
        String sql = "DELETE FROM documents WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete document", e);
        }
    }

    public synchronized void deleteByCollection(String collection) {
        String sql = "DELETE FROM documents WHERE collection = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, collection);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete collection", e);
        }
    }

    public synchronized List<Map<String, Object>> collectionStats() {
        String sql = "SELECT collection, COUNT(*) AS cnt FROM documents GROUP BY collection ORDER BY collection";
        List<Map<String, Object>> result = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(Map.of("collection", rs.getString("collection"), "count", rs.getLong("cnt")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get collection stats", e);
        }
        return result;
    }

    private static Document map(ResultSet rs) throws SQLException {
        String dataJson = rs.getString("data");
        Map<String, Object> data = JsonUtil.fromJson(dataJson, new TypeReference<>() {});
        return new Document(
                rs.getString("id"),
                rs.getString("collection"),
                data,
                rs.getString("owner_id"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }

    private static String sanitizeColumn(String col) {
        return col.replaceAll("[^a-zA-Z0-9_]", "");
    }
}
