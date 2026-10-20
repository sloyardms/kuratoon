# 07 — Frontend

Next.js App Router, shadcn/ui, Tailwind, TanStack Query. Dark theme by default.
Visual reference: clean dark manga-site look (Atsumaru-like) with less clutter.

## Design rules

- **Uncluttered.** No view counts, scan groups, comment icons, social buttons.
- **Cover cards**: 2:3 cover, title (max 2 lines), one meta line (type badge + latest chapter or
  relative time). Optional small badges only for: adult (when `BLUR` mode), user intent/progress
  in personal views. Ratings are not shown on cards.
- **Grid**: uniform cards, `repeat(auto-fill, minmax(~170px, 1fr))` inside a centered container
  (max ~1600px). Larger screens get more columns, not bigger text. Optionally bump root font size
  slightly at the largest breakpoint so 1440p is not tiny.
- **Masonry** only for the moments gallery.
- Responsive from 360px mobile to 2560px.
- Global top bar: logo, search (Ctrl/⌘ K command palette), Browse, user menu. Admin menu for admins.

## Pages

| Route | Contents |
|---|---|
| `/` | Continue Reading (horizontal row), Latest updates (grouped by media: "Ch. 41–45 · 2h ago"), New media, Most read (30 days). Each section has "See all". |
| `/browse` | Filters: text, type, genres/tags (include/exclude), status, creator, parody, character, language, minimum chapters, adult, my progress, my intent. Sort: latest update, newest, title, most read, rating. Infinite scroll. Filters in URL. |
| `/media/[id]` | Detail page (below). |
| `/read/[chapterId]` | Reader (below). Supports `?page=N&offset=X` deep links. |
| `/me` | Profile: overview (counts, recent), History, Favorites (media + chapters), Collections, Moments I submitted, Settings. Sidebar navigation. |
| `/me/collections/[id]` | Collection: grid with sort (added, title, last read, latest update) and status/type/tag filters. |
| `/admin/*` | Imports, Media (list/edit), Media types, Tags, Creators, Parodies, Characters, Moments review, Users, Settings, Audit log, Plugins, Maintenance. Offset-paginated tables. |
| `/setup`, `/login`, `/register`, `/forgot-password`, `/reset-password` | Auth flows. |

### Media detail

- Header: cover (crop applied), title, alternative titles (collapsed), type, status, year,
  authors/artists (links to browse filtered), language.
- Genres and LIBRARY tags shown as separate rows. Parodies and characters as small rows when present.
- Description. Missing-chapters warning when gaps exist. Rating "8.2 (3)" and favorites count.
- **Primary button**: Continue Ch. N / Start reading / Read again.
- **Icon row**: favorite, add to collection, rate, intent (Planned/On hold/Dropped), mark series
  read/unread.
- **Overflow menu**: Read first, Read latest; admin: edit metadata, refresh metadata, crop cover,
  replace cover, delete.
- **Related**: sequels, prequels, side stories, spin-offs (cards).
- **Chapters**: single row per chapter (number, title, added date, status indicator, favorite,
  moments icon when approved moments exist). Search by number, sort asc/desc. Row menu: mark
  read/unread, mark read up to here. Clicking the moments icon opens a popover/sheet with moment
  thumbnails + titles; clicking one opens the reader at that spot.
- Moments gallery tab (masonry) for the whole media.

### Reader

- Vertical scroll only. Pages stacked, centered, width = min(viewport, user `reader_max_width`).
- Every page reserves space with `aspect-ratio` from stored dimensions. Lazy-load with
  `loading="lazy"` plus preloading ~3 pages ahead.
- Floating panel: hides on scroll down, shows on scroll up; tapping the center of the screen
  toggles it. Contents: chapter title, previous, next, chapter picker, media page, favorite chapter,
  capture moment (admins, or users when submissions are enabled), reader width.
- Keyboard: ←/→ previous/next chapter, Space/PageDown scroll, `f` favorite, `m` media page,
  `Esc` close panels.
- End of chapter: Previous, Back to top, Next chapter (prominent).
- Resume: scroll to `page_index` + `page_offset` after layout is reserved.
- Capture moment: overlay with a free crop frame on the current page, title (required),
  description (optional), submit. Admin → APPROVED; user → PENDING.

## Data fetching and pagination

- TanStack Query everywhere. Query keys include all filter params.
- **Infinite scroll** (`useInfiniteQuery`, keyset cursors from the API) for: browse, home
  "See all" pages, history, collections, favorites.
- **Scroll restoration**: filters in URL; cached pages kept (`gcTime` ≥ 10 min) so back navigation
  renders the full list immediately and restores scroll position.
- **Offset pagination** with a pagination component for admin tables.
- Virtualization is not required in v1.

## Images

- `next/image` with `unoptimized`, or plain `<img>`, using API-provided `src`/`srcset`.
- Cover `sizes` matches the grid column width.
- In `BLUR` adult mode, adult covers render blurred with "18+ · tap to reveal".

## States

Every list/page has loading skeletons, empty states and error states with retry.
