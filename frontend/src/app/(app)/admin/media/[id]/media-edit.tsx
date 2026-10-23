"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { use, useState } from "react";
import { toast } from "sonner";

import { CoverCropTool } from "@/components/cover-crop-tool";
import { MetadataForm } from "@/components/metadata-form";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
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
import { apiFetch, sendJson } from "@/lib/api";
import {
  changedFields,
  fromFormState,
  toFormState,
  type MetadataFormState,
} from "@/lib/metadata";
import type {
  CoverCrop,
  CoverView,
  MediaEditView,
  MediaField,
  MediaRow,
  OffsetPage,
} from "@/lib/types";

type Confirm =
  { kind: "chapter"; id: string; number: number } | { kind: "media" } | null;

/** Admin media edit page: field locking, cover crop, relations, chapters (docs/spec/02, 06). */
export function MediaEdit({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const queryClient = useQueryClient();
  const router = useRouter();
  const key = ["admin", "media", "edit", id];
  const media = useQuery({
    queryKey: key,
    queryFn: () => apiFetch<MediaEditView>(`/api/v1/admin/media/${id}`),
  });
  /** The admin's unsaved edits; null shows the saved values. */
  const [edits, setEdits] = useState<MetadataFormState | null>(null);
  const [crop, setCrop] = useState<CoverCrop | null>(null);
  const [confirm, setConfirm] = useState<Confirm>(null);

  const saved = (view: MediaEditView, message: string) => {
    queryClient.setQueryData(key, view);
    queryClient.invalidateQueries({
      queryKey: ["admin", "media"],
      exact: false,
    });
    setEdits(null);
    toast.success(message);
  };

  const run = useMutation({
    mutationFn: (fn: () => Promise<void>) => fn(),
    onError: (e) => toast.error(e.message),
  });

  if (media.isError) {
    return (
      <div className="flex items-center gap-3 text-destructive">
        Could not load this media.
        <Button variant="outline" size="sm" onClick={() => media.refetch()}>
          Retry
        </Button>
      </div>
    );
  }
  if (!media.data) {
    return <p className="text-muted-foreground">Loading…</p>;
  }
  const view = media.data;
  const original = toFormState(view.values);
  const form = edits ?? original;
  const changed = changedFields(original, form);

  const saveMetadata = () =>
    run.mutate(async () => {
      const next = await sendJson<MediaEditView>(
        "PATCH",
        `/api/v1/admin/media/${id}`,
        { fields: changed, values: fromFormState(form) },
      );
      saved(next, "Saved; edited fields are now locked");
    });

  const toggleLock = (field: MediaField) =>
    run.mutate(async () => {
      const locked = view.lockedFields.includes(field)
        ? view.lockedFields.filter((f) => f !== field)
        : [...view.lockedFields, field];
      const next = await sendJson<MediaEditView>(
        "PUT",
        `/api/v1/admin/media/${id}/locked-fields`,
        { fields: locked },
      );
      queryClient.setQueryData(key, next);
    });

  const coverSaved = (result: { cover: CoverView; warning: string | null }) => {
    queryClient.setQueryData(key, { ...view, cover: result.cover });
    setCrop(null);
    if (result.warning) toast.warning(result.warning);
    else toast.success("Cover saved");
  };

  return (
    <div className="mx-auto grid max-w-5xl gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm text-muted-foreground">
            <Link
              href="/admin/media"
              className="underline-offset-4 hover:underline"
            >
              Media
            </Link>
          </p>
          <h1 className="text-2xl font-semibold tracking-tight">
            {view.values.title}
          </h1>
          {view.sourceUrl ? (
            <p className="text-sm break-all text-muted-foreground">
              {view.sourceUrl}
            </p>
          ) : null}
        </div>
        <Button
          variant="destructive"
          onClick={() => setConfirm({ kind: "media" })}
        >
          Delete media
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>
            <h2>Metadata</h2>
          </CardTitle>
          <CardDescription>
            Saving locks every edited field so re-scraping never overwrites it.
            Use the lock buttons to unlock a field.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <MetadataForm
            idPrefix="edit"
            value={form}
            onChange={setEdits}
            locked={view.lockedFields}
            onToggleLock={toggleLock}
          />
          <div className="flex gap-2">
            <Button
              disabled={changed.length === 0 || run.isPending}
              onClick={saveMetadata}
            >
              Save {changed.length > 0 ? `(${changed.length})` : ""}
            </Button>
            <Button
              variant="outline"
              disabled={changed.length === 0}
              onClick={() => setEdits(null)}
            >
              Reset
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>
            <h2>Cover</h2>
          </CardTitle>
          <CardDescription>
            Drag the 2:3 frame; thumbnails are regenerated from the untouched
            original.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          {view.cover.originalUrl && view.cover.width > 0 ? (
            <CoverCropTool
              key={view.cover.version}
              src={view.cover.originalUrl}
              imageWidth={view.cover.width}
              imageHeight={view.cover.height}
              initial={view.cover.crop}
              onChange={setCrop}
            />
          ) : (
            <p className="text-muted-foreground">No readable cover.</p>
          )}
          {!view.cover.thumbnails && view.cover.file ? (
            <p className="text-sm text-destructive">Thumbnails are missing.</p>
          ) : null}
          <div className="flex flex-wrap items-center gap-2">
            <Button
              disabled={crop === null || run.isPending}
              onClick={() =>
                run.mutate(async () =>
                  coverSaved(
                    await sendJson(
                      "PUT",
                      `/api/v1/admin/media/${id}/cover-crop`,
                      {
                        crop,
                      },
                    ),
                  ),
                )
              }
            >
              Save crop
            </Button>
            <Label htmlFor="cover-upload" className="sr-only">
              Replace cover
            </Label>
            <Input
              id="cover-upload"
              type="file"
              accept="image/jpeg,image/png,image/webp,image/avif,image/gif"
              className="max-w-xs"
              onChange={(e) => {
                const file = e.target.files?.[0];
                e.target.value = "";
                if (!file) return;
                const body = new FormData();
                body.append("file", file);
                run.mutate(async () =>
                  coverSaved(
                    await apiFetch(`/api/v1/admin/media/${id}/cover`, {
                      method: "POST",
                      body,
                    }),
                  ),
                );
              }}
            />
          </div>
        </CardContent>
      </Card>

      <Relations view={view} onSaved={(v) => saved(v, "Relations saved")} />

      <Card>
        <CardHeader>
          <CardTitle>
            <h2>Chapters</h2>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Number</TableHead>
                <TableHead>Title</TableHead>
                <TableHead>Pages</TableHead>
                <TableHead>Added</TableHead>
                <TableHead className="w-24">
                  <span className="sr-only">Actions</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {view.chapters.map((c) => (
                <TableRow key={c.id}>
                  <TableCell>{c.number}</TableCell>
                  <TableCell>{c.title ?? "—"}</TableCell>
                  <TableCell>{c.pageCount}</TableCell>
                  <TableCell>
                    {new Date(c.createdAt).toLocaleDateString()}
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() =>
                        setConfirm({
                          kind: "chapter",
                          id: c.id,
                          number: c.number,
                        })
                      }
                    >
                      Delete
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <AlertDialog
        open={confirm !== null}
        onOpenChange={(o) => !o && setConfirm(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {confirm?.kind === "chapter"
                ? `Delete chapter ${confirm.number}?`
                : `Delete ${view.values.title}?`}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {confirm?.kind === "chapter"
                ? "Its pages and every user's progress for it are deleted."
                : "The library folder, thumbnails and all reading data of this media are deleted. This cannot be undone."}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={() => {
                const c = confirm;
                setConfirm(null);
                if (c?.kind === "chapter") {
                  run.mutate(async () =>
                    saved(
                      await sendJson<MediaEditView>(
                        "DELETE",
                        `/api/v1/admin/media/${id}/chapters/${c.id}`,
                      ),
                      "Chapter deleted",
                    ),
                  );
                } else if (c?.kind === "media") {
                  run.mutate(async () => {
                    await sendJson("DELETE", `/api/v1/admin/media/${id}`);
                    toast.success("Media deleted");
                    queryClient.invalidateQueries({
                      queryKey: ["admin", "media"],
                    });
                    router.push("/admin/media");
                  });
                }
              }}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

const RELATION_TYPES = [
  { value: "SEQUEL", label: "Sequel" },
  { value: "SIDE_STORY", label: "Side story" },
  { value: "SPIN_OFF", label: "Spin-off" },
] as const;

/** Relations stored on this media (sequels, side stories, spin-offs of it); others read-only. */
function Relations({
  view,
  onSaved,
}: {
  view: MediaEditView;
  onSaved: (view: MediaEditView) => void;
}) {
  const [q, setQ] = useState("");
  const [type, setType] =
    useState<(typeof RELATION_TYPES)[number]["value"]>("SEQUEL");
  const outgoing = view.relations.filter((r) => r.direction === "OUTGOING");
  const incoming = view.relations.filter((r) => r.direction === "INCOMING");
  const search = useQuery({
    queryKey: ["admin", "media", "relation-search", q],
    queryFn: () =>
      apiFetch<OffsetPage<MediaRow>>(
        `/api/v1/admin/media?${new URLSearchParams({ q, size: "8" })}`,
      ),
    enabled: q.trim().length >= 2,
  });
  const save = useMutation({
    mutationFn: (relations: { type: string; mediaId: string }[]) =>
      sendJson<MediaEditView>(
        "PUT",
        `/api/v1/admin/media/${view.id}/relations`,
        {
          relations,
        },
      ),
    onSuccess: (next) => {
      setQ("");
      onSaved(next);
    },
    onError: (e) => toast.error(e.message),
  });
  const current = outgoing.map((r) => ({ type: r.type, mediaId: r.mediaId }));

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h2>Related media</h2>
        </CardTitle>
        <CardDescription>
          Media that are a sequel, side story or spin-off of this one.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        <ul className="grid gap-1">
          {outgoing.map((r) => (
            <li key={r.id} className="flex items-center gap-2 text-sm">
              <span className="min-w-24 text-muted-foreground">
                {r.label.replace("_", " ").toLowerCase()}
              </span>
              <Link href={`/admin/media/${r.mediaId}`} className="underline">
                {r.title}
              </Link>
              <Button
                variant="ghost"
                size="sm"
                onClick={() =>
                  save.mutate(current.filter((c) => c.mediaId !== r.mediaId))
                }
              >
                Remove
              </Button>
            </li>
          ))}
          {incoming.map((r) => (
            <li key={r.id} className="flex items-center gap-2 text-sm">
              <span className="min-w-24 text-muted-foreground">
                {r.label.replace("_", " ").toLowerCase()}
              </span>
              <Link href={`/admin/media/${r.mediaId}`} className="underline">
                {r.title}
              </Link>
              <span className="text-muted-foreground">
                (edit on that media)
              </span>
            </li>
          ))}
          {view.relations.length === 0 ? (
            <li className="text-sm text-muted-foreground">No related media.</li>
          ) : null}
        </ul>
        <div className="flex flex-wrap items-center gap-2">
          <Label htmlFor="relation-type" className="sr-only">
            Relation
          </Label>
          <select
            id="relation-type"
            className="h-8 rounded-lg border border-input bg-transparent px-2 text-sm dark:bg-input/30"
            value={type}
            onChange={(e) => setType(e.target.value as typeof type)}
          >
            {RELATION_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
          <Label htmlFor="relation-search" className="sr-only">
            Find media
          </Label>
          <Input
            id="relation-search"
            className="max-w-xs"
            placeholder="Find media by title"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </div>
        {search.data ? (
          <ul className="grid gap-1">
            {search.data.items
              .filter((m) => m.id !== view.id)
              .map((m) => (
                <li key={m.id}>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={save.isPending}
                    onClick={() =>
                      save.mutate([...current, { type, mediaId: m.id }])
                    }
                  >
                    Add “{m.title}”
                  </Button>
                </li>
              ))}
          </ul>
        ) : null}
      </CardContent>
    </Card>
  );
}
