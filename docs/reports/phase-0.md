# Phase 0 — Scaffolding

## What was built
- **Repository**: git repo (`main`, remote `sloyardms/kuratoon`), `.gitattributes` forcing LF for
  scripts, Dockerfiles, YAML and nginx templates, so Windows checkouts still run in Linux containers.
- **Backend** (`backend/`): Maven parent POM on Spring Boot 4.1.1 / Java 25, modules `plugin-api`,
  `app`, `example-plugin`, Maven wrapper 3.3.4 (Maven 3.9.16, distribution SHA-256 pinned).
  - `maven-enforcer-plugin` fails the build if `plugin-api` depends on anything except jsoup, or
    `example-plugin` on anything except `plugin-api`/jsoup (both `provided`/test scopes aside).
  - `plugin-api`: the spec-03 `MetadataProvider` interface as written, plus `MediaMetadata`
    (record, title required, lists never null), `CharacterRef`, `PublicationStatus`,
    `ParseException`, `PluginApi.VERSION = 1`.
  - `example-plugin`: empty package placeholder (implemented in phase 4).
  - `app`: Web MVC, Actuator, Validation, Data JPA, Flyway, PostgreSQL driver; virtual threads,
    RFC 9457 Problem Details, `open-in-view` off, `ddl-auto: validate`.
  - Flyway `V1__extensions.sql`: `pg_trgm` and `citext`.
  - Health: Actuator exposes only `health` (no details, no components, no probe groups, no
    discovery page) at `/actuator/health` for the compose healthcheck. `/api/v1/health` is a thin
    controller delegating to Actuator's `HealthEndpoint` and returning `{"status":"UP"}` (503 when down).
  - `KuratoonProperties` binds the deployment variables the backend consumes and fails startup
    with messages that name the variable: `APP_BASE_URL` required, `JWT_SECRET` required and
    ≥ 32 bytes, `IMAGE_ACCESS` must be `signed`/`public`, `IMAGE_SIGNING_SECRET` required when signed.
    Also solver URL/timeout (empty URL disables), default language, import poll/stable intervals,
    container paths (`/import`, `/library`, `/data`, `/plugins`).
- **Frontend** (`frontend/`): Next.js 16.4.2 App Router, TypeScript strict, Tailwind 4, shadcn
  (`base-nova` style, Button), dark mode as default, ESLint flat config (`eslint .`), Prettier with
  the Tailwind plugin, Vitest + Testing Library, Playwright. `output: "standalone"`,
  `images.unoptimized`. Placeholder home page. Exact versions pinned (`save-exact`).
- **Dockerfiles**: backend (Temurin JDK build → Temurin JRE runtime + `libvips-tools` 8.15.1,
  layered Spring Boot extract, curl healthcheck); frontend (deps → build → `node:24.22.0-slim`
  standalone runtime, `HOSTNAME=0.0.0.0`). Both default to uid 1000; compose runs them as `PUID:PGID`.
- **Deploy** (`deploy/`):
  - `docker-compose.yml`: postgres, byparr, backend, frontend, nginx on one internal network;
    only nginx publishes `${APP_PORT:-8080}`. Healthchecks on all five; `depends_on:
    service_healthy` (backend → postgres; nginx → backend + frontend). The backend does not depend
    on byparr. Backend datasource is mapped explicitly from `POSTGRES_*`. Postgres 18 volume is
    mounted at `/var/lib/postgresql` (PGDATA `/var/lib/postgresql/18/docker`). Required variables
    use `${VAR:?message}`, so compose refuses to start without them.
  - `docker-compose.dev.yml`: postgres + byparr only, on `127.0.0.1`.
  - `.env.example`: every variable from spec 08, with defaults, generation hints and the
    instruction to create the host folders owned by `PUID:PGID` before the first start.
  - nginx template: `/` → frontend, `/api/` → backend, `/healthz`, security headers, permissive
    CSP, gzip, `client_max_body_size 1m`, forwarded headers. `15-real-ip.sh` renders
    `set_real_ip_from` per `TRUSTED_PROXIES` entry (rejects anything that is not an IP/CIDR) plus
    `real_ip_header X-Forwarded-For` and `real_ip_recursive on`.
- **CI** (`.github/workflows/ci.yml`): `dorny/paths-filter` jobs:
  - backend: `./mvnw -B verify`, with Testcontainers.
  - frontend: lint, typecheck, unit tests, Playwright smoke test.
  - docker: builds the images, runs `compose up --wait` with a generated `.env`, then smoke-tests
    `/`, `/api/v1/health` and `/actuator/health` → 404 through nginx.
  - fixtures: determinism check.

  Changes under `.github/workflows/**` trigger every job.
- **Fixtures** (`fixtures/generate/`): `generate.py` (Pillow 12.3.0 pinned, vendored DejaVu Sans
  Bold + license) with a registry of all cases from `fixtures/README.md`. `series-basic` is
  implemented: manifest plus `Chapter 0 - Prologue` … `Chapter 5 - The End`, 4 numbered pages each.
  The other cases are stubs for phase 3. `check_deterministic.py` generates twice and compares SHA-256.

### Pinned versions
| Component | Version |
|---|---|
| Java | 25 LTS (`eclipse-temurin:25.0.4.1_1-jdk-noble` / `-jre-noble`) |
| Spring Boot | 4.1.1 (Flyway 12.4.0, PostgreSQL JDBC 42.7.13, Testcontainers 2.0.5 from its BOM) |
| jsoup | 1.23.2 |
| Maven | 3.9.16 via wrapper 3.3.4 |
| Node | 24 LTS (`node:24.22.0-slim`; CI `node-version: 24`) |
| Next.js / React | 16.4.2 / 19.3.1 |
| TypeScript | 5.9.3 |
| Tailwind CSS | 4.3.3 |
| shadcn | 4.22.1 (dev dependency) |
| ESLint / Prettier | 9.39.5 / 3.9.10 |
| Vitest / Playwright | 5.0.5 / 1.64.1 |
| PostgreSQL | `postgres:18.6` |
| nginx | `nginxinc/nginx-unprivileged:1.30.6-alpine` (stable line) |
| Byparr | `ghcr.io/thephaseless/byparr:3.0.4` |
| libvips | 8.15.1 (Ubuntu noble `libvips-tools`) |
| Pillow (fixtures) | 12.3.0 |

## Deviations from the spec
- **08 / Internal nginx**: `/img/…`, `secure_link` and the `limit_req`/`limit_conn` zones are not
  configured yet. The roadmap puts image delivery and rate limits in phase 5. The `RATE_LIMIT_*`
  variables are already documented in `.env.example`.
- **08 / Internal nginx**: the "official nginx" image is replaced by `nginxinc/nginx-unprivileged`
  (published by the nginx team), so nginx runs as a non-root user. It runs as its built-in uid 101,
  not `PUID:PGID`, because it only reads `/library` and `/data`. That works as long as the backend
  writes files world-readable (the default umask); phase 3 must keep that in mind.
- **08 / Internal nginx**: the CSP is permissive in phase 0 (`'unsafe-inline'` scripts/styles for
  Next.js). It should be tightened (nonces) once real pages exist.
- **08 / Services**: Byparr's image healthcheck runs every 15 minutes, which would stall
  `compose up --wait`. Compose overrides it with a 30 s check of `/health`.
- **Roadmap phase 0 / health**: spec 08 names `/actuator/health`, and the roadmap names
  `/api/v1/health`. Both are served. Actuator's `additional-path` only accepts a single path
  segment, so `/api/v1/health` is a small controller over `HealthEndpoint`, not an Actuator group.
- **backend/CLAUDE.md / Stack**: Spring Security is not added yet (phase 2). With it on the
  classpath every endpoint would be locked down before auth exists.
- **Dev database**: `docker-compose.dev.yml` and `application.yml` share a local-only default
  password (`kuratoon-dev`, bound to 127.0.0.1) so `./mvnw -pl app spring-boot:run` works with no
  setup. Production values come only from `deploy/.env`.

## Spec files that may need updating
- `08-deployment.md`: say that the public health URL is `/api/v1/health` and that
  `/actuator/health` is internal only.
- `08-deployment.md`: name the nginx image (`nginx-unprivileged`) and state that nginx needs read
  permission on `/library` and `/data` (world-readable files, or a matching uid).
- `08-deployment.md`: `TRUSTED_PROXIES` is also passed to the backend. Tomcat currently trusts
  forwarded headers only from private ranges (nginx on the internal network), which is correct
  but not written down.
- `08-deployment.md`: say where the Postgres 18 volume is mounted (`/var/lib/postgresql`) and that
  host folders must be pre-created with the right owner.

## Tests
- Added:
  - `MediaMetadataTest`: title required; null lists become empty; lists are immutable copies.
  - `KuratoonPropertiesTest` (8 tests): defaults bind; startup fails on missing `APP_BASE_URL`,
    missing or short `JWT_SECRET`, missing signing secret in signed mode, or invalid
    `IMAGE_ACCESS`; public mode needs no signing secret; an empty `SOLVER_URL` disables the solver.
  - `HealthAndSchemaIT` (Testcontainers `postgres:18.6`, 4 tests): Flyway V1 applied with
    `pg_trgm` + `citext`; `/api/v1/health` and `/actuator/health` return exactly
    `{"status":"UP"}`; `/actuator`, `/actuator/env|info|metrics|configprops|health/db` return 404.
  - Frontend: Vitest render test of the home page; Playwright smoke test loading `/` from the
    standalone build.
  - Fixtures: `check_deterministic.py`.
  - CI docker job: smoke test of the full stack through nginx.
- Status, all run locally on Windows 11 with Docker Desktop:
  - Backend: `./mvnw verify` passes 14 tests.
  - Frontend: `npm run lint`, `typecheck`, `test` (1 test) and `test:e2e` (1 test) pass;
    `prettier --check` is clean.
  - Fixtures: the determinism check passes (25 files identical).
  - Full stack: `docker compose up --build --wait` gets all five services healthy. Through nginx
    on 8080, `/` returns 200 with the placeholder, `/api/v1/health` returns `{"status":"UP"}`,
    `/actuator/health` returns 404, and the security headers are present.
- CI (GitHub Actions, `sloyardms/kuratoon`): every job is green.
  - Frontend and fixtures passed on the first run.
  - The first run's backend and docker failures were fixed in two commits:
    - `mvnw` had been committed without its executable bit (the repo was created on Windows).
    - The JDK build image lacked `unzip`, so the Maven wrapper fell back to the `.tar.gz`
      distribution and failed the pinned `.zip` SHA-256.
  - Run 39379150557 then passed docker and backend.

## Known issues and shortcuts
- `example-plugin` contains no code yet (phase 4).
- The fixture generator implements only `series-basic`; the other cases raise `NotImplementedError`
  (phase 3).
- `/healthz` on nginx is reachable from outside. It returns only `ok`; an allow-list is not
  reliable behind Docker's port proxy.
- `frontend/AGENTS.md` is written by `next dev` itself and is committed so the tree stays clean.
- Tomcat's internal-proxy trust (`forward-headers-strategy: native`) uses Tomcat's default
  private-range regex rather than `TRUSTED_PROXIES`. That is fine while only nginx talks to the
  backend; revisit when the auth rate limits need real client IPs (phase 2).

- Windows checkouts: the system Git config here sets `core.autocrlf=true`, and `git archive`
  applies it despite `.gitattributes`. Committed files are LF; to reproduce CI's Linux checkout
  locally, use `git -c core.autocrlf=false archive`. Executable bits must be set with
  `git update-index --chmod=+x`, because `core.filemode=false` on Windows.

## Open questions for the owner
- nginx running as uid 101 and relying on world-readable library files: acceptable, or should
  nginx run as `PUID:PGID` (needs a custom `nginx.conf` with writable temp/pid paths)?

## How to verify manually
1. Backend: `cd backend && ./mvnw verify` (Docker must be running for Testcontainers).
2. Frontend: `cd frontend && npm ci && npm run lint && npm run typecheck && npm test && npm run test:e2e`
   (first time: `npx playwright install chromium`).
3. Fixtures: create a venv, `pip install -r fixtures/generate/requirements.txt`, then
   `python fixtures/generate/generate.py` and `python fixtures/generate/check_deterministic.py`.
4. Stack:
   - `cp deploy/.env.example deploy/.env`, then fill in `POSTGRES_PASSWORD`, `JWT_SECRET` and
     `IMAGE_SIGNING_SECRET` (`openssl rand -hex 32`).
   - `mkdir -p deploy/data/{import,library,data,plugins,postgres}`, owned by `PUID:PGID` on Linux.
   - `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build --wait`
   - `curl http://localhost:8080/` returns the placeholder page.
   - `curl http://localhost:8080/api/v1/health` returns `{"status":"UP"}`.
   - `curl -i http://localhost:8080/actuator/health` returns 404.
5. Fail-fast: remove `JWT_SECRET` from `.env`. Compose then refuses to start. Set it to a short
   value instead, and the backend exits with "JWT_SECRET must be at least 32 bytes".
