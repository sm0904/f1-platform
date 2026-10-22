package io.f1platform.prediction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pairwise Elo ratings for drivers and constructors, on the Elo scale (400 points per factor of 10 in odds).
 *
 * Each race is treated as pairwise outcomes between classified finishers. A car's strength is its driver
 * rating plus its constructor rating. Non-classified cars do not change ratings in this version.
 * Ratings are deviations from the field average, so the average is 0.
 */
public final class EloRatings {

    public static final double SCALE = 400.0;

    private final double k;
    private final Map<Long, Double> driver = new HashMap<>();
    private final Map<Long, Double> constructor = new HashMap<>();

    public EloRatings(double k) {
        this.k = k;
    }

    public double strength(Entry e) {
        return driver.getOrDefault(e.driverId(), 0.0) + constructor.getOrDefault(e.constructorId(), 0.0);
    }

    public double driverRating(long driverId) {
        return driver.getOrDefault(driverId, 0.0);
    }

    public double constructorRating(long constructorId) {
        return constructor.getOrDefault(constructorId, 0.0);
    }

    /** Applies one race's finishing order. Strengths are read before any update in this race. */
    public void applyRace(List<Entry> entries) {
        List<Entry> ranked = entries.stream()
                .filter(Entry::classified)
                .sorted((a, b) -> Integer.compare(a.position(), b.position()))
                .toList();
        int n = ranked.size();
        if (n < 2) {
            return;
        }
        double[] pre = new double[n];
        for (int i = 0; i < n; i++) {
            pre[i] = strength(ranked.get(i));
        }
        double[] delta = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                // i finished ahead of j: outcome 1 for i.
                double expectedI = 1.0 / (1.0 + Math.pow(10.0, (pre[j] - pre[i]) / SCALE));
                double step = k * (1.0 - expectedI) / (n - 1);
                delta[i] += step;
                delta[j] -= step;
            }
        }
        // The driver takes the full change. The constructor takes half, averaged over its classified cars.
        Map<Long, Double> constructorSum = new HashMap<>();
        Map<Long, Integer> constructorCount = new HashMap<>();
        for (int i = 0; i < n; i++) {
            long dId = ranked.get(i).driverId();
            driver.merge(dId, delta[i], Double::sum);
            long cId = ranked.get(i).constructorId();
            constructorSum.merge(cId, delta[i], Double::sum);
            constructorCount.merge(cId, 1, Integer::sum);
        }
        for (Map.Entry<Long, Double> e : constructorSum.entrySet()) {
            double avg = e.getValue() / constructorCount.get(e.getKey());
            constructor.merge(e.getKey(), 0.5 * avg, Double::sum);
        }
    }

    /** Season rollover: each rating moves towards the average by the shrink factor lambda (1 = keep, 0 = reset). */
    public void shrink(double lambda) {
        driver.replaceAll((id, r) -> r * lambda);
        constructor.replaceAll((id, r) -> r * lambda);
    }
}
