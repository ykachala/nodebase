package io.nodebase.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final StorageRepository repository;
    private final Path storageRoot;
    private final long maxUploadBytes;

    public StorageService(StorageRepository repository, String storageRoot, int maxUploadMb) {
        this.repository = repository;
        this.storageRoot = Paths.get(storageRoot);
        this.maxUploadBytes = (long) maxUploadMb * 1024 * 1024;
    }

    public StorageObject store(String bucket, String path, InputStream data,
                               String contentType, long contentLength, String ownerId) throws IOException {
        validatePath(path);
        if (contentType == null || contentType.isBlank()) contentType = "application/octet-stream";
        if (contentLength > maxUploadBytes) {
            throw new StorageException("Upload exceeds maximum size of " + (maxUploadBytes / 1024 / 1024) + " MB");
        }

        Path dest = resolveDestination(bucket, path);
        Files.createDirectories(dest.getParent());
        long written = Files.copy(data, dest, StandardCopyOption.REPLACE_EXISTING);

        String filename = Paths.get(path).getFileName().toString();
        StorageObject obj = new StorageObject(
                UUID.randomUUID().toString(), bucket, path, filename,
                contentType, written, ownerId, System.currentTimeMillis()
        );
        repository.insert(obj);
        log.debug("Stored {}/{} ({} bytes)", bucket, path, written);
        return obj;
    }

    public Optional<StorageObject> getMeta(String bucket, String path) {
        return repository.findByBucketAndPath(bucket, path);
    }

    public void streamFile(String bucket, String path, OutputStream out) throws IOException {
        Path file = resolveDestination(bucket, path);
        if (!Files.exists(file)) throw new StorageException("File not found");
        Files.copy(file, out);
    }

    public boolean delete(String bucket, String path, String requesterId, String requesterRole) throws IOException {
        Optional<StorageObject> obj = repository.findByBucketAndPath(bucket, path);
        if (obj.isEmpty()) return false;
        if (!"ADMIN".equals(requesterRole) && !obj.get().getOwnerId().equals(requesterId)) {
            throw new StorageException("not authorized to delete this file");
        }
        Path file = resolveDestination(bucket, path);
        Files.deleteIfExists(file);
        repository.delete(bucket, path);
        return true;
    }

    public List<StorageObject> list(String bucket) {
        return repository.listByBucket(bucket);
    }

    public long totalSizeBytes() {
        return repository.totalSizeBytes();
    }

    private Path resolveDestination(String bucket, String filePath) {
        // Guard against path traversal
        Path resolved = storageRoot.resolve(bucket).resolve(filePath).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new StorageException("Invalid path: traversal not allowed");
        }
        return resolved;
    }

    private static void validatePath(String path) {
        if (path == null || path.isBlank()) throw new StorageException("Path is required");
        if (path.contains("..")) throw new StorageException("Path traversal not allowed");
        if (Paths.get(path).isAbsolute()) throw new StorageException("Absolute paths not allowed");
    }

    public static final class StorageException extends RuntimeException {
        public StorageException(String msg) { super(msg); }
    }
}
