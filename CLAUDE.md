# Kuratoon — instructions for Claude Code

Kuratoon is a self-hosted library and reader for manga, manhwa, manhua, comics, webtoons,
doujinshi and one-shots. The owner imports image folders; metadata is filled in from online
sources through parser plugins. Small user base (owner + friends), but built to production
standards: no "it's fine because it's just me" shortcuts.

## Repository layout

```
backend/    Maven multi-module (parent POM)
  plugin-api/      interface + DTOs for metadata plugins (no dependency on app/)
  app/             Spring Boot application
  example-plugin/  parses the fake site in fixtures/html (tests + reference plugin)
frontend/   Next.js (App Router) + npm
deploy/     docker-compose.yml, docker-compose.dev.yml, .env.example, nginx/
docs/       spec/, decisions/, reports/, roadmap.md
fixtures/   synthetic test data (import folders, fake HTML pages, image generator)
```

Each project has its own `CLAUDE.md` with stack-specific conventions. Read it when working there.

## Source of truth

1. `docs/spec/*.md` define WHAT to build. `docs/decisions/*.md` (ADRs) explain WHY.
2. `docs/roadmap.md` defines the phases and their acceptance criteria.
3. Work on one phase at a time. Read the phase in the roadmap and every spec file it references
   before planning.

## Hard rules (never break these without explicit instruction)

- **Do not edit `docs/spec/` or `docs/decisions/` on your own.** If the spec is wrong, unclear or
  impossible, either stop and ask, or implement the closest reasonable interpretation and list it
  under "Deviations" in the phase report. Spec edits happen only when the owner tells you to.
- **Do not add features not in the spec**, and do not re-add anything listed as scrapped in
  `docs/spec/00-overview.md`.
- **Schema changes only through Flyway migrations.** Never edit a migration that has been
  committed in a previous phase; add a new one.
- **IDs are UUIDv7** for every entity that appears in file paths, sidecars or URLs.
- **Plugins only parse.** The core fetches HTML; plugins receive a parsed document and return DTOs.
  No HTTP, cookies or solver logic inside plugins. Plugins load at startup only (no hot reload).
- **The core ships zero real metadata providers.** `example-plugin` targets only the fake site in
  `fixtures/html/`.
- **Media read status is always derived from chapter progress.** Never store a media-level
  read/unread flag. "Mark as read/unread" writes chapter records.
- **Never commit real manga/comic images or real site HTML.** Fixtures are synthetic.
- **No secrets in the repo.** Configuration comes from environment variables; `.env.example`
  documents every variable.
- **Tests are required.** Each phase's acceptance criteria must be covered by automated tests
  where technically possible. Write the tests for the acceptance criteria first, then implement.
- **Keep the build green**: backend tests, frontend lint/typecheck/tests and CI must pass before
  you report a phase as done.

## Workflow per phase

1. Read `CLAUDE.md` files, the roadmap phase and referenced spec files.
2. Propose a plan (plan mode) and wait for approval.
3. Implement in small, reviewable commits with clear messages (`phase-N: <what>`).
4. Run all tests and checks.
5. Write the phase report to `docs/reports/phase-N.md` using the template below, and print it
   as your final message.

## Phase report template

```markdown
# Phase N — <name>

## What was built
- ...

## Deviations from the spec
<!-- Every place where the implementation differs from docs/spec, and why. "None" if none. -->
- **<spec file / section>**: <what differs> — <reason>

## Spec files that may need updating
<!-- Things you found wrong, missing or ambiguous in the spec. Do NOT edit them yourself. -->
- ...

## Tests
- Added: ...
- Status: all passing / failures (explain)

## Known issues and shortcuts
- ...

## Open questions for the owner
- ...

## How to verify manually
1. ...
```

## Commands

- Dev dependencies (Postgres, Byparr): `docker compose -f deploy/docker-compose.dev.yml up -d`
- Full stack: `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build`
- Backend: see `backend/CLAUDE.md`
- Frontend: see `frontend/CLAUDE.md`
