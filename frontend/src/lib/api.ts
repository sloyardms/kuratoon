/**
 * Browser API client (ADR 0013). Requests go to `/api` on the same origin with the httpOnly auth
 * cookies. On a 401 it runs one shared refresh for all concurrent callers, retries the request
 * once, and sends the user to `/login` if the refresh fails.
 */

/** RFC 9457 Problem Details as returned by the backend. */
export interface Problem {
  status: number;
  title?: string;
  detail?: string;
  errors?: { field: string; message: string }[];
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: Problem | null,
  ) {
    super(problem?.detail ?? problem?.title ?? `Request failed (${status})`);
    this.name = "ApiError";
  }
}

const REFRESH_URL = "/api/v1/auth/refresh";

let refreshing: Promise<boolean> | null = null;

/** One refresh at a time; concurrent 401s share its result. */
export function refreshSession(): Promise<boolean> {
  refreshing ??= fetch(REFRESH_URL, { method: "POST", credentials: "include" })
    .then((res) => res.ok)
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

/** Where to go when the session cannot be refreshed; replaceable in tests. */
export const navigation = {
  toLogin() {
    const next = window.location.pathname + window.location.search;
    window.location.assign(
      next === "/" || next.startsWith("/login")
        ? "/login"
        : `/login?next=${encodeURIComponent(next)}`,
    );
  },
};

function isAuthEndpoint(path: string): boolean {
  return path.startsWith("/api/v1/auth/");
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers);
  if (init.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  return fetch(path, { ...init, headers, credentials: "include" });
}

async function toError(res: Response): Promise<ApiError> {
  let problem: Problem | null = null;
  try {
    problem = (await res.json()) as Problem;
  } catch {
    problem = null;
  }
  return new ApiError(res.status, problem);
}

/** Fetches `path` (e.g. `/api/v1/me`) and parses JSON; 204 resolves to `undefined`. */
export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  let res = await send(path, init);
  if (res.status === 401 && !isAuthEndpoint(path)) {
    if (await refreshSession()) {
      res = await send(path, init);
    }
    if (res.status === 401) {
      navigation.toLogin();
      throw await toError(res);
    }
  }
  if (!res.ok) {
    throw await toError(res);
  }
  if (res.status === 204 || res.headers.get("Content-Length") === "0") {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export function postJson<T>(path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, {
    method: "POST",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export function sendJson<T>(
  method: "PUT" | "PATCH" | "DELETE",
  path: string,
  body?: unknown,
) {
  return apiFetch<T>(path, {
    method,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}
