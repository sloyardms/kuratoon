"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { apiFetch, sendJson } from "@/lib/api";
import type { Settings, SystemStatus } from "@/lib/types";

function StatusBadge({ ok, label }: { ok: boolean; label: string }) {
  return <Badge variant={ok ? "default" : "secondary"}>{label}</Badge>;
}

export default function SettingsPage() {
  const queryClient = useQueryClient();
  const settings = useQuery({
    queryKey: ["admin", "settings"],
    queryFn: () => apiFetch<Settings>("/api/v1/admin/settings"),
  });
  const status = useQuery({
    queryKey: ["admin", "status"],
    queryFn: () => apiFetch<SystemStatus>("/api/v1/admin/status"),
  });
  const save = useMutation({
    mutationFn: (next: Settings) =>
      sendJson<Settings>("PUT", "/api/v1/admin/settings", next),
    onSuccess: (saved) => {
      queryClient.setQueryData(["admin", "settings"], saved);
      queryClient.invalidateQueries({ queryKey: ["auth-status"] });
      toast.success("Settings saved");
    },
    onError: (e) => toast.error(e.message),
  });

  const toggle = (key: keyof Settings, value: boolean) => {
    if (settings.data) save.mutate({ ...settings.data, [key]: value });
  };

  return (
    <div className="mx-auto grid max-w-3xl gap-6">
      <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>

      <Card>
        <CardHeader>
          <CardTitle>
            <h2>Access</h2>
          </CardTitle>
          <CardDescription>Who can join and contribute.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          {settings.isError ? (
            <p className="text-destructive">Could not load settings.</p>
          ) : null}
          <div className="flex items-center justify-between gap-4">
            <Label htmlFor="registration">Allow self-registration</Label>
            <Switch
              id="registration"
              checked={settings.data?.registrationEnabled ?? false}
              disabled={!settings.data || save.isPending}
              onCheckedChange={(v) => toggle("registrationEnabled", v)}
            />
          </div>
          <div className="flex items-center justify-between gap-4">
            <Label htmlFor="moments">Allow users to submit moments</Label>
            <Switch
              id="moments"
              checked={settings.data?.momentSubmissionsEnabled ?? false}
              disabled={!settings.data || save.isPending}
              onCheckedChange={(v) => toggle("momentSubmissionsEnabled", v)}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>
            <h2>Status</h2>
          </CardTitle>
          <CardDescription>
            Read-only. Configured through environment variables.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {status.isPending ? (
            <p className="text-muted-foreground">Checking…</p>
          ) : status.isError ? (
            <p className="text-destructive">Could not load the status.</p>
          ) : (
            <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-3 text-sm">
              <dt className="text-muted-foreground">Email (SMTP)</dt>
              <dd>
                {!status.data.smtp.configured ? (
                  <StatusBadge ok={false} label="Not configured" />
                ) : (
                  <StatusBadge
                    ok={status.data.smtp.ok}
                    label={
                      status.data.smtp.ok
                        ? "Working"
                        : `Failed (${status.data.smtp.error ?? "error"})`
                    }
                  />
                )}
              </dd>
              <dt className="text-muted-foreground">Challenge solver</dt>
              <dd>
                {!status.data.solver.configured ? (
                  <StatusBadge ok={false} label="Disabled" />
                ) : (
                  <StatusBadge
                    ok={status.data.solver.reachable}
                    label={
                      status.data.solver.reachable ? "Reachable" : "Unreachable"
                    }
                  />
                )}
              </dd>
              <dt className="text-muted-foreground">Plugins</dt>
              <dd>
                {status.data.plugins.length === 0
                  ? "None loaded"
                  : `${status.data.plugins.length} loaded`}
              </dd>
              <dt className="text-muted-foreground">Image access</dt>
              <dd>{status.data.imageAccess.toLowerCase()}</dd>
              <dt className="text-muted-foreground">libvips</dt>
              <dd>
                {status.data.versions.vips ?? (
                  <span className="text-destructive">
                    Unavailable: thumbnails cannot be generated
                  </span>
                )}
              </dd>
              <dt className="text-muted-foreground">Versions</dt>
              <dd>
                Kuratoon {status.data.versions.app} · Java{" "}
                {status.data.versions.java} · Spring Boot{" "}
                {status.data.versions.springBoot} · PostgreSQL{" "}
                {status.data.versions.postgres}
              </dd>
            </dl>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
