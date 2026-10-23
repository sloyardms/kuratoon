# Phase 3 — Ingestion (no scraping)

## What was built
- **Docs, owner-authorized** (commit 0): spec 02 now covers:
  - manifest-only NEW (a complete manifest without url/source.html imports with
    `source_url` NULL; duplicate detection still runs);
  - the Replace clamp resetting `page_offset` to 0;
  - roll-forward recovery with `committed_at`, per-chapter moves and the safe Replace swap;
  - the `.imported-<name>-<jobId>` rename before deleting the source;
  - RESTORE whose url belongs to another media → NEEDS_ATTENTION.

  The paragraph on the startup rewrite of stale sidecars was blocked during commit 0 and added
  afterwards in its own docs commit, at the owner's request.
- **Schema**: `V7__import_job_dismissed_committed.sql` widens the `import_job` status CHECK
  with `DISMISSED` and adds `committed_at timestamptz NULL`.
- **Chapter counters** (roadmap note):
  - The `Chapter` constructor no longer touches the media. It takes an explicit `created_at`,
    which RESTORE uses.
  - `ChapterCounters.recalculate(media)` derives `chapter_count` and `latest_chapter_added_at`
    from the chapter rows in the same transaction. It is called by ingestion (add, replace,
    restore), chapter delete and rebuild.
- **Invariant**: `MediaInvariants.requireAuthorOrArtist` is enforced in:
  - the ingestion commit (NEW and RESTORE; the resolver checks it first, so a failure is
    NEEDS_ATTENTION);
  - manual metadata entry (400);
  - media edit (400 when the last author/artist is removed).
- **Parsing** (`dev.kuratoon.ingestion`, pure code):
  - `ChapterNameParser`: keyword/number/title rules and the canonical folder.
  - `PageOrdering`: accepted extensions and junk, last number then natural order, `0001.<ext>`
    names.
  - `StructureDetector`: one-shot vs series, `cover.*`, nested folders, duplicate numbers, empty
    chapters. Problems are collected, not thrown.
  - `UrlNormalizer`.
  - `KuratoonYaml` (Jackson YAML): manifest/snapshot read and write, `source.txt`, schema version
    and kind checks. Set manifest fields become the lock list.
- **Image probe** (`dev.kuratoon.image.ImageProbe`):
  - pure-Java header read for jpg/png/gif/webp/avif;
  - EXIF orientation 5–8 swaps w/h for JPEG;
  - truncation checks: JPEG EOI searched in the last 64 KB after the scan start, so trailing
    bytes are accepted; PNG chunk walk to IEND; GIF trailer; WebP RIFF size; AVIF box chain.

  **Validation is header + truncation only, not a full decode**: damaged pixel data inside an
  intact file passes.
- **libvips** (`VipsRunner`, `ThumbnailService`):
  - The CLI runs with an argument list (no shell), with stdout/stderr drained on virtual threads
    and capped at 64 KB.
  - The process is killed after `kuratoon.images.command-timeout` (60 s, an internal property).
  - A failure gives an `ImageToolException` with the stderr tail.
  - Covers: `vips autorot` → `extract_area` (exact 2:3 from the crop, or centered) →
    `thumbnail … [Q=80,strip] W --height 1.5W --size force` for 240/480/720. Each file goes to a
    temp dir and is renamed into `/data/thumbs/<id>/cover-<w>.webp`.
  - The admin status panel shows the vips version or "unavailable".
- **Pipeline**:
  - `ImportScanner`: polling, stability window, ignores dot-folders and folders owned by an
    unfinished job.
  - `ImportWorker`: one job at a time; claims with `FOR UPDATE SKIP LOCKED`.
  - `ImportResolver`: NEW/UPDATE/RESTORE, chapter table, image checks, duplicate and conflict
    detection, canonical URL from `source.html`.
  - `ImportProcessor`: staging copy and validation.
  - `ImportCommitter`: one transaction that also sets `committed_at` and the `CommitPlan`.
  - `ImportFinisher`, idempotent: whole-folder move (NEW/RESTORE) or per-chapter move (UPDATE),
    the Replace swap through `.staging/<job>/backup/`, thumbnails, sidecar, then source →
    `.imported-*` → delete, then DONE.
  - `ImportRecovery`, on startup before polling:
    - committed PROCESSING jobs are rolled forward;
    - uncommitted ones become FAILED and their staging is deleted;
    - RESOLVING/FETCHING jobs are re-queued;
    - leftover staging, `.deleted-*` and `.imported-*` are removed;
    - stale sidecars are rewritten.
  - `ImportScheduler` runs recovery and then the poller on one background thread. Admin actions
    trigger it immediately.
- **Phase 4 boundary**:
  - Metadata comes from a `MetadataSource` port. Phase 3 ships `NoPluginMetadataSource`, so a job
    with a URL or `source.html` and an incomplete manifest goes to NEEDS_ATTENTION (`NO_PLUGIN`):
    "No metadata plugin supports <url>. Plugins are not available yet: fill in metadata
    manually."
  - A complete manifest skips fetching (status never enters FETCHING_METADATA in this phase).
- **Sidecar** (`SidecarService`): atomic write (tmp, fsync, rename) after every import. Metadata
  edits schedule a write debounced by 5 s after commit; startup rewrites stale ones.
- **Admin API**:
  - `/api/v1/admin/imports`: list with status filter, detail with chapter table and allowed
    actions, retry, manual metadata, conflicts, duplicate, dismiss.
  - `/api/v1/admin/media`:
    - list, edit view, PATCH that applies only the named fields and locks each one, locked-fields;
    - relations;
    - cover crop and 10 MB cover upload (probed before it replaces the original);
    - the original cover for the crop tool;
    - chapter delete, and media delete (folder renamed to `.deleted-<id>` first, so a rebuild
      cannot bring it back).
  - `/api/v1/admin/library`: integrity scan, thumbnail regeneration, rebuild from disk.
  - Every mutation is audited.
- **Frontend**:
  - `/admin/imports`: status filters in the URL, polling while jobs run, job dialog with the
    chapter table, conflict and duplicate choices, manual metadata form, retry and dismiss.
  - `/admin/media` list, and `/admin/media/[id]` edit page:
    - lock toggle per field; saving locks the edited fields;
    - custom 2:3 crop frame (drag, corner resize, arrow keys and +/-) and cover upload;
    - relations, chapter list with delete, media delete.
  - `/admin/maintenance`: scan report, regenerate thumbnails, rebuild result.
  - The admin menu links all three. `apiFetch` no longer forces a JSON content type on
    `FormData`.
- **Fixtures**: every case in `fixtures/README.md` is generated (deterministic check passes).
  `restore-snapshot` commits its sidecar with fixed UUIDs, and series-messy junk files are
  generated and git-ignored.
- **Deploy/CI**:
  - nginx allows 10 MB only on `^/api/v1/admin/media/<id>/cover$`; Spring multipart allows 10 MB.
  - The CI backend job installs `libvips-tools` and generates fixtures. It sets
    `KURATOON_TEST_REQUIRE_VIPS=true`, so vips tests cannot silently skip there.
  - The backend path filter includes `fixtures/**`.
  - The docker job generates the one-shot fixture into the stack's import folder, with 2 s
    polling and stability windows, for the e2e import flow.

### New pinned versions
| Component | Version |
|---|---|
| jackson-dataformat-yaml (`tools.jackson.dataformat`) | 3.1.5 (Spring Boot BOM) |
| libvips (CI runner and runtime image, Ubuntu noble `libvips-tools`) | 8.15.1 |

## Deviations from the spec
- **02 / "refresh metadata on update"**: the per-job toggle is not offered yet; there is no plugin
  to refresh from. UPDATE never changes metadata in this phase.
- **02 / Imports actions, "Choose plugin"**: deferred to phase 4. The NO_PLUGIN state it will
  use is already there.
- **02 / RESTORE relations**: a snapshot relation to a media that is not in the library is
  dropped with a warning on a single RESTORE import. "Rebuild library from disk" restores every
  missing media first, then all relations between them.
- **06 / "Spring never streams images"**: the admin crop tool loads the original cover from
  `GET /api/v1/admin/media/{id}/cover/original` (admin-only, `no-store`). nginx must never expose
  originals, and image delivery is phase 5. The URL comes from the API (`cover.originalUrl`).
- **02 / sidecar fsync**: the file is fsynced before the rename; the directory is not.

## Spec files that may need updating
- None open. After the phase review, the owner authorized spec edits for every item that was
  listed here. They are in the docs commit after the report:
  - 01: `import_job.committed_at`.
  - 02: the startup rewrite of stale sidecars; no Dismiss once committed (409, Retry finishes
    it); Replace keeps `created_at`; RESOLVING/FETCHING jobs re-queued at startup; staging kept
    for committed jobs that wait for Retry; the stability window restarts after a restart.
  - 03: the `t-<hex>` slug for tags whose name slugifies to "".

## Tests
- Added (backend unit):
  - `ChapterNameParserTest`: one test per row of the spec table, plus separators, comma
    decimals, keyword edge cases, precision and canonical folders.
  - `PageOrderingTest`: 1/01/001, `page N`, decimals, unnumbered last, natural tie-break, junk
    files, extensions.
  - `StructureDetectorTest`, `UrlNormalizerTest`, `KuratoonYamlTest` (manifest, `source.txt`,
    the fixture snapshot, round trip).
  - `ImageProbeTest`: every format, EXIF rotation, truncation, trailing bytes after EOI.
  - `CropRectTest`, `VipsRunnerTest`: fake tool via the test JVM's `java`, covering timeout,
    exit code, stderr, output cap and a missing binary.
  - `ThumbnailServiceTest`: real vips, exact sizes, crop placement via `vips getpoint`,
    regeneration, unreadable original.
  - `MediaInvariantsTest`.
- Added (backend integration, Testcontainers + `fixtures/import`):
  - `ImportPipelineIT`: new series; url-only → NO_PLUGIN → manual metadata; one-shot; messy
    names; update appending chapters; conflict → NEEDS_ATTENTION → Skip; Replace with progress
    kept and clamped; corrupt image → NEEDS_ATTENTION; duplicate/no-number chapters; html-only
    canonical URL; invalid manifest; unknown type; manifest-only NEW; duplicate → update/create;
    restore from the fixture; restore from sidecar after a DB wipe; already in library; URL used
    by another media.
  - `CrashRecoveryIT`:
    - simulated crash (an Error no pipeline code catches) at AFTER_COPY, AFTER_VALIDATE and
      IN_TRANSACTION: no media row, no library folder, staging gone, source intact; Retry then
      imports;
    - crash at AFTER_COMMIT, AFTER_MOVE, AFTER_THUMBNAILS, AFTER_SIDECAR, mid-move of an UPDATE
      and mid-Replace swap: rolled forward to DONE with a consistent library, the integrity scan
      included;
    - committed without staging → NEEDS_ATTENTION;
    - ordinary failure; interrupted resolution; leftover cleanup;
    - failed source delete never re-imports;
    - stale sidecar rewritten on startup.
  - `ImportScannerIT` (stability window, changes, dot folders, owned folders, order),
    `ImportAdminIT`, `MediaAdminIT`, `LibraryMaintenanceIT`, `ChapterCountersIT` (add, delete,
    delete all, replace).
  - Schema contract and access rules were updated.
- Added (frontend): `metadata.test.ts`, `crop.test.ts`, `import-job-dialog.test.tsx` (allowed
  actions, client-side check before PUT), `api.test.ts` (FormData content type), and the
  Playwright `e2e/imports.spec.ts` (fixture folder → Imports page → manual metadata → DONE →
  edit page).
- Status: all passing.
  - Backend `./mvnw verify` on Windows (no vips installed): 375 tests (all modules), 0 failures,
    5 vips tests skipped.
  - The same suite in a Linux container with libvips 8.15.1 and `KURATOON_TEST_REQUIRE_VIPS=true`:
    373 app tests, 0 failures, 1 skipped (the delete-failure test needs a non-root user; the CI
    runner is one).
  - Frontend lint, typecheck, test (29 tests), format:check and `next build` pass.
  - Playwright against a locally built compose stack (`auth.spec.ts`, `imports.spec.ts`): 2
    passed. The run also showed the container's libvips writing the three cover WebPs, the
    sidecar, the library layout and the removed source folder.
  - GitHub Actions [run #9](https://github.com/sloyardms/kuratoon/actions/runs/39911263882) on
    `ce843c4`: all five jobs green. In the backend job's log (confirmed by the owner)
    `CrashRecoveryIT` ran 15 tests with 0 skipped, so the delete-failure test really ran on the
    non-root runner. Since then CI guards this: with `KURATOON_TEST_REQUIRE_NONROOT=true` the test
    fails instead of skipping when run as root. A test summary (run/failed/skipped per module, plus
    every skipped test by name) is written to the backend job's `$GITHUB_STEP_SUMMARY`.

## Known issues and shortcuts
- The commit split is coarser than planned. Scanner, resolver, staging, commit, move, sidecar
  and recovery landed in one commit (`f276023`), because the worker needs all of them to
  compile and pass its tests.
- Image validation reads headers and checks truncation; it does not decode pixels (see above).
- Thumbnail regeneration from the Maintenance page runs in the request (fine for the missing
  ones of a small library). Rebuild queues its thumbnails on a background thread instead.
- If the import folder cannot be renamed at all (step 6), the job is DONE with a warning, and
  the folder would be picked up again as an UPDATE where every chapter conflicts. If only the
  delete fails, the `.imported-*` folder is ignored and removed at the next start.
- AVIF dimensions are read from the first `ispe` box and `irot` is honoured; `imir` and grid
  images are not special-cased.
- Duplicate detection compares lowercased titles in SQL, not the full NFKC/whitespace
  normalization used for names.
- The media edit page has no client-side component test; its rules are tested through the API
  and the shared helpers.

## Open questions for the owner
- None. Dismissing a committed job is not allowed (now in spec 02).

## How to verify manually
1. `docker compose -f deploy/docker-compose.dev.yml up -d`, then
   `python fixtures/generate/generate.py`.
2. Backend: `cd backend && ./mvnw verify` (vips tests run only when the `vips` CLI is on PATH).
   Frontend: `cd frontend && npm run lint && npm run typecheck && npm test`.
3. Full stack: `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build`,
   log in as admin.
4. Copy `fixtures/import/oneshot` into `IMPORT_DIR`. Within about 90 s (poll + stability) it
   appears on Admin → Imports as *Needs attention* (no plugin). Open it, use *Fill metadata
   manually* (title, type, an author), and it ends *Done*.
5. Copy `fixtures/import/series-basic` with a complete manifest (title/type/authors added), then
   `series-update` (appended), then `series-conflict` (choose Skip or Replace).
6. Copy `series-corrupt`: it needs attention naming `Chapter 2/002.jpg`; nothing appears in
   `LIBRARY_DIR`.
7. On a media edit page, change a field (it becomes locked), unlock it, drag the crop frame and
   save; `cover_version` bumps and the thumbnails in `DATA_DIR/thumbs/<id>/` change.
8. Stop the backend container during a large import, start it again: the job either finishes
   (it was committed) or is *Failed* with nothing in the library.
9. Maintenance → Run scan (clean), delete a page file by hand and scan again; wipe the database,
   then use Rebuild to restore everything from the sidecars.
