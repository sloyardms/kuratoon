# 0013 — Backend-issued httpOnly cookie sessions; no Auth.js

**Status**: accepted (supersedes 0002)

**Decision**: Spring Boot stores users and issues/verifies HS256 JWTs (15 min) plus rotating opaque
refresh tokens (30 days). Both are delivered as `httpOnly`, `SameSite=Lax` cookies on `Path=/`
(`Secure` when `APP_BASE_URL` is https). Next.js uses no session library: the browser calls `/api`
with credentials and does a single-flight refresh on 401; server rendering forwards the incoming
cookies, and `proxy.ts` refreshes an expiring access token before rendering. Refresh rotation has a
~10 s grace window so concurrent refreshes with the same token get the same successor pair. No
external identity provider.

**Why**: Auth.js v5 never left beta and the project moved to security-only maintenance, so it is
no longer a sound base. With cookies issued by the backend, the browser never sees a token in
JavaScript, role authority stays next to the authorization logic, and the frontend needs only a
thin fetch wrapper and a proxy. The grace window avoids logging users out when parallel requests
race to refresh, while reuse outside the window still revokes the family.
