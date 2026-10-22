-- V1: core historical schema.
-- Covers the four competitive session types.
-- Status is derived from positionText by the loader; see docs/STATUS_MAPPING.md. Practice sessions are out of scope.
-- Every provider identifier is kept in source_key, so provider mapping never leaks into domain IDs.

CREATE TABLE season (
    year        INTEGER PRIMARY KEY,
    CONSTRAINT season_year_range CHECK (year >= 1950)
);

CREATE TABLE circuit (
    id          BIGSERIAL PRIMARY KEY,
    source_key  TEXT NOT NULL UNIQUE,
    name        TEXT NOT NULL,
    locality    TEXT,
    country     TEXT,
    CONSTRAINT circuit_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE TABLE race (
    id           BIGSERIAL PRIMARY KEY,
    source_key   TEXT NOT NULL UNIQUE,
    season_year  INTEGER NOT NULL REFERENCES season (year),
    round        INTEGER NOT NULL,
    name         TEXT NOT NULL,
    circuit_id   BIGINT NOT NULL REFERENCES circuit (id),
    race_date    DATE NOT NULL,
    CONSTRAINT race_round_min CHECK (round >= 1),
    CONSTRAINT race_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT race_round_per_season UNIQUE (season_year, round)
);

CREATE TABLE session (
    id          BIGSERIAL PRIMARY KEY,
    source_key  TEXT UNIQUE,
    race_id     BIGINT NOT NULL REFERENCES race (id),
    type        TEXT NOT NULL,
    CONSTRAINT session_type_valid CHECK (type IN ('QUALIFYING', 'SPRINT_QUALIFYING', 'SPRINT', 'RACE')),
    CONSTRAINT session_one_per_type_per_race UNIQUE (race_id, type)
);

CREATE TABLE driver (
    id                BIGSERIAL PRIMARY KEY,
    source_key        TEXT NOT NULL UNIQUE,
    code              TEXT,
    permanent_number  INTEGER,
    given_name        TEXT NOT NULL,
    family_name       TEXT NOT NULL,
    nationality       TEXT,
    CONSTRAINT driver_code_check CHECK (code IS NULL OR code ~ '^[A-Z]{3}$'),
    CONSTRAINT driver_permanent_number_positive CHECK (permanent_number IS NULL OR permanent_number > 0)
    -- code and permanent_number are deliberately NOT unique: both were reused across eras.
);

CREATE TABLE constructor (
    id          BIGSERIAL PRIMARY KEY,
    source_key  TEXT NOT NULL UNIQUE,
    name        TEXT NOT NULL,
    nationality TEXT,
    CONSTRAINT constructor_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE TABLE session_result (
    id              BIGSERIAL PRIMARY KEY,
    source_key      TEXT UNIQUE,
    session_id      BIGINT NOT NULL REFERENCES session (id),
    driver_id       BIGINT NOT NULL REFERENCES driver (id),
    constructor_id  BIGINT NOT NULL REFERENCES constructor (id),
    -- Car number identifies the entry. Some drivers drove two cars in one race (1950 to 1964).
    car_number      INTEGER NOT NULL,
    position        INTEGER,
    position_text   TEXT NOT NULL,
    status          TEXT NOT NULL,
    -- Raw status text from the source (e.g. 'Engine', '+2 Laps'), kept for audit.
    status_detail   TEXT,
    grid            INTEGER,
    laps            INTEGER,
    time_text       TEXT,
    points          NUMERIC(4,1) NOT NULL DEFAULT 0,
    q1              TEXT,
    q2              TEXT,
    q3              TEXT,
    CONSTRAINT result_status_valid CHECK (
        status IN ('CLASSIFIED', 'RETIRED', 'DISQUALIFIED', 'EXCLUDED', 'WITHDRAWN', 'NOT_CLASSIFIED')
    ),
    CONSTRAINT classified_requires_position CHECK (status <> 'CLASSIFIED' OR position IS NOT NULL),
    CONSTRAINT result_position_positive CHECK (position IS NULL OR position >= 1),
    CONSTRAINT result_grid_non_negative CHECK (grid IS NULL OR grid >= 0),
    CONSTRAINT result_laps_non_negative CHECK (laps IS NULL OR laps >= 0),
    CONSTRAINT result_points_non_negative CHECK (points >= 0),
    CONSTRAINT result_car_number_positive CHECK (car_number > 0),
    CONSTRAINT result_one_entry_per_car UNIQUE (session_id, driver_id, car_number)
);

CREATE INDEX session_result_driver_idx ON session_result (driver_id);
CREATE INDEX session_result_constructor_idx ON session_result (constructor_id);
