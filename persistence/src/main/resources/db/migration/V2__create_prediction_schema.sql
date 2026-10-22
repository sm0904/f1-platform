-- V2: prediction runs and their per-driver outputs.
-- A run is a frozen prediction for one target race, computed from results up to data_cutoff_date.
-- Once stored, a run is never overwritten: the unique key stops a second run for the same target and model version.

CREATE TABLE prediction_run (
    id                 BIGSERIAL PRIMARY KEY,
    model_name         TEXT NOT NULL,
    model_version      TEXT NOT NULL,
    target_season      INTEGER NOT NULL,
    target_round       INTEGER NOT NULL,
    target_name        TEXT NOT NULL,
    target_date        DATE NOT NULL,
    data_cutoff_date   DATE NOT NULL,
    input_races        INTEGER NOT NULL,
    grid_known         BOOLEAN NOT NULL DEFAULT FALSE,
    simulations        INTEGER NOT NULL,
    random_seed        BIGINT NOT NULL,
    parameters         JSONB NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT run_cutoff_before_target CHECK (data_cutoff_date < target_date),
    CONSTRAINT run_input_races_positive CHECK (input_races > 0),
    CONSTRAINT run_simulations_positive CHECK (simulations > 0),
    CONSTRAINT run_one_per_target UNIQUE (model_name, model_version, target_season, target_round, grid_known)
);

CREATE TABLE prediction (
    id                  BIGSERIAL PRIMARY KEY,
    run_id              BIGINT NOT NULL REFERENCES prediction_run (id),
    driver_id           BIGINT NOT NULL REFERENCES driver (id),
    constructor_id      BIGINT NOT NULL REFERENCES constructor (id),
    rating_strength     DOUBLE PRECISION NOT NULL,
    win_prob            DOUBLE PRECISION NOT NULL,
    podium_prob         DOUBLE PRECISION NOT NULL,
    expected_position   DOUBLE PRECISION NOT NULL,
    CONSTRAINT prediction_win_range CHECK (win_prob >= 0 AND win_prob <= 1),
    CONSTRAINT prediction_podium_range CHECK (podium_prob >= 0 AND podium_prob <= 1),
    CONSTRAINT prediction_podium_ge_win CHECK (podium_prob >= win_prob),
    CONSTRAINT prediction_expected_position_range CHECK (expected_position >= 1),
    CONSTRAINT prediction_one_per_driver UNIQUE (run_id, driver_id)
);
