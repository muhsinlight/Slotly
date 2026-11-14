CREATE TABLE app_users (
    id UUID NOT NULL,
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(12) NOT NULL,
    username VARCHAR(40) NOT NULL,
    CONSTRAINT pk_app_users PRIMARY KEY (id),
    CONSTRAINT uk_app_users_username UNIQUE (username)
);

CREATE TABLE places (
    id UUID NOT NULL,
    active BOOLEAN NOT NULL,
    address VARCHAR(300) NOT NULL,
    capacity INTEGER NOT NULL,
    description VARCHAR(1000),
    name VARCHAR(120) NOT NULL,
    owner_id UUID NOT NULL,
    CONSTRAINT pk_places PRIMARY KEY (id),
    CONSTRAINT fk_places_owner FOREIGN KEY (owner_id) REFERENCES app_users (id)
);

CREATE TABLE offerings (
    id UUID NOT NULL,
    active BOOLEAN NOT NULL,
    description VARCHAR(1000),
    duration_minutes INTEGER,
    kind VARCHAR(12) NOT NULL,
    name VARCHAR(120) NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    place_id UUID NOT NULL,
    CONSTRAINT pk_offerings PRIMARY KEY (id),
    CONSTRAINT fk_offerings_place FOREIGN KEY (place_id) REFERENCES places (id)
);

CREATE TABLE business_hours (
    id UUID NOT NULL,
    closes_at TIME WITHOUT TIME ZONE NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    opens_at TIME WITHOUT TIME ZONE NOT NULL,
    place_id UUID NOT NULL,
    CONSTRAINT pk_business_hours PRIMARY KEY (id),
    CONSTRAINT fk_business_hours_place FOREIGN KEY (place_id) REFERENCES places (id)
);

CREATE TABLE appointments (
    id UUID NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(12) NOT NULL,
    customer_id UUID NOT NULL,
    offering_id UUID NOT NULL,
    place_id UUID NOT NULL,
    CONSTRAINT pk_appointments PRIMARY KEY (id),
    CONSTRAINT fk_appointments_customer FOREIGN KEY (customer_id) REFERENCES app_users (id),
    CONSTRAINT fk_appointments_offering FOREIGN KEY (offering_id) REFERENCES offerings (id),
    CONSTRAINT fk_appointments_place FOREIGN KEY (place_id) REFERENCES places (id)
);

CREATE TABLE waitlist_entries (
    id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    offer_expires_at TIMESTAMP WITH TIME ZONE,
    offered_at TIMESTAMP WITH TIME ZONE,
    requested_ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    requested_starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(12) NOT NULL,
    customer_id UUID NOT NULL,
    offering_id UUID NOT NULL,
    place_id UUID NOT NULL,
    CONSTRAINT pk_waitlist_entries PRIMARY KEY (id),
    CONSTRAINT fk_waitlist_customer FOREIGN KEY (customer_id) REFERENCES app_users (id),
    CONSTRAINT fk_waitlist_offering FOREIGN KEY (offering_id) REFERENCES offerings (id),
    CONSTRAINT fk_waitlist_place FOREIGN KEY (place_id) REFERENCES places (id)
);

CREATE INDEX idx_places_owner ON places (owner_id);
CREATE INDEX idx_offerings_place ON offerings (place_id);
CREATE INDEX idx_business_hours_place_day ON business_hours (place_id, day_of_week);
CREATE INDEX idx_appointments_place_times ON appointments (place_id, starts_at, ends_at);
CREATE INDEX idx_waitlist_customer_created ON waitlist_entries (customer_id, created_at DESC);
CREATE INDEX idx_waitlist_fifo ON waitlist_entries (place_id, offering_id, requested_starts_at, status, created_at);
CREATE INDEX idx_waitlist_offer_expiry ON waitlist_entries (status, offer_expires_at);
