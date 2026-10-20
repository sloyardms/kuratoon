"use client";

import { useState, type FormEvent, type ReactNode } from "react";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { FormField, fieldErrors } from "@/components/form-field";
import { ApiError, postJson } from "@/lib/api";

/** Username / optional email / password form shared by first-run setup and registration. */
export function AccountForm({
  title,
  description,
  endpoint,
  submitLabel,
  onCreated,
  footer,
}: {
  title: string;
  description: string;
  endpoint: string;
  submitLabel: string;
  onCreated: () => void;
  footer?: ReactNode;
}) {
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    if (form.get("password") !== form.get("confirm")) {
      setErrors({ confirm: "The passwords do not match." });
      return;
    }
    setPending(true);
    setErrors({});
    setError(null);
    try {
      const email = String(form.get("email") ?? "").trim();
      await postJson(endpoint, {
        username: form.get("username"),
        email: email === "" ? null : email,
        password: form.get("password"),
      });
      onCreated();
    } catch (e) {
      if (e instanceof ApiError) {
        setErrors(fieldErrors(e.problem?.errors));
        setError(e.problem?.errors?.length ? null : e.message);
      } else {
        setError("Something went wrong. Try again.");
      }
      setPending(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h1>{title}</h1>
        </CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <form onSubmit={onSubmit} noValidate>
        <CardContent className="grid gap-4">
          <FormField
            id="username"
            label="Username"
            autoComplete="username"
            hint="3–32 letters, digits, '_', '.' or '-'."
            error={errors.username}
            required
            autoFocus
          />
          <FormField
            id="email"
            label="Email (optional)"
            type="email"
            autoComplete="email"
            error={errors.email}
          />
          <FormField
            id="password"
            label="Password"
            type="password"
            autoComplete="new-password"
            hint="At least 10 characters."
            error={errors.password}
            required
          />
          <FormField
            id="confirm"
            label="Confirm password"
            type="password"
            autoComplete="new-password"
            error={errors.confirm}
            required
          />
          {error ? (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          ) : null}
        </CardContent>
        <CardFooter className="mt-4 flex flex-col items-stretch gap-3">
          <Button type="submit" disabled={pending}>
            {pending ? "Please wait…" : submitLabel}
          </Button>
          {footer}
        </CardFooter>
      </form>
    </Card>
  );
}
