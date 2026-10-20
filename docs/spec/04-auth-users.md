# 04 — Auth, users and settings

## Architecture

- Spring Boot owns users, passwords, roles and tokens. It is the only issuer and verifier of JWTs.
- Next.js uses Auth.js with a Credentials provider as a thin session layer: it calls the backend
  login endpoint and keeps the backend's tokens; it never decides authorization.
- All authorization is enforced in Spring Boot.

## Tokens

- **Access token**: JWT, HS256 with a single secret (`JWT_SECRET`, ≥ 32 bytes), lifetime 15 min.
  Claims: `sub` (user id), `role`, `tv` (token_version), `exp`, `iat`.
  Rejected if `tv` ≠ current `app_user.token_version` or the user is disabled.
- **Refresh token**: opaque random 256-bit value, stored hashed (`refresh_token`), lifetime 30 days,
  delivered in an `httpOnly`, `Secure` (when HTTPS), `SameSite=Lax` cookie.
  Rotated on every use. Reuse of an already-rotated token revokes the whole family.
- Logout revokes the current refresh token. "Log out everywhere" bumps `token_version`.

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

The app assumes HTTPS is terminated by the installer's reverse proxy. Spring uses forwarded
headers (`server.forward-headers-strategy=framework`) only from `TRUSTED_PROXIES`. Security headers
(CSP, X-Content-Type-Options, Referrer-Policy, frame-ancestors none) are set by the internal nginx.
