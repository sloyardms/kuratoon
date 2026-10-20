# Phase 1 — Schema and core domain

## What was built
- **Flyway migrations V2–V6** (V1 untouched) cover the full data model in `docs/spec/01-data-model.md`:
  - V2: users, sessions, settings, audit, preferences.
  - V3: library tables.
  - V4: per-user reading data.
  - V5: moments, import jobs, domain sessions.
  - V6: the seven seeded media types (fixed UUIDv7 ids, slugs, colors, sort order).

  Schema details:
  - Enums are `text` + `CHECK`.
  - Every index listed in spec 01 exists, including GIN trigram indexes on `media.title`,
    `media_title.title` and `creator.name`.
  - Character uniqueness uses `UNIQUE … NULLS NOT DISTINCT` (Postgres 15+).
  - The partial unique index allows one non-finished import job per `source_folder`
    (`WHERE finished_at IS NULL`).
- **Common infrastructure** (`dev.kuratoon.common`, `dev.kuratoon.audit`):
  - `UuidV7` generator. Ids are assigned by the application in entity constructors, and
    `BaseEntity` implements `Persistable` with an `isNew` flag reset in `@PostPersist`/`@PostLoad`.
  - `Names.normalize`: NFKC, trim, collapse whitespace, lowercase with `Locale.ROOT`.
  - `Slugs`: ASCII-fold, `[a-z0-9]+(-[a-z0-9]+)*`.
  - RFC 9457 Problem Details handler. `DataIntegrityViolationException` is mapped by SQLState:
    23505/23503 → 409, 23514 → 400, anything else → 500.
  - `OffsetPage`/`PageParams` (size ≤ 100) for admin tables.
  - `AuditService` writes `audit_log` in the caller's transaction (`Propagation.MANDATORY`).
- **Library entities and repositories**: media types, tags, creators, parodies, characters
  (`StoryCharacter`, table `character`), media (alternative titles, creators with role, tags,
  parodies, characters), relations, chapters (`pages` as jsonb `List<PageEntry(f,w,h)>`, mapped
  by Hibernate 7.4's Jackson 3 JSON format mapper).
- **Admin CRUD** under `/api/v1/admin/{media-types,tags,creators,parodies,characters}`:
  - list (offset paging, `q` filter, plus `kind` for tags and `parodyId` for characters), get,
    create (201 + `Location`), update (PUT), delete (204);
  - every mutation writes `audit_log`.
  - Delete rules:

    | Resource | When the delete is blocked (409) | What happens to links |
    |---|---|---|
    | media type | used by any media ("reassign first") | — |
    | creator | credited on any media | — |
    | tag | never | removed from media |
    | character | never | removed from media |
    | parody | its characters would duplicate an existing parody-less character | removed from media; its characters become parody-less |
- **Read endpoints**:
  - `GET /api/v1/media/{id}`: header data, authors/artists, genre and LIBRARY tags separately,
    parodies, characters with parody, relations with labels from both sides (SEQUEL/PREQUEL,
    SIDE_STORY/MAIN_STORY, SPIN_OFF/ORIGINAL), computed missing chapters, counters.
  - `GET /api/v1/media/{id}/chapters`: ascending, no pages; a projection query, so `pages`
    isn't loaded.
  - `GET /api/v1/chapters/{id}`: pages with dimensions.
  - Chapter numbers are rendered without trailing zeros (`12.5`, `10`).
- **Title search** (`MediaSearchService`) over `media.title` and `media_title.title`:
  - Queries of 3+ characters: escaped `ILIKE` substring match, or `q <% title` with
    `pg_trgm.word_similarity_threshold` pinned to **0.4** via `SET LOCAL`.
  - Queries of 1–2 characters: substring match only.
  - One hit per media, ranked by the best-matching title (word similarity, then similarity,
    then title).
  - No public endpoint yet (see Deviations).
- **nginx**: `/api/v1/admin/` returns 403 (Problem Details JSON) until phase 2 adds auth.
  CI smoke-tests this.
- **Test support**:
  - `ApiTestBase` (MockMvcTester + Testcontainers).
  - `DatabaseCleaner`: truncates all tables except `media_type`, then deletes non-seed media types.
  - `TestData` builders: `aMedia()` adds one AUTHOR by default; it is replaced when a creator is
    added explicitly or `withoutDefaultAuthor()` is called.

## Deviations from the spec
- **01 / Conventions, name normalization**: `normalized_name` also applies Unicode **NFKC** before
  lowercasing, so full-width or compatibility characters (`Ｏｄａ`) match their plain forms (`oda`).
  Spec 01 only says "lowercased, trimmed, collapsed whitespace".
- **01 / `media`, search**: title search exists as a tested service (`MediaSearchService`) without
  an HTTP endpoint. The command palette and browse (phase 5) will decide the URL, paging and
  visibility filters.
- **00 / audit**: `audit_log.actor_user_id` is null for all phase 1 mutations, because there is no
  authenticated user until phase 2.
- **Admin API exposure**: unauthenticated in the backend for this phase. It is blocked at nginx
  (403), and phase 2 removes that block.
- **Interpretations where spec 01 is silent** (not contradictions):
  - `media.media_type_id` and `media_creator.creator_id` are `ON DELETE RESTRICT` (deleting a
    type or creator in use → 409).
  - `user_hidden_media_type` and `user_excluded_tag` cascade when the type or tag is deleted.
  - `media_creator`'s primary key includes `role`, as listed.
  - A parody delete is refused when it would duplicate a parody-less character.
  - Slug format is `[a-z0-9]+(-[a-z0-9]+)*`, and an empty derived slug (non-Latin name) → 400
    unless an explicit slug is given.
  - Extra CHECKs:
    - `app_user.username ~ '^[a-zA-Z0-9_.-]{3,32}$'`;
    - `media_type.color` is hex `#rrggbb`;
    - slug formats;
    - `user_preference.reader_max_width` 400–2000;
    - `chapter_progress.page_offset` 0–1, `page_index ≥ 0`;
    - `chapter.number ≥ 0`;
    - `chapter.pages` is a JSON array with `page_count = jsonb_array_length(pages)` (CASE-guarded);
    - `moment.title` 1–80 characters;
    - `user_media_state.chapters_read ≥ 0`.
  - Added indexes beyond spec 01's minimum: `refresh_token(user_id)`,
    `refresh_token(family_id)`, `password_reset_token(user_id)`, `media_creator(creator_id)`,
    `media_parody(parody_id)`, `media_character(character_id)`, `media_relation(to_media_id)`.
    They back the in-use checks, cascades and two-way relation lookups.

FK behaviours that spec 01 states explicitly (`import_job.media_id`,
`user_media_state.last_read_chapter_id` and `character.parody_id` SET NULL; the CASCADEs) are
implemented as written and are not deviations.

## Spec files that may need updating
- **`04-auth-users.md` / Transport** still says `server.forward-headers-strategy=framework`,
  trusting `TRUSTED_PROXIES`. Since the phase 0 review, `08-deployment.md` specifies Tomcat
  `native` with trust limited to `INTERNAL_SUBNET`, with `TRUSTED_PROXIES` used by nginx only.
  04 should be aligned. I did not edit it.
- **ADR 0010** names the page keys `file/width/height`; spec 01 uses `f/w/h`. I followed spec 01
  (the column contract).
- **`01-data-model.md` / `import_job`**: "non-finished" is implemented as `finished_at IS NULL`.
  Spec 02 lists a "Dismiss" action but no `DISMISSED` status; phase 3 has to decide whether
  dismiss means `finished_at` plus the current status, or a new status (that would need a
  migration to widen the CHECK).
- **`01-data-model.md` / creators**: whether a creator in use may be deleted is unspecified
  (I chose 409). A "merge creators" action might be wanted later; it is not in the spec, so it
  was not built.

## Tests
- Added:
  - `SchemaContractIT` (6 tests): every table's column names, types and nullability against
    spec 01; required indexes (trigram GIN, partial unique); seeds; NULL-parody character
    uniqueness; the `pages`/`page_count` CHECK; one active import job per folder.
  - `MediaTypeAdminIT` (9 tests): list order and paging, `q`; derived slug + audit; non-Latin
    name needs an explicit slug; validation errors; **duplicate name/slug → 409**; update
    duplicate checks; delete unused; **deleting a type in use → 409**; 404s.
  - `TagAdminIT` (6 tests): kind filter; **same slug allowed across kinds, 409 within a kind**;
    non-Latin slug rule; validation; moving kind; delete cascades `media_tag`.
  - `CreatorAdminIT` (4 tests): CRUD + audit; normalized duplicate → 409; blank → 400;
    **credited creator delete → 409**.
  - `ParodyAndCharacterAdminIT` (6 tests): parody CRUD; character uniqueness per parody,
    including NULL parody; unknown parody → 400; parody delete detaches characters; colliding
    parody delete → 409; character delete cascades.
  - `MediaReadIT` (5 tests): full detail; relation labels from all sides; chapter list order
    without pages; chapter pages; 404/400.
  - `MediaSearchIT` (8 tests): main title; alternative-title-only match; typo within the
    **pinned 0.4 threshold** (asserts the similarity is between 0.4 and 0.6, so it only matches
    because the threshold is pinned); below threshold rejected; ranking + one hit per media;
    **1–2 character queries** ("Na", "bl", "zq"); escaped `%`/`_`; blank query and limit.
  - Unit tests:
    - `UuidV7Test`: version and variant, timestamp, 50k strictly increasing ids.
    - `NamesAndSlugsTest`: NFKC, `Locale.ROOT`, empty slugs.
    - `MissingChaptersTest`: gaps, decimals, display numbers.
    - `ApiExceptionHandlerTest`: SQLState mapping through the cause chain.
- Status: `./mvnw verify` passes all **102 tests** (2 plugin-api, 49 unit, 51 integration).
  A CI-equivalent compose run (from a clean LF export):
  - all services healthy;
  - Flyway V1–V6 applied on an empty database;
  - `/api/v1/admin/*` → 403 through nginx (and working from inside the network);
  - read endpoints → 404 Problem Details for unknown ids.

  **Not yet run in GitHub Actions**: phase 0's post-review fixes and all of phase 1 are
  committed locally but not pushed.

## Known issues and shortcuts
- The nginx admin block is temporary. Phase 2 must remove it together with adding Spring Security.
- The "at least one AUTHOR or ARTIST" invariant is not enforced yet, because phase 1 has no media
  write path. It belongs in the phase 3 media write services (ingestion commit, manual metadata
  entry, media edit). The test builder respects it by default.
- Denormalized counters: `chapter_count` and `latest_chapter_added_at` are maintained by
  `Chapter`'s constructor via `Media.chapterAdded`. Rating and favorite counters stay 0 until
  phase 7 writes them.
- Package cycle: `chapter.ChapterRepository` uses `media.MediaReadDtos.ChapterSummary`. It is
  harmless, but could be tidied by moving the chapter DTOs into `chapter`.
- Search ranking ties among titles that are equally good matches fall back to alphabetical
  order. Phase 5 may want a different secondary sort (e.g. popularity).

## Open questions for the owner
- Dismissed import jobs: `finished_at` with the existing status, or a new `DISMISSED` status
  (phase 3)?
- Should creators/parodies get a "merge" admin action later? Not in the spec, so not built.

## How to verify manually
1. `cd backend && ./mvnw verify` (Docker required).
2. Start the stack (see the phase 0 report) and run:
   - `curl -i localhost:8080/api/v1/admin/media-types` → 403 Problem Details.
   - `docker compose -f deploy/docker-compose.yml --env-file deploy/.env exec backend curl -s
     localhost:8080/api/v1/admin/media-types` → the seven seeded types.
   - Inside the backend container, POST `{"name":"Manga","slug":"manga-2","color":"#000000"}` to
     `/api/v1/admin/media-types` → 409.
3. `psql` → `\d media`, `\d character` (look for `NULLS NOT DISTINCT`) and
   `select * from flyway_schema_history`.
