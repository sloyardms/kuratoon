# 00 — Overview

## What it is

A web app to manage and read a personal library of manga and its relatives (manhwa, manhua,
comics, webtoons, doujinshi, one-shots). It looks and feels like an online manga site, but the
content is the owner's own bought, scanned or downloaded image folders. Metadata is filled in
automatically from online sources through parser plugins, with manual editing as the final word.

Deployed with Docker Compose, a `.env` file and the config files in `deploy/`.

## Audience and scale

- The owner plus a few friends. Multi-user: every user has their own progress, favorites,
  ratings, collections, preferences and history.
- Small scale, built properly: clean architecture, tests, performance-conscious queries and
  image delivery. Must run well on a home server / NAS CPU.

## Design philosophy

Existing apps are either overdesigned (credits for inkers and assistants, external review
sections) or underdesigned. Kuratoon keeps only fields and screens that are actually used.
When in doubt, leave it out.

## Roles

- **Admin**: manages everything — library content, metadata, imports, tags, media types, users,
  settings, moment review.
- **User**: reads. Users cannot change the library, but they own their personal data
  (progress, favorites, ratings, collections, preferences) and can submit moments when enabled.

## Feature summary

| Area | Spec file |
|---|---|
| Data model | `01-data-model.md` |
| Importing folders, manifests, sidecars, chapter parsing | `02-ingestion.md` |
| Metadata plugins, fetching through Byparr/FlareSolverr | `03-plugins-fetching.md` |
| Auth, users, first run, settings | `04-auth-users.md` |
| Reading progress and status model | `05-reading-status.md` |
| Thumbnails, image delivery, signed URLs, rate limits | `06-images.md` |
| Pages, components, design rules | `07-frontend.md` |
| Compose, environment variables, volumes, proxying | `08-deployment.md` |

## Out of scope for v1 (scrapped or deferred)

Do not build these.

| Feature | Status |
|---|---|
| Comments | Scrapped for v1 |
| Reading lists / ordered playlists | Scrapped (collections cover it) |
| Collector fields (purchase price, store/con, purchase date) | Scrapped for v1 |
| Volume numbers on chapters | Scrapped |
| Multiple languages of the same series | Scrapped: one language per series |
| Reading directions other than vertical scroll | Scrapped |
| Plugin hot reload | Scrapped: plugins load at startup |
| Notifications, public profiles, groups, badges, social features | Scrapped |
| Shipping a reverse proxy / TLS in compose | Scrapped: installer's responsibility |
| Statistics page | Deferred |
| Auto-advance to next chapter while scrolling | Deferred |
| CBZ/archive import | Deferred (post-v1) |
| Zip upload through the web form | Deferred: folder import only in v1 |
| OPDS feed, offline/PWA | Deferred |
| Checkbox bulk-select for marking chapters | Deferred ("mark read up to here" covers it) |

## Glossary

- **Media**: a series or one-shot (one entry in the library).
- **Chapter**: an ordered unit of a media, identified by a decimal number.
- **Page**: one image of a chapter.
- **Media type**: admin-defined category (Manga, Manhwa, …).
- **Tag**: a GENRE (describes the story) or LIBRARY tag (describes your copy, e.g. "hq").
- **Manifest**: `kuratoon.yml` written by a person in an import folder (partial metadata).
- **Sidecar / snapshot**: `kuratoon.yml` written by the app in a library folder (complete metadata).
- **Moment**: a curated, cropped highlight of a chapter page with a title, visible to everyone.
- **Intent**: a user's manual media status — Planned, On hold, Dropped.
