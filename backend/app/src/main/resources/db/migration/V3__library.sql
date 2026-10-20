-- Library: media types, media, taxonomy, relations, chapters (docs/spec/01-data-model.md).

CREATE TABLE media_type (
    id          uuid PRIMARY KEY,
    name        text        NOT NULL,
    slug        text        NOT NULL,
    color       text        NOT NULL,
    sort_order  int         NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT media_type_name_key UNIQUE (name),
    CONSTRAINT media_type_slug_key UNIQUE (slug),
    CONSTRAINT media_type_slug_check CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT media_type_color_check CHECK (color ~ '^#[0-9a-fA-F]{6}$')
);

-- Media types the user hides (per-user preference; needs media_type, so it lives here).
CREATE TABLE user_hidden_media_type (
    user_id        uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    media_type_id  uuid NOT NULL REFERENCES media_type (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, media_type_id)
);

CREATE TABLE media (
    id                       uuid PRIMARY KEY,
    title                    text         NOT NULL,
    description              text         NULL,
    media_type_id            uuid         NOT NULL REFERENCES media_type (id) ON DELETE RESTRICT,
    language                 text         NOT NULL,
    publication_status       text         NOT NULL DEFAULT 'UNKNOWN',
    publication_year         smallint     NULL,
    is_adult                 boolean      NOT NULL DEFAULT false,
    source_url               text         NULL,
    plugin_id                text         NULL,
    locked_fields            text[]       NOT NULL DEFAULT '{}',
    cover_file               text         NULL,
    cover_crop               jsonb        NULL,
    cover_version            int          NOT NULL DEFAULT 0,
    chapter_count            int          NOT NULL DEFAULT 0,
    latest_chapter_added_at  timestamptz  NULL,
    avg_rating               numeric(4,2) NULL,
    rating_count             int          NOT NULL DEFAULT 0,
    favorite_count           int          NOT NULL DEFAULT 0,
    metadata_refreshed_at    timestamptz  NULL,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT media_source_url_key UNIQUE (source_url),
    CONSTRAINT media_publication_status_check
        CHECK (publication_status IN ('ONGOING', 'COMPLETED', 'HIATUS', 'CANCELLED', 'UNKNOWN'))
);
CREATE INDEX media_title_trgm_idx ON media USING gin (title gin_trgm_ops);
CREATE INDEX media_media_type_id_idx ON media (media_type_id);
CREATE INDEX media_created_at_idx ON media (created_at DESC);
CREATE INDEX media_latest_chapter_added_at_idx ON media (latest_chapter_added_at DESC);
CREATE INDEX media_publication_status_idx ON media (publication_status);
CREATE INDEX media_language_idx ON media (language);

CREATE TABLE media_title (
    id        uuid PRIMARY KEY,
    media_id  uuid NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    title     text NOT NULL,
    language  text NULL,
    CONSTRAINT media_title_media_id_title_key UNIQUE (media_id, title)
);
CREATE INDEX media_title_title_trgm_idx ON media_title USING gin (title gin_trgm_ops);

CREATE TABLE creator (
    id               uuid PRIMARY KEY,
    name             text        NOT NULL,
    normalized_name  text        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT creator_normalized_name_key UNIQUE (normalized_name)
);
CREATE INDEX creator_name_trgm_idx ON creator USING gin (name gin_trgm_ops);

CREATE TABLE media_creator (
    media_id    uuid NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    creator_id  uuid NOT NULL REFERENCES creator (id) ON DELETE RESTRICT,
    role        text NOT NULL,
    PRIMARY KEY (media_id, creator_id, role),
    CONSTRAINT media_creator_role_check CHECK (role IN ('AUTHOR', 'ARTIST'))
);
CREATE INDEX media_creator_creator_id_idx ON media_creator (creator_id);

CREATE TABLE tag (
    id          uuid PRIMARY KEY,
    name        text        NOT NULL,
    slug        text        NOT NULL,
    kind        text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT tag_kind_slug_key UNIQUE (kind, slug),
    CONSTRAINT tag_kind_check CHECK (kind IN ('GENRE', 'LIBRARY')),
    CONSTRAINT tag_slug_check CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE media_tag (
    media_id  uuid NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    tag_id    uuid NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (media_id, tag_id)
);
CREATE INDEX media_tag_tag_id_media_id_idx ON media_tag (tag_id, media_id);

-- Tags the user excludes from feeds and browse.
CREATE TABLE user_excluded_tag (
    user_id  uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    tag_id   uuid NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, tag_id)
);

CREATE TABLE parody (
    id               uuid PRIMARY KEY,
    name             text        NOT NULL,
    normalized_name  text        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT parody_normalized_name_key UNIQUE (normalized_name)
);

CREATE TABLE media_parody (
    media_id   uuid NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    parody_id  uuid NOT NULL REFERENCES parody (id) ON DELETE CASCADE,
    PRIMARY KEY (media_id, parody_id)
);
CREATE INDEX media_parody_parody_id_idx ON media_parody (parody_id);

CREATE TABLE character (
    id               uuid PRIMARY KEY,
    name             text        NOT NULL,
    normalized_name  text        NOT NULL,
    parody_id        uuid        NULL REFERENCES parody (id) ON DELETE SET NULL,
    created_at       timestamptz NOT NULL DEFAULT now()
);
-- A NULL parody counts as a value: one parody-less character per normalized name.
CREATE UNIQUE INDEX character_normalized_name_parody_id_key
    ON character (normalized_name, parody_id) NULLS NOT DISTINCT;

CREATE TABLE media_character (
    media_id      uuid NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    character_id  uuid NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    PRIMARY KEY (media_id, character_id)
);
CREATE INDEX media_character_character_id_idx ON media_character (character_id);

CREATE TABLE media_relation (
    id             uuid PRIMARY KEY,
    from_media_id  uuid        NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    to_media_id    uuid        NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    type           text        NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT media_relation_from_to_key UNIQUE (from_media_id, to_media_id),
    CONSTRAINT media_relation_not_self_check CHECK (from_media_id <> to_media_id),
    CONSTRAINT media_relation_type_check CHECK (type IN ('SEQUEL', 'SIDE_STORY', 'SPIN_OFF'))
);
CREATE INDEX media_relation_to_media_id_idx ON media_relation (to_media_id);

CREATE TABLE chapter (
    id          uuid PRIMARY KEY,
    media_id    uuid          NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    number      numeric(9,3)  NOT NULL,
    title       text          NULL,
    folder      text          NOT NULL,
    pages       jsonb         NOT NULL,
    page_count  int           NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT chapter_media_id_number_key UNIQUE (media_id, number),
    CONSTRAINT chapter_number_check CHECK (number >= 0),
    -- CASE, not AND: Postgres does not guarantee short-circuit evaluation, and
    -- jsonb_array_length() raises an error on a non-array.
    CONSTRAINT chapter_pages_check CHECK (
        CASE WHEN jsonb_typeof(pages) = 'array' THEN page_count = jsonb_array_length(pages) ELSE false END)
);
-- (media_id, number) is also served by the unique constraint's index; spec 01 lists it explicitly.
CREATE INDEX chapter_media_id_number_idx ON chapter (media_id, number);
CREATE INDEX chapter_created_at_idx ON chapter (created_at DESC);
