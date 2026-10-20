# 0010 — Chapter pages stored as JSONB

**Status**: accepted

**Decision**: Each chapter row holds an ordered JSONB array of pages (`file`, `width`, `height`)
instead of a `page` table.

**Why**: Pages are always loaded as a whole chapter and never queried individually. Avoids millions
of rows for large libraries. Dimensions are stored so the reader can reserve layout space.
