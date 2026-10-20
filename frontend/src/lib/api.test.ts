import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError, apiFetch, navigation } from "./api";

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("apiFetch", () => {
  const fetchMock = vi.fn<typeof fetch>();
  const toLogin = vi.spyOn(navigation, "toLogin").mockImplementation(() => {});

  beforeEach(() => {
    fetchMock.mockReset();
    toLogin.mockClear();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sends cookies and parses JSON", async () => {
    fetchMock.mockResolvedValueOnce(json(200, { username: "admin" }));

    await expect(apiFetch("/api/v1/me")).resolves.toEqual({
      username: "admin",
    });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/me",
      expect.objectContaining({ credentials: "include" }),
    );
  });

  it("refreshes once for many concurrent 401s and retries each request once", async () => {
    let refreshed = false;
    let refreshCalls = 0;
    let releaseRefresh: () => void = () => {};
    fetchMock.mockImplementation(async (input) => {
      const url = String(input);
      if (url === "/api/v1/auth/refresh") {
        refreshCalls++;
        await new Promise<void>((resolve) => (releaseRefresh = resolve));
        refreshed = true;
        return new Response(null, { status: 200 });
      }
      return refreshed ? json(200, { url }) : json(401, { status: 401 });
    });

    const calls = ["/api/v1/a", "/api/v1/b", "/api/v1/c"].map((p) =>
      apiFetch(p),
    );
    await vi.waitFor(() => expect(refreshCalls).toBe(1));
    releaseRefresh();

    await expect(Promise.all(calls)).resolves.toEqual([
      { url: "/api/v1/a" },
      { url: "/api/v1/b" },
      { url: "/api/v1/c" },
    ]);
    expect(refreshCalls).toBe(1);
    // 3 failed attempts + 1 refresh + 3 retries
    expect(fetchMock).toHaveBeenCalledTimes(7);
    expect(toLogin).not.toHaveBeenCalled();
  });

  it("goes to login when the refresh fails", async () => {
    fetchMock.mockImplementation(async (input) =>
      String(input) === "/api/v1/auth/refresh"
        ? new Response(null, { status: 401 })
        : json(401, { status: 401, detail: "Authentication required." }),
    );

    await expect(apiFetch("/api/v1/me")).rejects.toBeInstanceOf(ApiError);
    expect(toLogin).toHaveBeenCalledTimes(1);
    // The original request is not retried after a failed refresh.
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("does not refresh for auth endpoints and surfaces Problem Details", async () => {
    fetchMock.mockResolvedValueOnce(
      json(401, { status: 401, detail: "Invalid username or password." }),
    );

    const error = (await apiFetch("/api/v1/auth/login", {
      method: "POST",
      body: "{}",
    }).catch((e) => e)) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(401);
    expect(error.message).toBe("Invalid username or password.");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(toLogin).not.toHaveBeenCalled();
  });

  it("resolves 204 responses to undefined", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await expect(
      apiFetch("/api/v1/x", { method: "DELETE" }),
    ).resolves.toBeUndefined();
  });
});
