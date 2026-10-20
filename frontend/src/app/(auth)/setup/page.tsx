"use client";

import { useRouter } from "next/navigation";
import { AccountForm } from "../account-form";

/** First-run setup: shown while no admin exists (proxy.ts sends every route here). */
export default function SetupPage() {
  const router = useRouter();
  return (
    <AccountForm
      title="Welcome to Kuratoon"
      description="Create the first admin account to get started."
      endpoint="/api/v1/auth/setup"
      submitLabel="Create admin"
      onCreated={() => router.push("/login")}
    />
  );
}
