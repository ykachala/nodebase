# Nodebase

A self-hosted Firebase-like Backend-as-a-Service platform built in pure Java 21.

## Vision
Give developers a single binary they can run anywhere that provides authentication, a NoSQL document database with real-time subscriptions, file storage, JSON-based security rules, and a clean Java SDK — no external dependencies beyond a JVM.

## Tech Stack
- **Java 21** (no Spring/Quarkus — pure Java for minimum overhead)
- **Embedded Jetty 12** — HTTP + WebSocket server
- **Jackson 2.x** — JSON serialization
- **SQLite JDBC** — embedded persistence with WAL mode
- **JJWT (io.jsonwebtoken 0.12.x)** — JWT signing/verification
- **jBCrypt (org.mindrot:jbcrypt:0.4)** — password hashing
- **SLF4J + Logback** — structured logging
- **Maven** — build system (fat JAR via maven-assembly-plugin)

## Folder Structure

```
nodebase/
├── pom.xml
├── README.md
├── CLAUDE.md
├── src/main/java/io/nodebase/
│   ├── NodebaseServer.java          # main entry point
│   ├── config/ServerConfig.java
│   ├── auth/                        # User, JwtProvider, AuthService, ApiKeyService
│   ├── database/                    # Document, DocumentRepository, QueryFilter, DatabaseService
│   ├── realtime/                    # RealtimeWebSocketHandler, SubscriptionManager, RealtimeEvent
│   ├── storage/                     # StorageObject, StorageRepository, StorageService
│   ├── security/                    # SecurityRule, RulesEngine
│   ├── admin/AdminHandler.java
│   ├── sdk/                         # NodebaseClient, NodebaseAuth, NodebaseDatabase, NodebaseStorage
│   ├── middleware/                  # RateLimiter, CorsFilter, AuthMiddleware, GzipFilter
│   ├── api/                         # Router, AuthHandler, DatabaseHandler, StorageHandler, HealthHandler
│   └── util/                        # JsonUtil, CryptoUtil
└── src/main/resources/
    ├── nodebase.properties
    ├── logback.xml
    └── security-rules.json
```

## Project Conventions
- All IDs are UUIDs (`UUID.randomUUID().toString()`).
- Timestamps are Unix epoch milliseconds (`System.currentTimeMillis()`).
- Error responses are always `{"error": "message"}`.
- Success responses are always JSON (HTTP 200/201).
- SQLite uses WAL mode (`PRAGMA journal_mode=WAL;`).
- Repositories own a shared `Connection` opened in the constructor.
- Never return stack traces to clients — log them server-side.
- Strict separation of concerns: handlers route, services own logic, repositories own data.
- Every public method has explicit parameter and return types.

## Commit Discipline
Every todo gets one commit, backdated to the phase schedule:

```bash
GIT_COMMITTER_DATE="YYYY-MM-DDTHH:MM:00 +0000" \
GIT_AUTHOR_DATE="YYYY-MM-DDTHH:MM:00 +0000" \
git commit -m "[Phase N] Description"
```

After all commits in a phase: `git push origin master`.

## Phases

### Phase 1 — Project Bootstrap & Core Server [status: in-progress]
Date: Mon Jan 6 2025
- [ ] 17:10 — Initialize Maven project with core dependencies
- [ ] 17:35 — Bootstrap embedded Jetty HTTP server
- [ ] 18:00 — Add configuration system with property file support
- [ ] 18:25 — Configure structured logging with Logback
- [ ] 18:50 — Write README with setup and run instructions

### Phase 2 — Authentication System [status: pending]
Date: Tue Jan 7 2025
- [ ] 17:08 — Add User model and SQLite-backed user repository
- [ ] 17:32 — Implement BCrypt password hashing and JWT token provider
- [ ] 17:58 — Build authentication service with register and login
- [ ] 18:22 — Add API key generation and validation service
- [ ] 18:45 — Expose /auth/register and /auth/login endpoints
- [ ] 18:58 — Add auth middleware for Bearer token and API key validation

### Phase 3 — Document Database Engine [status: pending]
Date: Wed Jan 8 2025
- [ ] 17:12 — Design Document and Collection data models
- [ ] 17:38 — Implement SQLite-backed document repository
- [ ] 18:02 — Build query filter engine for collection queries
- [ ] 18:25 — Build DatabaseService with full CRUD operations
- [ ] 18:48 — Expose /db/{collection} REST endpoints
- [ ] 18:58 — Add document timestamps and wire auth middleware into DB routes

### Phase 4 — Real-time WebSocket Engine [status: pending]
Date: Thu Jan 9 2025
- [ ] 17:15 — Implement WebSocket handler with Jetty WebSocket API
- [ ] 17:42 — Build subscription manager for collection and document watching
- [ ] 18:08 — Create real-time event model and broadcasting system
- [ ] 18:32 — Connect database mutations to real-time event pipeline
- [ ] 18:55 — Add JWT authentication in WebSocket handshake

### Phase 5 — File Storage System [status: pending]
Date: Fri Jan 10 2025
- [ ] 17:10 — Design storage object model and metadata repository
- [ ] 17:38 — Implement local filesystem storage backend
- [ ] 18:02 — Add file type validation and size limit enforcement
- [ ] 18:25 — Expose /storage/{bucket}/{path} REST endpoints
- [ ] 18:48 — Implement access control for storage objects
- [ ] 18:58 — Wire storage handler into router with auth middleware

### Phase 6 — Security Rules Engine [status: pending]
Date: Mon Jan 13 2025
- [ ] 17:15 — Design security rules DSL and data model
- [ ] 17:42 — Implement rules file loader and hot-reload support
- [ ] 18:08 — Build rule evaluator with context matching
- [ ] 18:28 — Integrate rules engine into database request pipeline
- [ ] 18:50 — Integrate rules engine into storage request pipeline
- [ ] 18:58 — Add rules management endpoints

### Phase 7 — Admin API & Java Client SDK [status: pending]
Date: Tue Jan 14 2025
- [ ] 17:08 — Build admin authentication with master key
- [ ] 17:35 — Implement admin management endpoints
- [ ] 18:00 — Create NodebaseClient Java SDK — auth module
- [ ] 18:22 — Create NodebaseClient Java SDK — database module
- [ ] 18:45 — Create NodebaseClient Java SDK — storage module
- [ ] 18:58 — Document SDK usage in README with code examples

### Phase 8 — Production Hardening [status: pending]
Date: Wed Jan 15 2025
- [ ] 17:10 — Implement per-IP and per-API-key rate limiting
- [ ] 17:35 — Add CORS support with configurable allowed origins
- [ ] 18:00 — Implement gzip response compression
- [ ] 18:25 — Add health check and readiness endpoints
- [ ] 18:50 — Implement graceful shutdown with connection draining
- [ ] 18:58 — Wire all middleware into request pipeline and finalize router

## Success Criteria
- `mvn package -q` produces `target/nodebase-1.0.jar` with no errors
- `java -jar target/nodebase-1.0.jar` starts on port 8080
- `curl localhost:8080/health` returns `{"status":"ok",...}`
- All 8 phases committed with the correct backdated dates and pushed to origin
