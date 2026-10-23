"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import type { ReactNode } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { postJson } from "@/lib/api";
import type { IntegrityReport, RebuildResult } from "@/lib/types";

function Findings({
  title,
  items,
}: {
  title: string;
  items: { key: string; content: ReactNode }[];
}) {
  return (
    <section className="grid gap-1">
      <h3 className="font-medium">
        {title} ({items.length})
      </h3>
      {items.length === 0 ? (
        <p className="text-sm text-muted-foreground">None.</p>
      ) : (
        <ul className="list-disc pl-5 text-sm">
          {items.map((i) => (
            <li key={i.key}>{i.content}</li>
          ))}
        </ul>
      )}
    </section>
  );
}

const mediaLink = (id: string, title: string) => (
  <Link href={`/admin/media/${id}`} className="underline">
    {title}
  </Link>
);

/** Maintenance page (docs/spec/02-ingestion.md "Admin maintenance actions"). */
export default function MaintenancePage() {
  const scan = useMutation({
    mutationFn: () =>
      postJson<IntegrityReport>("/api/v1/admin/library/integrity-scan"),
    onError: (e) => toast.error(e.message),
  });
  const regenerate = useMutation({
    mutationFn: (ids: string[]) =>
      postJson<{ regenerated: unknown[]; failed: { reason: string }[] }>(
        "/api/v1/admin/library/thumbnails",
        { mediaIds: ids },
      ),
    onSuccess: (r) => {
      if (r.failed.length > 0)
        toast.warning(`${r.failed.length} failed: ${r.failed[0].reason}`);
      else toast.success(`Regenerated ${r.regenerated.length}`);
      scan.mutate();
    },
    onError: (e) => toast.error(e.message),
  });
  const rebuild = useMutation({
    mutationFn: () => postJson<RebuildResult>("/api/v1/admin/library/rebuild"),
    onSuccess: () => toast.success("Rebuild finished"),
    onError: (e) => toast.error(e.message),
  });

  const report = scan.data;
  const result = rebuild.data;

  return (
    <div className="mx-auto grid max-w-3xl gap-6">
      <h1 className="text-2xl font-semibold tracking-tight">Maintenance</h1>

      <Card>
        <CardHeader>
          <CardTitle>
            <h2>Integrity scan</h2>
          </CardTitle>
          <CardDescription>
            Compares the library folder with the database: folders without
            media, media without folders, missing pages and missing thumbnails.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <div>
            <Button disabled={scan.isPending} onClick={() => scan.mutate()}>
              {scan.isPending ? "Scanning…" : "Run scan"}
            </Button>
          </div>
          {report ? (
            <div className="grid gap-4" aria-live="polite">
              <p className="text-sm text-muted-foreground">
                {report.mediaChecked} media checked at{" "}
                {new Date(report.scannedAt).toLocaleString()}.
              </p>
              <Findings
                title="Folders without media"
                items={report.foldersWithoutRows.map((f) => ({
                  key: f.folder,
                  content: (
                    <>
                      {f.folder}
                      {f.title ? ` (${f.title})` : ""}
                      {f.hasSidecar ? " — can be restored by a rebuild" : ""}
                    </>
                  ),
                }))}
              />
              <Findings
                title="Media without folders"
                items={report.rowsWithoutFolders.map((m) => ({
                  key: m.id,
                  content: mediaLink(m.id, m.title),
                }))}
              />
              <Findings
                title="Missing pages"
                items={report.missingPages.map((p) => ({
                  key: `${p.mediaId}-${p.chapter}`,
                  content: (
                    <>
                      {mediaLink(p.mediaId, p.title)}, chapter {p.chapter}:{" "}
                      {p.missing} missing (e.g. {p.example})
                    </>
                  ),
                }))}
              />
              <Findings
                title="Missing thumbnails"
                items={report.missingThumbnails.map((m) => ({
                  key: m.id,
                  content: mediaLink(m.id, m.title),
                }))}
              />
              {report.missingThumbnails.length > 0 ? (
                <div>
                  <Button
                    variant="outline"
                    disabled={regenerate.isPending}
                    onClick={() =>
                      regenerate.mutate(
                        report.missingThumbnails.map((m) => m.id),
                      )
                    }
                  >
                    Regenerate thumbnails
                  </Button>
                </div>
              ) : null}
            </div>
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>
            <h2>Rebuild library from disk</h2>
          </CardTitle>
          <CardDescription>
            Restores every media folder whose kuratoon.yml sidecar describes a
            media missing from the database (same as importing a snapshot).
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <div>
            <Button
              variant="outline"
              disabled={rebuild.isPending}
              onClick={() => rebuild.mutate()}
            >
              {rebuild.isPending ? "Rebuilding…" : "Rebuild"}
            </Button>
          </div>
          {result ? (
            <div className="grid gap-4" aria-live="polite">
              <Findings
                title="Restored"
                items={result.restored.map((m) => ({
                  key: m.id,
                  content: mediaLink(m.id, m.title),
                }))}
              />
              <Findings
                title="Skipped"
                items={result.skipped.map((s) => ({
                  key: s.folder,
                  content: `${s.folder}: ${s.reason}`,
                }))}
              />
              {result.warnings.length > 0 ? (
                <Findings
                  title="Warnings"
                  items={result.warnings.map((w) => ({ key: w, content: w }))}
                />
              ) : null}
              {result.thumbnailsQueued > 0 ? (
                <p className="text-sm text-muted-foreground">
                  Generating thumbnails for {result.thumbnailsQueued} media in
                  the background.
                </p>
              ) : null}
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
