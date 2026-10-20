import { NextResponse, type NextRequest } from "next/server";

import { authorize, decide, loginRedirect } from "@/lib/proxy-decision";
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  decodeAccessToken,
} from "@/lib/session";

/**
 * Runs before every page request (ADR 0013):
 * - while no admin exists, everything goes to /setup;
 * - an access cookie that is missing or expires in < 60 s is refreshed here, and the new cookies
 *   are set on the response *and* on the forwarded request, so server rendering in the same
 *   request already sees the new access token;
 * - otherwise unauthenticated users go to /login, non-admins are kept out of /admin.
 * The backend enforces all of this again; this is only routing.
 */

const BACKEND = (
  process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080"
).replace(/\/$/, "");
const APP_ORIGIN = new URL(process.env.APP_BASE_URL ?? "http://localhost:3000")
  .origin;

/**
 * Setup completion never reverts, so once seen it is cached for the process lifetime. "Required"
 * is never cached: right after setup the next page must already see it as done.
 */
let setupCompleted = false;

async function setupRequired(): Promise<boolean> {
  if (setupCompleted) return false;
  try {
    const res = await fetch(`${BACKEND}/api/v1/auth/status`, {
      cache: "no-store",
    });
    if (!res.ok) return false; // backend trouble: let the page render its own error state
    const status = (await res.json()) as { setupRequired: boolean };
    if (!status.setupRequired) {
      setupCompleted = true;
      return false;
    }
    return true;
  } catch {
    return false;
  }
}

interface Refreshed {
  setCookies: string[];
  cookies: Map<string, string>;
}

async function refresh(refreshToken: string): Promise<Refreshed | null> {
  try {
    const res = await fetch(`${BACKEND}/api/v1/auth/refresh`, {
      method: "POST",
      headers: {
        cookie: `${REFRESH_COOKIE}=${refreshToken}`,
        origin: APP_ORIGIN,
      },
      cache: "no-store",
    });
    if (!res.ok) return null;
    const setCookies = res.headers.getSetCookie();
    const cookies = new Map<string, string>();
    for (const header of setCookies) {
      const [pair] = header.split(";");
      const eq = pair.indexOf("=");
      cookies.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim());
    }
    return cookies.has(ACCESS_COOKIE) ? { setCookies, cookies } : null;
  } catch {
    return null;
  }
}

function redirect(request: NextRequest, to: string): NextResponse {
  return NextResponse.redirect(new URL(to, request.url));
}

export async function proxy(request: NextRequest): Promise<NextResponse> {
  const { pathname, search } = request.nextUrl;
  const decision = decide({
    pathname,
    pathWithSearch: pathname + search,
    setupRequired: await setupRequired(),
    access: decodeAccessToken(request.cookies.get(ACCESS_COOKIE)?.value),
    hasRefreshCookie: request.cookies.has(REFRESH_COOKIE),
    now: Math.floor(Date.now() / 1000),
  });

  if (decision.type === "redirect") return redirect(request, decision.to);
  if (decision.type === "next") return NextResponse.next();

  const refreshed = await refresh(request.cookies.get(REFRESH_COOKIE)!.value);
  if (!refreshed) {
    const response = redirect(request, loginRedirect(pathname + search));
    response.cookies.delete(ACCESS_COOKIE);
    response.cookies.delete(REFRESH_COOKIE);
    return response;
  }

  const access = decodeAccessToken(refreshed.cookies.get(ACCESS_COOKIE));
  const after = access
    ? authorize(pathname, access)
    : { type: "redirect" as const, to: "/login" };

  // Forward the new cookies to this request's rendering...
  const requestHeaders = new Headers(request.headers);
  const forwarded = new Map(
    request.cookies.getAll().map((c) => [c.name, c.value]),
  );
  refreshed.cookies.forEach((value, name) => forwarded.set(name, value));
  requestHeaders.set(
    "cookie",
    [...forwarded].map(([name, value]) => `${name}=${value}`).join("; "),
  );
  const response =
    after.type === "redirect"
      ? redirect(request, after.to)
      : NextResponse.next({ request: { headers: requestHeaders } });
  // ...and to the browser, with the backend's attributes (httpOnly, SameSite, Path, Secure).
  refreshed.setCookies.forEach((cookie) =>
    response.headers.append("set-cookie", cookie),
  );
  return response;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|img/|api/|favicon\\.ico).*)"],
};
