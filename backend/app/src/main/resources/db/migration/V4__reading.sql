-- Per-user reading data (docs/spec/01-data-model.md, docs/spec/05-reading-status.md).

CREATE TABLE chapter_progress (
    user_id       uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    chapter_id    uuid        NOT NULL REFERENCES chapter (id) ON DELETE CASCADE,
    media_id      uuid        NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    page_index    int         NOT NULL DEFAULT 0,
    page_offset   real        NOT NULL DEFAULT 0,
    completed_at  timestamptz NULL,
    last_read_at  timestamptz NULL,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, chapter_id),
    CONSTRAINT chapter_progress_page_index_check CHECK (page_index >= 0),
    CONSTRAINT chapter_progress_page_offset_check CHECK (page_offset >= 0 AND page_offset <= 1)
);
CREATE INDEX chapter_progress_user_id_last_read_at_idx ON chapter_progress (user_id, last_read_at DESC);
CREATE INDEX chapter_progress_user_id_media_id_idx ON chapter_progress (user_id, media_id);

CREATE TABLE user_media_state (
    user_id               uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    media_id              uuid        NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    intent                text        NULL,
    chapters_read         int         NOT NULL DEFAULT 0,
    last_read_chapter_id  uuid        NULL REFERENCES chapter (id) ON DELETE SET NULL,
    last_read_at          timestamptz NULL,
    updated_at            timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, media_id),
    CONSTRAINT user_media_state_intent_check CHECK (intent IN ('PLANNED', 'ON_HOLD', 'DROPPED')),
    CONSTRAINT user_media_state_chapters_read_check CHECK (chapters_read >= 0)
);
CREATE INDEX user_media_state_user_id_last_read_at_idx ON user_media_state (user_id, last_read_at DESC);

CREATE TABLE read_event (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    media_id    uuid        NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    chapter_id  uuid        NOT NULL REFERENCES chapter (id) ON DELETE CASCADE,
    read_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX read_event_read_at_idx ON read_event (read_at);
CREATE INDEX read_event_media_id_read_at_idx ON read_event (media_id, read_at);

CREATE TABLE favorite_media (
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    media_id    uuid        NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, media_id)
);

CREATE TABLE favorite_chapter (
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    chapter_id  uuid        NOT NULL REFERENCES chapter (id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, chapter_id)
);

CREATE TABLE rating (
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    media_id    uuid        NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    score       smallint    NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, media_id),
    CONSTRAINT rating_score_check CHECK (score BETWEEN 1 AND 10)
);

CREATE TABLE collection (
    id           uuid PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name         text        NOT NULL,
    description  text        NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT collection_user_id_name_key UNIQUE (user_id, name)
);

CREATE TABLE collection_media (
    collection_id  uuid        NOT NULL REFERENCES collection (id) ON DELETE CASCADE,
    media_id       uuid        NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    added_at       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (collection_id, media_id)
);
