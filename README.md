# Nodebase

A self-hosted **Firebase / Supabase-like** backend platform built in **pure Java 21**.
Single fat JAR, embedded SQLite, no external services to run.

## Features

- **Authentication** — JWT tokens and per-user API keys (BCrypt-hashed passwords)
- **Document database** — SQLite-backed NoSQL document store with collections, queries, filters, ordering, and pagination
- **Real-time** — WebSocket subscriptions on collections and individual documents
- **Storage** — File/blob storage with metadata, content-type, size limits, and access control
- **Security rules** — JSON-based rules engine with hot-reload
- **Admin API** — User management, collection inspection, rule editing
- **Java client SDK** — Strongly-typed client with auth, database, and storage modules
- **Production hardening** — Rate limiting, CORS, gzip, health checks, graceful shutdown

## Prerequisites

- Java 21 or newer
- Maven 3.8+ (for building from source)

## Quick start

```bash
git clone git@github.com:joel767443/nodebase.git
cd nodebase

mvn package -q
java -jar target/nodebase-1.0.jar
```

You should see:

```
Nodebase 1.0  listening on http://0.0.0.0:8080
```

Verify with:

```bash
curl http://localhost:8080/health
# {"status":"ok","version":"1.0",...}
```

## Configuration

Nodebase reads `nodebase.properties` from (in order):

1. The path supplied with `-Dnodebase.config=/path/to/nodebase.properties`
2. `./nodebase.properties` in the working directory
3. The bundled default on the classpath

Any value can also be overridden with a `-Dnodebase.KEY=VALUE` system property.

| Key                    | Default              | Notes                                          |
|------------------------|----------------------|------------------------------------------------|
| `host`                 | `0.0.0.0`            | Bind address                                   |
| `port`                 | `8080`               | HTTP port                                      |
| `dataDir`              | `./data`             | SQLite database location                       |
| `storagePath`          | `./storage`          | Root directory for uploaded files              |
| `jwtSecret`            | *(insecure default)* | HS256 signing key — **change in production**   |
| `masterKey`            | *(insecure default)* | Admin login secret — **change in production**  |
| `jwtExpiryMillis`      | `86400000` (24h)     | Access-token lifetime                          |
| `maxUploadMb`          | `50`                 | Per-file upload ceiling                        |
| `cors.allowedOrigins`  | `*`                  | Comma-separated list of allowed origins        |

## REST API overview

| Path                        | Phase     | Description                                |
|-----------------------------|-----------|--------------------------------------------|
| `POST /auth/register`       | Phase 2   | Create a user, return JWT                  |
| `POST /auth/login`          | Phase 2   | Authenticate, return JWT                   |
| `POST /auth/apikey/generate`| Phase 2   | Issue a personal API key                   |
| `DELETE /auth/apikey`       | Phase 2   | Revoke the active API key                  |
| `GET/POST/PUT/PATCH/DELETE /db/{collection}[/{id}]` | Phase 3 | CRUD on documents       |
| `GET /realtime` (WebSocket) | Phase 4   | Subscribe to collection / document events  |
| `POST/GET/DELETE /storage/{bucket}/{path}` | Phase 5 | Upload, download, delete files     |
| `GET/POST /admin/rules`     | Phase 6   | Read / replace security rules              |
| `POST /admin/login`         | Phase 7   | Master-key admin login                     |
| `GET /admin/stats`          | Phase 7   | Server-wide counters                       |
| `GET/DELETE /admin/users`   | Phase 7   | List / delete users                        |
| `GET/DELETE /admin/collections` | Phase 7 | Inspect / drop collections               |
| `GET /health`               | Phase 8   | Liveness probe                             |
| `GET /health/ready`         | Phase 8   | Readiness (DB connection alive)            |

## Authentication

Every protected route accepts either:

- `Authorization: Bearer <jwt>` — short-lived JWT, returned by `/auth/login`
- `x-api-key: nb_<32-hex>` — long-lived API key from `/auth/apikey/generate`

Auth is enforced by the `AuthMiddleware` and bypassed only for `/auth/*` and `/health`.

## Building

```bash
mvn clean package -q
ls target/nodebase-1.0.jar
```

The fat JAR contains every runtime dependency. No `lib/` directory required.

## Running with a custom config

```bash
java -Dnodebase.config=/etc/nodebase/nodebase.properties \
     -jar target/nodebase-1.0.jar
```

Or with JVM-level overrides:

```bash
java -Dnodebase.port=9090 -Dnodebase.jwtSecret=$(openssl rand -hex 32) \
     -jar target/nodebase-1.0.jar
```

## License

MIT.
