package io.f1platform.prediction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Walk-forward evaluation of the finishing-order model.
 *
 * Every race is scored using only the ratings built from races before it (time order, never shuffled).
 * Retirements are excluded from the finishing-order scores, because they are not evidence of pace. They are
 * tracked separately (see DnfReason and the retirement summary).
 *
 * Measures, per race, over the classified finishers:
 *  - Plackett-Luce log likelihood of the observed finishing order. This is the proper scoring rule for an
 *    ordering model: higher (less negative) is better. The uniform model scores -log(n!).
 *  - Spearman rank correlation between predicted strength and finishing order. 1 is a perfect ordering, 0 is none.
 *
 * Baselines, all converted to strengths with the same Plackett-Luce formula:
 *  - uniform: no information at all;
 *  - points: championship points before the race, which start from zero each season;
 *  - previous season: the previous season's final points, held fixed for the whole season. This uses only
 *    information a fan had before the season started, so it is the fairer benchmark for the start of a season.
 *
 * Spearman with a constant score vector (for example, every car on zero points) carries no ordering
 * information, so it is reported as 0.
 */
public final class Evaluation {

    public static final double POINTS_BETA = 0.1;
    private static final double LN10_OVER_400 = Math.log(10) / EloRatings.SCALE;

    public record RaceScore(int season, int round, int classified, int retirements,
                            double llModel, double llUniform, double llPoints, double llPrevSeason,
                            double rhoModel, double rhoPoints, double rhoPrevSeason) {}

    private Evaluation() {}

    /** Log likelihood of an observed order under Plackett-Luce. strengths[0] is the winner, strengths[1] second, and so on. */
    public static double plLogLikelihood(double[] strengthsInOrder) {
        int n = strengthsInOrder.length;
        double[] logw = new double[n];
        for (int i = 0; i < n; i++) {
            logw[i] = strengthsInOrder[i] * LN10_OVER_400;
        }
        // Sum over the remaining field at each step, computed as a running log-sum-exp from the back.
        double ll = 0;
        double logSuffix = Double.NEGATIVE_INFINITY;
        double[] logSuffixAt = new double[n];
        for (int k = n - 1; k >= 0; k--) {
            logSuffix = logAdd(logSuffix, logw[k]);
            logSuffixAt[k] = logSuffix;
        }
        for (int k = 0; k < n; k++) {
            ll += logw[k] - logSuffixAt[k];
        }
        return ll;
    }

    /** Log likelihood of an observed order when every car has equal strength: -log(n!). */
    public static double uniformLogLikelihood(int n) {
        double ll = 0;
        for (int m = 2; m <= n; m++) {
            ll -= Math.log(m);
        }
        return ll;
    }

    /** Spearman rank correlation between two score vectors, with tied values given their average rank. */
    public static double spearman(double[] x, double[] y) {
        double[] rx = averageRanks(x);
        double[] ry = averageRanks(y);
        int n = x.length;
        double mx = Arrays.stream(rx).average().orElse(0);
        double my = Arrays.stream(ry).average().orElse(0);
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            sxy += (rx[i] - mx) * (ry[i] - my);
            sxx += (rx[i] - mx) * (rx[i] - mx);
            syy += (ry[i] - my) * (ry[i] - my);
        }
        // A constant score vector carries no ordering information, so its correlation is reported as 0.
        return (sxx == 0 || syy == 0) ? 0.0 : sxy / Math.sqrt(sxx * syy);
    }

    static double[] averageRanks(double[] v) {
        int n = v.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, Comparator.comparingDouble(i -> v[i]));
        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && v[idx[j + 1]] == v[idx[i]]) {
                j++;
            }
            double avg = (i + j) / 2.0 + 1;
            for (int t = i; t <= j; t++) {
                ranks[idx[t]] = avg;
            }
            i = j + 1;
        }
        return ranks;
    }

    private static double logAdd(double a, double b) {
        if (a == Double.NEGATIVE_INFINITY) {
            return b;
        }
        if (b == Double.NEGATIVE_INFINITY) {
            return a;
        }
        double m = Math.max(a, b);
        return m + Math.log(Math.exp(a - m) + Math.exp(b - m));
    }

    /**
     * Walk-forward scores for every race from season `from` to season `to` (inclusive).
     * Ratings are rolled forward through every race in time order, so the scores never see a later result.
     */
    public static List<RaceScore> walkForward(List<RaceRecord> races, double k, double lambda, int from, int to) {
        return walkForward(races, k, lambda, POINTS_BETA, POINTS_BETA, from, to);
    }

    /**
     * As above, with each baseline's scale given explicitly. beta scales the in-season points baseline and
     * betaPrev scales the previous-season baseline. Each is tuned on the tuning window, because season totals and
     * in-season points have different sizes.
     */
    public static List<RaceScore> walkForward(List<RaceRecord> races, double k, double lambda, double beta,
                                              double betaPrev, int from, int to) {
        EloRatings ratings = new EloRatings(k);
        Map<Long, Double> seasonPoints = new HashMap<>();
        Map<Long, Double> previousSeasonPoints = new HashMap<>();
        Map<Long, Double> currentSeasonTotals = new HashMap<>();
        Integer previousSeason = null;
        List<RaceScore> out = new ArrayList<>();

        for (RaceRecord race : races) {
            if (previousSeason == null || race.season() != previousSeason) {
                if (previousSeason != null) {
                    ratings.shrink(lambda);
                    previousSeasonPoints = new HashMap<>(currentSeasonTotals);
                }
                seasonPoints.clear();
                currentSeasonTotals.clear();
                previousSeason = race.season();
            }
            race.sprintPoints().forEach((d, p) -> seasonPoints.merge(d, p, Double::sum));
            race.sprintPoints().forEach((d, p) -> currentSeasonTotals.merge(d, p, Double::sum));

            List<Entry> classified = new ArrayList<>();
            int retirements = 0;
            for (Entry e : race.entries()) {
                if (e.classified()) {
                    classified.add(e);
                }
                if (e.retired()) {
                    retirements++;
                }
            }
            classified.sort(Comparator.comparingInt(Entry::position));  // stable: ties keep list order

            if (race.season() >= from && race.season() <= to && classified.size() >= 3) {
                int n = classified.size();
                double[] model = new double[n];
                double[] points = new double[n];
                double[] prev = new double[n];
                double[] position = new double[n];
                for (int i = 0; i < n; i++) {
                    Entry e = classified.get(i);
                    model[i] = ratings.strength(e);
                    points[i] = beta * seasonPoints.getOrDefault(e.driverId(), 0.0);
                    prev[i] = betaPrev * previousSeasonPoints.getOrDefault(e.driverId(), 0.0);
                    position[i] = e.position();
                }
                // Strengths are listed in finishing order, so the observed order is the index order.
                double[] negPosition = Arrays.stream(position).map(p -> -p).toArray();
                out.add(new RaceScore(race.season(), race.round(), n, retirements,
                        plLogLikelihood(model), uniformLogLikelihood(n), plLogLikelihood(points),
                        plLogLikelihood(prev),
                        spearman(model, negPosition), spearman(points, negPosition), spearman(prev, negPosition)));
            }

            ratings.applyRace(race.entries());
            for (Entry e : race.entries()) {
                seasonPoints.merge(e.driverId(), e.points(), Double::sum);
                currentSeasonTotals.merge(e.driverId(), e.points(), Double::sum);
            }
        }
        return out;
    }

    /** Mean and percentile interval from a block bootstrap over seasons. Each season keeps all its races. */
    public record Interval(double mean, double lower, double upper, int seasons, int races) {}

    public static Interval seasonBlockBootstrap(List<Double> values, List<Integer> seasonOf, int resamples, long seed) {
        Map<Integer, List<Double>> bySeason = new java.util.TreeMap<>();
        for (int i = 0; i < values.size(); i++) {
            bySeason.computeIfAbsent(seasonOf.get(i), s -> new ArrayList<>()).add(values.get(i));
        }
        List<List<Double>> blocks = new ArrayList<>(bySeason.values());
        int m = blocks.size();
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        Random rnd = new Random(seed);
        double[] stats = new double[resamples];
        for (int b = 0; b < resamples; b++) {
            double sum = 0;
            int count = 0;
            for (int j = 0; j < m; j++) {
                for (double v : blocks.get(rnd.nextInt(m))) {
                    sum += v;
                    count++;
                }
            }
            stats[b] = count == 0 ? Double.NaN : sum / count;
        }
        Arrays.sort(stats);
        double lo = stats[(int) Math.floor(0.025 * (resamples - 1))];
        double hi = stats[(int) Math.ceil(0.975 * (resamples - 1))];
        return new Interval(mean, lo, hi, m, values.size());
    }
}
