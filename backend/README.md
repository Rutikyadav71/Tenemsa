# Tenemsa — Backend

Spring Boot 3 / Java 21 REST + WebSocket API for Tenemsa. Multi-tenant, role-based, JWT-authenticated.

See the repo root [`README.md`](../README.md) for the full project overview, and [`../docs/`](../docs/) for architecture, API reference, database schema, and deployment details.

## Stack

Java 21 · Spring Boot 3.5 · Spring Security · Spring Data JPA · PostgreSQL · Maven · WebSocket (STOMP)

## Local setup

```bash
cp .env.example .env
# fill in DB credentials, generate a JWT_SECRET:
openssl rand -base64 64

./mvnw spring-boot:run
```

Fresh database? Run `migration-phase1.sql` after the first startup — read the comments at the top first, the order matters.

## Structure

```
src/main/java/com/tenemsa/platform/
├── controller/   HTTP + WebSocket entry points
├── service/      business logic
├── repository/   Spring Data JPA interfaces
├── security/     JWT, filters, auth context helpers
├── model/        JPA entities
├── dto/          request/response shapes
└── config/       Cloudinary, WebSocket, etc.
```

Full explanation of the layering and multi-tenancy model: [`../docs/architecture.md`](../docs/architecture.md).

## Testing

```bash
./mvnw test
```

Coverage is currently limited to the Spring Boot context-load smoke test — expanding this is a Phase 3 item (see [`../docs/roadmap.md`](../docs/roadmap.md)).
