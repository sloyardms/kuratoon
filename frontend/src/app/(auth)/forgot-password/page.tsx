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
import { FormField } from "@/components/form-field";
import { ApiError, postJson, type Problem } from "@/lib/api";

import { useAuthStatus } from "../use-auth-status";

export default function ForgotPasswordPage() {
  const status = useAuthStatus();
  const [sent, setSent] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  if (status.isPending) return null;
  if (!status.data?.passwordResetEnabled) {
    return (
      <p className="text-center text-muted-foreground">
        Password reset by email is not available. Ask an admin to reset your
        password.
      </p>
    );
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      const res = await postJson<Problem>(
        "/api/v1/auth/password-reset/request",
        {
          email: new FormData(event.currentTarget).get("email"),
        },
      );
      setSent(res?.detail ?? "Check your email.");
    } catch (e) {
      setError(
        e instanceof ApiError ? e.message : "Something went wrong. Try again.",
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h1>Reset your password</h1>
        </CardTitle>
        <CardDescription>
          We will email you a link to choose a new password.
        </CardDescription>
      </CardHeader>
      {sent ? (
        <CardContent>
          <p role="status">{sent}</p>
        </CardContent>
      ) : (
        <form onSubmit={onSubmit} noValidate>
          <CardContent className="grid gap-4">
            <FormField
              id="email"
              label="Email"
              type="email"
              autoComplete="email"
              required
              autoFocus
            />
            {error ? (
              <p role="alert" className="text-sm text-destructive">
                {error}
              </p>
            ) : null}
          </CardContent>
          <CardFooter className="mt-4 flex flex-col items-stretch gap-3">
            <Button type="submit" disabled={pending}>
              {pending ? "Sending…" : "Send reset link"}
            </Button>
          </CardFooter>
        </form>
      )}
      <CardFooter className="justify-center text-sm">
        <Link
          href="/login"
          className="text-muted-foreground underline-offset-4 hover:underline"
        >
          Back to login
        </Link>
      </CardFooter>
    </Card>
  );
}
