import type { ImportJobSummary, ImportStatus } from "@/lib/types";

/** Status filters of the Imports page (`?status=`). */
export const STATUS_FILTERS: {
  value: string;
  label: string;
  statuses: ImportStatus[];
}[] = [
  { value: "", label: "All", statuses: [] },
  {
    value: "attention",
    label: "Needs attention",
    statuses: ["NEEDS_ATTENTION", "FAILED"],
  },
  {
    value: "active",
    label: "In progress",
    statuses: ["QUEUED", "RESOLVING", "FETCHING_METADATA", "PROCESSING"],
  },
  { value: "done", label: "Done", statuses: ["DONE"] },
  { value: "dismissed", label: "Dismissed", statuses: ["DISMISSED"] },
];

export function statusesFor(filter: string): ImportStatus[] {
  return STATUS_FILTERS.find((f) => f.value === filter)?.statuses ?? [];
}

export const STATUS_LABELS: Record<ImportStatus, string> = {
  QUEUED: "Queued",
  RESOLVING: "Resolving",
  FETCHING_METADATA: "Fetching metadata",
  PROCESSING: "Processing",
  DONE: "Done",
  NEEDS_ATTENTION: "Needs attention",
  FAILED: "Failed",
  DISMISSED: "Dismissed",
};

/** Jobs the worker is still busy with: the page polls while any is shown. */
export function isActive(job: Pick<ImportJobSummary, "status">): boolean {
  return statusesFor("active").includes(job.status);
}

export function statusVariant(
  status: ImportStatus,
): "default" | "secondary" | "destructive" | "outline" {
  switch (status) {
    case "DONE":
      return "default";
    case "NEEDS_ATTENTION":
    case "FAILED":
      return "destructive";
    case "DISMISSED":
      return "outline";
    default:
      return "secondary";
  }
}
