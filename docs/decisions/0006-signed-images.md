# 0006 — Signed image URLs via nginx secure_link

**Status**: accepted

**Decision**: Default `IMAGE_ACCESS=signed`. The backend signs one token per directory
(chapter, cover folder, moment) with 24-hour expiry windows; nginx validates with `secure_link`
without calling the backend. `public` mode exists for LAN/Tailscale-only installs. Rate limiting
applies in both modes.

**Why**: Friends may access the app through a public URL (not everyone will install a VPN), and
the owner's bandwidth is limited. Verification in nginx costs microseconds; day-stable URLs keep
browser caching effective. Cloudflare tunnels were rejected to avoid depending on third-party
terms of service.
