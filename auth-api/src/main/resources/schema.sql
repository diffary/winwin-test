CREATE TABLE IF NOT EXISTS users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS processing_log (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id),
    input_text  TEXT NOT NULL,
    output_text TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);
