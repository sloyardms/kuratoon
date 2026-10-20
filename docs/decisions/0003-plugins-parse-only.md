# 0003 — Plugins only parse; core fetches; no hot reload; zero bundled providers

**Status**: accepted

**Decision**: `MetadataProvider.parse(Document, URI)` turns HTML into DTOs. The core owns HTTP,
cookies, the FlareSolverr/Byparr solver and the `source.html` fallback. Plugins load once at
startup. Kuratoon ships no real providers; `example-plugin` targets only a fake fixture site.

**Why**: The online path and the saved-HTML fallback share one code path. Plugins stay tiny and
easy to write. Hot-reloading classloaders leaks memory and buys little. Not bundling providers
keeps the project clear of any association with specific sites.

**Fetching approach**: like Suwayomi/Mihon — on a challenge, call the solver, store its cookies and
user agent per domain, reuse them with OkHttp until they expire or are challenged again.
