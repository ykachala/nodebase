package io.nodebase.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class DatabaseService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);

    private final DocumentRepository repository;
    private Consumer<RealtimeEvent> eventEmitter = event -> {};

    public DatabaseService(DocumentRepository repository) {
        this.repository = repository;
    }

    public void setEventEmitter(Consumer<RealtimeEvent> emitter) {
        this.eventEmitter = emitter;
    }

    public Document create(String collection, Map<String, Object> data, String ownerId) {
        long now = System.currentTimeMillis();
        Document doc = new Document(UUID.randomUUID().toString(), collection, new HashMap<>(data), ownerId, now, now);
        repository.insert(doc);
        log.debug("Created document {}/{}", collection, doc.getId());
        eventEmitter.accept(new RealtimeEvent(RealtimeEvent.Type.CREATED, collection, doc.getId(), doc.getData()));
        return doc;
    }

    public Optional<Document> get(String id) {
        return repository.findById(id);
    }

    public List<Document> list(String collection, QueryFilter filter) {
        return repository.findByCollection(collection, filter);
    }

    public Optional<Document> replace(String id, Map<String, Object> data, String requesterId, String requesterRole) {
        Optional<Document> existing = repository.findById(id);
        if (existing.isEmpty()) return Optional.empty();

        Document doc = existing.get();
        if (!canWrite(doc, requesterId, requesterRole)) {
            throw new AccessDeniedException("not authorized to modify this document");
        }

        long now = System.currentTimeMillis();
        repository.update(id, new HashMap<>(data), now);
        doc.setData(data);
        doc.setUpdatedAt(now);
        eventEmitter.accept(new RealtimeEvent(RealtimeEvent.Type.UPDATED, doc.getCollection(), id, data));
        return Optional.of(doc);
    }

    public Optional<Document> patch(String id, Map<String, Object> patch, String requesterId, String requesterRole) {
        Optional<Document> existing = repository.findById(id);
        if (existing.isEmpty()) return Optional.empty();

        Document doc = existing.get();
        if (!canWrite(doc, requesterId, requesterRole)) {
            throw new AccessDeniedException("not authorized to modify this document");
        }

        Map<String, Object> merged = new HashMap<>(doc.getData());
        merged.putAll(patch);
        long now = System.currentTimeMillis();
        repository.update(id, merged, now);
        doc.setData(merged);
        doc.setUpdatedAt(now);
        eventEmitter.accept(new RealtimeEvent(RealtimeEvent.Type.UPDATED, doc.getCollection(), id, merged));
        return Optional.of(doc);
    }

    public boolean delete(String id, String requesterId, String requesterRole) {
        Optional<Document> existing = repository.findById(id);
        if (existing.isEmpty()) return false;

        Document doc = existing.get();
        if (!canWrite(doc, requesterId, requesterRole)) {
            throw new AccessDeniedException("not authorized to delete this document");
        }

        repository.delete(id);
        eventEmitter.accept(new RealtimeEvent(RealtimeEvent.Type.DELETED, doc.getCollection(), id, Map.of()));
        return true;
    }

    public List<Map<String, Object>> collectionStats() {
        return repository.collectionStats();
    }

    public void dropCollection(String collection) {
        repository.deleteByCollection(collection);
    }

    private static boolean canWrite(Document doc, String requesterId, String requesterRole) {
        if ("ADMIN".equals(requesterRole)) return true;
        return doc.getOwnerId() != null && doc.getOwnerId().equals(requesterId);
    }

    public static final class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String msg) { super(msg); }
    }

    public record RealtimeEvent(Type type, String collection, String documentId, Map<String, Object> data) {
        public enum Type { CREATED, UPDATED, DELETED }
    }
}
