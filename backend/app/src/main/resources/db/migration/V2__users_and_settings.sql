-- Users, sessions, settings, audit and per-user preferences (docs/spec/01-data-model.md).

CREATE TABLE app_user (
    id             uuid PRIMARY KEY,
    username       citext      NOT NULL,
    email          citext      NULL,
    password_hash  text        NOT NULL,
    role           text        NOT NULL,
    enabled        boolean     NOT NULL DEFAULT true,
    token_version  int         NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    last_login_at  timestamptz NULL,
    CONSTRAINT app_user_username_key UNIQUE (username),
    CONSTRAINT app_user_email_key UNIQUE (email),
    CONSTRAINT app_user_username_check CHECK (username ~ '^[a-zA-Z0-9_.-]{3,32}$'),
    CONSTRAINT app_user_role_check CHECK (role IN ('ADMIN', 'USER'))
);

CREATE TABLE refresh_token (
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    family_id   uuid        NOT NULL,
    token_hash  text        NOT NULL,
    expires_at  timestamptz NOT NULL,
    rotated_at  timestamptz NULL,
    revoked_at  timestamptz NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT refresh_token_token_hash_key UNIQUE (token_hash)
);
CREATE INDEX refresh_token_user_id_idx ON refresh_token (user_id);
CREATE INDEX refresh_token_family_id_idx ON refresh_token (family_id);

CREATE TABLE password_reset_token (
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash  text        NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT password_reset_token_token_hash_key UNIQUE (token_hash)
);
CREATE INDEX password_reset_token_user_id_idx ON password_reset_token (user_id);

CREATE TABLE app_setting (
    key         text PRIMARY KEY,
    value       jsonb       NOT NULL,
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id  uuid        NULL REFERENCES app_user (id) ON DELETE SET NULL,
    action         text        NOT NULL,
    target_type    text        NOT NULL,
    target_id      text        NULL,
    details        jsonb       NULL,
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_log_created_at_idx ON audit_log (created_at DESC);

CREATE TABLE user_preference (
    user_id           uuid PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    theme             text        NOT NULL DEFAULT 'DARK',
    adult_mode        text        NOT NULL DEFAULT 'HIDE',
    reader_max_width  int         NOT NULL DEFAULT 800,
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT user_preference_theme_check CHECK (theme IN ('DARK', 'LIGHT', 'SYSTEM')),
    CONSTRAINT user_preference_adult_mode_check CHECK (adult_mode IN ('HIDE', 'BLUR', 'SHOW')),
    CONSTRAINT user_preference_reader_max_width_check CHECK (reader_max_width BETWEEN 400 AND 2000)
);
