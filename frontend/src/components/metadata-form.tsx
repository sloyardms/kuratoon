"use client";

import { useQuery } from "@tanstack/react-query";
import { Lock, LockOpen } from "lucide-react";
import type { ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiFetch } from "@/lib/api";
import { FIELD_LABELS, STATUSES, type MetadataFormState } from "@/lib/metadata";
import type { MediaField, MediaTypeOption, OffsetPage } from "@/lib/types";

const controlClass =
  "w-full rounded-lg border border-input bg-transparent px-2.5 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 aria-invalid:border-destructive dark:bg-input/30";

/** Media types for the type select (admin API, at most 100). */
export function useMediaTypes() {
  return useQuery({
    queryKey: ["admin", "media-types", "all"],
    queryFn: () =>
      apiFetch<OffsetPage<MediaTypeOption>>(
        "/api/v1/admin/media-types?size=100",
      ),
    staleTime: 60_000,
  });
}

/**
 * Metadata fields of a media (manual import metadata, media edit). With `locked`/`onToggleLock`
 * each field shows a lock toggle (docs/spec/02-ingestion.md: fields can be unlocked individually).
 */
export function MetadataForm({
  idPrefix,
  value,
  onChange,
  errors = {},
  locked,
  onToggleLock,
}: {
  idPrefix: string;
  value: MetadataFormState;
  onChange: (next: MetadataFormState) => void;
  errors?: Partial<Record<MediaField, string>>;
  locked?: MediaField[];
  onToggleLock?: (field: MediaField) => void;
}) {
  const types = useMediaTypes();
  const set = (field: MediaField) => (v: string) =>
    onChange({ ...value, [field]: v });

  const row = (field: MediaField, control: ReactNode, hint?: string) => {
    const id = `${idPrefix}-${field}`;
    const error = errors[field];
    const isLocked = locked?.includes(field) ?? false;
    return (
      <div className="grid gap-1.5" key={field}>
        <div className="flex items-center justify-between gap-2">
          <Label htmlFor={id}>{FIELD_LABELS[field]}</Label>
          {onToggleLock ? (
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              aria-pressed={isLocked}
              aria-label={`${isLocked ? "Unlock" : "Lock"} ${FIELD_LABELS[field]}`}
              title={
                isLocked
                  ? "Locked: re-scraping never overwrites it"
                  : "Unlocked"
              }
              onClick={() => onToggleLock(field)}
            >
              {isLocked ? <Lock aria-hidden /> : <LockOpen aria-hidden />}
            </Button>
          ) : null}
        </div>
        {control}
        {error ? (
          <p id={`${id}-error`} className="text-sm text-destructive">
            {error}
          </p>
        ) : hint ? (
          <p className="text-sm text-muted-foreground">{hint}</p>
        ) : null}
      </div>
    );
  };

  const input = (field: MediaField, props: Record<string, unknown> = {}) => {
    const id = `${idPrefix}-${field}`;
    return (
      <Input
        id={id}
        value={value[field]}
        onChange={(e) => set(field)(e.target.value)}
        aria-invalid={errors[field] ? true : undefined}
        aria-describedby={errors[field] ? `${id}-error` : undefined}
        {...props}
      />
    );
  };

  const lines = (field: MediaField, rows = 3) => {
    const id = `${idPrefix}-${field}`;
    return (
      <textarea
        id={id}
        rows={rows}
        className={controlClass}
        value={value[field]}
        onChange={(e) => set(field)(e.target.value)}
        aria-invalid={errors[field] ? true : undefined}
        aria-describedby={errors[field] ? `${id}-error` : undefined}
      />
    );
  };

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <div className="sm:col-span-2">{row("title", input("title"))}</div>
      {row(
        "type",
        <select
          id={`${idPrefix}-type`}
          className={controlClass}
          value={value.type}
          onChange={(e) => set("type")(e.target.value)}
          aria-invalid={errors.type ? true : undefined}
        >
          <option value="">Choose…</option>
          {types.data?.items.map((t) => (
            <option key={t.id} value={t.slug}>
              {t.name}
            </option>
          ))}
        </select>,
      )}
      {row(
        "language",
        input("language", { placeholder: "en" }),
        "BCP-47, e.g. en, ja, ko",
      )}
      {row(
        "status",
        <select
          id={`${idPrefix}-status`}
          className={controlClass}
          value={value.status}
          onChange={(e) => set("status")(e.target.value)}
        >
          <option value="">Unknown</option>
          {STATUSES.filter((s) => s !== "UNKNOWN").map((s) => (
            <option key={s} value={s}>
              {s.charAt(0) + s.slice(1).toLowerCase()}
            </option>
          ))}
        </select>,
      )}
      {row(
        "year",
        input("year", { inputMode: "numeric", placeholder: "2023" }),
      )}
      {row("authors", lines("authors"), "One per line")}
      {row("artists", lines("artists"), "One per line")}
      {row("genres", lines("genres"), "One per line")}
      {row("tags", lines("tags"), "Library tags, e.g. hq")}
      {row("titles", lines("titles"), "One per line")}
      {row("parodies", lines("parodies"), "One per line")}
      <div className="sm:col-span-2">
        {row("characters", lines("characters"), "One per line: Name | Parody")}
      </div>
      <div className="sm:col-span-2">
        {row("description", lines("description", 5))}
      </div>
      {row(
        "adult",
        <label className="flex items-center gap-2 text-sm">
          <input
            id={`${idPrefix}-adult`}
            type="checkbox"
            checked={value.adult === "true"}
            onChange={(e) => set("adult")(e.target.checked ? "true" : "false")}
          />
          Adult content
        </label>,
      )}
    </div>
  );
}
