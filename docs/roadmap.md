# Roadmap

One phase at a time. A phase is done when every acceptance criterion is met, covered by automated
tests where possible, CI is green, and `docs/reports/phase-N.md` is written.
The owner reviews each report before the next phase starts.

---

## Phase 0 — Scaffolding

Specs: `08-deployment.md`, `CLAUDE.md` files.

- Maven parent POM with modules `plugin-api`, `app`, `example-plugin`; Maven wrapper committed.
- Spring Boot app with Actuator health, Flyway wired (empty baseline migration allowed).
- Next.js app (TypeScript strict, Tailwind, shadcn initialized, ESLint, Prettier, Vitest, Playwright).
- `deploy/docker-compose.yml`, `docker-compose.dev.yml`, `.env.example`, nginx template routing
  `/` and `/api/`.
- Dockerfiles for backend (with libvips) and frontend (standalone).
- GitHub Actions CI with path filters.
- `fixtures/generate` script skeleton (see `fixtures/README.md`).

**Done when**
- `docker compose up --build` serves a placeholder page through nginx on `APP_PORT`, and
  `/api/v1/health` returns OK through nginx.
- `./mvnw verify` and `npm run lint && npm run typecheck && npm test` pass locally and in CI.
- Report lists all pinned versions (Java, Spring Boot, Node, Next.js, Postgres, nginx, Byparr).

## Phase 1 — Schema and core domain

Specs: `01-data-model.md`.

- Flyway migrations for the full data model.
- JPA entities/repositories for library tables; services + admin REST CRUD for media types, tags,
  creators, parodies, characters; media and chapter read endpoints (no import yet).
- Seed media types. Test data builders for tests.

**Done when**
- Migrations apply on an empty DB (Testcontainers) and match the spec.
- CRUD endpoints tested, including constraint violations (duplicate slugs, deleting a type in use).
- Trigram title search over `media.title` and `media_title.title` tested.

## Phase 2 — Auth, users, settings

Specs: `04-auth-users.md`.

- Login, refresh rotation with reuse detection, logout, log out everywhere.
- First-run setup, env bootstrap, `ADMIN_FORCE_RESET`.
- Registration toggle, SMTP check + password reset, admin Users page API, settings API, audit log.
- Frontend: Auth.js integration, `/setup`, `/login`, `/register`, `/forgot-password`,
  `/reset-password`, app shell with top bar, admin Users + Settings pages.

**Done when**
- Security tests: wrong password, disabled user, revoked token_version, refresh reuse revokes
  family, rate limit triggers, anti-enumeration responses identical, registration disabled
  enforced server-side, last admin protection.
- Playwright: first-run setup → login → logout.

## Phase 3 — Ingestion (no scraping)

Specs: `02-ingestion.md`, `06-images.md` (generation part).

- Polling + stability detection, job lifecycle, manifest/`source.txt` parsing, structure detection,
  chapter/page parsing, staging + commit order, NEW/UPDATE/RESTORE, duplicate detection,
  sidecar writing, thumbnails via libvips, cover selection.
- Manual metadata entry for jobs in NEEDS_ATTENTION.
- Admin Imports page; media edit page with field locking; cover crop tool; integrity scan;
  rebuild library from disk.

**Done when**
- Every example in the chapter parsing table is a unit test; page ordering tests cover 1/01/001,
  "page N", decimals and junk files.
- Integration tests using `fixtures/import`: new series, one-shot, update appending chapters,
  conflicting chapter → NEEDS_ATTENTION, corrupt image → NEEDS_ATTENTION, restore from sidecar
  after DB wipe, crash during PROCESSING leaves no partial library folder.

**Notes**
- Move `media.chapter_count` / `latest_chapter_added_at` maintenance out of the `Chapter`
  constructor into an explicit service method, with tests for add, delete and replace.

## Phase 4 — Plugins and fetching

Specs: `03-plugins-fetching.md`.

- `plugin-api` finalized; plugin loading with isolated classloaders; admin Plugins page.
- OkHttp fetcher with challenge detection, solver call, `domain_session` cache, per-domain
  throttling, cover download; `source.html` fallback and canonical URL detection.
- `example-plugin` + mock server serving `fixtures/html`.

**Done when**
- Tests: plugin selection by URL, canonical fallback, plugin picker path; direct fetch; simulated
  challenge → solver → cookies stored → next request reuses cookies without solver; solver failure
  → NEEDS_ATTENTION with the HTML instruction; retry with `source.html` succeeds; manifest values
  override and lock scraped fields.

## Phase 5 — Library UI

Specs: `07-frontend.md`, `06-images.md` (delivery), `01-data-model.md` (feeds).

- Image URLs in API responses, signed mode + public mode, nginx `secure_link` and rate limits.
- Home sections, browse with all filters, command-palette search, media detail page (read-only
  parts), user visibility filters (adult mode, hidden types, excluded tags).
- Infinite scroll with keyset pagination and scroll restoration.

**Done when**
- nginx tests (script or integration): valid signature 200, tampered 403, expired 410, public mode
  works, `kuratoon.yml` not reachable, rate limit returns 429.
- Playwright: browse → filter → open media → back restores filters and scroll position.
- Layout checked at 360px, 1920×1080 and 2560×1440 (screenshots in the report).

## Phase 6 — Reader and reading status

Specs: `05-reading-status.md`, `07-frontend.md` (reader).

- Reader with reserved layout, lazy-load/preload, floating panel, keyboard, end-of-chapter actions,
  deep links, resume.
- Progress saving, completion rule, read events, intent, all manual actions, Continue Reading,
  history, read first/latest.

**Done when**
- Service tests for every rule in `05-reading-status.md` (sticky read, new chapter → In progress,
  intent clearing and non-clearing cases, mark up to here, Continue Reading inclusion rules).
- Playwright: read halfway → leave → Continue Reading resumes at the same spot.

## Phase 7 — Personal features

- Favorites (media, chapters) with counters, ratings with aggregates, collections (CRUD, add/remove,
  sort/filter), profile pages, user settings (theme, adult mode, hidden types, excluded tags,
  reader width, password/email change).

**Done when**
- Per-user isolation tests (user A never sees or changes user B's data).
- Counter consistency tests (favorite_count, avg_rating, rating_count, chapters_read).

## Phase 8 — Moments

- Capture flow in the reader, crop + thumbnail generation, moments on chapter list and media
  gallery, deep link into the reader, submissions toggle, admin review queue, user's submitted
  moments page, moments in sidecar.

**Done when**
- Tests: admin moment auto-approved; user submission PENDING and invisible to others; approve/reject
  flows; rejected deletes images; submissions disabled → endpoint 403.
- Playwright: open moment → reader lands on the moment's page and offset.

## Phase 9 — Hardening and release

- Performance pass (query plans for browse/home with ~5k media fixtures, N+1 checks), read_event
  purge job, error pages, accessibility pass, README install guide with reverse proxy examples,
  `.env.example` review, fixture generator for a large library.

**Done when**
- Browse and home API p95 < 150 ms on the large fixture library (documented in the report).
- Fresh install following only the README works.
