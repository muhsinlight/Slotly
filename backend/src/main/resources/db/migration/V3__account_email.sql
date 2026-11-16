CREATE TABLE account_emails (
    id uuid PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    email varchar(254) NOT NULL UNIQUE,
    verified boolean NOT NULL DEFAULT false,
    last_requested_at timestamptz NOT NULL
);

CREATE TABLE account_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    purpose varchar(20) NOT NULL,
    destination varchar(254) NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz
);

CREATE TABLE email_outbox (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    recipient varchar(254) NOT NULL,
    subject varchar(200) NOT NULL,
    body text NOT NULL,
    deduplication_key varchar(200) NOT NULL UNIQUE,
    state varchar(20) NOT NULL DEFAULT 'PENDING',
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    sent_at timestamptz
);
CREATE INDEX email_outbox_pending ON email_outbox(state, next_attempt_at);
