# 04 — Auth, users and settings

## Architecture

- Spring Boot owns users, passwords, roles and tokens. It is the only issuer and verifier of JWTs
  (ADR 0013).
- The backend delivers both tokens as `httpOnly` cookies. There is no session library in Next.js:
  - **Browser**: TanStack Query calls `/api` directly with `credentials: 'include'`. On a 401 it runs
    a single-flight refresh (`POST /api/v1/auth/refresh`), retries the request once, and navigates
    to `/login` if the refresh fails.
  - **SSR**: Next.js forwards the incoming cookies to the backend's internal URL. `proxy.ts`
    refreshes when the access cookie is missing or expires in less than 60 s, and sets the new
    cookies on both the response and the forwarded request, so rendering in the same request
    already sees the new access token.
- Next.js never decides authorization. All authorization is enforced in Spring Boot.

## Tokens

- **Access token**: JWT, HS256 with a single secret (`JWT_SECRET`, ≥ 32 bytes), lifetime 15 min.
  Claims: `sub` (user id), `role`, `tv` (token_version), `exp`, `iat`.
  Rejected if `tv` ≠ current `app_user.token_version` or the user is disabled.
  Delivered in the `kuratoon_access` cookie (`Path=/`, 15 min). The backend reads it from that
  cookie or from an `Authorization: Bearer` header (tests and tools).
- **Refresh token**: opaque random 256-bit value, stored hashed (`refresh_token`), lifetime 30 days,
  delivered in the `kuratoon_refresh` cookie (`Path=/`, 30 days). The backend ignores it everywhere
  except `/api/v1/auth/*`.
- Both cookies are `httpOnly`, `SameSite=Lax`, and `Secure` when `APP_BASE_URL` is https.
- Rotated on every use. Reusing the just-rotated token within a **~10 s grace window** returns the
  same successor pair, which covers parallel requests and prefetches refreshing at the same time.
  Reuse outside the window revokes the whole family.
- **CSRF**: `SameSite=Lax` plus an Origin check on every non-GET request. Origin must match the
  `APP_BASE_URL` origin; `APP_BASE_URL` is the only supported access URL.
- Logout revokes the current refresh token and clears both cookies. "Log out everywhere" bumps
  `token_version`.

## Passwords

- Argon2id. Minimum length 10. No composition rules.
- Rate limiting on `/api/v1/auth/*` (login, register, reset): per IP and per username
  (e.g. 5/min, burst 10), enforced in the backend; nginx adds a coarse per-IP limit.
- Login and reset responses never reveal whether a username/email exists (anti-enumeration).

## First run

- If no ADMIN exists and no admin env vars are set, every route redirects to `/setup`, where the
  first admin is created (username, optional email, password). Then `setup.completed = true`.
- Env bootstrap (`ADMIN_USERNAME`, `ADMIN_PASSWORD`, optional `ADMIN_EMAIL`):
  - If no user with `ADMIN_USERNAME` exists → create it as ADMIN (setup wizard is skipped).
  - If it exists and `ADMIN_FORCE_RESET=true` → reset its password, set email if `ADMIN_EMAIL` is
    given, force role ADMIN, set enabled, bump `token_version`. Log a WARN on every boot while the
    flag is true ("remove ADMIN_FORCE_RESET after use").
  - If it exists and the flag is false → do nothing.

## Registration and password reset

- Self-registration is controlled by `registration.enabled` (admin setting, default off).
  Enforced server-side; the UI only hides the link.
- SMTP is configured only via env (`SMTP_*`). At startup the app verifies the connection; if it
  works, "Forgot password" is enabled. If not configured or failing, the link is hidden and the
  endpoint returns 404.
- Reset tokens: random, hashed, 30 min lifetime, single use. Using one bumps `token_version`.
- Without SMTP, admins reset passwords from the Users page.

## Users page (admin)

List with search and offset pagination. Actions: create user, edit role, enable/disable,
reset password (admin sets a new one), revoke sessions, delete (cascades all personal data).
An admin cannot delete or demote themselves, and the last admin cannot be removed or disabled.

## Settings page (admin)

- Registration enabled.
- Moment submissions enabled.
- Read-only status panel: SMTP status, solver reachable, loaded plugins, image access mode,
  versions.

## User settings

Theme (dark/light/system), adult mode (hide/blur/show), hidden media types, excluded tags,
reader max width, change password, change email, log out everywhere.

## Permissions summary

| Action | User | Admin |
|---|---|---|
| Read library, reader | ✓ | ✓ |
| Own progress, favorites, ratings, collections, preferences | ✓ | ✓ |
| Submit moment (when enabled) | ✓ | ✓ (auto-approved) |
| Edit/delete media, chapters, metadata, covers | | ✓ |
| Imports, plugins view, maintenance actions | | ✓ |
| Media types, tags, creators, parodies, characters CRUD | | ✓ |
| Review/delete moments | | ✓ |
| Users and settings | | ✓ |

## Audit

Every admin mutation writes `audit_log` (actor, action, target, details). Admin page lists it
with filters (newest first, offset pagination).

## Transport

The app assumes HTTPS is terminated by the installer's reverse proxy. Forwarded headers are
handled as in `08-deployment.md`:

- `TRUSTED_PROXIES` is consumed by the internal nginx only (`set_real_ip_from`). nginx overwrites
  `X-Forwarded-For` / `X-Real-IP` with the resolved client address before proxying.
- The backend uses Tomcat's native forwarded-header handling (`server.forward-headers-strategy=native`)
  and trusts those headers only from `INTERNAL_SUBNET` (the compose network), never from arbitrary
  private ranges. Per-IP rate limits therefore see the real client address.

Security headers (CSP, X-Content-Type-Options, Referrer-Policy, frame-ancestors none) are set by the
internal nginx.
