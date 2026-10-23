# 02 — Ingestion

How content enters the library. v1 supports only folder import (no web upload).

## Mounted folders

| Container path | Purpose |
|---|---|
| `/import` | Drop folders here. One folder = one media (new, update or restore). |
| `/library` | Final library. Managed by the app only. |
| `/data` | Generated files: thumbnails, moment images. Rebuildable. |
| `/plugins` | Metadata plugin JARs. |

## Import folder layout

```
/import/<any name>/
  kuratoon.yml          # manifest (preferred) — or —
  source.txt            # a single line with the metadata URL
  source.html           # optional: saved page, used when fetching fails
  cover.jpg             # optional: overrides the scraped cover
  Chapter 1/001.jpg …   # series: one subfolder per chapter
  001.jpg …             # one-shot: images directly in the folder
```

The folder name is irrelevant to identity; it is only a fallback title.

## Manifest (`kuratoon.yml`, written by a person)

All fields optional. Without a `url` or a `source.html` file, the manifest must itself contain
complete metadata (title, `type`, at least one author or artist; `language` falls back to
`DEFAULT_LANGUAGE`), or the admin fills metadata manually.

```yaml
kuratoon: 1            # schema version
kind: manifest
url: https://example.site/series/foo
type: manhwa           # media_type slug
language: en
adult: false
title: Foo
titles: [ "Foo (Alt)" ]
status: ONGOING
year: 2023
description: ...
authors: [ "Name" ]
artists: [ "Name" ]
genres: [ "Action", "Fantasy" ]
tags: [ "hq" ]         # LIBRARY tags
parodies: [ "..." ]
characters:
  - { name: "...", parody: "..." }
```

**Precedence**: manifest values override scraped values, and every field set in the manifest is
added to `locked_fields`.

`source.txt` is equivalent to a manifest containing only `url`.

## Sidecar snapshot (`kuratoon.yml`, written by the app)

After every successful import and after every metadata change (debounced ~5 s), the app writes
`/library/<mediaId>/kuratoon.yml` with `kind: snapshot`. It is a superset of the manifest:

```yaml
kuratoon: 1
kind: snapshot
id: 0192f1c3-...            # media UUID
url: ...
plugin: example
type: manhwa
# ...all manifest fields...
locked: [ "title", "type" ]
cover: { file: cover.jpg, crop: { x: 0.1, y: 0, w: 0.6, h: 1 } }
created_at: 2026-10-19T00:00:00Z
chapters:
  - { number: 0, title: Prologue, folder: "0", created_at: 2026-10-19T00:00:00Z,
      pages: [ { f: 0001.jpg, w: 800, h: 12000 } ] }
relations:
  - { type: SEQUEL, media_id: 0192f1c4-... }
moments:
  - { id: ..., chapter: 12, page: 4, crop: {...}, title: ..., description: ..., status: APPROVED }
```

The snapshot never contains per-user data (progress, favorites, ratings, collections).

Each chapter entry includes its `created_at`. RESTORE uses it as `chapter.created_at`, so restored
chapters keep their original "added" dates (and the media's `latest_chapter_added_at` is derived
from them) instead of the restore time.

Write atomically: write to `kuratoon.yml.tmp`, fsync, rename.

The debounce is in memory, so a pending write can be lost on shutdown or crash. On startup, before
polling starts, the app rewrites the sidecar of every media whose `updated_at` is newer than the
sidecar file's modification time, or whose sidecar is missing.

## Job lifecycle

```
QUEUED → RESOLVING → FETCHING_METADATA → PROCESSING → DONE
                    ↘ NEEDS_ATTENTION (admin action required)
                    ↘ FAILED (unexpected error; retryable)
NEEDS_ATTENTION / FAILED → DISMISSED (admin action; finished)
```

- `QUEUED`: folder detected and stable.
- `RESOLVING`: read manifest/snapshot, decide kind (NEW / UPDATE / RESTORE), parse chapters.
- `FETCHING_METADATA`: fetch + parse via plugin (skipped for RESTORE and when metadata is complete).
- `PROCESSING`: validate images, copy files, write DB, generate thumbnails, write sidecar.
- `NEEDS_ATTENTION`: a decision is needed. `message` explains it; `detail` holds the data.
- `FAILED`: technical failure. Retry button re-runs from the start.
- `DISMISSED`: the admin dismissed the job (only from `NEEDS_ATTENTION` or `FAILED`, and never
  once `committed_at` is set: that returns 409, and Retry finishes the job instead). It is
  finished (`finished_at` set). Dismiss renames the source folder in `/import` to
  `.dismissed-<name>-<jobId>`, so the poller (which ignores folders starting with `.`) does not
  pick it up again. The job id makes the name unique, so it never collides. To re-import, the
  admin renames the folder manually.

The Imports admin page lists jobs with status, message, the parsed chapter table
(folder → number → title → page count → warnings) and actions: Retry, Fill metadata manually,
Choose plugin, Resolve conflict, Dismiss.

## Folder detection

- Poll `/import` every `IMPORT_POLL_SECONDS` (default 30). Do not use inotify (unreliable on
  network mounts).
- A folder is ready when its total file count and total size are unchanged for
  `IMPORT_STABLE_SECONDS` (default 60).
- Ignore folders starting with `.` and folders already owned by a non-finished job.
- Stability observations are kept in memory: after a restart, the window starts again.

## Resolution: NEW vs UPDATE vs RESTORE

1. If `kuratoon.yml` has `kind: snapshot` → **RESTORE**. If a media with that `id` already exists,
   NEEDS_ATTENTION ("already in library"). If the snapshot's `url` is the `source_url` of a
   different existing media, NEEDS_ATTENTION ("URL belongs to another media").
2. Else determine the URL (manifest `url`, else `source.txt`, else canonical URL from
   `source.html` — see plugins spec). Normalize it (lowercase host, strip fragment and tracking
   params, no trailing slash).
3. If a media has that `source_url` → **UPDATE** (append chapters).
4. Else → **NEW**. Before creating, run duplicate detection: same normalized title (or alt title)
   and same media type → NEEDS_ATTENTION with the candidate, admin chooses "Update existing" or
   "Create new".
5. No URL, no HTML, no snapshot: if the manifest has complete metadata (see "Manifest") →
   **NEW** with `source_url` NULL (duplicate detection still runs). Otherwise NEEDS_ATTENTION
   ("no metadata source"); admin may fill metadata manually.

## Structure detection

- Folder contains image files directly (and no chapter subfolders) → **one-shot**: one chapter,
  number 1, no title.
- Folder contains subfolders → each subfolder is a chapter. Image files at the top level alongside
  subfolders are ignored except `cover.*`.
- Nested subfolders deeper than one level → NEEDS_ATTENTION.

## Chapter number and title parsing

Applied to each chapter subfolder name:

1. If the name contains a `chapter` / `chap` / `ch` / `ch.` keyword (case-insensitive), the number
   is the first number after it. Otherwise the number is the first number in the name.
2. Numbers may be decimal with `.` or `,` (`12.5`, `12,5`). Leading zeros are ignored. 0 is valid.
3. Title: the text after the number, with a leading separator (`-`, `–`, `—`, `:`, `_`) and
   whitespace stripped. Empty → no title.

Examples:

| Folder | Number | Title |
|---|---|---|
| `Chapter 0 - Prologue` | 0 | Prologue |
| `Chapter 25 - The End` | 25 | The End |
| `chapter 012` | 12 | — |
| `Ch.12.5` | 12.5 | — |
| `Vol 2 Chapter 10` | 10 | — |
| `S2 Ch 5 - Return` | 5 | Return |
| `045` | 45 | — |
| `Extra` | — | NEEDS_ATTENTION |

Errors → NEEDS_ATTENTION (never silently skipped): no number found, two folders with the same
number, a chapter with zero valid pages.

Canonical chapter folder in the library: the number without trailing zeros (`12`, `12.5`, `0`).

## Page ordering

- Accepted extensions (case-insensitive): jpg, jpeg, png, webp, avif, gif.
- Ignored: hidden files, `Thumbs.db`, `desktop.ini`, `.DS_Store`, `*.txt`, `*.html`, `*.yml`,
  `*.url`, anything else not an image.
- Sort key: the last number in the file name as a decimal (`page 10.5` → 10.5; `001` → 1),
  tie-broken by a natural sort of the full name. Files with no number sort after numbered ones by
  natural order.
- Read each image header (dimensions). An unreadable image flags the chapter: NEEDS_ATTENTION with
  the file name.
- Pages are renamed `0001.<ext>`, `0002.<ext>`… in sorted order. Original files are not re-encoded.

## UPDATE rules

- New chapter numbers are appended.
- A chapter number that already exists → NEEDS_ATTENTION with options: Skip, Replace.
  Replace swaps the pages but keeps users' progress rows; any `page_index` now out of range is
  clamped to the last page, and its `page_offset` is reset to 0. The replaced chapter keeps its
  `created_at` (it is not a newly added chapter); only `updated_at` changes.
- Metadata is refreshed on UPDATE only for unlocked fields, and only if the admin enabled
  "refresh metadata on update" for the job (default: off).

## Library layout

```
/library/<mediaId>/
  kuratoon.yml
  cover.<ext>              # original cover (never modified)
  <chapterFolder>/0001.jpg ...
/data/thumbs/<mediaId>/cover-<width>.webp
/data/moments/<momentId>/<width>.webp
```

## Commit order (crash safety)

1. Copy files from `/import/<folder>` into a staging dir `/library/.staging/<jobId>/`.
2. Validate everything in staging.
3. In one DB transaction: create/update media, chapters, tags, creators, etc., and set the job's
   `committed_at`.
4. Move staging into place (rename within the same filesystem). For UPDATE, each new chapter
   folder is moved separately. Replace swaps safely: rename the old chapter folder to a backup
   inside staging, move the new one in, delete the backup.
5. Generate thumbnails, write sidecar.
6. Rename the source folder to `/import/.imported-<name>-<jobId>` (ignored by the poller), then
   delete it. A failed delete never causes a re-import.

Steps 4–6 are idempotent: the move only moves what is not in place yet, a half-done Replace swap
is completed, and thumbnails, sidecar and source delete can re-run safely.

On startup (before polling starts):
- PROCESSING jobs with `committed_at` are **rolled forward**: steps 4–6 are completed and the job
  ends DONE. If the job is committed but its staging dir is gone and the library folder is
  incomplete, the job goes to NEEDS_ATTENTION with an explanation.
- PROCESSING jobs without `committed_at` are marked FAILED and their staging is deleted.
- RESOLVING and FETCHING_METADATA jobs are re-queued (QUEUED): resolution has no side effects.
- Leftover `/library/.staging/*` are deleted, except the staging of a committed job that is
  waiting for Retry (NEEDS_ATTENTION after a failed finish), which Retry still needs. Leftover
  `/import/.imported-*` folders are deleted.

## Cover

Priority: `cover.*` in the import folder > cover image URL from the plugin (downloaded through the
same fetch session) > first page of the first chapter. Stored as the original in the library folder.

## Admin maintenance actions

- **Refresh metadata** for a media: re-fetch and update unlocked fields.
- **Rebuild library from disk**: scan `/library/*/kuratoon.yml` and restore any media missing from
  the DB (same code path as RESTORE).
- **Integrity scan**: report media folders without DB rows, DB rows without folders, missing pages,
  missing thumbnails (with a "regenerate" action).
- Manual metadata edit locks each edited field. Fields can be unlocked individually.
