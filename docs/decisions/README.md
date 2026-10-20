# Architecture decision records

Short records of non-obvious choices. Do not reverse a decision without the owner's approval;
propose a new ADR instead.

| # | Decision |
|---|---|
| 0001 | Monorepo with Maven backend and npm frontend |
| 0002 | ~~Spring Boot owns auth; Auth.js is a thin session layer~~ (superseded by 0013) |
| 0003 | Plugins only parse; core fetches; no hot reload; zero bundled providers |
| 0004 | UUIDv7 IDs and self-describing library folders (sidecars) |
| 0005 | Media read status is derived from chapters |
| 0006 | Signed image URLs via nginx `secure_link` |
| 0007 | No reverse proxy or TLS shipped |
| 0008 | Collections only, no ordered reading lists |
| 0009 | One language per series; vertical reader only |
| 0010 | Chapter pages stored as JSONB on the chapter row |
| 0011 | Folder polling instead of inotify |
| 0012 | Moments are curated and public, with optional user submissions |
| 0013 | Backend-issued httpOnly cookie sessions; no Auth.js |
