import { describe, expect, it } from "vitest";

import { decide, type ProxyInput } from "./proxy-decision";

const NOW = 1_800_000_000;

function input(overrides: Partial<ProxyInput>): ProxyInput {
  return {
    pathname: "/",
    pathWithSearch: "/",
    setupRequired: false,
    access: { exp: NOW + 600, role: "USER" },
    hasRefreshCookie: true,
    now: NOW,
    ...overrides,
  };
}

describe("proxy decision", () => {
  it("sends everything to /setup while setup is required", () => {
    expect(
      decide(input({ setupRequired: true, pathname: "/admin/users" })),
    ).toEqual({
      type: "redirect",
      to: "/setup",
    });
    expect(decide(input({ setupRequired: true, pathname: "/login" }))).toEqual({
      type: "redirect",
      to: "/setup",
    });
    expect(
      decide(input({ setupRequired: true, pathname: "/setup", access: null })),
    ).toEqual({
      type: "next",
    });
  });

  it("closes /setup once setup is done", () => {
    expect(decide(input({ pathname: "/setup" }))).toEqual({
      type: "redirect",
      to: "/login",
    });
  });

  it("leaves public pages alone, even without cookies", () => {
    for (const pathname of [
      "/login",
      "/register",
      "/forgot-password",
      "/reset-password",
    ]) {
      expect(
        decide(input({ pathname, access: null, hasRefreshCookie: false })),
      ).toEqual({
        type: "next",
      });
    }
  });

  it("passes a valid access token with at least 60 s left", () => {
    expect(decide(input({ access: { exp: NOW + 60, role: "USER" } }))).toEqual({
      type: "next",
    });
  });

  it("refreshes an access token that expires in less than 60 s", () => {
    expect(decide(input({ access: { exp: NOW + 59, role: "USER" } }))).toEqual({
      type: "refresh",
    });
  });

  it("refreshes when the access token is expired or missing but a refresh cookie exists", () => {
    expect(decide(input({ access: { exp: NOW - 5, role: "USER" } }))).toEqual({
      type: "refresh",
    });
    expect(decide(input({ access: null }))).toEqual({ type: "refresh" });
  });

  it("redirects to login with the original path when there are no cookies", () => {
    expect(
      decide(
        input({
          pathname: "/admin/users",
          pathWithSearch: "/admin/users?q=bob",
          access: null,
          hasRefreshCookie: false,
        }),
      ),
    ).toEqual({
      type: "redirect",
      to: "/login?next=%2Fadmin%2Fusers%3Fq%3Dbob",
    });
    expect(decide(input({ access: null, hasRefreshCookie: false }))).toEqual({
      type: "redirect",
      to: "/login",
    });
  });

  it("keeps non-admins out of /admin", () => {
    expect(decide(input({ pathname: "/admin/settings" }))).toEqual({
      type: "redirect",
      to: "/",
    });
    expect(
      decide(
        input({
          pathname: "/admin/settings",
          access: { exp: NOW + 600, role: "ADMIN" },
        }),
      ),
    ).toEqual({ type: "next" });
    // "/administrator" is not the admin area.
    expect(decide(input({ pathname: "/administrator" }))).toEqual({
      type: "next",
    });
  });
});
