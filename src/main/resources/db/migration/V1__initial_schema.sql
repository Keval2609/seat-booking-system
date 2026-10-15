CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       role VARCHAR(20) NOT NULL
                           CHECK (role IN ('USER', 'ADMIN')),
                       created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE events (
                        id BIGSERIAL PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        venue VARCHAR(255) NOT NULL,
                        starts_at TIMESTAMPTZ NOT NULL,
                        created_by BIGINT NOT NULL REFERENCES users(id),
                        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);