-- A live VERIFY_EMAIL token holds the requested address until its owner confirms it.
-- Keep the last request time for rate limiting, including for accounts with no verified address.
ALTER TABLE account_emails ALTER COLUMN email DROP NOT NULL;
UPDATE account_emails SET email = NULL WHERE NOT verified;
ALTER TABLE account_emails ADD CONSTRAINT account_email_verified_address
    CHECK (verified = (email IS NOT NULL));
CREATE INDEX account_tokens_pending_address ON account_tokens(destination, expires_at)
    WHERE purpose = 'VERIFY_EMAIL' AND consumed_at IS NULL;
CREATE INDEX account_tokens_pending_user ON account_tokens(user_id, purpose)
    WHERE consumed_at IS NULL;

UPDATE email_outbox o SET reference_id = t.id, expires_at = t.expires_at
FROM account_tokens t WHERE o.kind = 'ACCOUNT' AND o.deduplication_key = 'account:' || t.id;
ALTER TABLE email_outbox ADD COLUMN next_publish_at timestamptz;
CREATE INDEX email_outbox_account_links ON email_outbox(created_at, id)
    WHERE kind = 'ACCOUNT' AND body <> '';
