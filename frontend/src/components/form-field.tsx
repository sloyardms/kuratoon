import type { ComponentProps } from "react";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

/** Labelled input with an optional hint or field error (from Problem Details `errors`). */
export function FormField({
  id,
  label,
  hint,
  error,
  ...input
}: {
  id: string;
  label: string;
  hint?: string;
  error?: string;
} & ComponentProps<typeof Input>) {
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        name={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        {...input}
      />
      {error ? (
        <p id={`${id}-error`} className="text-sm text-destructive">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-sm text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

/** Field errors from a Problem Details `errors` array, keyed by field name. */
export function fieldErrors(
  errors: { field: string; message: string }[] | undefined,
): Record<string, string> {
  return Object.fromEntries((errors ?? []).map((e) => [e.field, e.message]));
}
