# 05 — Reading progress and status

## Chapter status (per user)

From the `chapter_progress` row:

| Status | Condition |
|---|---|
| Unread | no row, or row with `completed_at` NULL and `last_read_at` NULL |
| In progress | `completed_at` NULL and `last_read_at` NOT NULL |
| Read | `completed_at` NOT NULL |

- The reader saves position (`page_index`, `page_offset`, `last_read_at`) debounced every ~3 s and
  on tab hide / navigation (`navigator.sendBeacon` or keepalive fetch).
- A chapter becomes Read when the user reaches the last page or ≥ 95% of the chapter's total
  height. That sets `completed_at` (if not already set) and inserts a `read_event`.
- **Read is sticky.** Re-reading a Read chapter updates position and `last_read_at` but never
  clears `completed_at`. Only "mark as unread" clears it.

## Media progress (per user, always derived)

From `user_media_state.chapters_read` vs `media.chapter_count`:

| Progress | Condition |
|---|---|
| Unread | `chapters_read = 0` and no chapter In progress |
| Read | `chapter_count > 0` and `chapters_read = chapter_count` |
| In progress | anything else |

Consequences:
- Importing a new chapter into a Read media makes it In progress automatically. Previously read
  chapters stay Read.
- There is no stored media-level read flag.

## Intent (per user, manual, optional)

`user_media_state.intent`: `PLANNED`, `ON_HOLD`, `DROPPED`, or NULL.

- Set/cleared by the user from the media page.
- **Cleared automatically** when the user reads any chapter of the media, uses "mark read up to
  here", or "mark series as read".
- **Not cleared** by: "mark series as unread", marking a single chapter unread, or a new chapter
  being imported.
- When set, the media badge shows the intent instead of progress. Filters can combine both.

## Manual actions

| Action | Effect |
|---|---|
| Mark chapter read | upsert row, set `completed_at` if NULL; clears intent |
| Mark chapter unread | set `completed_at` NULL, reset position to 0, `last_read_at` NULL |
| Mark read up to here | mark this chapter and every chapter with a lower number read; clears intent |
| Mark series read | mark all chapters read; clears intent |
| Mark series unread | mark all chapters unread; intent unchanged |

Manual marking does not insert `read_event` rows. Every action keeps `chapters_read` in sync in the
same transaction.

## Continue Reading

- Home section and per-media primary button.
- Lists media where the user's `last_read_at` is set, ordered by `last_read_at DESC`, excluding
  intent ON_HOLD/DROPPED and excluding media whose progress is Read **unless** the last read
  chapter itself is In progress (a reread in progress).
- Target: the `last_read_chapter_id` at its saved position. If that chapter is Read and not being
  reread, target the next chapter by number.
- Media page primary button: "Continue Ch. N" / "Start reading" (lowest chapter) /
  "Read again" (fully Read, nothing in progress).

## History

Per-user list of chapters by `last_read_at DESC`, grouped by day, infinite scroll, searchable by
media title. Each entry: cover, media title, chapter, progress bar, time. Actions: remove an entry
(clears that row's `last_read_at` but not `completed_at`), clear history for a media.

## Read first / read last

Media page overflow menu: "Read first chapter" (lowest number), "Read latest chapter" (highest).
