"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";

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

export default function ResetPasswordPage() {
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    if (form.get("password") !== form.get("confirm")) {
      setErrors({ confirm: "The passwords do not match." });
      return;
    }
    const token =
      new URLSearchParams(window.location.search).get("token") ?? "";
    setPending(true);
    setErrors({});
    setError(null);
    try {
      await postJson("/api/v1/auth/password-reset/confirm", {
        token,
        password: form.get("password"),
      });
      setDone(true);
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setError("Password reset by email is not available.");
      } else if (e instanceof ApiError) {
        setErrors(fieldErrors(e.problem?.errors));
        setError(e.problem?.errors?.length ? null : e.message);
      } else {
        setError("Something went wrong. Try again.");
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h1>Choose a new password</h1>
        </CardTitle>
        <CardDescription>
          The link in your email is valid for 30 minutes and works once.
        </CardDescription>
      </CardHeader>
      {done ? (
        <CardContent>
          <p role="status">
            Your password was changed and every session was logged out.{" "}
            <Link href="/login" className="underline underline-offset-4">
              Log in
            </Link>
          </p>
        </CardContent>
      ) : (
        <form onSubmit={onSubmit} noValidate>
          <CardContent className="grid gap-4">
            <FormField
              id="password"
              label="New password"
              type="password"
              autoComplete="new-password"
              hint="At least 10 characters."
              error={errors.password}
              required
              autoFocus
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
          <CardFooter className="mt-4 flex flex-col items-stretch">
            <Button type="submit" disabled={pending}>
              {pending ? "Saving…" : "Set password"}
            </Button>
          </CardFooter>
        </form>
      )}
    </Card>
  );
}
