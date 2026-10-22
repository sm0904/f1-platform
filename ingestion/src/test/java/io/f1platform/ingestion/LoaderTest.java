package io.f1platform.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.f1platform.ingestion.JolpicaReader.RaceKey;
import io.f1platform.persistence.Migrations;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class LoaderTest {

    private static long count(Connection c, String sql) throws Exception {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    void loadsFixtureAndIsIdempotent() throws Exception {
        try (var pg = new PostgreSQLContainer("postgres:16-alpine")) {
            pg.start();
            Migrations.migrate(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());

            Path root = JolpicaReaderTest.fixtureRoot();
            Map<RaceKey, JolpicaReader.RaceBundle> races = JolpicaReader.read(root);
            PostgresLoader loader = new PostgresLoader(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());

            PostgresLoader.Summary first = loader.load(races);
            assertEquals(3, first.races());
            assertEquals(5, first.sessions());
            assertEquals(124, first.results());

            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
                assertEquals(2, count(c, "SELECT count(*) FROM season"));
                assertEquals(3, count(c, "SELECT count(*) FROM race"));
                assertEquals(5, count(c, "SELECT count(*) FROM session"));
                assertEquals(124, count(c, "SELECT count(*) FROM session_result"));
                assertEquals(2, count(c, "SELECT count(*) FROM session_result sr JOIN driver d ON d.id = sr.driver_id "
                        + "JOIN session s ON s.id = sr.session_id JOIN race r ON r.id = s.race_id "
                        + "WHERE d.source_key = 'bettenhausen' AND r.season_year = 1950 AND r.round = 3"));
            }

            PostgresLoader.Summary second = new PostgresLoader(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                    .load(races);
            assertEquals(first, second);
            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
                assertEquals(124, count(c, "SELECT count(*) FROM session_result"));
                assertEquals(3, count(c, "SELECT count(*) FROM race"));
            }
        }
    }

    @Test
    void rejectsSameDriverAndCarTwiceInOneSession() throws Exception {
        // Copy the 2024 race page, duplicate its first result row, and bump the total so completeness passes.
        Path root = JolpicaReaderTest.fixtureRoot();
        Path tmp = Files.createTempDirectory("dup-fixture");
        Path src = root.resolve("race/2024/results-0.json");
        Path dst = tmp.resolve("race/2024");
        Files.createDirectories(dst);
        String json = Files.readString(src);
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode doc = (com.fasterxml.jackson.databind.node.ObjectNode) m.readTree(json);
        com.fasterxml.jackson.databind.node.ObjectNode mr = (com.fasterxml.jackson.databind.node.ObjectNode) doc.get("MRData");
        com.fasterxml.jackson.databind.node.ArrayNode results =
                (com.fasterxml.jackson.databind.node.ArrayNode) mr.get("RaceTable").get("Races").get(0).get("Results");
        results.add(results.get(0).deepCopy());
        mr.put("total", String.valueOf(results.size()));
        Files.writeString(dst.resolve("results-0.json"), m.writeValueAsString(doc));

        try (var pg = new PostgreSQLContainer("postgres:16-alpine")) {
            pg.start();
            Migrations.migrate(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            Map<RaceKey, JolpicaReader.RaceBundle> races = JolpicaReader.read(tmp);
            PostgresLoader loader = new PostgresLoader(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> loader.load(races));
            assertTrue(e.getMessage().contains("appears twice"), e.getMessage());
        }
    }
}
