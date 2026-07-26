# Deployment

This project is built with a student/portfolio cost constraint in mind — every recommendation here prioritizes free tiers and open-source self-hosting over paid services. See [`docs/roadmap.md`](roadmap.md) for the DevOps phase this will eventually formalize into Docker Compose + CI/CD.

## Environment variables

### Backend (`backend/.env`, see `backend/.env.example`)

| Variable | Required | Notes |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Yes | PostgreSQL connection. |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Yes | |
| `JWT_SECRET` | Yes | Generate with `openssl rand -base64 64`. The app **refuses to start** without this — that's intentional. Never commit a real value. |
| `JWT_EXPIRATION_MS` | No | Defaults to 3,600,000 (1 hour). |
| `CORS_ALLOWED_ORIGINS` | No | Comma-separated. Defaults to `http://localhost:5173,https://ry-ems.vercel.app` — update once you have your own deployed frontend URL. |
| `MAIL_HOST`, `MAIL_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Only if `app.email.enabled=true` | Welcome emails on employee creation. |
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

Follow the run order documented at the top of `backend/migration-phase1.sql` exactly — it's written as a staged, reviewable process (backup → deploy → migrate → verify → smoke-test → optional cleanup), not a single blind script execution. Running it out of order against a database with existing data risks data loss.

## What's not set up yet

Documented here so it's an explicit roadmap item, not a silent gap:
- No CI pipeline (GitHub Actions or otherwise) — every deploy today is manual.
- No Docker Compose for spinning up the full stack (backend + Postgres + frontend + ml-service) with one command locally.
- No monitoring/alerting (Prometheus/Grafana are on the original project roadmap but not started).

These are Phase 10 (DevOps) on the roadmap.
