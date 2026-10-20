"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { Pagination } from "@/components/pagination";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { apiFetch } from "@/lib/api";
import type { AuditEntry, OffsetPage, User } from "@/lib/types";
import { pageParam, useUrlParams } from "@/lib/use-url-params";

import { AuditFilters, filtersFromParams } from "./audit-filters";

const PAGE_SIZE = 50;

/** Audit log page (docs/spec/04-auth-users.md "Audit"): newest first, filters in the URL. */
export function AuditLog() {
  const { params, update } = useUrlParams();
  const filters = filtersFromParams(params);
  const page = pageParam(params);

  const entries = useQuery({
    queryKey: ["admin", "audit-log", filters, page],
    queryFn: () => {
      const query = new URLSearchParams({
        page: String(page),
        size: String(PAGE_SIZE),
      });
      for (const [key, value] of Object.entries(filters))
        if (value) query.set(key, value);
      return apiFetch<OffsetPage<AuditEntry>>(
        `/api/v1/admin/audit-log?${query}`,
      );
    },
    placeholderData: keepPreviousData,
  });
  const admins = useQuery({
    queryKey: ["admin", "users", "all-for-filter"],
    queryFn: () => apiFetch<OffsetPage<User>>("/api/v1/admin/users?size=100"),
  });

  return (
    <div className="grid gap-4">
      <h1 className="text-2xl font-semibold tracking-tight">Audit log</h1>
      <AuditFilters
        // Re-mount when the URL changes (back/forward) so the inputs show the applied filters.
        key={params.toString()}
        value={filters}
        actors={admins.data?.items ?? []}
        onApply={(next) => update({ ...next, page: null })}
      />
      {entries.isError ? (
        <div className="flex items-center gap-3 text-destructive">
          Could not load the audit log.
          <Button variant="outline" size="sm" onClick={() => entries.refetch()}>
            Retry
          </Button>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>When</TableHead>
              <TableHead>Actor</TableHead>
              <TableHead>Action</TableHead>
              <TableHead>Target</TableHead>
              <TableHead>Details</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {entries.isPending ? (
              <TableRow>
                <TableCell colSpan={5} className="text-muted-foreground">
                  Loading…
                </TableCell>
              </TableRow>
            ) : entries.data.items.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-muted-foreground">
                  No entries.
                </TableCell>
              </TableRow>
            ) : (
              entries.data.items.map((entry) => (
                <TableRow key={entry.id}>
                  <TableCell className="whitespace-nowrap">
                    {new Date(entry.createdAt).toLocaleString()}
                  </TableCell>
                  <TableCell>{entry.actor?.username ?? "system"}</TableCell>
                  <TableCell className="font-mono text-xs">
                    {entry.action}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {entry.targetType.replace("_", " ")}
                  </TableCell>
                  <TableCell
                    className="max-w-md truncate font-mono text-xs"
                    title={JSON.stringify(entry.details)}
                  >
                    {entry.details ? JSON.stringify(entry.details) : "—"}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      )}
      {entries.data ? (
        <Pagination
          page={entries.data.page}
          totalPages={entries.data.totalPages}
          totalItems={entries.data.totalItems}
          onPage={(p) => update({ page: p || null })}
        />
      ) : null}
    </div>
  );
}
