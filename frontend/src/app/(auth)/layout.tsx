import Link from "next/link";

/** Centered card layout for setup, login, registration and password reset. */
export default function AuthLayout({ children }: LayoutProps<"/">) {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 p-4">
      <Link href="/" className="text-2xl font-semibold tracking-tight">
        Kuratoon
      </Link>
      <div className="w-full max-w-sm">{children}</div>
    </main>
  );
}
