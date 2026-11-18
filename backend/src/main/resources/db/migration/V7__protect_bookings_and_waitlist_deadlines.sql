DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM appointments WHERE status = 'CONFIRMED'
        GROUP BY customer_id, offering_id, starts_at HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate confirmed appointments require review before migration'
            USING HINT = 'Group confirmed appointments by customer_id, offering_id and starts_at to identify conflicts.';
    END IF;
END $$;

CREATE UNIQUE INDEX uq_confirmed_customer_offering_start
    ON appointments (customer_id, offering_id, starts_at) WHERE status = 'CONFIRMED';

UPDATE waitlist_entries SET offer_expires_at = requested_starts_at
WHERE status = 'OFFERED' AND (offer_expires_at IS NULL OR offer_expires_at > requested_starts_at);

UPDATE email_outbox AS message SET
    expires_at = entry.offer_expires_at,
    body = regexp_replace(message.body, E'\nSon kabul: [^\n]*',
        E'\nSon kabul: ' || to_char(entry.offer_expires_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'))
FROM waitlist_entries AS entry
WHERE message.kind = 'OFFER' AND message.reference_id = entry.id AND entry.status = 'OFFERED'
    AND message.state = 'PENDING' AND message.expires_at > entry.offer_expires_at;

CREATE INDEX idx_waitlist_waiting_start ON waitlist_entries (requested_starts_at, created_at, id)
    WHERE status = 'WAITING';
CREATE INDEX idx_admin_appointments ON appointments (starts_at DESC, id DESC);
CREATE INDEX idx_admin_waitlist ON waitlist_entries (created_at DESC, id DESC);
