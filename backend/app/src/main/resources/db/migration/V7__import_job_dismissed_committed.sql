-- Phase 3 ingestion (docs/spec/02-ingestion.md): DISMISSED status, and the commit marker used by
-- startup recovery to roll a committed job forward.

ALTER TABLE import_job DROP CONSTRAINT import_job_status_check;
ALTER TABLE import_job ADD CONSTRAINT import_job_status_check CHECK (status IN (
    'QUEUED', 'RESOLVING', 'FETCHING_METADATA', 'PROCESSING', 'DONE', 'NEEDS_ATTENTION', 'FAILED', 'DISMISSED'));

-- Set in the same transaction that writes the media/chapter rows (commit step 3).
ALTER TABLE import_job ADD COLUMN committed_at timestamptz NULL;
