import type { Role } from "./session";

/** Shapes of the backend responses used by the frontend. */

export interface AuthStatus {
  setupRequired: boolean;
  registrationEnabled: boolean;
  passwordResetEnabled: boolean;
}

export interface Me {
  id: string;
  username: string;
  email: string | null;
  role: Role;
}

export interface OffsetPage<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface User {
  id: string;
  username: string;
  email: string | null;
  role: Role;
  enabled: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface Settings {
  registrationEnabled: boolean;
  momentSubmissionsEnabled: boolean;
}

export interface SystemStatus {
  smtp: { configured: boolean; ok: boolean; error: string | null };
  solver: { configured: boolean; reachable: boolean };
  plugins: unknown[];
  imageAccess: string;
  versions: {
    app: string;
    java: string;
    springBoot: string;
    postgres: string;
    vips: string | null;
  };
}

export interface AuditEntry {
  id: number;
  createdAt: string;
  actor: { id: string; username: string } | null;
  action: string;
  targetType: string;
  targetId: string | null;
  details: Record<string, unknown> | null;
}
