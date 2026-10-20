# 0004 — UUIDv7 IDs and self-describing library folders

**Status**: accepted

**Decision**: Media, chapters and moments use app-generated UUIDv7 keys. Library folders are named
by media ID. Each media folder contains a `kuratoon.yml` snapshot with all non-personal metadata
(including relations and moments). The same schema serves as the import manifest
(`kind: manifest` vs `kind: snapshot`).

**Why**: After a database loss, the library can be rebuilt from disk with the same IDs, so folder
paths and relations stay valid. Serial IDs would be reassigned. One format means a processed folder
can simply be dropped back into `/import`.
