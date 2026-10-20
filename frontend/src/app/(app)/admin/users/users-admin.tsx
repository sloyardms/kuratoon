"use client";

import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { MoreHorizontal } from "lucide-react";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";

import { FormField, fieldErrors } from "@/components/form-field";
import { Pagination } from "@/components/pagination";
import { useMe } from "@/components/top-bar";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ApiError, apiFetch, postJson, sendJson } from "@/lib/api";
import type { Role } from "@/lib/session";
import type { OffsetPage, User } from "@/lib/types";
import { pageParam, useUrlParams } from "@/lib/use-url-params";

const PAGE_SIZE = 25;

type Dialogs =
  | { kind: "create" }
  | { kind: "password"; user: User }
  | { kind: "delete"; user: User }
  | null;

function formatDate(iso: string | null) {
  return iso ? new Date(iso).toLocaleString() : "—";
}

/** Users page (docs/spec/04-auth-users.md): every action the API offers. */
export function UsersAdmin() {
  const { params, update } = useUrlParams();
  const q = params.get("q") ?? "";
  const page = pageParam(params);
  const me = useMe();
  const queryClient = useQueryClient();
  const [dialog, setDialog] = useState<Dialogs>(null);

  const users = useQuery({
    queryKey: ["admin", "users", { q, page }],
    queryFn: () =>
      apiFetch<OffsetPage<User>>(
        `/api/v1/admin/users?${new URLSearchParams({ q, page: String(page), size: String(PAGE_SIZE) })}`,
      ),
    placeholderData: keepPreviousData,
  });

  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
  const action = useMutation({
    mutationFn: (run: () => Promise<unknown>) => run(),
    onSuccess: () => refresh(),
    onError: (e) => toast.error(e.message),
  });

  const patch = (
    user: User,
    body: Partial<Pick<User, "role" | "enabled">>,
    done: string,
  ) =>
    action.mutate(() =>
      sendJson("PATCH", `/api/v1/admin/users/${user.id}`, body).then(() =>
        toast.success(done),
      ),
    );

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">Users</h1>
        <Button onClick={() => setDialog({ kind: "create" })}>New user</Button>
      </div>

      <form
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          update({
            q: String(new FormData(e.currentTarget).get("q") ?? ""),
            page: null,
          });
        }}
        className="flex max-w-md gap-2"
      >
        <Label htmlFor="q" className="sr-only">
          Search users
        </Label>
        <Input
          id="q"
          name="q"
          defaultValue={q}
          placeholder="Search username or email"
        />
        <Button type="submit" variant="outline">
          Search
        </Button>
      </form>

      {users.isError ? (
        <div className="flex items-center gap-3 text-destructive">
          Could not load users.
          <Button variant="outline" size="sm" onClick={() => users.refetch()}>
            Retry
          </Button>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Username</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Role</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Last login</TableHead>
              <TableHead className="w-12">
                <span className="sr-only">Actions</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.isPending ? (
              <TableRow>
                <TableCell colSpan={6} className="text-muted-foreground">
                  Loading…
                </TableCell>
              </TableRow>
            ) : users.data.items.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-muted-foreground">
                  No users match.
                </TableCell>
              </TableRow>
            ) : (
              users.data.items.map((user) => {
                const isSelf = user.id === me.data?.id;
                return (
                  <TableRow key={user.id}>
                    <TableCell className="font-medium">
                      {user.username}
                      {isSelf ? (
                        <span className="text-muted-foreground"> (you)</span>
                      ) : null}
                    </TableCell>
                    <TableCell>{user.email ?? "—"}</TableCell>
                    <TableCell>
                      <Badge
                        variant={
                          user.role === "ADMIN" ? "default" : "secondary"
                        }
                      >
                        {user.role.toLowerCase()}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {user.enabled ? "Active" : "Disabled"}
                    </TableCell>
                    <TableCell>{formatDate(user.lastLoginAt)}</TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger
                          render={
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              aria-label={`Actions for ${user.username}`}
                            />
                          }
                        >
                          <MoreHorizontal aria-hidden />
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          {user.role === "ADMIN" ? (
                            <DropdownMenuItem
                              disabled={isSelf}
                              onClick={() =>
                                patch(user, { role: "USER" }, "Role changed")
                              }
                            >
                              Make user
                            </DropdownMenuItem>
                          ) : (
                            <DropdownMenuItem
                              onClick={() =>
                                patch(user, { role: "ADMIN" }, "Role changed")
                              }
                            >
                              Make admin
                            </DropdownMenuItem>
                          )}
                          <DropdownMenuItem
                            disabled={isSelf && user.enabled}
                            onClick={() =>
                              patch(
                                user,
                                { enabled: !user.enabled },
                                user.enabled ? "User disabled" : "User enabled",
                              )
                            }
                          >
                            {user.enabled ? "Disable" : "Enable"}
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() =>
                              setDialog({ kind: "password", user })
                            }
                          >
                            Set password
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() =>
                              action.mutate(() =>
                                postJson(
                                  `/api/v1/admin/users/${user.id}/revoke-sessions`,
                                ).then(() => toast.success("Sessions revoked")),
                              )
                            }
                          >
                            Revoke sessions
                          </DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem
                            variant="destructive"
                            disabled={isSelf}
                            onClick={() => setDialog({ kind: "delete", user })}
                          >
                            Delete
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      )}

      {users.data ? (
        <Pagination
          page={users.data.page}
          totalPages={users.data.totalPages}
          totalItems={users.data.totalItems}
          onPage={(p) => update({ page: p || null })}
        />
      ) : null}

      <CreateUserDialog
        open={dialog?.kind === "create"}
        onClose={() => setDialog(null)}
        onCreated={refresh}
      />
      <SetPasswordDialog
        user={dialog?.kind === "password" ? dialog.user : null}
        onClose={() => setDialog(null)}
      />
      <AlertDialog
        open={dialog?.kind === "delete"}
        onOpenChange={(open) => !open && setDialog(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              Delete {dialog?.kind === "delete" ? dialog.user.username : ""}?
            </AlertDialogTitle>
            <AlertDialogDescription>
              This deletes the account and all of its personal data (progress,
              favorites, ratings, collections). It cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={() => {
                if (dialog?.kind !== "delete") return;
                const user = dialog.user;
                setDialog(null);
                action.mutate(() =>
                  sendJson("DELETE", `/api/v1/admin/users/${user.id}`).then(
                    () => toast.success("User deleted"),
                  ),
                );
              }}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function CreateUserDialog({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [role, setRole] = useState<Role>("USER");
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const email = String(form.get("email") ?? "").trim();
    setPending(true);
    try {
      await postJson("/api/v1/admin/users", {
        username: form.get("username"),
        email: email === "" ? null : email,
        password: form.get("password"),
        role,
      });
      toast.success("User created");
      setErrors({});
      onCreated();
      onClose();
    } catch (e) {
      if (e instanceof ApiError && e.problem?.errors?.length)
        setErrors(fieldErrors(e.problem.errors));
      else
        toast.error(
          e instanceof Error ? e.message : "Could not create the user",
        );
    } finally {
      setPending(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New user</DialogTitle>
          <DialogDescription>
            Share the password with the person; they can change it later.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <FormField
            id="new-username"
            name="username"
            label="Username"
            error={errors.username}
            required
          />
          <FormField
            id="new-email"
            name="email"
            label="Email (optional)"
            type="email"
            error={errors.email}
          />
          <FormField
            id="new-password"
            name="password"
            label="Password"
            type="password"
            autoComplete="new-password"
            hint="At least 10 characters."
            error={errors.password}
            required
          />
          <fieldset className="grid gap-2">
            <legend className="text-sm font-medium">Role</legend>
            <div className="flex gap-4 text-sm">
              {(["USER", "ADMIN"] as const).map((r) => (
                <label key={r} className="flex items-center gap-2">
                  <input
                    type="radio"
                    name="role"
                    value={r}
                    checked={role === r}
                    onChange={() => setRole(r)}
                  />
                  {r === "USER" ? "User" : "Admin"}
                </label>
              ))}
            </div>
          </fieldset>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={pending}>
              Create
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function SetPasswordDialog({
  user,
  onClose,
}: {
  user: User | null;
  onClose: () => void;
}) {
  const [error, setError] = useState<string | undefined>();
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!user) return;
    setPending(true);
    try {
      await postJson(`/api/v1/admin/users/${user.id}/password`, {
        password: new FormData(event.currentTarget).get("password"),
      });
      toast.success(
        `Password set for ${user.username}; their sessions were logged out`,
      );
      setError(undefined);
      onClose();
    } catch (e) {
      setError(
        e instanceof ApiError
          ? (e.problem?.errors?.[0]?.message ?? e.message)
          : "Could not set the password",
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <Dialog open={user !== null} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Set password for {user?.username}</DialogTitle>
          <DialogDescription>
            All of their sessions are logged out.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <FormField
            id="set-password"
            name="password"
            label="New password"
            type="password"
            autoComplete="new-password"
            hint="At least 10 characters."
            error={error}
            required
          />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={pending}>
              Set password
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
