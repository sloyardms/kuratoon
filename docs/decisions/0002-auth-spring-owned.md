# 0002 — Spring Boot owns auth; Auth.js is a thin session layer

**Status**: accepted

**Decision**: Spring Boot stores users and issues/verifies HS256 JWTs (short-lived) plus rotating
opaque refresh tokens. Next.js Auth.js uses a Credentials provider only to hold the session.
No external identity provider (Keycloak etc.).

**Why**: Role authority must live next to the authorization logic. Splitting it across two systems
adds complexity without benefit at this scale. A single shared secret is enough because Spring is
the only issuer and verifier.
