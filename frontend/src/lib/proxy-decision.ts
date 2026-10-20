import type { AccessClaims } from "./session";

/** Refresh when the access token has less than this many seconds left (ADR 0013). */
export const REFRESH_MARGIN_SECONDS = 60;

/** Pages reachable without a session. `/setup` is handled separately. */
const PUBLIC_PATHS = [
  "/login",
  "/register",
  "/forgot-password",
  "/reset-password",
];

export interface ProxyInput {
  pathname: string;
  /** Path plus query string, used for `?next=` after login. */
  pathWithSearch: string;
  setupRequired: boolean;
  access: AccessClaims | null;
  hasRefreshCookie: boolean;
  /** Seconds since the epoch. */
  now: number;
}

export type ProxyDecision =
  { type: "next" } | { type: "refresh" } | { type: "redirect"; to: string };

export function isPublicPath(pathname: string): boolean {
  return PUBLIC_PATHS.some(
    (p) => pathname === p || pathname.startsWith(`${p}/`),
  );
}

export function loginRedirect(pathWithSearch: string): string {
  return pathWithSearch === "/"
    ? "/login"
    : `/login?next=${encodeURIComponent(pathWithSearch)}`;
}

/**
 * What `proxy.ts` does with a page request. Pure, so it is unit-tested; the backend still
 * enforces every rule.
 */
export function decide(input: ProxyInput): ProxyDecision {
  const { pathname, setupRequired } = input;

  if (setupRequired) {
    return pathname === "/setup"
      ? { type: "next" }
      : { type: "redirect", to: "/setup" };
  }
  if (pathname === "/setup") {
    return { type: "redirect", to: "/login" };
  }
  if (isPublicPath(pathname)) {
    return { type: "next" };
  }

  const fresh =
    input.access !== null &&
    input.access.exp - input.now >= REFRESH_MARGIN_SECONDS;
  if (!fresh) {
    return input.hasRefreshCookie
      ? { type: "refresh" }
      : { type: "redirect", to: loginRedirect(input.pathWithSearch) };
  }
  return authorize(pathname, input.access!);
}

/** After a valid (or freshly refreshed) access token: optimistic admin check. */
export function authorize(
  pathname: string,
  access: AccessClaims,
): ProxyDecision {
  if (
    (pathname === "/admin" || pathname.startsWith("/admin/")) &&
    access.role !== "ADMIN"
  ) {
    return { type: "redirect", to: "/" };
  }
  return { type: "next" };
}
