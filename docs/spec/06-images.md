# 06 — Images

## Generation

- Tool: libvips (`vips` CLI or a binding) in the backend container. Not ImageIO.
- Generated once at import (and on cover/crop change). Output WebP, quality ~80.
- **Covers**: crop the original with `cover_crop` (or a centered 2:3 crop if none), then produce
  widths **240, 480, 720** at 2:3 → `/data/thumbs/<mediaId>/cover-<w>.webp`.
- **Moments**: crop the page with the moment's `crop`, produce widths **480, 960** (aspect kept)
  → `/data/moments/<momentId>/<w>.webp`.
- Page images are served as-is (original jpg/png/webp). Never re-encoded.
- Regeneration is idempotent; `/data` can be deleted and rebuilt via the integrity scan.

## Cover crop tool (admin)

Opens the original cover; the admin drags a 2:3 frame; saves normalized `{x,y,w,h}`.
Saving bumps `cover_version` and regenerates thumbnails from the untouched original.
The admin can also upload a replacement cover.

## Delivery

The internal nginx serves files directly; Spring never streams images.

| Route | Source |
|---|---|
| `/img/p/<mediaId>/<chapterFolder>/<file>` | `/library` pages |
| `/img/c/<mediaId>/cover-<w>.webp?v=<cover_version>` | `/data/thumbs` |
| `/img/m/<momentId>/<w>.webp` | `/data/moments` |

The `/library` mount is read-only for nginx. Only these route patterns are served; `kuratoon.yml`,
originals of covers and anything else are never exposed.

Cache headers: `Cache-Control: public, max-age=31536000, immutable` for covers (versioned) and
moments; `private, max-age=86400` for pages.

The API returns ready-to-use URLs (and `srcset` strings for covers/moments). The frontend never
builds image URLs.

## Access modes (`IMAGE_ACCESS`)

### `signed` (default)

- The backend signs **per directory** with the nginx `secure_link` scheme:
  `md5(expires + dir + IMAGE_SIGNING_SECRET)`, base64url, appended as `?s=<hash>&e=<expires>`.
  - pages: dir = `/img/p/<mediaId>/<chapterFolder>/` (one token per chapter)
  - covers: dir = `/img/c/<mediaId>/`
  - moments: dir = `/img/m/<momentId>/`
- `expires` = end of the current UTC day + 24 h, so URLs stay identical for a full day and remain
  browser-cacheable.
- nginx validates with `secure_link` / `secure_link_md5`; invalid → 403, expired → 410.
- Signed URLs are only issued in authenticated API responses.

### `public`

No signature; same routes. Intended for Tailscale/LAN-only installs.

## Rate limiting (both modes, nginx)

- Images: `limit_req` per client IP, default 40 r/s, burst 200, plus `limit_conn` 20 per IP.
- API: 20 r/s, burst 60. Auth endpoints: 10 r/min, burst 10.
- All values configurable via env (`RATE_LIMIT_*`) and templated into the nginx config.
- Client IP comes from `X-Forwarded-For` only when the request comes from `TRUSTED_PROXIES`
  (nginx `set_real_ip_from` + `real_ip_header`).

Note: rate limiting stops scraping and abuse, not volumetric DDoS. That is out of scope.
