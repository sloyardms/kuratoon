"use client";

import { useQuery } from "@tanstack/react-query";
import { ChevronDown, UserRound } from "lucide-react";
import Link from "next/link";

import { Button, buttonVariants } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { apiFetch, postJson } from "@/lib/api";
import type { Me } from "@/lib/types";

export function useMe() {
  return useQuery({
    queryKey: ["me"],
    queryFn: () => apiFetch<Me>("/api/v1/me"),
  });
}

async function logOut(everywhere: boolean) {
  try {
    await postJson(
      everywhere ? "/api/v1/auth/logout-all" : "/api/v1/auth/logout",
    );
  } finally {
    // A full reload, not router.push: it drops every cached query of the old session.
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.assign("/login");
  }
}

const ADMIN_LINKS = [
  { href: "/admin/imports", label: "Imports" },
  { href: "/admin/media", label: "Media" },
  { href: "/admin/maintenance", label: "Maintenance" },
  { href: "/admin/users", label: "Users" },
  { href: "/admin/settings", label: "Settings" },
  { href: "/admin/audit-log", label: "Audit log" },
];

/** Global top bar (docs/spec/07-frontend.md): logo, Browse, admin menu, user menu. */
export function TopBar() {
  const me = useMe();

  return (
    <header className="sticky top-0 z-40 border-b bg-background/95 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-[1600px] items-center gap-4 px-4">
        <Link href="/" className="text-lg font-semibold tracking-tight">
          Kuratoon
        </Link>
        <nav aria-label="Main" className="flex items-center gap-1">
          {/* Browse lands in phase 5. */}
          <Link
            href="/"
            className={buttonVariants({ variant: "ghost", size: "sm" })}
          >
            Browse
          </Link>
          {me.data?.role === "ADMIN" ? (
            <DropdownMenu>
              <DropdownMenuTrigger
                render={<Button variant="ghost" size="sm" />}
              >
                Admin <ChevronDown aria-hidden className="size-4" />
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start">
                {ADMIN_LINKS.map((link) => (
                  <DropdownMenuItem
                    key={link.href}
                    render={<Link href={link.href} />}
                  >
                    {link.label}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          ) : null}
        </nav>
        <div className="ml-auto">
          {me.data ? (
            <DropdownMenu>
              <DropdownMenuTrigger
                render={
                  <Button variant="ghost" size="sm" aria-label="User menu" />
                }
              >
                <UserRound aria-hidden className="size-4" />
                <span className="hidden sm:inline">{me.data.username}</span>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {/* Base UI group labels must sit inside a group. */}
                <DropdownMenuGroup>
                  <DropdownMenuLabel>{me.data.username}</DropdownMenuLabel>
                </DropdownMenuGroup>
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={() => logOut(false)}>
                  Log out
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => logOut(true)}>
                  Log out everywhere
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : null}
        </div>
      </div>
    </header>
  );
}
