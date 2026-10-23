# 03 — Plugins and fetching

## Principle

The core fetches pages; plugins only parse them. The same `parse()` runs whether the HTML came
from the network or from a `source.html` file.

## Plugin API (`backend/plugin-api`)

Plain Java, depends only on jsoup. Versioned independently (semantic versioning); the core
rejects plugins built for an incompatible major version.

```java
public interface MetadataProvider {
    /** Stable unique id, e.g. "example". */
    String id();
    String displayName();
    /** Plugin API version this plugin was built against. */
    int apiVersion();
    /** True if this plugin can parse pages for this URL. */
    boolean supports(URI url);
    /** Parse a fetched or saved page. Must not perform I/O. */
    MediaMetadata parse(Document html, URI sourceUrl) throws ParseException;
}
```

`MediaMetadata` (record) fields, all nullable unless noted:
`title` (required), `alternativeTitles` (list), `description`, `authors` (list), `artists` (list),
`genres` (list), `type` (free text hint, e.g. "Manhwa"), `language`, `status`
(`PublicationStatus` enum), `year`, `adult` (Boolean), `coverUrl` (URI), `parodies` (list),
`characters` (list of `{name, parody}`).

Mapping rules in the core:
- `type` hint is matched case-insensitively against `media_type.name`/`slug`; no match → manifest
  value, else NEEDS_ATTENTION ("choose a type").
- Missing `language` → manifest value, else `DEFAULT_LANGUAGE` env (default `en`).
- Creators/tags/parodies/characters are matched by normalized name, created if missing
  (GENRE kind for scraped genres). A tag whose name slugifies to an empty string (e.g. non-Latin
  names) gets the slug `t-<hex>`, a hash of its normalized name.

## Loading

- JARs in `/plugins` are loaded at startup via `ServiceLoader`, each in its own `URLClassLoader`
  whose parent exposes only the `plugin-api` and jsoup packages.
- Duplicate `id()` → log an error, load neither.
- No hot reload. Admin UI shows loaded plugins (id, name, version, file) and load errors.
- Plugins run in-process with full permissions. The README warns: only install trusted plugins.
- A plugin that throws or takes longer than 10 s in `parse()` fails the job with the plugin's
  message.

## Choosing the plugin

1. URL from the manifest or `source.txt` → first plugin whose `supports(url)` is true.
2. Only `source.html` and no URL → read `<link rel="canonical">`, then `<meta property="og:url">`;
   use that URL for both `supports()` and `source_url`.
3. No match → NEEDS_ATTENTION with a plugin picker (admin selects a plugin explicitly).

## Fetching (`app` module, `fetch` package)

HTTP client: OkHttp with a persistent cookie jar backed by `domain_session`.

Flow for a page URL:

1. If `source.html` exists in the import folder **and** the job is a retry after a failed fetch,
   use the file and skip the network.
2. If a non-expired `domain_session` exists for the domain: request directly with its cookies and
   its exact `User-Agent`.
3. If the response is a challenge (HTTP 403/503 with Cloudflare markers such as `cf-mitigated`
   header, `Just a moment...` title, or `challenge-platform` script) or step 2 was skipped:
   call the solver (`POST {SOLVER_URL}/v1`, `cmd: request.get`, `maxTimeout` from env).
   Use the HTML from the solver response directly, and store its cookies + user agent in
   `domain_session` (expiry = earliest cookie expiry, or 30 min if none).
4. If the solver fails or still returns a challenge → job NEEDS_ATTENTION:
   "Could not fetch <url>: <reason>. Save the page as HTML (`source.html`) into the import folder
   and press Retry."
5. If `source.html` exists and the network path fails, fall back to the file automatically.

The solver API is the FlareSolverr v1 API; Byparr implements it. Do not depend on solver
sessions (`sessions.create`), since not every implementation supports them.

Other rules:
- Timeouts: connect 10 s, read 30 s (direct); solver per `SOLVER_TIMEOUT_MS` (default 60000).
- At most one in-flight request per domain; 2 s minimum delay between requests to the same domain.
- Cover images are downloaded through the same client and session right after the page.
- Max response size 10 MB (pages), 25 MB (images).
- If `SOLVER_URL` is not set, step 3 is skipped (direct fetch only).

## Example plugin (`backend/example-plugin`)

Parses the fake site in `fixtures/html/` (served in tests by a local mock server). It is the
reference implementation for plugin authors and is used by integration tests. It must not target
any real website.
