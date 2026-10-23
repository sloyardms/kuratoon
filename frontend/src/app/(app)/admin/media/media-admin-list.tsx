"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import Link from "next/link";

import { useMediaTypes } from "@/components/metadata-form";
import { Pagination } from "@/components/pagination";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { apiFetch } from "@/lib/api";
import type { MediaRow, OffsetPage } from "@/lib/types";
import { pageParam, useUrlParams } from "@/lib/use-url-params";

const PAGE_SIZE = 25;

/** Admin media list: search and type filter, links to the edit page. */
export function MediaAdminList() {
  const { params, update } = useUrlParams();
  const q = params.get("q") ?? "";
  const type = params.get("type") ?? "";
  const page = pageParam(params);
  const types = useMediaTypes();

  const media = useQuery({
    queryKey: ["admin", "media", { q, type, page }],
    queryFn: () => {
      const query = new URLSearchParams({
        q,
        page: String(page),
        size: String(PAGE_SIZE),
      });
      if (type) query.set("type", type);
      return apiFetch<OffsetPage<MediaRow>>(`/api/v1/admin/media?${query}`);
    },
    placeholderData: keepPreviousData,
  });

  return (
    <div className="grid gap-4">
      <h1 className="text-2xl font-semibold tracking-tight">Media</h1>

      <form
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          update({
            q: String(new FormData(e.currentTarget).get("q") ?? ""),
            page: null,
          });
        }}
        className="flex flex-wrap gap-2"
      >
        <Label htmlFor="q" className="sr-only">
          Search titles
        </Label>
        <Input
          id="q"
          name="q"
          defaultValue={q}
          placeholder="Search titles"
          className="max-w-xs"
        />
        <Label htmlFor="type" className="sr-only">
          Type
        </Label>
        <select
          id="type"
          className="rounded-lg border border-input bg-transparent px-2.5 text-sm dark:bg-input/30"
          value={type}
          onChange={(e) => update({ type: e.target.value, page: null })}
        >
          <option value="">All types</option>
          {types.data?.items.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
        <Button type="submit" variant="outline">
          Search
        </Button>
      </form>

      {media.isError ? (
        <div className="flex items-center gap-3 text-destructive">
          Could not load media.
          <Button variant="outline" size="sm" onClick={() => media.refetch()}>
            Retry
          </Button>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Title</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Chapters</TableHead>
              <TableHead>Latest chapter</TableHead>
              <TableHead>Added</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {media.isPending ? (
              <TableRow>
                <TableCell colSpan={5} className="text-muted-foreground">
                  Loading…
                </TableCell>
              </TableRow>
            ) : media.data.items.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-muted-foreground">
                  No media match.
                </TableCell>
              </TableRow>
            ) : (
              media.data.items.map((m) => (
                <TableRow key={m.id}>
                  <TableCell className="font-medium">
                    <Link
                      href={`/admin/media/${m.id}`}
                      className="underline-offset-4 hover:underline"
                    >
                      {m.title}
                    </Link>
                  </TableCell>
                  <TableCell>{m.type.name}</TableCell>
                  <TableCell>{m.chapterCount}</TableCell>
                  <TableCell>
                    {m.latestChapterAddedAt
                      ? new Date(m.latestChapterAddedAt).toLocaleDateString()
                      : "—"}
                  </TableCell>
                  <TableCell>
                    {new Date(m.createdAt).toLocaleDateString()}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      )}

      {media.data ? (
        <Pagination
          page={media.data.page}
          totalPages={media.data.totalPages}
          totalItems={media.data.totalItems}
          onPage={(p) => update({ page: p || null })}
        />
      ) : null}
    </div>
  );
}
