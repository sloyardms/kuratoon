"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback } from "react";

/**
 * Filters, paging and search live in the URL (frontend/CLAUDE.md), so back/forward restores them.
 * `update` merges the given values; empty strings and nulls remove a parameter.
 */
export function useUrlParams() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const update = useCallback(
    (values: Record<string, string | number | null | undefined>) => {
      const next = new URLSearchParams(params.toString());
      for (const [key, value] of Object.entries(values)) {
        if (value === null || value === undefined || value === "")
          next.delete(key);
        else next.set(key, String(value));
      }
      const query = next.toString();
      router.replace(query ? `${pathname}?${query}` : pathname, {
        scroll: false,
      });
    },
    [params, router, pathname],
  );

  return { params, update };
}

/** Page number from `?page=` (0-based), never negative. */
export function pageParam(params: URLSearchParams): number {
  const page = Number(params.get("page") ?? 0);
  return Number.isInteger(page) && page > 0 ? page : 0;
}
