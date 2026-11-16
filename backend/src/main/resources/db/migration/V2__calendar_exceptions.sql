CREATE TABLE calendar_exceptions (
    id UUID PRIMARY KEY,
    place_id UUID NOT NULL REFERENCES places (id),
    date DATE NOT NULL,
    closed BOOLEAN NOT NULL,
    opens_at TIME WITHOUT TIME ZONE,
    closes_at TIME WITHOUT TIME ZONE,
    CONSTRAINT uk_calendar_exception_date UNIQUE (place_id, date),
    CONSTRAINT ck_calendar_exception_hours CHECK (
        (closed AND opens_at IS NULL AND closes_at IS NULL)
        OR (NOT closed AND opens_at IS NOT NULL AND closes_at IS NOT NULL AND opens_at < closes_at)
    )
);
