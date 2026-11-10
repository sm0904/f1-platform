package io.f1platform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.f1platform.api.Dtos.ConstructorStanding;
import io.f1platform.api.Dtos.DriverStanding;
import io.f1platform.api.Dtos.PredictedDriver;
import io.f1platform.api.Dtos.PredictionRun;
import io.f1platform.api.Dtos.RaceSummary;
import io.f1platform.api.Dtos.ResultRow;
import io.f1platform.api.Dtos.SeasonSummary;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only queries over the V1 and V2 schema. Each method opens one connection from the pool
 * and closes it. Standings count race and sprint points, as the championship does; wins count races only.
 */
public final class Queries {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Database db;

    public Queries(Database db) {
        this.db = db;
    }

    public List<SeasonSummary> seasons() throws SQLException {
        List<SeasonSummary> out = new ArrayList<>();
        String sql = """
                SELECT s.year, count(r.id) FROM season s
                LEFT JOIN race r ON r.season_year = s.year
                GROUP BY s.year ORDER BY s.year""";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new SeasonSummary(rs.getInt(1), rs.getInt(2)));
            }
        }
        return out;
    }

    public boolean seasonExists(int year) throws SQLException {
        return exists("SELECT 1 FROM season WHERE year = ?", year);
    }

    public List<RaceSummary> races(int year) throws SQLException {
        List<RaceSummary> out = new ArrayList<>();
        String sql = """
                SELECT r.season_year, r.round, r.name, r.race_date, c.name, c.locality, c.country
                FROM race r JOIN circuit c ON c.id = r.circuit_id
                WHERE r.season_year = ? ORDER BY r.round""";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, year);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new RaceSummary(rs.getInt(1), rs.getInt(2), rs.getString(3),
                            rs.getObject(4, LocalDate.class), rs.getString(5), rs.getString(6), rs.getString(7)));
                }
            }
        }
        return out;
    }

    public boolean raceExists(int year, int round) throws SQLException {
        return exists("SELECT 1 FROM race WHERE season_year = ? AND round = ?", year, round);
    }

    public List<ResultRow> results(int year, int round, String sessionType) throws SQLException {
        List<ResultRow> out = new ArrayList<>();
        String sql = """
                SELECT sr.position, sr.position_text, sr.status, sr.status_detail, sr.car_number, sr.grid, sr.laps,
                       sr.time_text, sr.points, d.code, d.given_name, d.family_name, co.name, sr.q1, sr.q2, sr.q3
                FROM session_result sr
                JOIN session s ON s.id = sr.session_id
                JOIN race r ON r.id = s.race_id
                JOIN driver d ON d.id = sr.driver_id
                JOIN constructor co ON co.id = sr.constructor_id
                WHERE r.season_year = ? AND r.round = ? AND s.type = ?
                ORDER BY sr.position NULLS LAST, sr.position_text, d.family_name""";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, year);
            ps.setInt(2, round);
            ps.setString(3, sessionType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ResultRow(nullableInt(rs, 1), rs.getString(2), rs.getString(3), rs.getString(4),
                            nullableInt(rs, 5), nullableInt(rs, 6), nullableInt(rs, 7), rs.getString(8),
                            rs.getBigDecimal(9), rs.getString(10), rs.getString(11), rs.getString(12),
                            rs.getString(13), rs.getString(14), rs.getString(15), rs.getString(16)));
                }
            }
        }
        return out;
    }

    public List<DriverStanding> driverStandings(int year) throws SQLException {
        List<DriverStanding> out = new ArrayList<>();
        String sql = """
                SELECT d.code, d.given_name, d.family_name,
                       SUM(sr.points) AS points,
                       COUNT(*) FILTER (WHERE s.type = 'RACE' AND sr.position = 1) AS wins
                FROM session_result sr
                JOIN session s ON s.id = sr.session_id
                JOIN race r ON r.id = s.race_id
                JOIN driver d ON d.id = sr.driver_id
                WHERE r.season_year = ? AND s.type IN ('RACE', 'SPRINT')
                GROUP BY d.id, d.code, d.given_name, d.family_name
                ORDER BY points DESC, wins DESC, d.family_name""";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, year);
            try (ResultSet rs = ps.executeQuery()) {
                int position = 0;
                while (rs.next()) {
                    position++;
                    out.add(new DriverStanding(position, rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getBigDecimal(4), rs.getInt(5)));
                }
            }
        }
        return out;
    }

    public List<ConstructorStanding> constructorStandings(int year) throws SQLException {
        List<ConstructorStanding> out = new ArrayList<>();
        String sql = """
                SELECT co.name, SUM(sr.points) AS points,
                       COUNT(*) FILTER (WHERE s.type = 'RACE' AND sr.position = 1) AS wins
                FROM session_result sr
                JOIN session s ON s.id = sr.session_id
                JOIN race r ON r.id = s.race_id
                JOIN constructor co ON co.id = sr.constructor_id
                WHERE r.season_year = ? AND s.type IN ('RACE', 'SPRINT')
                GROUP BY co.id, co.name
                ORDER BY points DESC, wins DESC, co.name""";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, year);
            try (ResultSet rs = ps.executeQuery()) {
                int position = 0;
                while (rs.next()) {
                    position++;
                    out.add(new ConstructorStanding(position, rs.getString(1), rs.getBigDecimal(2), rs.getInt(3)));
                }
            }
        }
        return out;
    }

    /** All stored prediction runs for one target race, newest first. Each run lists its drivers, best first. */
    public List<PredictionRun> predictionRuns(int year, int round) throws SQLException {
        List<PredictionRun> runs = new ArrayList<>();
        String runSql = """
                SELECT id, model_name, model_version, target_season, target_round, target_name, target_date,
                       data_cutoff_date, input_races, grid_known, simulations, random_seed, created_at, parameters
                FROM prediction_run WHERE target_season = ? AND target_round = ?
                ORDER BY created_at DESC, id DESC""";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(runSql)) {
            ps.setInt(1, year);
            ps.setInt(2, round);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    JsonNode parameters = readJson(rs.getString(14));
                    runs.add(new PredictionRun(id, rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5),
                            rs.getString(6), rs.getObject(7, LocalDate.class), rs.getObject(8, LocalDate.class),
                            rs.getInt(9), rs.getBoolean(10), rs.getInt(11), rs.getLong(12),
                            rs.getObject(13, OffsetDateTime.class).toInstant(), parameters, new ArrayList<>()));
                }
            }
        }
        List<PredictionRun> withPredictions = new ArrayList<>();
        for (PredictionRun run : runs) {
            withPredictions.add(withPredictions(run, c -> predictionsFor(c, run.id())));
        }
        return withPredictions;
    }

    private List<PredictedDriver> predictionsFor(Connection c, long runId) throws SQLException {
        List<PredictedDriver> out = new ArrayList<>();
        String sql = """
                SELECT d.given_name, d.family_name, co.name, p.rating_strength, p.win_prob, p.podium_prob,
                       p.expected_position
                FROM prediction p
                JOIN driver d ON d.id = p.driver_id
                JOIN constructor co ON co.id = p.constructor_id
                WHERE p.run_id = ? ORDER BY p.expected_position, d.family_name""";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PredictedDriver(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getDouble(4), rs.getDouble(5), rs.getDouble(6), rs.getDouble(7)));
                }
            }
        }
        return out;
    }

    private interface PredictionLoader {
        List<PredictedDriver> load(Connection c) throws SQLException;
    }

    private PredictionRun withPredictions(PredictionRun run, PredictionLoader loader) throws SQLException {
        try (Connection c = db.connection()) {
            return new PredictionRun(run.id(), run.modelName(), run.modelVersion(), run.targetSeason(),
                    run.targetRound(), run.targetName(), run.targetDate(), run.dataCutoffDate(), run.inputRaces(),
                    run.gridKnown(), run.simulations(), run.randomSeed(), run.createdAt(), run.parameters(),
                    loader.load(c));
        }
    }

    private boolean exists(String sql, Object... params) throws SQLException {
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Integer nullableInt(ResultSet rs, int col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static JsonNode readJson(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("stored parameters are not valid JSON", e);
        }
    }

    // --- Points progression ---

    public List<Dtos.PointsProgression> pointsProgression(int year) throws SQLException {
        // One row per driver per round, with cumulative points across race and sprint.
        String sql = """
                WITH per_round AS (
                    SELECT d.id AS driver_id, d.code, d.given_name, d.family_name, co.name AS constructor,
                           r.round, r.name AS race_name, SUM(sr.points) AS round_points
                    FROM session_result sr
                    JOIN session s ON s.id = sr.session_id
                    JOIN race r ON r.id = s.race_id
                    JOIN driver d ON d.id = sr.driver_id
                    JOIN constructor co ON co.id = sr.constructor_id
                    WHERE r.season_year = ? AND s.type IN ('RACE', 'SPRINT')
                    GROUP BY d.id, d.code, d.given_name, d.family_name, co.name, r.round, r.name
                )
                SELECT driver_id, code, given_name, family_name, constructor, round, race_name,
                       SUM(round_points) OVER (PARTITION BY driver_id ORDER BY round) AS cumulative
                FROM per_round
                ORDER BY driver_id, round""";
        java.util.LinkedHashMap<Long, Dtos.PointsProgression> map = new java.util.LinkedHashMap<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, year);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long did = rs.getLong(1);
                    String code = rs.getString(2); String gn = rs.getString(3);
                    String fn = rs.getString(4); String ctor = rs.getString(5);
                    map.computeIfAbsent(did, id -> new Dtos.PointsProgression(code, gn, fn, ctor, new ArrayList<>()));
                    map.get(did).rounds().add(new Dtos.RoundPoints(rs.getInt(6), rs.getString(7), rs.getDouble(8)));
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    // --- Driver profile and results ---

    public Dtos.DriverProfile driverByCode(String code) throws SQLException {
        String sql = "SELECT id, code, given_name, family_name, nationality, permanent_number FROM driver WHERE code = ? ORDER BY id DESC LIMIT 1";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Dtos.DriverProfile(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), nullableInt(rs, 6));
            }
        }
    }

    public List<Dtos.DriverResult> driverResults(long driverId, Integer season) throws SQLException {
        String sql = """
                SELECT r.season_year, r.round, r.name, ci.name, s.type, sr.position, sr.position_text, sr.status,
                       sr.grid, sr.points, co.name
                FROM session_result sr
                JOIN session s ON s.id = sr.session_id
                JOIN race r ON r.id = s.race_id
                JOIN circuit ci ON ci.id = r.circuit_id
                JOIN constructor co ON co.id = sr.constructor_id
                WHERE sr.driver_id = ? AND s.type IN ('RACE', 'QUALIFYING')"""
                + (season != null ? " AND r.season_year = ?" : "")
                + " ORDER BY r.season_year DESC, r.round DESC, s.type";
        List<Dtos.DriverResult> out = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, driverId);
            if (season != null) ps.setInt(2, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Dtos.DriverResult(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), nullableInt(rs, 6), rs.getString(7), rs.getString(8),
                            nullableInt(rs, 9), rs.getDouble(10), rs.getString(11)));
                }
            }
        }
        return out;
    }

    // --- Constructor profile ---

    public Dtos.ConstructorProfile constructorByName(String name) throws SQLException {
        String sql = "SELECT id, name, nationality FROM constructor WHERE LOWER(name) = LOWER(?) LIMIT 1";
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Dtos.ConstructorProfile(rs.getLong(1), rs.getString(2), rs.getString(3));
            }
        }
    }

    public List<Dtos.ConstructorSeason> constructorSeasons(long constructorId) throws SQLException {
        String sql = """
                SELECT r.season_year, SUM(sr.points),
                       COUNT(*) FILTER (WHERE s.type='RACE' AND sr.position=1),
                       COUNT(*) FILTER (WHERE s.type='RACE' AND sr.position<=3),
                       (SELECT d2.family_name FROM session_result sr2 JOIN session s2 ON s2.id=sr2.session_id
                        JOIN race r2 ON r2.id=s2.race_id JOIN driver d2 ON d2.id=sr2.driver_id
                        WHERE sr2.constructor_id=? AND r2.season_year=r.season_year AND s2.type IN ('RACE','SPRINT')
                        GROUP BY d2.family_name ORDER BY SUM(sr2.points) DESC LIMIT 1)
                FROM session_result sr JOIN session s ON s.id=sr.session_id JOIN race r ON r.id=s.race_id
                WHERE sr.constructor_id=? AND s.type IN ('RACE','SPRINT')
                GROUP BY r.season_year ORDER BY r.season_year DESC""";
        List<Dtos.ConstructorSeason> out = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, constructorId);
            ps.setLong(2, constructorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Dtos.ConstructorSeason(rs.getInt(1), rs.getDouble(2), rs.getInt(3), rs.getInt(4), rs.getString(5)));
                }
            }
        }
        return out;
    }

    // --- Grid vs finish ---

    public List<Dtos.GridFinish> gridVsFinish(int year, int round) throws SQLException {
        String sql = """
                SELECT d.code, d.family_name, co.name, sr.grid, sr.position
                FROM session_result sr JOIN session s ON s.id=sr.session_id JOIN race r ON r.id=s.race_id
                JOIN driver d ON d.id=sr.driver_id JOIN constructor co ON co.id=sr.constructor_id
                WHERE r.season_year=? AND r.round=? AND s.type='RACE' AND sr.grid IS NOT NULL AND sr.position IS NOT NULL
                ORDER BY sr.position""";
        List<Dtos.GridFinish> out = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, year); ps.setInt(2, round);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int grid = rs.getInt(4), finish = rs.getInt(5);
                    out.add(new Dtos.GridFinish(rs.getString(1), rs.getString(2), rs.getString(3), grid, finish, grid - finish));
                }
            }
        }
        return out;
    }

}
