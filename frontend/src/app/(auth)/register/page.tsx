"use client";

import { useRouter } from "next/navigation";
import Link from "next/link";

import { AccountForm } from "../account-form";
import { useAuthStatus } from "../use-auth-status";

export default function RegisterPage() {
  const router = useRouter();
  const status = useAuthStatus();

  if (status.isPending) return null;
  if (!status.data?.registrationEnabled) {
    return (
      <p className="text-center text-muted-foreground">
        Registration is closed.{" "}
        <Link href="/login" className="underline underline-offset-4">
          Log in
        </Link>
      </p>
    );
  }
  return (
    <AccountForm
      title="Create an account"
      description="Pick a username and a password."
      endpoint="/api/v1/auth/register"
      submitLabel="Create account"
      onCreated={() => router.push("/login")}
      footer={
        <Link
          href="/login"
          className="text-center text-sm text-muted-foreground underline-offset-4 hover:underline"
        >
          Already have an account? Log in
        </Link>
      }
    />
  );
}
