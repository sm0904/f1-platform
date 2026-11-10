package io.f1platform.prediction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runs the validation protocol and prints the results.
 *
 * Protocol (time-ordered, one test window):
 *   1. Tune K and lambda (season shrink) for the model, and the scale beta for the points baseline, on seasons
 *      2014-2021 only, choosing the values with the highest mean Plackett-Luce log likelihood.
 *   2. Test the chosen lambda on seasons 2022-2026. Every race is scored from the ratings built before it.
 *   3. Report the difference from each baseline, with 95% intervals from a season-block bootstrap.
 *
 * Usage: EvaluationReport <jdbc-url> <user> <password>
 */
public final class EvaluationReport {

    static final double[] K_GRID = {16.0, 32.0, 48.0, 64.0, 96.0, 128.0, 192.0, 256.0};
    static final double[] BETA_GRID = {0.05, 0.1, 0.2, 0.3, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0};
    static final int TUNE_FROM = 2014, TUNE_TO = 2021;
    static final int TEST_FROM = 2022, TEST_TO = 2026;
    static final double[] LAMBDA_GRID = {0.0, 0.25, 0.5, 0.75, 1.0};
    static final int RESAMPLES = 5000;
    static final long SEED = 2026L;

    private EvaluationReport() {}

    public static void main(String[] args) throws Exception {
        List<RaceRecord> races = RaceRepository.loadRaces(args[0], args[1], args[2]);
        System.out.println("races loaded: " + races.size());

        // Tune on 2014-2021 only: the season shrink lambda and K for the model, and the scale beta for the points baseline.
        System.out.printf("%nTuning on %d-%d (mean Plackett-Luce log likelihood per race, higher is better)%n", TUNE_FROM, TUNE_TO);
        double bestLambda = LAMBDA_GRID[0];
        double bestK = K_GRID[0];
        double bestMean = Double.NEGATIVE_INFINITY;
        for (double k : K_GRID) {
            for (double lambda : LAMBDA_GRID) {
                List<Evaluation.RaceScore> s = Evaluation.walkForward(races, k, lambda, TUNE_FROM, TUNE_TO);
                double mean = s.stream().mapToDouble(Evaluation.RaceScore::llModel).average().orElse(Double.NaN);
                System.out.printf("  model  K %4.0f  lambda %.2f  mean LL %8.4f%n", k, lambda, mean);
                if (mean > bestMean) {
                    bestMean = mean;
                    bestLambda = lambda;
                    bestK = k;
                }
            }
        }
        double bestBeta = BETA_GRID[0];
        double bestPointsMean = Double.NEGATIVE_INFINITY;
        for (double beta : BETA_GRID) {
            List<Evaluation.RaceScore> s = Evaluation.walkForward(races, bestK, bestLambda, beta, Evaluation.POINTS_BETA, TUNE_FROM, TUNE_TO);
            double mean = s.stream().mapToDouble(Evaluation.RaceScore::llPoints).average().orElse(Double.NaN);
            System.out.printf("  points beta %.2f  mean LL %8.4f%n", beta, mean);
            if (mean > bestPointsMean) {
                bestPointsMean = mean;
                bestBeta = beta;
            }
        }
        double bestBetaPrev = BETA_GRID[0];
        double bestPrevMean = Double.NEGATIVE_INFINITY;
        for (double beta : BETA_GRID) {
            List<Evaluation.RaceScore> s = Evaluation.walkForward(races, bestK, bestLambda, bestBeta, beta, TUNE_FROM, TUNE_TO);
            double mean = s.stream().mapToDouble(Evaluation.RaceScore::llPrevSeason).average().orElse(Double.NaN);
            System.out.printf("  previous-season beta %.2f  mean LL %8.4f%n", beta, mean);
            if (mean > bestPrevMean) {
                bestPrevMean = mean;
                bestBetaPrev = beta;
            }
        }
        System.out.printf("chosen: model K %.0f, lambda %.2f; points beta %.2f; previous-season beta %.2f%n",
                bestK, bestLambda, bestBeta, bestBetaPrev);

        List<Evaluation.RaceScore> test = Evaluation.walkForward(races, bestK, bestLambda, bestBeta, bestBetaPrev, TEST_FROM, TEST_TO);
        List<Integer> seasons = new ArrayList<>();
        List<Double> dLlPoints = new ArrayList<>();
        List<Double> dLlUniform = new ArrayList<>();
        List<Double> dRhoPoints = new ArrayList<>();
        for (Evaluation.RaceScore r : test) {
            seasons.add(r.season());
            dLlPoints.add(r.llModel() - r.llPoints());
            dLlUniform.add(r.llModel() - r.llUniform());
            dRhoPoints.add(r.rhoModel() - r.rhoPoints());
        }

        System.out.printf("%nTest window %d-%d (K %.0f, lambda %.2f, points beta %.2f, previous-season beta %.2f), %d races%n",
                TEST_FROM, TEST_TO, bestK, bestLambda, bestBeta, bestBetaPrev, test.size());
        double meanModel = test.stream().mapToDouble(Evaluation.RaceScore::llModel).average().orElse(Double.NaN);
        double meanPoints = test.stream().mapToDouble(Evaluation.RaceScore::llPoints).average().orElse(Double.NaN);
        double meanUniform = test.stream().mapToDouble(Evaluation.RaceScore::llUniform).average().orElse(Double.NaN);
        double meanPrev = test.stream().mapToDouble(Evaluation.RaceScore::llPrevSeason).average().orElse(Double.NaN);
        System.out.printf("  mean LL  model %.4f | points %.4f | previous season %.4f | uniform %.4f%n",
                meanModel, meanPoints, meanPrev, meanUniform);
        printInterval("  model minus points (LL)", Evaluation.seasonBlockBootstrap(dLlPoints, seasons, RESAMPLES, SEED));
        printInterval("  model minus uniform (LL)", Evaluation.seasonBlockBootstrap(dLlUniform, seasons, RESAMPLES, SEED));
        List<Double> dLlPrev = new ArrayList<>();
        for (Evaluation.RaceScore r : test) {
            dLlPrev.add(r.llModel() - r.llPrevSeason());
        }
        printInterval("  model minus previous season (LL)", Evaluation.seasonBlockBootstrap(dLlPrev, seasons, RESAMPLES, SEED));
        double rhoModel = test.stream().mapToDouble(Evaluation.RaceScore::rhoModel).average().orElse(Double.NaN);
        double rhoPoints = test.stream().mapToDouble(Evaluation.RaceScore::rhoPoints).average().orElse(Double.NaN);
        double rhoPrev = test.stream().mapToDouble(Evaluation.RaceScore::rhoPrevSeason).average().orElse(Double.NaN);
        System.out.printf("  mean Spearman  model %.4f | points %.4f | previous season %.4f%n", rhoModel, rhoPoints, rhoPrev);
        printInterval("  model minus points (Spearman)", Evaluation.seasonBlockBootstrap(dRhoPoints, seasons, RESAMPLES, SEED));

        System.out.println("\nPer season (test window):");
        System.out.println("  season races  LL model  LL points  LL uniform  rho model  rho points");
        Map<Integer, List<Evaluation.RaceScore>> bySeason = new TreeMap<>();
        for (Evaluation.RaceScore r : test) {
            bySeason.computeIfAbsent(r.season(), s -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<Integer, List<Evaluation.RaceScore>> e : bySeason.entrySet()) {
            List<Evaluation.RaceScore> rs = e.getValue();
            System.out.printf("  %d  %3d   %9.3f  %9.3f  %10.3f  %9.3f  %10.3f%n", e.getKey(), rs.size(),
                    avg(rs, Evaluation.RaceScore::llModel), avg(rs, Evaluation.RaceScore::llPoints),
                    avg(rs, Evaluation.RaceScore::llUniform), avg(rs, Evaluation.RaceScore::rhoModel),
                    avg(rs, Evaluation.RaceScore::rhoPoints));
        }

        printRetirements(races);
        printReasonTotals(races);
    }

    /** Retirements are reported as league-wide frequencies by season and reason. They do not feed any strength. */
    static void printRetirements(List<RaceRecord> races) {
        System.out.println("\nRetirements by season (classified and retired cars; league-wide, not used in strengths):");
        System.out.println("  season  entries  retired  rate    top reason");
        Map<Integer, int[]> totals = new TreeMap<>();
        Map<Integer, EnumMap<DnfReason, Integer>> reasons = new TreeMap<>();
        for (RaceRecord race : races) {
            for (Entry e : race.entries()) {
                int[] t = totals.computeIfAbsent(race.season(), s -> new int[2]);
                t[0]++;
                if (e.retired()) {
                    t[1]++;
                    reasons.computeIfAbsent(race.season(), s -> new EnumMap<>(DnfReason.class))
                            .merge(DnfReason.of(e.statusDetail()), 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<Integer, int[]> e : totals.entrySet()) {
            int season = e.getKey();
            int[] t = e.getValue();
            String top = "-";
            EnumMap<DnfReason, Integer> r = reasons.get(season);
            if (r != null && !r.isEmpty()) {
                top = r.entrySet().stream().max(Map.Entry.comparingByValue())
                        .map(x -> x.getKey().label + " (" + x.getValue() + ")").orElse("-");
            }
            System.out.printf("  %d  %6d  %6d   %5.1f%%  %s%n", season, t[0], t[1],
                    100.0 * t[1] / Math.max(1, t[0]), top);
        }
    }

    /** All-history breakdown of retirements by reason group, so the unclassified share stays visible. */
    static void printReasonTotals(List<RaceRecord> races) {
        EnumMap<DnfReason, Integer> totals = new EnumMap<>(DnfReason.class);
        int all = 0;
        for (RaceRecord race : races) {
            for (Entry e : race.entries()) {
                if (e.retired()) {
                    totals.merge(DnfReason.of(e.statusDetail()), 1, Integer::sum);
                    all++;
                }
            }
        }
        System.out.printf("%nRetirement reasons, all seasons (%d retirements):%n", all);
        for (Map.Entry<DnfReason, Integer> e : totals.entrySet()) {
            System.out.printf("  %-36s %6d  %5.1f%%%n", e.getKey().label, e.getValue(), 100.0 * e.getValue() / all);
        }
    }

    private static double avg(List<Evaluation.RaceScore> rs, java.util.function.ToDoubleFunction<Evaluation.RaceScore> f) {
        return rs.stream().mapToDouble(f).average().orElse(Double.NaN);
    }

    private static void printInterval(String label, Evaluation.Interval iv) {
        System.out.printf("%s: %+.4f  [95%% CI %+.4f, %+.4f]  (%d seasons, %d races)%n",
                label, iv.mean(), iv.lower(), iv.upper(), iv.seasons(), iv.races());
    }
}
