# Phase 2 — Auth, users, settings

## What was built
- **Docs, owner-authorized**:
  - Spec 04 "Architecture"/"Tokens" rewritten for backend-issued httpOnly cookies.
  - ADR 0013 ("Backend-issued httpOnly cookie sessions; no Auth.js") supersedes ADR 0002.
  - `frontend/CLAUDE.md` and the roadmap phase 2 line no longer mention Auth.js.
  - Earlier in this phase: 02 DISMISSED status with `.dismissed-<name>-<jobId>`, sidecar chapter
    `created_at`, the roadmap phase 3 note, and 04 "Transport" aligned with 08.
- **Security core** (`dev.kuratoon.auth`):
  - Stateless Spring Security 7 resource server, deny by default.
  - The HS256 access JWT (claims `sub, role, tv, iat, exp`, 15 min) comes from the
    `kuratoon_access` cookie or an `Authorization: Bearer` header.
  - A per-request validator rejects disabled users and stale `token_version`.
  - The timestamp validator has zero clock skew.
  - 401 and 403 responses are Problem Details.
  - Public endpoints never read a token, so an expired cookie can't block login or refresh.
- **Cookies**: `kuratoon_access` (15 min) and `kuratoon_refresh` (30 days):
  - both `HttpOnly; SameSite=Lax; Path=/`;
  - `Secure` when `APP_BASE_URL` is https;
  - the refresh cookie is read only by `/api/v1/auth/*`.
- **CSRF**: `OriginCheckFilter` covers every non-GET request:
  - the Origin must equal the `APP_BASE_URL` origin (default ports normalized);
  - a request without an Origin header is allowed only if it carries no auth cookies (tools and
    tests using Bearer).
- **Refresh rotation** (`RefreshTokenService`):
  - Tokens are opaque 256-bit values, stored as SHA-256 hashes.
  - Rotation runs under `SELECT … FOR UPDATE`.
  - The successor pair is cached for the 10 s grace window **before commit** and evicted by a
    `TransactionSynchronization` on rollback.
  - Reuse inside the window returns the same pair.
  - A cache miss inside the window → 401 without revoking anything.
  - Reuse outside the window revokes the whole family.
  - Logout revokes the current token and clears both cookies. Log out everywhere bumps
    `token_version` and revokes all refresh tokens.
- **Login**:
  - It always verifies a password hash first (a dummy Argon2 hash for unknown users), then checks
    `enabled`.
  - Wrong password, unknown user and disabled user get byte-identical 401s.
  - Usernames and emails are `citext`; lookups cast explicitly.
- **Rate limits**:
  - Backend `AuthRateLimiter`, per IP: every login, register, setup and reset attempt counts
    (burst 10, 5/min).
  - Backend, per username: failed logins only (same bucket size). While that bucket is empty,
    IPs that failed for that username in the last 15 min get 429 *before* verification; fresh IPs
    are still verified.
  - 429 responses include `Retry-After`.
  - nginx adds a coarse per-IP zone on `/api/v1/auth/*` from `RATE_LIMIT_AUTH_RPM`/`_BURST`.
    `GET /api/v1/auth/status` is excluded, because every auth page requests it.
- **First run**:
  - `GET /api/v1/auth/status` returns `{setupRequired, registrationEnabled, passwordResetEnabled}`.
  - `POST /api/v1/auth/setup` runs under a Postgres advisory lock, so only one admin can ever be
    created.
  - The `ADMIN_*` bootstrap implements all three spec cases. Force reset sets the password, email,
    ADMIN role and enabled flag, bumps tv and revokes refresh tokens. It logs a WARN on every boot
    while the flag is true.
  - Property validation fails startup on a bad admin username, a short password, or force reset
    without credentials.
- **Registration**: `POST /api/v1/auth/register` returns 403 while `registration.enabled` is off.
  Otherwise it creates a USER (409 on a taken username or email, checked case-insensitively).
- **Password reset**:
  - SMTP is configured from `SMTP_*`, with a connection check at startup (5 s timeouts). Reset is
    available only if that check passed; otherwise the endpoints return 404.
  - `request` always returns the same 202 body. The token is random, hashed, valid for 30 min and
    single use. The mail is sent after commit on the task executor.
  - `confirm` sets the password, bumps tv, revokes refresh tokens and marks all of the user's
    outstanding links used.
- **Users admin** (`/api/v1/admin/users`):
  - list with search and offset paging, create, PATCH role/enabled/email, set password, revoke
    sessions, delete (personal data cascades);
  - role changes and disabling end the user's sessions, because the role claim would otherwise be
    stale for up to 15 min;
  - self delete and self demote are refused;
  - the last enabled admin can't be disabled, demoted or deleted;
  - every mutation is audited with the acting admin.
- **Settings and status**: `GET/PUT /api/v1/admin/settings` (registration, moment submissions;
  audited only when something changes). `GET /api/v1/admin/status` reports SMTP, solver
  reachability (2 s), plugins (empty until phase 4), image access mode, and the app, Java, Spring
  Boot and PostgreSQL versions.
- **Audit log**:
  - `GET /api/v1/admin/audit-log`, newest first, filters on action, target type and actor, offset
    paging, actor username joined;
  - `AuditService` now records the authenticated user (null for system actions such as the
    bootstrap).
- **Frontend**:
  - `src/proxy.ts`:
    - while setup is required, every route goes to `/setup`;
    - `/setup` is closed afterwards;
    - an access cookie that is missing or expires in < 60 s is refreshed internally, and the
      backend's `Set-Cookie` headers go to the browser while the new cookies are written into the
      forwarded request's `Cookie` header;
    - with no session it redirects to `/login?next=`;
    - non-admins are kept out of `/admin/*`;
    - the matcher excludes `/_next/static`, `/_next/image`, `/img/`, `/api/` and `favicon.ico`.
  - `lib/api.ts`: `apiFetch` sends `credentials: 'include'`, shares one refresh among concurrent
    401s, retries once, and goes to `/login` when the refresh fails.
  - Pages: `/setup`, `/login`, `/register` (only shown when enabled), `/forgot-password` and
    `/reset-password` (only when SMTP works).
  - App shell top bar: logo, Browse placeholder, Admin menu for admins, user menu with log out and
    log out everywhere.
  - Admin pages:
    - `/admin/users`: table with every action, plus create and set-password dialogs and a delete
      confirmation;
    - `/admin/settings`: toggles and the status panel;
    - `/admin/audit-log`: filters and paging in the URL.
  - TanStack Query is the server-state layer (gcTime 10 min).
  - `next dev` rewrites `/api` to the backend; in production nginx routes it.
- **Deploy and CI**:
  - The temporary nginx admin block is gone.
  - The frontend gets `BACKEND_INTERNAL_URL=http://backend:8080` (compose wiring, not an installer
    variable).
  - `SMTP_PORT` and `SMTP_TLS` have real defaults (an empty string can't bind to an int or a
    boolean).
  - `.env.example` documents that `APP_BASE_URL` is the only supported access URL.
  - The CI docker job runs Playwright against the compose stack; the frontend job adds
    `format:check`.

### New pinned versions
| Component | Version |
|---|---|
| Spring Security | 7.1.1 (Spring Boot BOM) |
| Bouncy Castle `bcprov-jdk18on` (Argon2id) | 1.86 |
| Caffeine | 3.2.4 (BOM) |
| Angus Mail | 2.0.5 (BOM) |
| GreenMail (tests) | 2.1.14 |
| @tanstack/react-query | 5.104.5 |
| sonner / next-themes (via shadcn) | 2.0.8 / 0.4.6 |
| server-only | 0.0.1 |

## Deviations from the spec
- **04 / Tokens, refresh reuse**: a ~10 s grace window replaces strict "any reuse revokes the
  family", as the owner directed. Reuse inside the window returns the same successor pair; outside
  it, the family is revoked.
- **04 / Tokens, grace cache**: the cache is in-memory, which assumes a single backend instance.
  A rotated token reused inside the window but missing from the cache (e.g. after a backend
  restart) gets a 401 **without** revoking the family. The client's parallel request that already
  holds the successor keeps working.
- **04 / Passwords, rate limits**: the spec example ("per IP and per username, e.g. 5/min,
  burst 10") is implemented as burst 10 and 5/min refill. The per-username bucket counts
  **failed logins only** and blocks only IPs that already failed for that username, as the owner
  directed. A distributed attacker therefore gets one guess per fresh IP per 15 min, on top of the
  per-IP limit and the Argon2 cost.
- **08 / `SMTP_TLS`** values were unspecified: `true` means STARTTLS required, anything else a
  plain connection. Implicit TLS (port 465) is not supported.
- **04 / Registration**: a taken username or email gets 409, which reveals that it exists.
  Spec 04's anti-enumeration rule covers login and reset only.
- **04 / Users page**: changing a user's role, or disabling them, also ends their sessions. This
  isn't in the spec, but without it a demoted admin would keep admin rights until the access
  token expires.
- **Secure cookies** are decided by the `APP_BASE_URL` scheme, not the request scheme.
- **Roadmap phase 2 line** edited to say "cookie-based auth integration (ADR 0013)" instead of
  Auth.js, following the owner's decision to drop Auth.js.
- **Server rendering**: the phase 2 pages fetch on the client (TanStack Query). `proxy.ts` still
  refreshes cookies before rendering, and `lib/server-api.ts` (cookie forwarding to
  `BACKEND_INTERNAL_URL`) is in place for later server-rendered pages. Reading `cookies()` in
  layouts would have required Suspense boundaries everywhere under Next 16 Cache Components,
  for no benefit in this phase.

## Spec files that may need updating
- **08 / Environment variables**: `BACKEND_INTERNAL_URL` (frontend → backend, set by compose) is
  not in the table. It is internal wiring, but it could be listed for people running the frontend
  outside compose.
- **04 / Users page**: say whether disabling yourself is allowed. Currently it is, unless you are
  the last enabled admin; the spec only forbids deleting or demoting yourself.
- **04 / Rate limits**: document the failed-login-only per-username rule and the nginx exclusion of
  `/api/v1/auth/status`.
- **07 / Pages**: `/admin/audit-log` exists now. It is listed in 07's admin section but no roadmap
  phase covered it (built here at the owner's request).

## Tests
- Backend: `./mvnw verify` passes. The integration suite has 129 tests; the 2 that first failed
  (both test mistakes) were fixed and re-run green.
  - New integration tests:
    - `LoginAndTokenIT`: cookie attributes, cookie vs Bearer, identical 401s for wrong
      password/unknown/disabled, case-insensitive username, expired and forged JWTs, bumped
      token_version, disabled user's live token, refresh cookie ignored outside auth, expired
      access cookie not blocking refresh, access rules.
    - `RefreshIT`: rotation; reuse inside the window gives the same pair; **6 concurrent refreshes
      with the same token** get the same pair with no revocation; cache miss inside the window →
      401 without revocation; reuse after 11 s revokes the family; other families survive;
      rollback evicts the cached pair; expired, unknown or missing tokens; logout.
    - `OriginCheckIT`: foreign Origin, matching Origin, missing Origin with cookies, Bearer
      without Origin, GET exempt.
    - `RateLimitIT`: per-IP limit; registration and reset count; failed IP locked once the
      username bucket is empty; **a flood from other IPs doesn't block the real user from a fresh
      IP**; successes don't consume the username bucket.
    - `SetupAndRegistrationIT`: setupRequired lifecycle; setup refused once an admin exists;
      **concurrent setups → exactly one admin**; registration disabled → 403 even when called
      directly; enabled registration; validation; reset endpoints 404 without SMTP.
    - `AdminBootstrapIT`: create, leave alone, force reset (sessions ended), WARN on every boot,
      not configured.
    - `PasswordResetIT` (GreenMail): identical 202s for existing and unknown emails; the link
      works once and ends sessions; expiry; short password; disabled users get no mail.
    - `SecureCookieIT`: https base URL → `Secure` cookies; other origins rejected.
    - `UsersAdminIT`: list/search/paging; create and duplicates; role change and disable end
      sessions; set password and revoke sessions; delete cascades; self delete/demote refused;
      **last enabled admin can't be disabled**; **two admins demoting each other concurrently
      leave exactly one admin**; USER → 403.
    - `SettingsAndAuditIT`: settings defaults and audit; status panel; audit log order, filters,
      actor, paging; USER → 403.
    - `AccessRulesIT`: every admin endpoint 401/403/200; read endpoints need a login; everything
      outside `/api/v1` is denied.
  - Updated: `ApiTestBase` sends a fresh ADMIN's Bearer token by default and has a `TestClock`.
    `ForwardedHeadersIT` and `HealthAndSchemaIT` were adapted to the auth rules.
  - Unit: `KuratoonPropertiesTest` gains admin, SMTP and origin-normalization cases.
- Frontend: lint, typecheck, `format:check` and **17 Vitest tests** pass:
  - `proxy-decision.test.ts`, including "expiring in < 60 s → refresh";
  - `api.test.ts`: 3 concurrent 401s → exactly one refresh, each request retried once; a failed
    refresh → login;
  - `audit-filters.test.tsx`.

  `next build` succeeds.
- **Playwright** (`e2e/auth.spec.ts`) passes against a fresh compose stack built from a clean LF
  export, as in CI: first-run setup → setup closed afterwards → wrong-password error → login →
  httpOnly/Lax cookies not visible to `document.cookie` → admin Users page → log out (cookies
  cleared) → protected page redirects to `/login?next=`.
- **Manual checks against the stack**:
  - A page request with only the refresh cookie returns 200, with new cookies carrying the
    backend's attributes.
  - The same token again (inside the grace window) also returns 200.
  - A bogus refresh token redirects to `/login?next=…` and clears both cookies.
  - The nginx auth limit returns 429; `/auth/status` is not limited.
- **CI**: not yet run on these commits (they are local; see the end of this report).

## Known issues and shortcuts
- Rate-limit buckets and the grace cache live in memory: they reset on restart and assume one
  backend instance.
- Expired and revoked `refresh_token` rows, and used reset tokens, are never purged. That is a
  small table at this scale; a cleanup job could join the phase 9 `read_event` purge.
- The audit log "Actor" filter lists the first 100 users. That is enough for an owner-and-friends
  install.
- The Browse link in the top bar points at the home page until phase 5.
- Dev setup: with `next dev`, set the backend's `APP_BASE_URL=http://localhost:3000` (the Origin
  check), and run the backend locally or set `BACKEND_INTERNAL_URL`.

## Open questions for the owner
- Should an admin be allowed to disable themselves (when another admin remains)? The spec is
  silent; it is currently allowed.
- Should old refresh and reset tokens get a purge job (phase 9 with the `read_event` purge)?

## How to verify manually
1. `cd backend && ./mvnw verify`
2. `cd frontend && npm ci && npm run lint && npm run typecheck && npm test`
3. Fresh stack (empty `DATA_ROOT`):
   - `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build --wait`
   - `cd frontend && BASE_URL=http://localhost:8080 npm run test:e2e`
4. In a browser at `APP_BASE_URL`:
   - `/` redirects to `/setup`; create the admin, then log in.
   - Devtools: both cookies are HttpOnly, SameSite=Lax, Path=/.
   - Users page: create a USER, then log in as them in another browser. `/admin/users` sends them
     to `/`, and the API returns 403.
   - "Log out everywhere" in one tab logs the other tab out on its next request.
5. Optional SMTP: set `SMTP_HOST`/`SMTP_FROM`. The status panel shows "Working" and "Forgot
   password?" appears on `/login`.
