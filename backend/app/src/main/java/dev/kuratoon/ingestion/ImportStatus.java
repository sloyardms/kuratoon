package dev.kuratoon.ingestion;

/** {@code import_job.status} (docs/spec/02-ingestion.md "Job lifecycle"). */
public enum ImportStatus {
    QUEUED,
    RESOLVING,
    FETCHING_METADATA,
    PROCESSING,
    DONE,
    NEEDS_ATTENTION,
    FAILED,
    DISMISSED;

    /** Finished jobs have {@code finished_at} set and no longer own their source folder. */
    public boolean finished() {
        return this == DONE || this == DISMISSED;
    }

    /** States from which the admin can act (retry, fill metadata, resolve, dismiss). */
    public boolean waitingForAdmin() {
        return this == NEEDS_ATTENTION || this == FAILED;
    }
}
