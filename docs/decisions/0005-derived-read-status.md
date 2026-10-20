# 0005 — Media read status is derived from chapters

**Status**: accepted

**Decision**: There is no stored media-level read flag. Media progress (Unread / In progress / Read)
is computed from `chapters_read` vs `chapter_count`. "Mark series read/unread" writes chapter
records. A chapter's Read state is sticky (`completed_at`) and survives rereading. The manual
intent (Planned / On hold / Dropped) is separate and cleared by reading activity.

**Why**: One source of truth. Manual and automatic paths cannot disagree, and a newly imported
chapter correctly turns a Read series into In progress.
