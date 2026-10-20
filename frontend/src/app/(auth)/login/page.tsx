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
import { ApiError, postJson } from "@/lib/api";

import { safeNext, useAuthStatus } from "../use-auth-status";

export default function LoginPage() {
  const status = useAuthStatus();
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPending(true);
    setError(null);
    try {
      await postJson("/api/v1/auth/login", {
        username: form.get("username"),
        password: form.get("password"),
      });
      // Full navigation so every layer (proxy, server rendering) sees the new cookies.
      window.location.assign(safeNext());
    } catch (e) {
      setError(
        e instanceof ApiError ? e.message : "Could not log in. Try again.",
      );
      setPending(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h1>Log in</h1>
        </CardTitle>
        <CardDescription>Welcome back.</CardDescription>
      </CardHeader>
      <form onSubmit={onSubmit} noValidate>
        <CardContent className="grid gap-4">
          <FormField
            id="username"
            label="Username"
            autoComplete="username"
            required
            autoFocus
          />
          <FormField
            id="password"
            label="Password"
            type="password"
            autoComplete="current-password"
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
            {pending ? "Logging in…" : "Log in"}
          </Button>
          <div className="flex justify-between text-sm text-muted-foreground">
            {status.data?.passwordResetEnabled ? (
              <Link
                href="/forgot-password"
                className="underline-offset-4 hover:underline"
              >
                Forgot password?
              </Link>
            ) : (
              <span />
            )}
            {status.data?.registrationEnabled ? (
              <Link
                href="/register"
                className="underline-offset-4 hover:underline"
              >
                Create an account
              </Link>
            ) : null}
          </div>
        </CardFooter>
      </form>
    </Card>
  );
}
