# Frontend — conventions

## Stack

- Next.js (App Router), TypeScript strict, npm (commit `package-lock.json`).
- Tailwind CSS + shadcn/ui. Custom components for cover cards, the reader and the crop tools.
- TanStack Query for server state (including `useInfiniteQuery` for feeds).
- No session library: the backend sets httpOnly auth cookies (ADR 0013). The browser calls `/api`
  with `credentials: 'include'`; `src/proxy.ts` refreshes cookies for server rendering.
- Testing: Vitest + Testing Library for components; Playwright for critical flows (login, reader).
- Lint/format: ESLint + Prettier.

## Commands

- Dev: `npm run dev`
- Checks: `npm run lint && npm run typecheck && npm test`
- E2E: `npm run test:e2e`

## Conventions

- All data comes from the backend REST API (`/api/v1`). No direct DB access, no business rules
  duplicated in the frontend (e.g. read status is computed by the backend).
- Filters, sort and search live in URL query params so back/forward restores state.
- Images: use the URLs (signed or public) returned by the API; never build image URLs by hand.
  `next/image` must be `unoptimized` — the backend already produces size variants, use `srcset`.
- Every image element reserves its space via width/height or `aspect-ratio` (no layout shift).
- Dark mode is the default; light mode and "system" are user settings.
- Keep the UI uncluttered: follow the design rules in `docs/spec/07-frontend.md`. Do not add
  counters, badges or sections that are not in the spec.
- Accessibility: keyboard navigable, visible focus, labelled icon buttons.
