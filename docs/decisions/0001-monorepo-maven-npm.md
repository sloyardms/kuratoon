# 0001 — Monorepo with Maven backend and npm frontend

**Status**: accepted

**Decision**: One GitHub repository with `backend/` (Maven multi-module: `plugin-api`, `app`,
`example-plugin`), `frontend/` (Next.js + npm), `deploy/`, `docs/`, `fixtures/`. Maven wrapper and
`package-lock.json` are committed. Dockerfiles live with their project; compose lives in `deploy/`.

**Why**: Spec, code and fixtures change together and are reviewed per phase. Maven is the owner's
preference. `plugin-api` as a module keeps one build while staying independently publishable.
