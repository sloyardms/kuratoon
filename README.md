# Kuratoon

A self-hosted library and reader for manga, manhwa, manhua, comics, webtoons, doujinshi and
one-shots.

Drop your image folders into an import folder; Kuratoon parses chapters, fetches metadata through
parser plugins, generates thumbnails and gives you a clean vertical-scroll reader with per-user
progress, favorites, ratings, collections and chapter moments.

> Status: in development. See [`docs/roadmap.md`](docs/roadmap.md).

## Repository

| Folder | Contents |
|---|---|
| `backend/` | Spring Boot app, plugin API, example plugin (Maven) |
| `frontend/` | Next.js web app (npm) |
| `deploy/` | Docker Compose files, `.env.example`, internal nginx config |
| `docs/` | Specification, architecture decisions, phase reports |
| `fixtures/` | Synthetic test data |

## Metadata plugins

Kuratoon ships no metadata providers. Plugins are JAR files you place in the mounted `plugins/`
folder; they parse a page and return metadata. See `docs/spec/03-plugins-fetching.md`.
Only install plugins you trust: they run inside the backend process.

## Exposing it to the internet

Kuratoon does not ship a reverse proxy or TLS. Put your own (Caddy, Traefik, Nginx Proxy Manager…)
in front of the single port the stack exposes. See `docs/spec/08-deployment.md`.
