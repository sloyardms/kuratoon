/**
 * Auth cookie names and an unverified look at the access token (ADR 0013). The backend verifies
 * every token; the frontend only reads `exp` and `role` to decide when to refresh and for
 * optimistic redirects.
 */
export const ACCESS_COOKIE = "kuratoon_access";
export const REFRESH_COOKIE = "kuratoon_refresh";

export type Role = "ADMIN" | "USER";

export interface AccessClaims {
  /** Expiry, seconds since the epoch. */
  exp: number;
  role: Role;
}

/** Decodes the JWT payload without verifying it; null if the token is malformed. */
export function decodeAccessToken(
  token: string | undefined,
): AccessClaims | null {
  if (!token) return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const json = JSON.parse(
      atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "=")),
    );
    if (
      typeof json.exp !== "number" ||
      (json.role !== "ADMIN" && json.role !== "USER")
    ) {
      return null;
    }
    return { exp: json.exp, role: json.role };
  } catch {
    return null;
  }
}
