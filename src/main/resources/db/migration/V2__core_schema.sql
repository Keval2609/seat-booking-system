CREATE TABLE holds (
                       id BIGSERIAL PRIMARY KEY,
                       user_id BIGINT NOT NULL REFERENCES users(id),
                       event_id BIGINT NOT NULL REFERENCES events(id),
                       status VARCHAR(20) NOT NULL
                           CHECK (status IN ('ACTIVE', 'CONVERTED', 'RELEASED', 'EXPIRED')),
                       expires_at TIMESTAMPTZ NOT NULL,
                       created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_holds_active_expiry ON holds (expires_at) WHERE status = 'ACTIVE';

CREATE TABLE seats (
                       id BIGSERIAL PRIMARY KEY,
                       event_id BIGINT NOT NULL REFERENCES events(id),
                       label VARCHAR(20) NOT NULL,
                       price_cents INT NOT NULL CHECK (price_cents >= 0),
                       status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                           CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
                       hold_id BIGINT REFERENCES holds(id),
                       version BIGINT NOT NULL DEFAULT 0,
                       UNIQUE (event_id, label),
                       CHECK (
                           (status = 'AVAILABLE' AND hold_id IS NULL)
                               OR (status = 'HELD' AND hold_id IS NOT NULL)
                               OR (status = 'BOOKED' AND hold_id IS NULL)
                           )
);

CREATE INDEX idx_seats_hold ON seats (hold_id) WHERE hold_id IS NOT NULL;

CREATE TABLE bookings (
                          id BIGSERIAL PRIMARY KEY,
                          user_id BIGINT NOT NULL REFERENCES users(id),
                          event_id BIGINT NOT NULL REFERENCES events(id),
                          hold_id BIGINT NOT NULL UNIQUE REFERENCES holds(id),
                          status VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
                          total_cents INT NOT NULL CHECK (total_cents >= 0),
                          created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_bookings_user ON bookings (user_id);

CREATE TABLE booking_items (
                               id BIGSERIAL PRIMARY KEY,
                               booking_id BIGINT NOT NULL REFERENCES bookings(id),
                               seat_id BIGINT NOT NULL REFERENCES seats(id),
                               status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED'))
);

CREATE UNIQUE INDEX uq_active_seat_booking
    ON booking_items (seat_id) WHERE status = 'ACTIVE';

CREATE INDEX idx_booking_items_booking ON booking_items (booking_id);

CREATE TABLE idempotency_keys (
                                  user_id BIGINT NOT NULL REFERENCES users(id),
                                  idem_key VARCHAR(100) NOT NULL,
                                  request_hash CHAR(64) NOT NULL,
                                  response_status INT,
                                  response_body JSONB,
                                  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  PRIMARY KEY (user_id, idem_key)
);