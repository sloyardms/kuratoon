"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";

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
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { apiFetch, postJson, sendJson } from "@/lib/api";
import {
  fromFormState,
  toFormState,
  validateForCreate,
  type MetadataFormState,
} from "@/lib/metadata";
import type { ImportJobDetail, MediaField } from "@/lib/types";

import { STATUS_LABELS, isActive, statusVariant } from "./import-status";

type Choice = "SKIP" | "REPLACE";

/** One import job: chapter table, warnings and the actions the API allows for it. */
export function ImportJobDialog({
  jobId,
  onClose,
}: {
  jobId: string | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const job = useQuery({
    queryKey: ["admin", "imports", "job", jobId],
    queryFn: () => apiFetch<ImportJobDetail>(`/api/v1/admin/imports/${jobId}`),
    enabled: jobId !== null,
    refetchInterval: (q) =>
      q.state.data && isActive(q.state.data.job) ? 3000 : false,
  });
  const [form, setForm] = useState<MetadataFormState | null>(null);
  const [errors, setErrors] = useState<Partial<Record<MediaField, string>>>({});
  const [choices, setChoices] = useState<Record<string, Choice>>({});
  const [confirmDismiss, setConfirmDismiss] = useState(false);

  const act = useMutation({
    mutationFn: (run: () => Promise<ImportJobDetail>) => run(),
    onSuccess: (detail) => {
      queryClient.setQueryData(["admin", "imports", "job", jobId], detail);
      queryClient.invalidateQueries({ queryKey: ["admin", "imports"] });
      setForm(null);
      setChoices({});
      toast.success(STATUS_LABELS[detail.job.status]);
    },
    onError: (e) => toast.error(e.message),
  });

  const close = () => {
    setForm(null);
    setErrors({});
    setChoices({});
    onClose();
  };

  const d = job.data;
  const can = (a: string) => d?.job.actions.includes(a as never) ?? false;
  const base = `/api/v1/admin/imports/${jobId}`;

  const submitMetadata = () => {
    if (!form) return;
    const fields = fromFormState(form);
    const problems = validateForCreate(fields);
    setErrors(problems);
    if (Object.keys(problems).length > 0) return;
    act.mutate(() =>
      sendJson<ImportJobDetail>("PUT", `${base}/metadata`, fields),
    );
  };

  return (
    <Dialog open={jobId !== null} onOpenChange={(o) => !o && close()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{d?.job.sourceFolder ?? "Import"}</DialogTitle>
          <DialogDescription>
            {d ? (
              <span className="flex flex-wrap items-center gap-2">
                <Badge variant={statusVariant(d.job.status)}>
                  {STATUS_LABELS[d.job.status]}
                </Badge>
                {d.job.kind ? <span>{d.job.kind.toLowerCase()}</span> : null}
                <span>attempt {d.job.attempts}</span>
              </span>
            ) : (
              "Loading…"
            )}
          </DialogDescription>
        </DialogHeader>

        {job.isError ? (
          <p className="text-destructive">Could not load the job.</p>
        ) : d ? (
          <div className="grid gap-4">
            {d.job.message ? <p role="status">{d.job.message}</p> : null}
            {d.url ? (
              <p className="text-sm break-all text-muted-foreground">
                URL: {d.url}
              </p>
            ) : null}
            {d.job.mediaId && d.job.mediaTitle ? (
              <p className="text-sm">
                Media:{" "}
                <Link
                  className="underline"
                  href={`/admin/media/${d.job.mediaId}`}
                >
                  {d.job.mediaTitle}
                </Link>
              </p>
            ) : null}
            {d.warnings.length > 0 ? (
              <ul className="list-disc pl-5 text-sm text-muted-foreground">
                {d.warnings.map((w) => (
                  <li key={w}>{w}</li>
                ))}
              </ul>
            ) : null}

            {d.chapters.length > 0 ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Folder</TableHead>
                    <TableHead>Number</TableHead>
                    <TableHead>Title</TableHead>
                    <TableHead>Pages</TableHead>
                    <TableHead>Warnings</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {d.chapters.map((c) => (
                    <TableRow key={c.sourceFolder || "(pages)"}>
                      <TableCell>{c.sourceFolder || "(folder root)"}</TableCell>
                      <TableCell>{c.number ?? "—"}</TableCell>
                      <TableCell>{c.title ?? "—"}</TableCell>
                      <TableCell>{c.pageCount}</TableCell>
                      <TableCell className="text-destructive">
                        {c.warnings.join(" ")}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : null}

            {can("RESOLVE_CONFLICT") ? (
              <section aria-labelledby="conflicts-title" className="grid gap-2">
                <h3 id="conflicts-title" className="font-medium">
                  Chapters that already exist
                </h3>
                {d.conflicts.map((c) => (
                  <fieldset
                    key={c.folder}
                    className="flex flex-wrap items-center gap-3 text-sm"
                  >
                    <legend className="sr-only">Chapter {c.folder}</legend>
                    <span className="min-w-40">
                      Chapter {c.folder}: {c.existingPageCount} →{" "}
                      {c.newPageCount} pages
                    </span>
                    {(["SKIP", "REPLACE"] as const).map((choice) => (
                      <label key={choice} className="flex items-center gap-1">
                        <input
                          type="radio"
                          name={`conflict-${c.folder}`}
                          checked={choices[c.folder] === choice}
                          onChange={() =>
                            setChoices({ ...choices, [c.folder]: choice })
                          }
                        />
                        {choice === "SKIP" ? "Skip" : "Replace"}
                      </label>
                    ))}
                  </fieldset>
                ))}
                <div>
                  <Button
                    disabled={
                      act.isPending ||
                      d.conflicts.some((c) => !choices[c.folder])
                    }
                    onClick={() =>
                      act.mutate(() =>
                        postJson<ImportJobDetail>(`${base}/conflicts`, {
                          choices,
                        }),
                      )
                    }
                  >
                    Apply and import
                  </Button>
                </div>
              </section>
            ) : null}

            {can("RESOLVE_DUPLICATE") && d.duplicate ? (
              <section className="flex flex-wrap items-center gap-2">
                <span className="text-sm">
                  Possible duplicate of{" "}
                  <Link
                    className="underline"
                    href={`/admin/media/${d.duplicate.mediaId}`}
                  >
                    {d.duplicate.title}
                  </Link>
                  .
                </span>
                <Button
                  variant="outline"
                  disabled={act.isPending}
                  onClick={() =>
                    act.mutate(() =>
                      postJson<ImportJobDetail>(`${base}/duplicate`, {
                        choice: "UPDATE_EXISTING",
                      }),
                    )
                  }
                >
                  Update existing
                </Button>
                <Button
                  variant="outline"
                  disabled={act.isPending}
                  onClick={() =>
                    act.mutate(() =>
                      postJson<ImportJobDetail>(`${base}/duplicate`, {
                        choice: "CREATE_NEW",
                      }),
                    )
                  }
                >
                  Create new
                </Button>
              </section>
            ) : null}

            {form ? (
              <section aria-labelledby="metadata-title" className="grid gap-3">
                <h3 id="metadata-title" className="font-medium">
                  Fill in metadata
                </h3>
                <MetadataForm
                  idPrefix="import"
                  value={form}
                  onChange={setForm}
                  errors={errors}
                />
                <div className="flex gap-2">
                  <Button disabled={act.isPending} onClick={submitMetadata}>
                    Save and import
                  </Button>
                  <Button variant="outline" onClick={() => setForm(null)}>
                    Cancel
                  </Button>
                </div>
              </section>
            ) : null}

            <div className="flex flex-wrap gap-2">
              {can("RETRY") ? (
                <Button
                  variant="outline"
                  disabled={act.isPending}
                  onClick={() =>
                    act.mutate(() => postJson<ImportJobDetail>(`${base}/retry`))
                  }
                >
                  Retry
                </Button>
              ) : null}
              {can("FILL_METADATA") && !form ? (
                <Button
                  variant="outline"
                  onClick={() => {
                    setErrors({});
                    setForm(toFormState(d.prefill));
                  }}
                >
                  Fill metadata manually
                </Button>
              ) : null}
              {can("DISMISS") ? (
                <Button
                  variant="destructive"
                  onClick={() => setConfirmDismiss(true)}
                >
                  Dismiss
                </Button>
              ) : null}
            </div>
          </div>
        ) : null}

        <AlertDialog open={confirmDismiss} onOpenChange={setConfirmDismiss}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Dismiss this import?</AlertDialogTitle>
              <AlertDialogDescription>
                The folder is renamed to .dismissed-{d?.job.sourceFolder}-… and
                ignored from now on. Rename it back to import it again.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction
                variant="destructive"
                onClick={() => {
                  setConfirmDismiss(false);
                  act.mutate(() =>
                    postJson<ImportJobDetail>(`${base}/dismiss`),
                  );
                }}
              >
                Dismiss
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </DialogContent>
    </Dialog>
  );
}
