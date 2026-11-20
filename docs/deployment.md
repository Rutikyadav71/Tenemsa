# Deployment

This project is built with a student/portfolio cost constraint in mind — every recommendation here prioritizes free tiers and open-source self-hosting over paid services. See [`docs/roadmap.md`](roadmap.md) for the DevOps phase this will eventually formalize into Docker Compose + CI/CD.

## Environment variables

### Backend (`backend/.env`, see `backend/.env.example`)

| Variable | Required | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | No | `postgres` (default) or `mysql`. |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Yes | Database connection. |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Yes | |
| `JWT_SECRET` | Yes | Generate with `openssl rand -base64 64`. The app **refuses to start** without this — that's intentional. Never commit a real value. |
| `JWT_EXPIRATION_MS` | No | Access token lifetime, defaults to 900,000 (15 minutes). Sessions are kept alive by rotating refresh tokens (`REFRESH_TOKEN_EXPIRATION_DAYS`, `REMEMBER_ME_EXPIRATION_DAYS`). |
| `CORS_ALLOWED_ORIGINS` | No | Comma-separated. Defaults to `http://localhost:5173,https://ry-ems.vercel.app` — update once you have your own deployed frontend URL. |
| `MAIL_HOST`, `MAIL_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Only if `app.email.enabled=true` | Welcome emails on employee creation. |
| `RATE_LIMIT_ENABLED` | No | Defaults to `true`. Per-IP limits on sign-in, registration, workspace lookup, password-reset mail and invitation links (see architecture doc). |
| `RATE_LIMIT_TRUST_PROXY` | No | Defaults to `false`. Set `true` **only** behind a reverse proxy that appends the client address to `X-Forwarded-For`; otherwise every user shares the proxy's address and gets rate-limited together. Do not enable it when the backend is directly reachable, because clients can forge that header. |
| `CLOUD_NAME`, `API_KEY`, `API_SECRET` | Yes | Cloudinary — free tier is generous enough for a portfolio project's file/image uploads; no reason to switch to S3/MinIO for this scale. |

### Frontend (`frontend/.env`, see `frontend/.env.example`)

| Variable | Required | Notes |
|---|---|---|
| `VITE_API_BASE_URL` | No | Defaults to the deployed backend URL if unset. Set to `http://localhost:8080` for local dev against a local backend. |

## Database hosting

The backend targets PostgreSQL (not MySQL, despite some early planning docs mentioning it — see roadmap for that decision). Free-tier managed Postgres options that work well here:

- **Supabase** — generous free tier, built-in connection pooling.
- **Neon** — serverless Postgres, scales to zero, good for a project with low/spiky traffic.
- **Railway** — simplest setup if you're already using Railway for hosting the backend itself.

Any of these work identically from the application's perspective — it's a standard JDBC Postgres connection either way.

## Backend hosting

The `dockerfile` in `backend/` is a standard two-stage build (Maven build → slim JRE runtime image) and works with any container host. The `procfile` is there for Heroku-style buildpack deployment as an alternative to Docker (currently targets `render.com` based on the CORS defaults — update `CORS_ALLOWED_ORIGINS` if you deploy elsewhere).

Free-tier options that work with a Spring Boot app this size:
- **Render** — what this project has been deployed to historically (see the default CORS origin). Free tier sleeps after inactivity, which adds cold-start latency — fine for a portfolio demo, not for anything expecting consistent uptime.
- **Railway** — usage-based free tier, no sleep behavior, generally faster cold starts than Render's free tier.
- **Fly.io** — works well for containerized Spring Boot, has a free allowance.

## Frontend hosting

Static build (`npm run build` → `dist/`) deploys to any static host. `vercel.json` is already present, so Vercel is the path of least resistance, but Netlify/Cloudflare Pages work identically for a Vite SPA.

## ML service hosting

Optional. If you want leave predictions in a deployed environment (not just local dev), it needs to run somewhere reachable by the backend — `LeaveService`'s `ML_API_URL` is currently hardcoded to `http://localhost:5000/predict-leave`, so deploying this for real requires either:
1. Making that URL configurable via an environment variable (currently isn't — worth fixing before deploying this service), or
2. Running it on the same host/network as the backend.

Given the `leave_type` schema gap documented in `docs/database.md`, confirm the prediction endpoint actually works end-to-end before investing time in deploying this piece.

## Migrations in a deployed environment

**Phase 4e onwards:** Flyway runs automatically at startup (see `docs/database.md`). Take a database backup before the first start with Flyway; an existing database is baselined, not rebuilt. Then, once it has started cleanly, set `DDL_AUTO=validate`. New schema changes ship as new `V<n>__*.sql` files in both `db/migration/postgresql` and `db/migration/mysql`.

The older instructions below apply only to databases still on a pre-Phase-4 schema. Follow the run order documented at the top of `backend/migration-phase1.sql` exactly — it's written as a staged, reviewable process (backup → deploy → migrate → verify → smoke-test → optional cleanup), not a single blind script execution. Running it out of order against a database with existing data risks data loss.

## Demo data (never in production)

`--tenemsa.seed.enabled=true` fills a database with demo companies and data for testing (see `docs/seed-data.md`). It is off by default, only touches companies whose slug starts with `demo-`, and refuses to run when the `prod` or `production` Spring profile is active. Use it on local and staging databases only; every demo account has the same known password.

## What's not set up yet

Documented here so it's an explicit roadmap item, not a silent gap:
- No CI pipeline (GitHub Actions or otherwise) — every deploy today is manual.
- The leave predictor (`ml-service`) is not in the Docker Compose file; it is parked.
- No monitoring/alerting (Prometheus/Grafana are on the original project roadmap but not started).

These are on the roadmap as the DevOps phase.

## Choosing the database (PostgreSQL or MySQL)

The backend supports both through Spring profiles. Set one variable:

| `SPRING_PROFILES_ACTIVE` | Config file | Default port |
|---|---|---|
| `postgres` (default) | `application-postgres.properties` | 5432 |
| `mysql` | `application-mysql.properties` | 3306 |

`DB_HOST`, `DB_PORT`, `DB_NAME`, `DATABASE_USERNAME` and `DATABASE_PASSWORD` are read by both. Shared settings stay in `application.properties`; only the datasource URL/driver differ. Hibernate picks the dialect from the driver, so don't set one manually.

PowerShell example: `$env:SPRING_PROFILES_ACTIVE="mysql"; ./mvnw spring-boot:run` (or `./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql`). Schema is created by `ddl-auto=update`. The hand-written `migration-phase1.sql` / `migration-phase4.sql` scripts are PostgreSQL syntax and only matter for an existing database that already holds data; a new database needs neither. Pick one database per environment — profiles do not copy data between them.

## Enabling AI (Phase 6)

AI is off by default and the app runs normally without it (AI endpoints return 503).

1. Create a key in Google AI Studio.
2. In `backend/.env` set `AI_PROVIDER=google-genai` and `GEMINI_API_KEY=<key>`. The key stays on the server only.
3. Restart the backend. Optional: `AI_MODEL`, `AI_TIMEOUT_SECONDS`, `AI_PER_USER_PER_HOUR`, `AI_PER_COMPANY_PER_DAY`, `AI_MAX_INPUT_CHARS`.

Database: MySQL is for local development; production and any vector/RAG work use PostgreSQL (`SPRING_PROFILES_ACTIVE=postgres`-style profile as already configured).

### AI cost estimate and company switch
Set `AI_PRICE_INPUT_PER_MILLION_USD` and `AI_PRICE_OUTPUT_PER_MILLION_USD` to your model's published prices to show an estimated cost on the AI Usage page (left at 0, the figure is hidden). Owners and admins can switch AI off for their company under Company settings.

## Knowledge base and pgvector

- Embeddings follow `AI_PROVIDER=google-genai` and `GEMINI_API_KEY`. Without them documents cannot be added (503).
- PostgreSQL (production): run `CREATE EXTENSION vector;` once as a privileged user if V7 cannot (Render Postgres supports it), then restart; with the extension present Flyway V7 creates the vector column and HNSW index. If you enable the extension after V7 ran, add manually: `ALTER TABLE kb_chunks ADD COLUMN embedding vector(768); CREATE INDEX idx_kb_chunks_embedding ON kb_chunks USING hnsw (embedding vector_cosine_ops);` then Re-index each document.
- MySQL (local): works through the scan search, capped by `KB_SCAN_MAX_CHUNKS`.
- Settings: `KB_VECTOR_STORE` (auto|pgvector|scan), `KB_MIN_SCORE`, `KB_EMBED_PER_COMPANY_PER_DAY`, `KB_TOP_K`.

## Document storage

Uploaded files are stored in the database (`document_files`). Limit per company: `DOCUMENTS_MAX_COMPANY_BYTES` (default 209715200 = 200 MB). Include the database in backups. Individual uploads are limited to 10 MB by `spring.servlet.multipart.*`.

## Platform console

Not deployed with the site. Backend switch `PLATFORM_ENABLED` (default false); set `PLATFORM_JWT_SECRET`, `PLATFORM_SETUP_KEY` (remove after both admins register) and add `http://localhost:5174` to `CORS_ALLOWED_ORIGINS` to use it locally. Full steps in [platform-admin.md](platform-admin.md).

## Billing

Set `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` and (for renewals) `RAZORPAY_WEBHOOK_SECRET`; register `https://YOUR-BACKEND/api/billing/webhook/razorpay` in Razorpay. See [billing.md](billing.md).

## Connected apps

Set `CONNECTIONS_SECRET`, `BACKEND_URL` and the GitHub/Google client ids and secrets, and register `BACKEND_URL/api/connections/callback/<app>` with each provider. See [connections.md](connections.md).

## Run everything with Docker Compose (Phase 11)

`docker-compose.yml` at the project root starts PostgreSQL, the backend and the website together.

```
cp .env.docker.example .env      # then set DATABASE_PASSWORD and JWT_SECRET (openssl rand -base64 64)
docker compose up -d --build
```

The site is at `http://localhost:3000` and the API at `http://localhost:8080`. Flyway builds the database on first start. Optional features (email, AI, billing, connected apps, file uploads) stay off until you fill in their variables in `.env`.

Things to know:
- The site's address for the API (`VITE_API_BASE_URL`) and the public-page details (`VITE_SUPPORT_EMAIL`, `VITE_LEGAL_*`) are baked in when the website image is built. Change them in `.env` and run `docker compose up -d --build frontend`.
- On a real domain, set `FRONTEND_URL`, `BACKEND_URL` and `CORS_ALLOWED_ORIGINS` to the public https addresses, and put a reverse proxy with HTTPS in front. Set `RATE_LIMIT_TRUST_PROXY=true` only when that proxy appends the client address.
- `DDL_AUTO` defaults to `update` in the compose file. After the first clean start, set it to `validate`.
- The Dockerfiles and the compose file have been checked for syntax only. They were written without a Docker daemon to build them, so run the first build yourself and read the logs.
- The platform console is not part of the compose file (see below).

## Health check

`GET /api/health` is public. It answers `200 {"status":"UP","database":"UP"}` when the app can reach its database and `503` with `DOWN` when it cannot. It says nothing else. Use it as the health check on Render, Railway, Fly or a load balancer; the backend image already uses it.

## Go-live checklist

Before real people use it:

1. **Secrets:** `JWT_SECRET` generated and unique; no `.env` file committed; `PLATFORM_SETUP_KEY` removed once both platform admins are registered.
2. **Database:** managed PostgreSQL with automatic backups, and a restore you have tried once. Document files live in the database, so they are included.
3. **Addresses:** HTTPS everywhere; `FRONTEND_URL`, `BACKEND_URL`, `CORS_ALLOWED_ORIGINS` set to the real addresses; OAuth and Razorpay callback addresses registered with the providers.
4. **Email:** a real SMTP sender, with SPF and DKIM set up on your domain, then send yourself a password-reset email and an invitation.
5. **Legal:** have a lawyer review the Privacy and Terms pages, fill in `VITE_LEGAL_NAME`, `VITE_LEGAL_ADDRESS`, `VITE_SUPPORT_EMAIL`, then set `VITE_LEGAL_REVIEWED=true` to remove the notice on those pages.
6. **Billing:** Razorpay in test mode first. Trials (`start_at`), plan changes and top-up orders have not been tried against Razorpay, so run one of each, and confirm the webhook arrives.
7. **AI and connected apps:** if switched on, set a spending alert with the provider; try one question in each feature; check the Microsoft and Google consent screens show your own app name.
8. **Demo data:** the seeder is off; no demo accounts exist.
9. **Logs:** `SHOW_SQL` is off by default now; leave it off.
10. **Smoke test as a new company:** register, add a person by invitation, apply and approve leave, create a project, ask the assistant, open Plan & billing. Then try the same as an employee on a phone.
