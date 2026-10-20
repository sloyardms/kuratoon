# Backend — conventions

## Stack

- Java: latest LTS supported by the chosen Spring Boot version. Pin versions in the parent POM and
  record them in the phase 0 report.
- Spring Boot (Web, Security, Data JPA, Validation, Actuator), Flyway, PostgreSQL (`pg_trgm`).
- OkHttp for outbound HTTP (metadata fetching), jsoup for HTML parsing.
- libvips (CLI `vips` in the container) for thumbnails and crops. Do not use ImageIO for WebP.
- Argon2id password hashing (Spring Security `Argon2PasswordEncoder`, Bouncy Castle).
- Testing: JUnit 5, AssertJ, Spring Boot Test, Testcontainers (PostgreSQL). No H2.
- Maven with the wrapper committed (`./mvnw`). Never rely on a globally installed Maven.

## Modules

- `plugin-api`: plain Java, depends only on jsoup. Contains `MetadataProvider` and the DTOs.
  Must never depend on Spring or on `app`.
- `app`: the Spring Boot application.
- `example-plugin`: depends on `plugin-api` only; built as a JAR used by integration tests.

## Commands

- Build + all tests: `./mvnw verify`
- Run app (needs dev compose up): `./mvnw -pl app spring-boot:run`
- Single module tests: `./mvnw -pl app test`

## Code conventions

- Package by feature, not by layer: `media`, `chapter`, `ingestion`, `plugin`, `fetch`, `auth`,
  `user`, `reading`, `collection`, `moment`, `image`, `settings`, `audit`.
- Controllers are thin; business rules live in services. Entities are not returned from controllers;
  map to DTO records.
- REST under `/api/v1`. JSON, camelCase. Errors use RFC 9457 Problem Details.
- List endpoints for user-facing feeds use keyset (cursor) pagination; admin tables use offset
  pagination. See `docs/spec/07-frontend.md` and `01-data-model.md`.
- Use `@Transactional` at service level. File moves happen outside DB transactions following the
  copy → commit → delete order in `docs/spec/02-ingestion.md`.
- Configuration via `@ConfigurationProperties` bound from environment variables
  (see `docs/spec/08-deployment.md`). No hardcoded paths.
- Time: store `timestamptz`, use `Instant` in Java.
- Logging: SLF4J, structured where useful. Never log passwords, tokens or cookies.
