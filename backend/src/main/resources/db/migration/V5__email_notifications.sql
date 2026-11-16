CREATE TABLE notification_preferences (
    id uuid PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    offer_email boolean NOT NULL DEFAULT false,
    reminder_email boolean NOT NULL DEFAULT false
);
ALTER TABLE email_outbox ADD COLUMN kind varchar(20) NOT NULL DEFAULT 'ACCOUNT';
ALTER TABLE email_outbox ADD COLUMN reference_id uuid;
ALTER TABLE email_outbox ADD COLUMN expires_at timestamptz;
