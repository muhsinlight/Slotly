ALTER TABLE notification_preferences ADD COLUMN reminder_hours integer NOT NULL DEFAULT 24
    CHECK (reminder_hours IN (2, 24));
CREATE INDEX email_outbox_reference ON email_outbox(kind, reference_id, expires_at);
