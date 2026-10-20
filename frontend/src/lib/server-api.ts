import "server-only";

import { cookies } from "next/headers";

import { BACKEND_INTERNAL_URL } from "./server-env";

/**
 * GET from the backend during server rendering, forwarding the request's cookies (ADR 0013).
 * `proxy.ts` has already refreshed an expiring access cookie for this request.
 */
export async function serverGet<T>(
  path: string,
): Promise<{ status: number; data: T | null }> {
  const cookieHeader = (await cookies()).toString();
  const res = await fetch(`${BACKEND_INTERNAL_URL}${path}`, {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
    cache: "no-store",
  });
  if (!res.ok) {
    return { status: res.status, data: null };
  }
  return { status: res.status, data: (await res.json()) as T };
}
