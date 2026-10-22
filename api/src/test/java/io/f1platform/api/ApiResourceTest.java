package io.f1platform.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import io.f1platform.api.resources.PredictionResource;
import io.f1platform.api.resources.RaceResource;
import io.f1platform.api.resources.SeasonResource;
import io.f1platform.persistence.Migrations;
import jakarta.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;

@ExtendWith(DropwizardExtensionsSupport.class)
class ApiResourceTest {

    /**
     * Two seasons, one race in 2024 with a race and a sprint, and one stored prediction run for 2026 round 18.
     * In 2024, Hamilton scores 26 (second in the race, first in the sprint) and Verstappen 25 (first in the race,
     * retired from the sprint), so the standings must put Hamilton first.
     */
    static final String FIXTURE = """
            INSERT INTO season(year) VALUES (2024), (2026);
            INSERT INTO circuit(id, source_key, name, locality, country) VALUES (1, 'bahrain', 'Bahrain International Circuit', 'Sakhir', 'Bahrain');
            INSERT INTO circuit(id, source_key, name, locality, country) VALUES (2, 'americas', 'Circuit of the Americas', 'Austin', 'USA');
            INSERT INTO race(id, source_key, season_year, round, name, circuit_id, race_date) VALUES (1, '2024-1', 2024, 1, 'Bahrain Grand Prix', 1, '2024-03-02');
            INSERT INTO race(id, source_key, season_year, round, name, circuit_id, race_date) VALUES (2, '2026-18', 2026, 18, 'United States Grand Prix', 2, '2026-10-25');
            INSERT INTO session(id, source_key, race_id, type) VALUES (1, '2024-1-RACE', 1, 'RACE'), (2, '2024-1-SPRINT', 1, 'SPRINT');
            INSERT INTO driver(id, source_key, code, given_name, family_name) VALUES (1, 'max', 'VER', 'Max', 'Verstappen'), (2, 'hamilton', 'HAM', 'Lewis', 'Hamilton');
            INSERT INTO constructor(id, source_key, name) VALUES (1, 'red_bull', 'Red Bull'), (2, 'mercedes', 'Mercedes');
            INSERT INTO session_result(source_key, session_id, driver_id, constructor_id, car_number, position, position_text, status, grid, laps, time_text, points)
              VALUES ('r-ver', 1, 1, 1, 1, 1, '1', 'CLASSIFIED', 1, 57, '1:31:44.742', 25),
                     ('r-ham', 1, 2, 2, 44, 2, '2', 'CLASSIFIED', 2, 57, '+5.0', 18),
                     ('s-ham', 2, 2, 2, 44, 1, '1', 'CLASSIFIED', 1, 17, NULL, 8),
                     ('s-ver', 2, 1, 1, 1, NULL, 'R', 'RETIRED', NULL, 3, NULL, 0);
            INSERT INTO prediction_run(id, model_name, model_version, target_season, target_round, target_name, target_date, data_cutoff_date, input_races, grid_known, simulations, random_seed, parameters)
              VALUES (1, 'elo-plackett-luce', 'elo-k32-lambda1.0', 2026, 18, 'United States Grand Prix', '2026-10-25', '2026-10-11', 1166, FALSE, 20000, 2026, '{"k": 32, "lambda": 1.0}');
            INSERT INTO prediction(run_id, driver_id, constructor_id, rating_strength, win_prob, podium_prob, expected_position)
              VALUES (1, 1, 1, 581.2, 0.206, 0.536, 3.81), (1, 2, 2, 508.6, 0.135, 0.394, 4.88);
            """;

    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static final ObjectMapper JSON = new ObjectMapper();
    static final Queries QUERIES;

    static {
        PG.start();
        try {
            Migrations.migrate(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
            try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
                 Statement s = c.createStatement()) {
                s.execute(FIXTURE);
            }
            DatabaseConfig cfg = new DatabaseConfig();
            cfg.setUrl(PG.getJdbcUrl());
            cfg.setUser(PG.getUsername());
            cfg.setPassword(PG.getPassword());
            QUERIES = new Queries(new Database(cfg));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static final ResourceExtension EXT = ResourceExtension.builder()
            .setMapper(io.dropwizard.jackson.Jackson.newObjectMapper())
            .addResource(new SeasonResource(QUERIES))
            .addResource(new RaceResource(QUERIES))
            .addResource(new PredictionResource(QUERIES))
            .addProvider(new ApiExceptionMapper())
            .build();

    private static JsonNode body(Response r) throws Exception {
        return JSON.readTree(r.readEntity(String.class));
    }

    @Test
    void listsSeasonsWithRaceCounts() throws Exception {
        Response r = EXT.target("/api/v1/seasons").request().get();
        assertEquals(200, r.getStatus());
        assertTrue(r.getHeaderString("Cache-Control").contains("max-age"));
        JsonNode seasons = body(r);
        assertEquals(2024, seasons.get(0).get("year").asInt());
        assertEquals(1, seasons.get(0).get("races").asInt());
        assertEquals(1, seasons.get(1).get("races").asInt());
    }

    @Test
    void driverStandingsCountSprintPointsAndOrderByTotal() throws Exception {
        Response r = EXT.target("/api/v1/seasons/2024/standings/drivers").request().get();
        assertEquals(200, r.getStatus());
        JsonNode rows = body(r);
        assertEquals("HAM", rows.get(0).get("driverCode").asText());
        assertEquals(26, rows.get(0).get("points").asInt());
        assertEquals(0, rows.get(0).get("wins").asInt());
        assertEquals("VER", rows.get(1).get("driverCode").asText());
        assertEquals(25, rows.get(1).get("points").asInt());
        assertEquals(1, rows.get(1).get("wins").asInt());
    }

    @Test
    void constructorStandingsSumBothSessions() throws Exception {
        JsonNode rows = body(EXT.target("/api/v1/seasons/2024/standings/constructors").request().get());
        assertEquals("Mercedes", rows.get(0).get("name").asText());
        assertEquals(26, rows.get(0).get("points").asInt());
    }

    @Test
    void raceResultsDefaultToRaceAndFilterBySession() throws Exception {
        JsonNode race = body(EXT.target("/api/v1/seasons/2024/races/1/results").request().get());
        assertEquals(2, race.size());
        assertEquals("VER", race.get(0).get("driverCode").asText());

        JsonNode sprint = body(EXT.target("/api/v1/seasons/2024/races/1/results")
                .queryParam("session", "SPRINT").request().get());
        assertEquals("HAM", sprint.get(0).get("driverCode").asText());
        assertEquals("RETIRED", sprint.get(1).get("status").asText());
    }

    @Test
    void unknownSeasonAndRaceReturnProblemDetails() throws Exception {
        Response missing = EXT.target("/api/v1/seasons/1999/races").request().get();
        assertEquals(404, missing.getStatus());
        assertEquals("application/problem+json", missing.getMediaType().toString());
        assertEquals(404, body(missing).get("status").asInt());

        assertEquals(404, EXT.target("/api/v1/seasons/2024/races/9/results").request().get().getStatus());
    }

    @Test
    void invalidSessionIsRejected() {
        Response r = EXT.target("/api/v1/seasons/2024/races/1/results")
                .queryParam("session", "PRACTICE_1").request().get();
        assertEquals(400, r.getStatus());
    }

    @Test
    void predictionRunIsReturnedWithDriversBestFirst() throws Exception {
        Response r = EXT.target("/api/v1/predictions/2026/18").request().get();
        assertEquals(200, r.getStatus());
        JsonNode run = body(r).get(0);
        assertEquals("elo-k32-lambda1.0", run.get("modelVersion").asText());
        assertEquals("2026-10-11", run.get("dataCutoffDate").asText());
        assertEquals(false, run.get("gridKnown").asBoolean());
        assertEquals(1.0, run.get("parameters").get("lambda").asDouble(), 1e-9);
        assertEquals("Verstappen", run.get("predictions").get(0).get("familyName").asText());
        assertEquals(0.206, run.get("predictions").get(0).get("winProb").asDouble(), 1e-9);
    }

    @Test
    void noPredictionForAnUnknownTargetIsNotFound() {
        assertEquals(404, EXT.target("/api/v1/predictions/2026/19").request().get().getStatus());
    }
}
