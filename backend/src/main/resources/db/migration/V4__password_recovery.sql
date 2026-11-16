ALTER TABLE app_users ADD COLUMN auth_version bigint NOT NULL DEFAULT 0;
CREATE TABLE password_reset_limits (
    id varchar(64) PRIMARY KEY,
    last_requested_at timestamptz NOT NULL
);
