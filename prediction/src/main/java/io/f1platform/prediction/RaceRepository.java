package io.f1platform.prediction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads completed races, in date order, from the V1 schema. */
public final class RaceRepository {

    private RaceRepository() {}

    public static List<RaceRecord> loadRaces(String url, String user, String password) throws SQLException {
        return loadRacesBefore(url, user, password, null);
    }

    /**
     * Completed races strictly before cutoffDate (exclusive). Passing the target race's date enforces the
     * prediction cutoff in the query itself, so later results can never leak into a prediction.
     * A null cutoff loads everything.
     */
    public static List<RaceRecord> loadRacesBefore(String url, String user, String password, LocalDate cutoffDate)
            throws SQLException {
        Map<String, Acc> byRace = new LinkedHashMap<>();
        String sql = """
                SELECT r.season_year, r.round, r.name, r.race_date, s.type,
                       sr.driver_id, sr.constructor_id, sr.position, sr.points
                FROM session_result sr
                JOIN session s ON s.id = sr.session_id
                JOIN race r ON r.id = s.race_id
                JOIN driver d ON d.id = sr.driver_id
                WHERE s.type IN ('RACE', 'SPRINT')
                  AND (CAST(? AS DATE) IS NULL OR r.race_date < CAST(? AS DATE))
                -- Field order must not depend on how the database returns rows: the simulation draws
                -- cars in list order, so the order is fixed by provider keys compared in byte order (COLLATE "C"),
                -- because text collation can differ between databases and machines.
                ORDER BY r.season_year, r.round, s.type, d.source_key COLLATE "C", sr.car_number NULLS FIRST""";
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, cutoffDate);
            ps.setObject(2, cutoffDate);
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int season = rs.getInt(1);
                int round = rs.getInt(2);
                String raceName = rs.getString(3);
                LocalDate raceDate = rs.getDate(4).toLocalDate();
                String key = season + "-" + round;
                Acc acc = byRace.computeIfAbsent(key, k -> new Acc(season, round, raceName, raceDate));
                String type = rs.getString(5);
                long driverId = rs.getLong(6);
                long constructorId = rs.getLong(7);
                Integer position = rs.getObject(8) == null ? null : rs.getInt(8);
                double points = rs.getDouble(9);
                if (type.equals("RACE")) {
                    acc.entries.add(new Entry(driverId, constructorId, position, points));
                } else {
                    acc.sprintPoints.merge(driverId, points, Double::sum);
                }
            }
            }
        }
        List<RaceRecord> races = new ArrayList<>();
        for (Acc acc : byRace.values()) {
            if (!acc.entries.isEmpty()) {
                races.add(new RaceRecord(acc.season, acc.round, acc.name, acc.date, acc.entries, acc.sprintPoints));
            }
        }
        return races;
    }

    private static final class Acc {
        final int season;
        final int round;
        final String name;
        final LocalDate date;
        final List<Entry> entries = new ArrayList<>();
        final Map<Long, Double> sprintPoints = new HashMap<>();

        Acc(int season, int round, String name, LocalDate date) {
            this.season = season;
            this.round = round;
            this.name = name;
            this.date = date;
        }
    }
}
