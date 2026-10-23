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

export type PublicationStatus =
  "ONGOING" | "COMPLETED" | "HIATUS" | "CANCELLED" | "UNKNOWN";

/** Media metadata as in a manifest / the manual metadata form (backend `MediaFields`). */
export interface MediaFields {
  title: string | null;
  titles: string[];
  description: string | null;
  type: string | null;
  language: string | null;
  status: PublicationStatus | null;
  year: number | null;
  adult: boolean | null;
  authors: string[];
  artists: string[];
  genres: string[];
  tags: string[];
  parodies: string[];
  characters: { name: string; parody: string | null }[];
}

export type MediaField = keyof MediaFields;

export interface MediaTypeOption {
  id: string;
  name: string;
  slug: string;
  color: string;
  sortOrder: number;
}

export type ImportStatus =
  | "QUEUED"
  | "RESOLVING"
  | "FETCHING_METADATA"
  | "PROCESSING"
  | "DONE"
  | "NEEDS_ATTENTION"
  | "FAILED"
  | "DISMISSED";

export type ImportAction =
  | "RETRY"
  | "FILL_METADATA"
  | "RESOLVE_CONFLICT"
  | "RESOLVE_DUPLICATE"
  | "DISMISS";

export interface ImportJobSummary {
  id: string;
  sourceFolder: string;
  kind: "NEW" | "UPDATE" | "RESTORE" | null;
  status: ImportStatus;
  message: string | null;
  attention: string | null;
  mediaId: string | null;
  mediaTitle: string | null;
  chapterCount: number;
  attempts: number;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
  actions: ImportAction[];
}

export interface ChapterRow {
  sourceFolder: string;
  number: number | null;
  title: string | null;
  folder: string | null;
  pageCount: number;
  warnings: string[];
}

export interface ImportJobDetail {
  job: ImportJobSummary;
  url: string | null;
  chapters: ChapterRow[];
  warnings: string[];
  missing: string[];
  prefill: MediaFields | null;
  conflicts: {
    number: number;
    folder: string;
    existingChapterId: string;
    existingPageCount: number;
    newPageCount: number;
  }[];
  duplicate: { mediaId: string; title: string } | null;
  committed: boolean;
}

export interface CoverCrop {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface MediaRow {
  id: string;
  title: string;
  type: { id: string; name: string; slug: string; color: string };
  chapterCount: number;
  latestChapterAddedAt: string | null;
  createdAt: string;
}

export interface CoverView {
  file: string | null;
  crop: CoverCrop | null;
  version: number;
  width: number;
  height: number;
  thumbnails: boolean;
  originalUrl: string | null;
}

export interface MediaEditView {
  id: string;
  values: MediaFields;
  lockedFields: MediaField[];
  sourceUrl: string | null;
  pluginId: string | null;
  cover: CoverView;
  relations: {
    id: string;
    type: "SEQUEL" | "SIDE_STORY" | "SPIN_OFF";
    direction: "OUTGOING" | "INCOMING";
    label: string;
    mediaId: string;
    title: string;
  }[];
  chapters: {
    id: string;
    number: number;
    title: string | null;
    pageCount: number;
    createdAt: string;
  }[];
  createdAt: string;
  updatedAt: string;
}

export interface IntegrityReport {
  scannedAt: string;
  mediaChecked: number;
  foldersWithoutRows: {
    folder: string;
    title: string | null;
    hasSidecar: boolean;
  }[];
  rowsWithoutFolders: { id: string; title: string }[];
  missingPages: {
    mediaId: string;
    title: string;
    chapter: string;
    missing: number;
    example: string;
  }[];
  missingThumbnails: { id: string; title: string }[];
}

export interface RebuildResult {
  restored: { id: string; title: string }[];
  skipped: { folder: string; reason: string }[];
  warnings: string[];
  thumbnailsQueued: number;
}
