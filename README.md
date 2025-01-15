# Nodebase

Self-hosted Backend-as-a-Service written in pure Java 21. Ships as a single fat JAR — no application server, no external broker, no managed cloud required. Drop it on any JVM host and get a production-grade backend with auth, a document store, real-time subscriptions, file storage, and a hot-reloading security rules engine.

**Stack:** Jetty 12 · SQLite (WAL) · JJWT · BCrypt · Jackson · Logback · Maven assembly

---

## Architecture

```
┌────────────────────────────────────────────────────────┐
│                      HTTP / WS                         │
│          Jetty 12 (embedded, single process)           │
├──────────────┬─────────────────────────────────────────┤
│  Middleware  │  CorsFilter → RateLimiter → AuthMiddleware│
├──────────────┴─────────────────────────────────────────┤
│  Router (servlet)                                      │
│  /auth/**   /db/**   /storage/**   /admin/**   /health │
├────────────┬──────────────┬───────────────┬────────────┤
│ AuthService│DatabaseService│StorageService│AdminHandler│
│ ApiKeySvc  │DocumentRepo   │StorageRepo   │RulesEngine │
│ JwtProvider│QueryFilter    │              │            │
└─────┬──────┴──────┬────────┴──────┬────── ┴──── ┬──────┘
      │             │               │             │
      └─────────────┴───────────────┴─────────────┘
                           │
                    SQLite (WAL mode)
                    data/nodebase.db
                           │
              SubscriptionManager ──► WebSocket /realtime
```

All state lives in a single SQLite database opened in WAL mode (`PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON`). Storage objects are written to the local filesystem under `storagePath`; metadata is kept in the same DB. There are no external dependencies at runtime.

---

## Requirements

| Dependency | Version |
|---|---|
| JDK | 21+ |
| Maven | 3.8+ (build only) |

No other runtime dependencies. The fat JAR bundles everything including Jetty, SQLite JDBC, and Jackson.

---

## Build

```bash
git clone https://github.com/joel767443/nodebase.git
cd nodebase
mvn package -q
# produces target/nodebase-1.0.jar
```

The `maven-assembly-plugin` shades all dependencies into a single executable JAR. The manifest `Main-Class` is `io.nodebase.NodebaseServer`.

---

## Run

```bash
java -jar target/nodebase-1.0.jar
```

```
================================================================
 Nodebase 1.0  |  http://0.0.0.0:8080
 dashboard : http://0.0.0.0:8080/dashboard
 data dir  : ./data
 storage   : ./storage
================================================================
```

Health check:

```bash
curl -s http://localhost:8080/health
# {"status":"ok","version":"1.0","uptime":3}
```

---

## Configuration

Configuration is resolved in this priority order (highest wins):

1. JVM system properties (`-Dnodebase.<key>=<value>`)
2. Environment variables (see table below)
3. External `nodebase.properties` in the working directory
4. Classpath `nodebase.properties` (bundled defaults)

| Property key | Env var | Default | Description |
|---|---|---|---|
| `port` | `NODEBASE_PORT` | `8080` | HTTP listen port |
| `host` | `NODEBASE_HOST` | `0.0.0.0` | Bind address |
| `dataDir` | `NODEBASE_DATA_DIR` | `./data` | SQLite DB + rules file directory |
| `storagePath` | `NODEBASE_STORAGE_PATH` | `./storage` | Filesystem root for uploaded objects |
| `jwtSecret` | `NODEBASE_JWT_SECRET` | *(insecure default — override in prod)* | HMAC-SHA256 signing key, min 32 chars |
| `masterKey` | `NODEBASE_MASTER_KEY` | *(insecure default)* | Admin authentication credential |
| `maxUploadMb` | `NODEBASE_MAX_UPLOAD_MB` | `50` | Maximum upload size in MiB |
| `cors.allowedOrigins` | `NODEBASE_CORS_ORIGINS` | `*` | Comma-separated allowed origins |
| `jwtExpiryMillis` | `NODEBASE_JWT_EXPIRY_MS` | `86400000` | JWT TTL in milliseconds (default 24 h) |
| `rateLimit.perIp` | — | `100` | Requests/minute per IP |
| `rateLimit.perKey` | — | `1000` | Requests/minute per API key |

**Example — override via properties file:**

```properties
# nodebase.properties
port=9090
jwtSecret=at-least-32-characters-of-entropy-here
masterKey=a-strong-admin-secret
cors.allowedOrigins=https://app.example.com
maxUploadMb=100
```

**Example — override via JVM flags (useful for containers):**

```bash
java \
  -Dnodebase.jwtSecret=prod-secret \
  -Dnodebase.masterKey=prod-master \
  -Dnodebase.port=8080 \
  -jar nodebase-1.0.jar
```

---

## API Reference

All request/response bodies are `application/json`. Authenticated endpoints require either:

- `Authorization: Bearer <jwt>` — issued by `/auth/login`
- `x-api-key: <key>` — generated via `/auth/apikey`

Errors follow a consistent envelope:

```json
{ "error": "<message>" }
```

### Authentication

#### `POST /auth/register`

```json
// Request
{ "email": "user@example.com", "password": "s3cr3t!" }

// Response 201
{ "token": "<jwt>", "userId": "<uuid>", "role": "USER" }
```

| Status | Condition |
|---|---|
| 201 | User created |
| 400 | Missing/invalid fields |
| 409 | Email already registered |

#### `POST /auth/login`

```json
// Request
{ "email": "user@example.com", "password": "s3cr3t!" }

// Response 200
{ "token": "<jwt>", "userId": "<uuid>", "role": "USER" }
```

| Status | Condition |
|---|---|
| 200 | OK |
| 401 | Bad credentials |

#### `POST /auth/admin/login`

```json
// Request
{ "masterKey": "<configured-master-key>" }

// Response 200
{ "token": "<jwt>", "role": "ADMIN" }
```

#### `POST /auth/apikey`

Requires Bearer auth. Generates a new API key bound to the authenticated user.

```json
// Response 201
{ "apiKey": "<opaque-key>" }
```

#### `DELETE /auth/apikey`

Revokes the authenticated user's current API key.

---

### Database

The document store is schemaless. Collections are created implicitly on first write. Documents are stored as JSON blobs in SQLite with UUID primary keys, `createdAt`, and `updatedAt` timestamps (Unix millis).

#### `GET /db/{collection}`

Returns an array of documents. Supports query filters:

```
GET /db/users?where[name][eq]=Alice
             &where[age][gte]=18
             &orderBy=createdAt
             &order=desc
             &limit=20
             &offset=0
```

Supported filter operators: `eq` `ne` `gt` `lt` `gte` `lte` `contains`

```json
// Response 200
[
  { "id": "<uuid>", "name": "Alice", "age": 30, "createdAt": 1704067200000, "updatedAt": 1704067200000 }
]
```

#### `POST /db/{collection}`

```json
// Request — arbitrary JSON object
{ "name": "Alice", "age": 30 }

// Response 201
{ "id": "<uuid>", "name": "Alice", "age": 30, "createdAt": 1704067200000, "updatedAt": 1704067200000 }
```

#### `GET /db/{collection}/{id}`

```json
// Response 200 | 404
{ "id": "<uuid>", ... }
```

#### `PUT /db/{collection}/{id}`

Full replacement. Preserves `id` and `createdAt`.

#### `PATCH /db/{collection}/{id}`

Shallow merge. Only provided fields are updated; omitted fields are left intact.

#### `DELETE /db/{collection}/{id}`

```
// Response 204 | 404
```

All mutations publish a real-time event through `SubscriptionManager` before returning the HTTP response.

---

### Storage

Objects are stored on the local filesystem under `{storagePath}/{bucket}/{path}`. Path traversal is blocked at the `StorageService` layer — any `..` segment returns 400.

#### `POST /storage/{bucket}/{path}`

Upload a file. The `Content-Type` header is stored alongside the object and returned on download.

```bash
curl -X POST http://localhost:8080/storage/avatars/alice.png \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: image/png" \
  --data-binary @alice.png
```

| Status | Condition |
|---|---|
| 201 | Uploaded |
| 400 | Path traversal detected |
| 413 | Exceeds `maxUploadMb` |

#### `GET /storage/{bucket}/{path}`

Downloads the object. Responds with the stored `Content-Type`.

#### `DELETE /storage/{bucket}/{path}`

```
// Response 204 | 404
```

#### `GET /storage/{bucket}`

Lists all objects in the bucket.

```json
// Response 200
[
  { "bucket": "avatars", "path": "alice.png", "size": 24056, "contentType": "image/png", "createdAt": 1704067200000 }
]
```

---

### Health

```
GET /health       → 200  {"status":"ok","version":"1.0","uptime":<seconds>}
GET /health/ready → 200  {"status":"ready","database":"ok"}
                   503   {"status":"unavailable","database":"error"}
```

`/health/ready` issues a live `SELECT 1` against SQLite. Use this as the Kubernetes readiness probe; use `/health` as the liveness probe.

---

### Admin

All endpoints require a token obtained from `POST /auth/admin/login`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/admin/stats` | Server stats: user count, document count, storage usage |
| `GET` | `/admin/users` | List all users |
| `DELETE` | `/admin/users/{id}` | Delete user and revoke their tokens |
| `GET` | `/admin/collections` | List all collections with document counts |
| `DELETE` | `/admin/collections/{name}` | Drop an entire collection |
| `GET` | `/admin/rules` | Retrieve active security rules |
| `POST` | `/admin/rules` | Replace security rules (persisted to disk, hot-reloaded) |

---

## Security Rules

Rules are loaded from `{dataDir}/security-rules.json`. If the file is absent, the classpath default is used. The engine polls for changes every 30 seconds using a daemon thread — no restart required.

### Rule schema

```json
[
  {
    "resource":  "db/*",
    "operation": "READ",
    "condition": "AUTHENTICATED",
    "enabled":   true
  }
]
```

| Field | Values |
|---|---|
| `resource` | Exact path (`db/users`) or wildcard (`db/*`, `storage/*`, `admin/*`) |
| `operation` | `READ` `WRITE` `DELETE` `ALL` |
| `condition` | `PUBLIC` `AUTHENTICATED` `OWNER` `ADMIN` |
| `enabled` | `true` / `false` — disabled rules are skipped without removal |

### Condition semantics

| Condition | Access granted when |
|---|---|
| `PUBLIC` | Always — no token required |
| `AUTHENTICATED` | Valid JWT or API key present |
| `OWNER` | Authenticated and the document's `userId` field matches the caller's ID |
| `ADMIN` | Token issued via `POST /auth/admin/login` |

### Default ruleset

```json
[
  { "resource": "db/*",      "operation": "READ",   "condition": "AUTHENTICATED", "enabled": true },
  { "resource": "db/*",      "operation": "WRITE",  "condition": "AUTHENTICATED", "enabled": true },
  { "resource": "db/*",      "operation": "DELETE", "condition": "OWNER",         "enabled": true },
  { "resource": "storage/*", "operation": "READ",   "condition": "PUBLIC",        "enabled": true },
  { "resource": "storage/*", "operation": "WRITE",  "condition": "AUTHENTICATED", "enabled": true },
  { "resource": "admin/*",   "operation": "ALL",    "condition": "ADMIN",         "enabled": true }
]
```

---

## Real-time (WebSocket)

Connect to `ws://localhost:8080/realtime?token=<jwt>`. The token is validated on connection upgrade; unauthenticated connections are rejected with `401`.

### Client → server messages

```json
{ "type": "subscribe",   "channel": "db/messages" }
{ "type": "unsubscribe", "channel": "db/messages" }
{ "type": "ping" }
```

`channel` must be a `db/{collection}` path. Wildcards are not supported.

### Server → client events

```json
{
  "type":       "CREATED",
  "collection": "messages",
  "documentId": "<uuid>",
  "data":       { "text": "Hello", "author": "Alice" },
  "timestamp":  1704067200000
}
```

`type` is one of `CREATED` `UPDATED` `DELETED`. Events are broadcast to all sessions subscribed to the affected collection synchronously within the same request thread that performed the mutation.

---

## Java SDK

The embedded client (`io.nodebase.sdk.NodebaseClient`) communicates with the server over HTTP. Include the fat JAR on your classpath or copy the `sdk` package into your project.

```java
NodebaseClient client = new NodebaseClient("http://localhost:8080");

// Authentication
String token = client.auth().login("user@example.com", "s3cr3t!");
// token is stored internally on the client; subsequent calls include it automatically

// Registration
client.auth().register("newuser@example.com", "password");

// Document operations
NodebaseDatabase db = client.database();

Map<String, Object> doc = db.collection("messages")
    .add(Map.of("text", "Hello", "author", "Alice"));

List<Map<String, Object>> messages = db.collection("messages").list();

db.collection("messages")
    .update(doc.get("id").toString(), Map.of("text", "Updated"));

db.collection("messages").delete(doc.get("id").toString());

// Storage operations
NodebaseStorage storage = client.storage();

storage.upload("avatars", "alice.png", imageBytes, "image/png");
byte[] data = storage.download("avatars", "alice.png");
storage.delete("avatars", "alice.png");
```

`NodebaseException` is thrown on non-2xx responses. It carries the HTTP status code and the server error message.

---

## Rate Limiting

The `RateLimiter` middleware uses a per-identifier sliding window keyed on request arrival time.

| Identifier | Limit | Window |
|---|---|---|
| Client IP | 100 req/min (configurable via `rateLimit.perIp`) | 60 s |
| API key | 1 000 req/min (configurable via `rateLimit.perKey`) | 60 s |

When the limit is exceeded:

```
HTTP 429
Retry-After: 60
{ "error": "rate limit exceeded", "retryAfter": 60 }
```

IP is extracted from `X-Forwarded-For` if present, falling back to `remoteAddr`. If the request carries both an API key and an IP, the API key identifier takes precedence.

---

## Production Deployment

### Secrets

Before going to production, **always override** these two defaults in `nodebase.properties` or via env vars:

```properties
jwtSecret=<min-32-char-high-entropy-string>
masterKey=<high-entropy-admin-credential>
```

### systemd

```ini
[Unit]
Description=Nodebase
After=network.target

[Service]
User=nodebase
WorkingDirectory=/opt/nodebase
EnvironmentFile=/opt/nodebase/.env
ExecStart=/usr/bin/java \
  -Xmx512m \
  -Dnodebase.jwtSecret=${JWT_SECRET} \
  -Dnodebase.masterKey=${MASTER_KEY} \
  -Dnodebase.port=8080 \
  -jar /opt/nodebase/nodebase-1.0.jar
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

### Docker

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/nodebase-1.0.jar nodebase.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "nodebase.jar"]
```

```bash
docker build -t nodebase:1.0 .
docker run -d \
  -p 8080:8080 \
  -e NODEBASE_JWT_SECRET=prod-secret \
  -e NODEBASE_MASTER_KEY=prod-master \
  -v nodebase-data:/app/data \
  -v nodebase-storage:/app/storage \
  nodebase:1.0
```

### Nginx (with WebSocket upgrade)

```nginx
upstream nodebase {
    server 127.0.0.1:8080;
    keepalive 64;
}

server {
    listen 443 ssl http2;
    server_name api.example.com;

    location / {
        proxy_pass         http://nodebase;
        proxy_http_version 1.1;
        proxy_set_header   Upgrade    $http_upgrade;
        proxy_set_header   Connection "upgrade";
        proxy_set_header   Host       $host;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 3600s;
    }
}
```

The `proxy_read_timeout 3600s` is required for long-lived WebSocket connections.

---

## Operational Notes

**Graceful shutdown** — A JVM shutdown hook calls `RulesEngine.shutdown()`, closes the Jetty server with a 30-second drain timeout (`setStopTimeout(30_000L)`), then closes the SQLite connection. SIGTERM is safe.

**SQLite WAL mode** — WAL is enabled at startup (`PRAGMA journal_mode=WAL`). This allows one writer and multiple concurrent readers without blocking. For single-host deployments this is sufficient; horizontal scaling is not supported by this storage backend.

**SQLite foreign keys** — Enabled via `PRAGMA foreign_keys=ON`. Repositories enforce referential integrity at the application layer as well.

**Rules hot-reload** — The `RulesEngine` polls `{dataDir}/security-rules.json` every 30 seconds on a daemon thread. Updates are applied atomically via `AtomicReference`. There is no HTTP endpoint to trigger an immediate reload; use `POST /admin/rules` instead.

**Log output** — Structured JSON logging via Logback. Override `src/main/resources/logback.xml` before building to customise appenders or log levels.

---

## Known Limitations

- **Single-node only.** SQLite is not designed for multi-process concurrent writes. Do not run multiple instances sharing the same `dataDir`.
- **No document-level ownership index.** The `OWNER` rule condition performs an in-memory field comparison against `userId` on each matched document — not a DB index scan. Large collections with `OWNER`-guarded reads will be slow.
- **No query index hints.** All `where` filters run as in-memory predicates after a full collection scan from SQLite. There is no `CREATE INDEX` path exposed.
- **Storage is local filesystem only.** There is no S3 or object-storage backend. Horizontal scaling requires a shared mount (NFS, EFS, etc.).
- **WebSocket fan-out is in-process.** Real-time events are broadcast synchronously in the mutation request thread. High subscriber counts under write load will increase mutation latency.
