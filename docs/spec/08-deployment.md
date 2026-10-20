# 08 — Deployment

## Services (`deploy/docker-compose.yml`)

| Service | Image | Notes |
|---|---|---|
| `postgres` | official postgres (pinned major) | volume `${DATA_ROOT}/postgres` |
| `byparr` | `ghcr.io/thephaseless/byparr` (pinned tag) | internal only; optional (comment out to disable) |
| `backend` | built from `backend/Dockerfile` | includes libvips; internal only |
| `frontend` | built from `frontend/Dockerfile` | Next.js standalone output; internal only |
| `nginx` | official nginx (pinned) | **the only published port** (`${APP_PORT:-8080}`) |

All services on one internal network. Healthchecks on postgres, backend (`/actuator/health`),
frontend and nginx; `depends_on` with `condition: service_healthy`.

`deploy/docker-compose.dev.yml` runs only `postgres` and `byparr` (ports published to localhost)
for local development.

No reverse proxy, TLS or certificates are shipped. The installer puts their own proxy in front of
`APP_PORT`. The docs include short example snippets (Caddy, Nginx Proxy Manager) as a courtesy.

## Volumes

| Host (env) | Container | Used by | Mode |
|---|---|---|---|
| `${IMPORT_DIR}` | `/import` | backend | rw |
| `${LIBRARY_DIR}` | `/library` | backend rw, nginx ro | |
| `${DATA_DIR}` | `/data` | backend rw, nginx ro | |
| `${PLUGINS_DIR}` | `/plugins` | backend | ro |
| `${DATA_ROOT}/postgres` | postgres data | postgres | rw |

Imports are copied from `/import` into `/library/.staging/`, then renamed into place, so `/import`
may be on a different disk; only the final rename must stay within `/library`. Containers run as a non-root user with configurable
`PUID`/`PGID`.

Backups: stop the stack and copy the volumes. The library is also self-describing through
sidecars ("Rebuild library from disk").

## Internal nginx (`deploy/nginx/`)

Config is a template rendered from env at container start (`envsubst` via the official image's
templates directory).

- `/` → frontend, `/api/` → backend, `/img/…` → files (see `06-images.md`).
- `secure_link` validation when `IMAGE_ACCESS=signed`.
- `limit_req` / `limit_conn` zones from `RATE_LIMIT_*`.
- `set_real_ip_from` for each entry of `TRUSTED_PROXIES`, `real_ip_header X-Forwarded-For`,
  `real_ip_recursive on`.
- Security headers, gzip for text, `client_max_body_size` small (no uploads in v1 except covers:
  10 MB on the cover upload route).

## Environment variables (`deploy/.env.example`)

| Variable | Default | Purpose |
|---|---|---|
| `APP_PORT` | 8080 | published port |
| `APP_BASE_URL` | — (required) | public URL, used in emails/absolute links |
| `PUID` / `PGID` | 1000 | file ownership |
| `IMPORT_DIR`, `LIBRARY_DIR`, `DATA_DIR`, `PLUGINS_DIR`, `DATA_ROOT` | — | host paths |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | kuratoon / kuratoon / — | database |
| `JWT_SECRET` | — (required, ≥32 bytes) | access token signing |
| `IMAGE_ACCESS` | signed | `signed` \| `public` |
| `IMAGE_SIGNING_SECRET` | — (required when signed) | nginx secure_link secret |
| `TRUSTED_PROXIES` | — | comma-separated IPs/CIDRs of the installer's reverse proxy |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` / `ADMIN_EMAIL` | — | optional admin bootstrap |
| `ADMIN_FORCE_RESET` | false | reset that admin on boot (remove after use) |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` / `SMTP_FROM` / `SMTP_TLS` | — | enables password reset |
| `SOLVER_URL` | http://byparr:8191 | FlareSolverr-compatible endpoint; empty disables |
| `SOLVER_TIMEOUT_MS` | 60000 | |
| `DEFAULT_LANGUAGE` | en | when metadata has none |
| `IMPORT_POLL_SECONDS` | 30 | |
| `IMPORT_STABLE_SECONDS` | 60 | |
| `RATE_LIMIT_IMAGES_RPS` / `_BURST` | 40 / 200 | |
| `RATE_LIMIT_API_RPS` / `_BURST` | 20 / 60 | |
| `RATE_LIMIT_AUTH_RPM` / `_BURST` | 10 / 10 | |
| `LOG_LEVEL` | INFO | |

Startup fails fast with a clear message if a required variable is missing or invalid.

## Images and builds

- Multi-stage Dockerfiles; backend runtime is a slim JRE image + libvips; frontend uses Next.js
  `output: standalone`.
- CI (`.github/workflows/`): backend `./mvnw verify` (Testcontainers), frontend lint/typecheck/test,
  Docker build check. Path filters per project.
- Images are built locally by `docker compose build` in v1; publishing to a registry is deferred.
