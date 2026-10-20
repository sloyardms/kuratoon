# 0007 — No reverse proxy or TLS shipped

**Status**: accepted

**Decision**: Compose contains only the app (postgres, byparr, backend, frontend, internal nginx).
The internal nginx is part of the app (routing, image serving, secure_link, rate limits) and exposes
one port. TLS, domains and edge proxying are the installer's responsibility. The app supports
running behind a proxy via `APP_BASE_URL`, `TRUSTED_PROXIES` and forwarded headers.

**Why**: Every self-hoster already has their own proxy setup; shipping one would conflict with it.
