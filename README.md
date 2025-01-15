# Nodebase

A self-hosted Firebase-like backend-as-a-service built in pure Java. Drop it on any server, point your app at it, and get auth, a real-time document database, file storage, and a security rules engine — no cloud required.

---

## Features

- **Authentication** — JWT tokens, API keys, BCrypt password hashing
- **Document Database** — NoSQL collections, full CRUD, JSON field filtering
- **Real-time** — WebSocket subscriptions on collections and documents
- **File Storage** — Upload/download/delete with per-bucket access control
- **Security Rules** — JSON-based rules engine with hot-reload
- **Admin API** — User management, stats, collection management
- **Java SDK** — Embedded client for JVM applications
- **Production ready** — Rate limiting, CORS, graceful shutdown, health probes

---

## Requirements

- Java 21
- Maven 3.8+ (for building from source)

---

## Quick Start

### 1. Build

```bash
git clone https://github.com/joel767443/nodebase.git
cd nodebase
mvn package -q
```

This produces `target/nodebase-1.0.jar` (fat JAR, no external dependencies).

### 2. Run

```bash
java -jar target/nodebase-1.0.jar
```

Server starts on `http://0.0.0.0:8080` by default.

### 3. Verify

```bash
curl http://localhost:8080/health
# {"status":"ok","uptime":2,"version":"1.0"}
```

---

## Configuration

Create a `nodebase.properties` file in your working directory to override defaults:

```properties
port=8080
host=0.0.0.0
dataDir=./data
storagePath=./storage
jwtSecret=change-me-use-at-least-32-characters
masterKey=change-me-master-key
maxUploadMb=50
cors.allowedOrigins=*
jwtExpiryMillis=86400000
```

All settings can also be passed as JVM system properties:

```bash
java -Dnodebase.port=9090 -Dnodebase.jwtSecret=my-secret -jar nodebase-1.0.jar
```

---

## API Reference

### Authentication

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/register` | Register a new user |
| POST | `/auth/login` | Login and get JWT |
| POST | `/auth/admin/login` | Admin login with master key |
| POST | `/auth/apikey` | Generate API key (auth required) |
| DELETE | `/auth/apikey` | Revoke API key (auth required) |

```bash
# Register
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'
# {"token":"<jwt>","userId":"<id>","role":"USER"}

# Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'
```

All subsequent requests use `Authorization: Bearer <token>` or `x-api-key: <key>`.

### Database

| Method | Path | Description |
|--------|------|-------------|
| GET | `/db/{collection}` | List documents |
| POST | `/db/{collection}` | Create document |
| GET | `/db/{collection}/{id}` | Get document |
| PUT | `/db/{collection}/{id}` | Replace document |
| PATCH | `/db/{collection}/{id}` | Partial update |
| DELETE | `/db/{collection}/{id}` | Delete document |

**Query filters:**
```
GET /db/users?where[name][eq]=Alice&orderBy=createdAt&order=desc&limit=20&offset=0
```

Supported operators: `eq`, `ne`, `gt`, `lt`, `gte`, `lte`, `contains`

### Storage

| Method | Path | Description |
|--------|------|-------------|
| POST | `/storage/{bucket}/{path}` | Upload file |
| GET | `/storage/{bucket}/{path}` | Download file |
| DELETE | `/storage/{bucket}/{path}` | Delete file |
| GET | `/storage/{bucket}` | List files in bucket |

```bash
curl -X POST http://localhost:8080/storage/avatars/user123.png \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: image/png" \
  --data-binary @avatar.png
```

### Real-time (WebSocket)

Connect to `ws://localhost:8080/realtime?token=<jwt>` and send JSON messages:

```json
{ "type": "subscribe",   "channel": "db/messages" }
{ "type": "unsubscribe", "channel": "db/messages" }
{ "type": "ping" }
```

Events received on mutations:
```json
{ "type": "CREATED", "collection": "messages", "documentId": "<id>", "data": {...}, "timestamp": 1704567890 }
```

### Health

```
GET /health        → {"status":"ok","version":"1.0","uptime":<seconds>}
GET /health/ready  → {"status":"ready","database":"ok"}   (503 if DB is down)
```

### Admin

All admin endpoints require a token from `POST /auth/admin/login` with the master key.

```
GET    /admin/stats
GET    /admin/users
DELETE /admin/users/{id}
GET    /admin/collections
DELETE /admin/collections/{name}
GET    /admin/rules
POST   /admin/rules
```

---

## Security Rules

Rules are loaded from `{dataDir}/security-rules.json` and hot-reloaded every 30 seconds.

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

**Conditions:** `PUBLIC`, `AUTHENTICATED`, `OWNER`, `ADMIN`  
**Operations:** `READ`, `WRITE`, `DELETE`, `ALL`  
**Resources:** exact (`db/users`) or wildcard (`db/*`)

---

## Java SDK

```java
NodebaseClient client = new NodebaseClient("http://localhost:8080");

// Auth
String token = client.auth().login("user@example.com", "password123");

// Database
Map<String, Object> doc = client.database()
    .collection("messages")
    .add(Map.of("text", "Hello", "author", "Alice"));

List<Map<String, Object>> all = client.database().collection("messages").list();

client.database().collection("messages")
    .update(doc.get("id").toString(), Map.of("text", "Hello, World!"));

// Storage
client.storage().upload("avatars", "user.png", imageBytes, "image/png");
byte[] data = client.storage().download("avatars", "user.png");
```

---

## Production Deployment

### systemd service

```ini
[Unit]
Description=Nodebase Server
After=network.target

[Service]
User=nodebase
WorkingDirectory=/opt/nodebase
ExecStart=/usr/bin/java \
  -Dnodebase.jwtSecret=<strong-secret> \
  -Dnodebase.masterKey=<strong-master-key> \
  -Dnodebase.port=8080 \
  -jar /opt/nodebase/nodebase-1.0.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

### Nginx reverse proxy (with WebSocket support)

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

---

## Rate Limits

- **Per IP:** 100 requests/minute
- **Per API key:** 1000 requests/minute
- Exceeding the limit returns HTTP 429 with a `Retry-After` header.
