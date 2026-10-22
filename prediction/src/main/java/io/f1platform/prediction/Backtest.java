package io.f1platform.prediction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Time-based backtest. Ratings are built strictly in date order. Each race is predicted from the ratings
 * before it, then the result is applied. Scores are taken only over races in the evaluation window.
 *
 * Metrics: winner log loss and Brier score (over the full field), Spearman correlation between predicted
 * strength rank and finishing order (classified cars only), and a calibration table for win probabilities.
 * The baseline ranks cars by championship points before the race (sprint points included, since they are
 * scored before the race in the same weekend), with the same Plackett-Luce conversion.
 */
public final class Backtest {

    public record Calibration(int bin, int count, double meanPredicted, double observedRate) {}

    public record Metrics(int races, double logLoss, double brier, double spearman,
                          double baselineLogLoss, double baselineBrier, double baselineSpearman,
                          List<Calibration> calibration) {}

    public static final double BASELINE_BETA = 0.1;
    private static final int BINS = 10;

    private Backtest() {}

    public static Metrics evaluate(List<RaceRecord> races, double k, double lambda,
                                   int fromSeason, int toSeason) {
        EloRatings ratings = new EloRatings(k);
        Map<Long, Double> seasonPoints = new HashMap<>();
        Integer previousSeason = null;

        int count = 0;
        double ll = 0, br = 0, sp = 0;
        double bll = 0, bbr = 0, bsp = 0;
        double[] binCount = new double[BINS], binP = new double[BINS], binY = new double[BINS];

        for (RaceRecord race : races) {
            if (previousSeason == null || race.season() != previousSeason) {
                if (previousSeason != null) {
                    ratings.shrink(lambda);
                }
                seasonPoints.clear();
                previousSeason = race.season();
            }
            // Sprint points for this weekend count before the race.
            race.sprintPoints().forEach((d, p) -> seasonPoints.merge(d, p, Double::sum));

            Entry winner = race.winner();
            boolean inWindow = race.season() >= fromSeason && race.season() <= toSeason && winner != null;
            if (inWindow) {
                List<Entry> field = race.entries();
                int n = field.size();
                double[] modelS = new double[n];
                double[] baseS = new double[n];
                for (int i = 0; i < n; i++) {
                    Entry e = field.get(i);
                    modelS[i] = ratings.strength(e);
                    baseS[i] = BASELINE_BETA * seasonPoints.getOrDefault(e.driverId(), 0.0);
                }
                double[] pModel = PlackettLuce.winProbabilities(modelS);
                double[] pBase = PlackettLuce.winProbabilities(baseS);
                int w = field.indexOf(winner);

                ll += -Math.log(Math.max(pModel[w], 1e-12));
                bll += -Math.log(Math.max(pBase[w], 1e-12));
                br += brier(pModel, w);
                bbr += brier(pBase, w);
                sp += spearman(field, modelS);
                bsp += spearman(field, baseS);

                for (int i = 0; i < n; i++) {
                    int b = Math.min(BINS - 1, (int) (pModel[i] * BINS));
                    binCount[b]++;
                    binP[b] += pModel[i];
                    binY[b] += (i == w) ? 1 : 0;
                }
                count++;
            }

            ratings.applyRace(race.entries());
            for (Entry e : race.entries()) {
                seasonPoints.merge(e.driverId(), e.points(), Double::sum);
            }
        }

        List<Calibration> cal = new ArrayList<>();
        for (int b = 0; b < BINS; b++) {
            if (binCount[b] > 0) {
                cal.add(new Calibration(b, (int) binCount[b], binP[b] / binCount[b], binY[b] / binCount[b]));
            }
        }
        if (count == 0) {
            return new Metrics(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, cal);
        }
        return new Metrics(count, ll / count, br / count, sp / count,
                bll / count, bbr / count, bsp / count, cal);
    }

    static double brier(double[] p, int winnerIndex) {
        double s = 0;
        for (int i = 0; i < p.length; i++) {
            double y = (i == winnerIndex) ? 1 : 0;
            s += (p[i] - y) * (p[i] - y);
        }
        return s / p.length;
    }

    /** Spearman correlation between predicted rank (by strength, descending) and finishing position. */
    static double spearman(List<Entry> field, double[] strength) {
        List<Integer> classified = new ArrayList<>();
        for (int i = 0; i < field.size(); i++) {
            if (field.get(i).classified()) {
                classified.add(i);
            }
        }
        int m = classified.size();
        if (m < 3) {
            return Double.NaN;
        }
        List<Integer> byStrength = new ArrayList<>(classified);
        byStrength.sort((a, b) -> Double.compare(strength[b], strength[a]));
        double sumD2 = 0;
        for (int predictedRank = 0; predictedRank < m; predictedRank++) {
            int idx = byStrength.get(predictedRank);
            int actual = field.get(idx).position();
            // Rank of the actual finishing position within the classified set (positions are unique).
            int actualRank = 0;
            for (int other : classified) {
                if (field.get(other).position() < actual) {
                    actualRank++;
                }
            }
            double d = (predictedRank + 1) - (actualRank + 1);
            sumD2 += d * d;
        }
        return 1.0 - (6.0 * sumD2) / ((double) m * ((double) m * m - 1));
    }

    /** Command-line report: tunes lambda on 2022-2023, then reports 2024-2025 and the 2026 season so far. */
    public static void main(String[] args) throws Exception {
        String url = args[0];
        String user = args[1];
        String password = args[2];
        double k = args.length > 3 ? Double.parseDouble(args[3]) : 32.0;
        List<RaceRecord> races = RaceRepository.loadRaces(url, user, password);
        System.out.println("races loaded: " + races.size());

        double[] grid = {0.0, 0.25, 0.5, 0.75, 1.0};
        double bestLambda = grid[0];
        double bestLoss = Double.POSITIVE_INFINITY;
        System.out.println("\nTuning window 2022-2023 (lambda chosen here only):");
        System.out.println("lambda  races  model_logloss  baseline_logloss  model_brier  model_spearman");
        for (double lambda : grid) {
            Backtest.Metrics m = evaluate(races, k, lambda, 2022, 2023);
            System.out.printf("%.2f    %3d    %.4f         %.4f            %.4f       %.3f%n",
                    lambda, m.races(), m.logLoss(), m.baselineLogLoss(), m.brier(), m.spearman());
            if (m.logLoss() < bestLoss) {
                bestLoss = m.logLoss();
                bestLambda = lambda;
            }
        }
        System.out.printf("%nchosen lambda: %.2f%n", bestLambda);

        for (int[] window : new int[][] {{2024, 2025}, {2026, 2026}}) {
            Backtest.Metrics m = evaluate(races, k, bestLambda, window[0], window[1]);
            System.out.printf("%nevaluation %d-%d (lambda %.2f): races %d%n", window[0], window[1], bestLambda, m.races());
            System.out.printf("  model:    logloss %.4f  brier %.4f  spearman %.3f%n", m.logLoss(), m.brier(), m.spearman());
            System.out.printf("  baseline: logloss %.4f  brier %.4f  spearman %.3f%n",
                    m.baselineLogLoss(), m.baselineBrier(), m.baselineSpearman());
            System.out.println("  calibration (bin: count, mean predicted, observed win rate):");
            for (Calibration c : m.calibration()) {
                System.out.printf("    %d: n=%d  predicted %.3f  observed %.3f%n", c.bin(), c.count(), c.meanPredicted(), c.observedRate());
            }
        }
    }
}
