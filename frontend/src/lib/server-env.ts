import "server-only";

/**
 * Server-side settings. `BACKEND_INTERNAL_URL` is compose wiring (`http://backend:8080`), not an
 * installer setting; `APP_BASE_URL` is the public URL, whose origin the backend's Origin check
 * expects on non-GET requests.
 */
export const BACKEND_INTERNAL_URL = (
  process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080"
).replace(/\/$/, "");

export const APP_ORIGIN = new URL(
  process.env.APP_BASE_URL ?? "http://localhost:3000",
).origin;
