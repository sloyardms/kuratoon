# Fixtures

Synthetic test data only. Never add real manga/comic images or HTML copied from real websites.

## Layout

```
fixtures/
  generate/        script(s) that build the image fixtures (committed)
  import/          sample import folders (generated images are git-ignored; manifests committed)
  html/            fake metadata site pages used by example-plugin and fetch tests
  large/           generated large library for performance tests (git-ignored)
```

## Image generation

Images are produced by a script (e.g. Python + Pillow, or `vips`), not committed, to keep the repo
small. Each image shows its chapter/page number in large text so ordering bugs are visible.
Running the script must be deterministic.

## Required import cases (`fixtures/import/`)

| Folder | Purpose |
|---|---|
| `series-basic/` | `kuratoon.yml` with url; `Chapter 0 - Prologue` … `Chapter 5 - The End`; pages `001.jpg`… |
| `series-messy/` | mixed names: `chapter 012`, `Ch.12.5`, `Vol 2 Chapter 10`, `045`; pages `1.jpg`, `01.webp`, `page 10.5.png`; `Thumbs.db`, `desktop.ini` |
| `series-update/` | same URL as `series-basic`, chapters 6–7 (append) |
| `series-conflict/` | same URL as `series-basic`, includes chapter 3 again |
| `series-duplicate-number/` | `Chapter 2` and `chapter 02` |
| `series-no-number/` | contains `Extra/` |
| `series-corrupt/` | one truncated/corrupt jpg |
| `series-html-only/` | no URL, only `source.html` with a canonical link to the fake site |
| `series-manifest-override/` | manifest sets title/type/language that differ from the fake site |
| `oneshot/` | images directly in the folder, `source.txt` |
| `webtoon-tall/` | very tall strips (e.g. 800×12000) for reader layout tests |
| `restore-snapshot/` | complete `kind: snapshot` sidecar + chapters |

## Fake metadata site (`fixtures/html/`)

A few static pages imitating a typical manga site layout (title, alt titles, authors, artists,
genres, status, type, description, cover image), plus:
- a challenge page (Cloudflare-style "Just a moment..." markup) for challenge-detection tests;
- a page with a `<link rel="canonical">` for the HTML-only path;
- a page with missing optional fields.

Tests serve these with a local mock server (e.g. OkHttp MockWebServer). `example-plugin` parses
this fake site only.
