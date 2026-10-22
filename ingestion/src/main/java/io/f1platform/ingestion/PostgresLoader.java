package io.f1platform.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import io.f1platform.ingestion.JolpicaReader.RaceBundle;
import io.f1platform.ingestion.JolpicaReader.RaceKey;
import io.f1platform.ingestion.JolpicaReader.SessionKind;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes race bundles into the V1 schema. Every write is an upsert keyed on source_key,
 * so re-running a load updates rows rather than duplicating them. One transaction per race.
 */
public final class PostgresLoader {

    public record Summary(int races, int sessions, int drivers, int constructors, int results) {}

    private final String jdbcUrl;
    private final String user;
    private final String password;

    private final Map<String, Long> driverIds = new HashMap<>();
    private final Map<String, Long> constructorIds = new HashMap<>();

    public PostgresLoader(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
    }

    public Summary load(Map<RaceKey, RaceBundle> races) throws SQLException {
        int sessions = 0;
        int results = 0;
        int loaded = 0;
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            conn.setAutoCommit(false);
            List<RaceKey> keys = races.keySet().stream()
                    .sorted((a, b) -> a.season() != b.season()
                            ? Integer.compare(a.season(), b.season())
                            : Integer.compare(a.round(), b.round()))
                    .toList();
            for (RaceKey key : keys) {
                RaceBundle bundle = races.get(key);
                try {
                    int[] counts = loadRace(conn, bundle);
                    conn.commit();
                    sessions += counts[0];
                    results += counts[1];
                    loaded++;
                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw new IllegalStateException(
                            "failed loading " + key.season() + " round " + key.round() + " (" + bundle.raceName() + "): "
                                    + e.getMessage(), e);
                }
            }
        }
        return new Summary(loaded, sessions, driverIds.size(), constructorIds.size(), results);
    }

    /** Returns {sessions written, results written} for one race. */
    private int[] loadRace(Connection conn, RaceBundle bundle) throws SQLException {
        RaceKey key = bundle.key();
        upsertSeason(conn, key.season());
        long circuitId = upsertCircuit(conn, bundle.circuit());
        long raceId = upsertRace(conn, bundle, circuitId);

        int sessionCount = 0;
        int resultCount = 0;
        for (Map.Entry<SessionKind, List<JsonNode>> entry : bundle.results().entrySet()) {
            SessionKind kind = entry.getKey();
            long sessionId = upsertSession(conn, key, kind, raceId);
            sessionCount++;
            Set<String> seenEntries = new HashSet<>();
            for (JsonNode row : entry.getValue()) {
                long driverId = upsertDriver(conn, row.path("Driver"));
                long constructorId = upsertConstructor(conn, row.path("Constructor"));
                Integer carNumber = carNumberOrNull(row);
                String driverKey = row.path("Driver").path("driverId").asText();
                String entryKey = driverKey + "|" + (carNumber == null ? "none" : carNumber);
                if (!seenEntries.add(entryKey)) {
                    throw new IllegalStateException(kind.directory + ": driver " + driverKey
                            + " appears twice with car number " + (carNumber == null ? "none" : carNumber)
                            + " in the same session");
                }
                upsertResult(conn, key, kind, sessionId, driverId, constructorId, carNumber, row);
                resultCount++;
            }
        }
        return new int[] {sessionCount, resultCount};
    }

    private static void upsertSeason(Connection conn, int year) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO season(year) VALUES (?) ON CONFLICT (year) DO NOTHING")) {
            ps.setInt(1, year);
            ps.executeUpdate();
        }
    }

    private static long upsertCircuit(Connection conn, JsonNode circuit) throws SQLException {
        JsonNode location = circuit.path("Location");
        return returningId(conn, """
                INSERT INTO circuit(source_key, name, locality, country) VALUES (?, ?, ?, ?)
                ON CONFLICT (source_key) DO UPDATE SET name = EXCLUDED.name,
                    locality = EXCLUDED.locality, country = EXCLUDED.country
                RETURNING id""",
                circuit.path("circuitId").asText(),
                circuit.path("circuitName").asText(),
                nullableText(location.path("locality")),
                nullableText(location.path("country")));
    }

    private static long upsertRace(Connection conn, RaceBundle bundle, long circuitId) throws SQLException {
        RaceKey key = bundle.key();
        return returningId(conn, """
                INSERT INTO race(source_key, season_year, round, name, circuit_id, race_date) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_key) DO UPDATE SET name = EXCLUDED.name,
                    circuit_id = EXCLUDED.circuit_id, race_date = EXCLUDED.race_date
                RETURNING id""",
                raceKey(key), key.season(), key.round(), bundle.raceName(), circuitId,
                java.sql.Date.valueOf(LocalDate.parse(bundle.date())));
    }

    private static long upsertSession(Connection conn, RaceKey key, SessionKind kind, long raceId) throws SQLException {
        return returningId(conn, """
                INSERT INTO session(source_key, race_id, type) VALUES (?, ?, ?)
                ON CONFLICT (source_key) DO UPDATE SET type = EXCLUDED.type
                RETURNING id""",
                sessionKey(key, kind), raceId, kind.dbType);
    }

    private long upsertDriver(Connection conn, JsonNode driver) throws SQLException {
        String sourceKey = driver.path("driverId").asText();
        Long cached = driverIds.get(sourceKey);
        if (cached != null) {
            return cached;
        }
        String permanent = driver.path("permanentNumber").asText("");
        long id = returningId(conn, """
                INSERT INTO driver(source_key, code, permanent_number, given_name, family_name, nationality)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_key) DO UPDATE SET code = EXCLUDED.code,
                    permanent_number = EXCLUDED.permanent_number, given_name = EXCLUDED.given_name,
                    family_name = EXCLUDED.family_name, nationality = EXCLUDED.nationality
                RETURNING id""",
                sourceKey,
                nullableText(driver.path("code")),
                permanent.isEmpty() ? null : Integer.parseInt(permanent),
                driver.path("givenName").asText(),
                driver.path("familyName").asText(),
                nullableText(driver.path("nationality")));
        driverIds.put(sourceKey, id);
        return id;
    }

    private long upsertConstructor(Connection conn, JsonNode constructor) throws SQLException {
        String sourceKey = constructor.path("constructorId").asText();
        Long cached = constructorIds.get(sourceKey);
        if (cached != null) {
            return cached;
        }
        long id = returningId(conn, """
                INSERT INTO constructor(source_key, name, nationality) VALUES (?, ?, ?)
                ON CONFLICT (source_key) DO UPDATE SET name = EXCLUDED.name, nationality = EXCLUDED.nationality
                RETURNING id""",
                sourceKey, constructor.path("name").asText(), nullableText(constructor.path("nationality")));
        constructorIds.put(sourceKey, id);
        return id;
    }

    private static void upsertResult(Connection conn, RaceKey key, SessionKind kind, long sessionId,
                                     long driverId, long constructorId, Integer carNumber, JsonNode row) throws SQLException {
        String positionText = row.has("positionText")
                ? row.path("positionText").asText()
                : row.path("position").asText();
        String statusText = row.path("status").asText("");
        StatusMapper.Status status = StatusMapper.map(positionText, statusText);
        Integer position = status == StatusMapper.Status.CLASSIFIED ? Integer.valueOf(positionText) : null;
        Integer grid = row.has("grid") ? Integer.valueOf(row.path("grid").asText()) : null;
        Integer laps = row.has("laps") ? Integer.valueOf(row.path("laps").asText()) : null;
        BigDecimal points = row.has("points") ? new BigDecimal(row.path("points").asText()) : BigDecimal.ZERO;
        JsonNode time = row.path("Time").path("time");
        String sourceKey = sessionKey(key, kind) + "|" + row.path("Driver").path("driverId").asText()
                + "|" + (carNumber == null ? "none" : carNumber);

        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO session_result(source_key, session_id, driver_id, constructor_id, car_number,
                    position, position_text, status, status_detail, grid, laps, time_text, points, q1, q2, q3)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_key) DO UPDATE SET position = EXCLUDED.position,
                    position_text = EXCLUDED.position_text, status = EXCLUDED.status,
                    status_detail = EXCLUDED.status_detail, grid = EXCLUDED.grid, laps = EXCLUDED.laps,
                    time_text = EXCLUDED.time_text, points = EXCLUDED.points,
                    q1 = EXCLUDED.q1, q2 = EXCLUDED.q2, q3 = EXCLUDED.q3""")) {
            ps.setString(1, sourceKey);
            ps.setLong(2, sessionId);
            ps.setLong(3, driverId);
            ps.setLong(4, constructorId);
            setNullableInt(ps, 5, carNumber);
            setNullableInt(ps, 6, position);
            ps.setString(7, positionText);
            ps.setString(8, status.name());
            ps.setString(9, statusText.isEmpty() ? null : statusText);
            setNullableInt(ps, 10, grid);
            setNullableInt(ps, 11, laps);
            ps.setString(12, time.isMissingNode() || time.isNull() ? null : time.asText());
            ps.setBigDecimal(13, points);
            ps.setString(14, nullableText(row.path("Q1")));
            ps.setString(15, nullableText(row.path("Q2")));
            ps.setString(16, nullableText(row.path("Q3")));
            ps.executeUpdate();
        }
    }

    /** The provider reports no number as a missing value, or as 0 for placeholders. Both become NULL. */
    static Integer carNumberOrNull(JsonNode row) {
        JsonNode n = row.path("number");
        if (n.isMissingNode() || n.isNull() || n.asText().isEmpty()) {
            return null;
        }
        int value = n.asInt();
        return value > 0 ? value : null;
    }

    private static long returningId(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static String nullableText(JsonNode node) {
        return node.isMissingNode() || node.isNull() || node.asText().isEmpty() ? null : node.asText();
    }

    static String raceKey(RaceKey key) {
        return key.season() + "-" + key.round();
    }

    static String sessionKey(RaceKey key, SessionKind kind) {
        return raceKey(key) + "-" + kind.dbType;
    }
}
