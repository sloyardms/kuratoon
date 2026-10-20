"use client";

import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export interface AuditFilterValues {
  action: string;
  targetType: string;
  actorId: string;
}

export const EMPTY_FILTERS: AuditFilterValues = {
  action: "",
  targetType: "",
  actorId: "",
};

/** Reads the filters from the URL query. */
export function filtersFromParams(params: URLSearchParams): AuditFilterValues {
  return {
    action: params.get("action") ?? "",
    targetType: params.get("targetType") ?? "",
    actorId: params.get("actorId") ?? "",
  };
}

/** Target types written by the backend so far. */
const TARGET_TYPES = [
  "user",
  "settings",
  "media_type",
  "tag",
  "creator",
  "parody",
  "character",
];

/**
 * Audit log filters. Applying them resets to the first page; the parent writes them to the URL.
 */
export function AuditFilters({
  value,
  actors,
  onApply,
}: {
  value: AuditFilterValues;
  actors: { id: string; username: string }[];
  onApply: (filters: AuditFilterValues) => void;
}) {
  const [draft, setDraft] = useState(value);

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onApply({
      action: draft.action.trim().toUpperCase(),
      targetType: draft.targetType,
      actorId: draft.actorId,
    });
  }

  const select =
    "h-8 rounded-lg border border-input bg-transparent px-2 text-sm dark:bg-input/30";

  return (
    <form
      onSubmit={onSubmit}
      aria-label="Audit log filters"
      className="flex flex-wrap items-end gap-3"
    >
      <div className="grid gap-1.5">
        <Label htmlFor="filter-action">Action</Label>
        <Input
          id="filter-action"
          value={draft.action}
          placeholder="e.g. USER_CREATED"
          onChange={(e) => setDraft({ ...draft, action: e.target.value })}
        />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="filter-target">Target type</Label>
        <select
          id="filter-target"
          className={select}
          value={draft.targetType}
          onChange={(e) => setDraft({ ...draft, targetType: e.target.value })}
        >
          <option value="">Any</option>
          {TARGET_TYPES.map((t) => (
            <option key={t} value={t}>
              {t.replace("_", " ")}
            </option>
          ))}
        </select>
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="filter-actor">Actor</Label>
        <select
          id="filter-actor"
          className={select}
          value={draft.actorId}
          onChange={(e) => setDraft({ ...draft, actorId: e.target.value })}
        >
          <option value="">Anyone</option>
          {actors.map((a) => (
            <option key={a.id} value={a.id}>
              {a.username}
            </option>
          ))}
        </select>
      </div>
      <Button type="submit">Apply</Button>
      <Button
        type="button"
        variant="ghost"
        onClick={() => {
          setDraft(EMPTY_FILTERS);
          onApply(EMPTY_FILTERS);
        }}
      >
        Clear
      </Button>
    </form>
  );
}
