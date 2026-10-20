-- Moments, import jobs and fetch sessions (docs/spec/01-data-model.md, 02-ingestion.md).

CREATE TABLE moment (
    id           uuid PRIMARY KEY,
    chapter_id   uuid        NOT NULL REFERENCES chapter (id) ON DELETE CASCADE,
    page_index   int         NOT NULL,
    crop         jsonb       NOT NULL,
    title        text        NOT NULL,
    description  text        NULL,
    status       text        NOT NULL,
    created_by   uuid        NULL REFERENCES app_user (id) ON DELETE SET NULL,
    reviewed_by  uuid        NULL REFERENCES app_user (id) ON DELETE SET NULL,
    reviewed_at  timestamptz NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT moment_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT moment_title_check CHECK (char_length(title) BETWEEN 1 AND 80),
    CONSTRAINT moment_page_index_check CHECK (page_index >= 0)
);
CREATE INDEX moment_chapter_id_status_idx ON moment (chapter_id, status);
CREATE INDEX moment_status_created_at_idx ON moment (status, created_at);

CREATE TABLE import_job (
    id             uuid PRIMARY KEY,
    source_folder  text        NOT NULL,
    kind           text        NULL,
    status         text        NOT NULL,
    media_id       uuid        NULL REFERENCES media (id) ON DELETE SET NULL,
    plugin_id      text        NULL,
    message        text        NULL,
    detail         jsonb       NULL,
    attempts       int         NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    finished_at    timestamptz NULL,
    CONSTRAINT import_job_kind_check CHECK (kind IN ('NEW', 'UPDATE', 'RESTORE')),
    CONSTRAINT import_job_status_check CHECK (status IN (
        'QUEUED', 'RESOLVING', 'FETCHING_METADATA', 'PROCESSING', 'DONE', 'NEEDS_ATTENTION', 'FAILED'))
);
CREATE INDEX import_job_status_created_at_idx ON import_job (status, created_at);
-- At most one non-finished job per source folder.
CREATE UNIQUE INDEX import_job_active_source_folder_key ON import_job (source_folder) WHERE finished_at IS NULL;

CREATE TABLE domain_session (
    domain      text PRIMARY KEY,
    cookies     jsonb       NOT NULL,
    user_agent  text        NOT NULL,
    expires_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL DEFAULT now()
);
