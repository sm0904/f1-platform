package io.f1platform.prediction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import io.f1platform.persistence.Migrations;

/**
 * Builds the frozen Austin 2026 prediction (pre-qualifying) from all completed races up to the cutoff,
 * and stores it in prediction_run and prediction (V2). Stored runs are never overwritten.
 *
 * Usage: PredictionRunner <jdbc-url> <user> <password> <lambda> [simulations] [dry-run]
 * With dry-run, the model is computed and printed but nothing is stored.
 */
public final class PredictionRunner {

    static final int K = 32;
    static final int TARGET_SEASON = 2026;
    static final int TARGET_ROUND = 18;
    static final String TARGET_NAME = "United States Grand Prix";
    static final LocalDate TARGET_DATE = LocalDate.of(2026, 10, 25);
    static final long SEED = 2026L;

    private PredictionRunner() {}

    public static void main(String[] args) throws Exception {
        String url = args[0];
        String user = args[1];
        String password = args[2];
        double lambda = Double.parseDouble(args[3]);
        int simulations = args.length > 4 ? Integer.parseInt(args[4]) : 20_000;
        boolean dryRun = args.length > 5 && args[5].equals("dry-run");
        Migrations.migrate(url, user, password);

        List<RaceRecord> races = RaceRepository.loadRaces(url, user, password);
        RaceRecord last = races.get(races.size() - 1);
        if (last.season() != TARGET_SEASON || last.round() != TARGET_ROUND - 1) {
            throw new IllegalStateException("expected the last completed race to be 2026 round 17, found "
                    + last.season() + " round " + last.round());
        }

        EloRatings ratings = new EloRatings(K);
        Integer previousSeason = null;
        for (RaceRecord race : races) {
            if (previousSeason != null && race.season() != previousSeason) {
                ratings.shrink(lambda);
            }
            previousSeason = race.season();
            ratings.applyRace(race.entries());
        }

        List<Entry> field = last.entries();
        double[] strengths = new double[field.size()];
        for (int i = 0; i < field.size(); i++) {
            strengths[i] = ratings.strength(field.get(i));
        }
        double[][] sim = PlackettLuce.simulate(strengths, simulations, SEED);
        double[] exactWin = PlackettLuce.winProbabilities(strengths);

        String version = "elo-k" + K + "-lambda" + lambda;
        String parameters = String.format(
                "{\"k\": %d, \"lambda\": %.2f, \"scale\": 400, \"model\": \"pairwise-elo + plackett-luce\","
                        + " \"field\": \"entries of the last completed race\", \"grid_known\": false,"
                        + " \"cutoff_race\": \"%d round %d\"}",
                K, lambda, last.season(), last.round());

        if (dryRun) {
            System.out.println("dry-run: nothing stored");
        } else try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            long runId;
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO prediction_run(model_name, model_version, target_season, target_round, target_name,
                        target_date, data_cutoff_date, input_races, grid_known, simulations, random_seed, parameters)
                    VALUES ('elo-plackett-luce', ?, ?, ?, ?, ?, ?, ?, FALSE, ?, ?, CAST(? AS JSONB))
                    RETURNING id""")) {
                ps.setString(1, version);
                ps.setInt(2, TARGET_SEASON);
                ps.setInt(3, TARGET_ROUND);
                ps.setString(4, TARGET_NAME);
                ps.setObject(5, TARGET_DATE);
                ps.setObject(6, last.date());
                ps.setInt(7, races.size());
                ps.setInt(8, simulations);
                ps.setLong(9, SEED);
                ps.setString(10, parameters);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    runId = rs.getLong(1);
                }
            } catch (SQLException e) {
                c.rollback();
                throw new IllegalStateException("a frozen run already exists for " + version
                        + " and this target (runs are never overwritten): " + e.getMessage(), e);
            }

            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO prediction(run_id, driver_id, constructor_id, rating_strength,
                        win_prob, podium_prob, expected_position)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""")) {
                for (int i = 0; i < field.size(); i++) {
                    Entry e = field.get(i);
                    ps.setLong(1, runId);
                    ps.setLong(2, e.driverId());
                    ps.setLong(3, e.constructorId());
                    ps.setDouble(4, strengths[i]);
                    ps.setDouble(5, exactWin[i]);  // exact closed form, not the simulated estimate
                    ps.setDouble(6, Math.max(sim[1][i], sim[0][i]));
                    ps.setDouble(7, Math.max(1.0, sim[2][i]));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
            System.out.printf("stored run %d (%s, %d drivers, cutoff %s)%n",
                    runId, version, field.size(), last.date());
        }

        System.out.printf("%nAustin %d prediction (pre-qualifying, cutoff %s):%n", TARGET_SEASON, last.date());
        System.out.println("driver_id  strength   win(exact)  win(sim)  podium   expected_pos");
        Integer[] order = new Integer[field.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(sim[2][a], sim[2][b]));
        for (int idx : order) {
            System.out.printf("%-10d %8.1f   %.4f      %.3f     %.3f    %.2f%n",
                    field.get(idx).driverId(), strengths[idx], exactWin[idx], sim[0][idx], sim[1][idx], sim[2][idx]);
        }
        double winSum = 0;
        double podiumSum = 0;
        for (int i = 0; i < field.size(); i++) {
            winSum += exactWin[i];
            podiumSum += sim[1][i];
        }
        System.out.printf("checks: win probabilities sum to %.3f (expect 1), podium to %.3f (expect 3)%n", winSum, podiumSum);
    }
}
