"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { Pagination } from "@/components/pagination";
import { Badge } from "@/components/ui/badge";
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
import type { ImportJobSummary, OffsetPage } from "@/lib/types";
import { pageParam, useUrlParams } from "@/lib/use-url-params";

import { ImportJobDialog } from "./import-job-dialog";
import {
  STATUS_FILTERS,
  STATUS_LABELS,
  isActive,
  statusVariant,
  statusesFor,
} from "./import-status";

const PAGE_SIZE = 25;

/** Imports admin page (docs/spec/02-ingestion.md "Job lifecycle"). */
export function ImportsAdmin() {
  const { params, update } = useUrlParams();
  const filter = params.get("status") ?? "";
  const page = pageParam(params);
  const openJob = params.get("job");

  const jobs = useQuery({
    queryKey: ["admin", "imports", { filter, page }],
    queryFn: () => {
      const query = new URLSearchParams({
        page: String(page),
        size: String(PAGE_SIZE),
      });
      for (const s of statusesFor(filter)) query.append("status", s);
      return apiFetch<OffsetPage<ImportJobSummary>>(
        `/api/v1/admin/imports?${query}`,
      );
    },
    placeholderData: keepPreviousData,
    // Follow jobs the worker is still processing.
    refetchInterval: (q) => (q.state.data?.items.some(isActive) ? 3000 : false),
  });

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">Imports</h1>
        <p className="text-sm text-muted-foreground">
          Folders in the import directory are picked up automatically once they
          stop changing.
        </p>
      </div>

      <div
        role="group"
        aria-label="Filter by status"
        className="flex flex-wrap gap-2"
      >
        {STATUS_FILTERS.map((f) => (
          <Button
            key={f.value}
            size="sm"
            variant={filter === f.value ? "default" : "outline"}
            aria-pressed={filter === f.value}
            onClick={() => update({ status: f.value, page: null })}
          >
            {f.label}
          </Button>
        ))}
      </div>

      {jobs.isError ? (
        <div className="flex items-center gap-3 text-destructive">
          Could not load imports.
          <Button variant="outline" size="sm" onClick={() => jobs.refetch()}>
            Retry
          </Button>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Folder</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Kind</TableHead>
              <TableHead>Media</TableHead>
              <TableHead>Message</TableHead>
              <TableHead>Updated</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {jobs.isPending ? (
              <TableRow>
                <TableCell colSpan={6} className="text-muted-foreground">
                  Loading…
                </TableCell>
              </TableRow>
            ) : jobs.data.items.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-muted-foreground">
                  No imports here.
                </TableCell>
              </TableRow>
            ) : (
              jobs.data.items.map((job) => (
                <TableRow key={job.id}>
                  <TableCell className="font-medium">
                    <button
                      type="button"
                      className="text-left underline-offset-4 hover:underline focus-visible:underline"
                      onClick={() => update({ job: job.id })}
                    >
                      {job.sourceFolder}
                    </button>
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(job.status)}>
                      {STATUS_LABELS[job.status]}
                    </Badge>
                  </TableCell>
                  <TableCell>{job.kind?.toLowerCase() ?? "—"}</TableCell>
                  <TableCell>{job.mediaTitle ?? "—"}</TableCell>
                  <TableCell
                    className="max-w-md truncate"
                    title={job.message ?? undefined}
                  >
                    {job.message ?? "—"}
                  </TableCell>
                  <TableCell>
                    {new Date(job.updatedAt).toLocaleString()}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      )}

      {jobs.data ? (
        <Pagination
          page={jobs.data.page}
          totalPages={jobs.data.totalPages}
          totalItems={jobs.data.totalItems}
          onPage={(p) => update({ page: p || null })}
        />
      ) : null}

      <ImportJobDialog jobId={openJob} onClose={() => update({ job: null })} />
    </div>
  );
}
