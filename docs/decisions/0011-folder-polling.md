# 0011 — Folder polling instead of inotify

**Status**: accepted

**Decision**: The import folder is polled (default every 30 s); a folder is ready when its file
count and total size are unchanged for 60 s.

**Why**: inotify is unreliable on network shares and some Docker bind mounts. Polling a small import
folder is cheap, and the stability window avoids importing half-copied folders.
