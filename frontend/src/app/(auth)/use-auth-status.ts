"use client";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { AuthStatus } from "@/lib/types";

/** Public status: whether setup, registration and password reset are available. */
export function useAuthStatus() {
  return useQuery({
    queryKey: ["auth-status"],
    queryFn: () => apiFetch<AuthStatus>("/api/v1/auth/status"),
  });
}

/** The `next` query parameter, only if it is a local path (no open redirects). */
export function safeNext(): string {
  if (typeof window === "undefined") return "/";
  const next = new URLSearchParams(window.location.search).get("next");
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/";
}
