# Tenemsa — Backend

Spring Boot 3.5 / Java 21 REST + WebSocket API for Tenemsa. Multi-tenant, role-based, JWT-authenticated.

See the repo root [`README.md`](../README.md) for the project overview, and [`../docs/`](../docs/) for architecture, API reference, database schema, deployment and seed data.

## Stack

Java 21 · Spring Boot 3.5 · Spring Security · Spring Data JPA · Flyway · PostgreSQL (or MySQL) · Maven · WebSocket (STOMP) · Spring AI (optional)

## Local setup

```bash
cp .env.example .env
# fill in DB credentials, generate a JWT_SECRET:
openssl rand -base64 64

./mvnw spring-boot:run
```

Flyway creates the schema on first start (`src/main/resources/db/migration/{postgresql,mysql}`, currently V1–V12). Choose the database with `SPRING_PROFILES_ACTIVE=postgres|mysql`. The `migration-phase*.sql` files are only for databases from before Phase 4.

## Demo data

```bash
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--tenemsa.seed.enabled=true"
```
Creates many demo companies with people, attendance, leave, projects, meetings, documents, chat and more (login `owner@demo-001.test` / `Demo@1234`). Options, accounts and what each dataset tests: [`../docs/seed-data.md`](../docs/seed-data.md). Code: `seed/DemoDataSeeder.java`.

## Structure

Package by feature under `src/main/java/com/tenemsa/platform/`:

```
auth/  security/  company/  employee/  invitation/  audit/  mail/   identity, tenancy, access
attendance/  leave/  team/  notification/  document/  chat/         HR and collaboration
project/ (issue, sprint)  meeting/  analytics/                      work and insight
ai/ (gateway, prompts, assistants, conversation)  knowledge/        Tenemsa AI
config/  common/  upload/  seed/                                    wiring and tools
```

Each feature keeps its own `controller`, `service`, `repository`, `model` and `dto` folders. Layering and multi-tenancy: [`../docs/architecture.md`](../docs/architecture.md).

## Testing

```bash
./mvnw test
```

Uses an in-memory H2 database in MySQL mode (Flyway off, schema created from the entities). About 118 tests cover tenant isolation, roles and permissions, sign-in and sessions, leave, notifications, documents, chat attachments, analytics, AI (switch, limits, failures, saved chats) and prompt contracts. The seeder is not loaded in tests.

## Platform admin API

Package `platformadmin`, off unless `PLATFORM_ENABLED=true`. See `docs/platform-admin.md`.
