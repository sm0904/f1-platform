package io.f1platform.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelTest {

    @Test
    void winProbabilitiesSumToOneAndFollowStrength() {
        double[] p = PlackettLuce.winProbabilities(new double[] {0, 100, -100});
        assertEquals(1.0, p[0] + p[1] + p[2], 1e-12);
        assertTrue(p[1] > p[0] && p[0] > p[2]);
    }

    @Test
    void equalStrengthsGiveEqualChances() {
        double[] p = PlackettLuce.winProbabilities(new double[] {0, 0, 0, 0});
        for (double x : p) {
            assertEquals(0.25, x, 1e-12);
        }
    }

    @Test
    void simulationAgreesWithClosedFormForWins() {
        double[] s = {0, 50, 120, -40, 10};
        double[] exact = PlackettLuce.winProbabilities(s);
        double[][] sim = PlackettLuce.simulate(s, 200_000, 7L);
        for (int i = 0; i < s.length; i++) {
            assertEquals(exact[i], sim[0][i], 0.01, "car " + i);
        }
        // Podium probabilities across the field sum to 3 (three podium places).
        double podium = 0;
        for (double x : sim[1]) {
            podium += x;
        }
        assertEquals(3.0, podium, 1e-9);
    }

    @Test
    void winnerOfARaceGainsRatingRelativeToLoser() {
        EloRatings r = new EloRatings(32);
        List<Entry> race = List.of(new Entry(1, 10, 1, 25), new Entry(2, 20, 2, 18));
        r.applyRace(race);
        assertTrue(r.driverRating(1) > 0);
        assertTrue(r.driverRating(2) < 0);
        // The pair is zero-sum for drivers.
        assertEquals(0.0, r.driverRating(1) + r.driverRating(2), 1e-9);
    }

    @Test
    void shrinkMovesRatingsTowardsAverage() {
        EloRatings r = new EloRatings(32);
        r.applyRace(List.of(new Entry(1, 10, 1, 25), new Entry(2, 20, 2, 18)));
        double before = r.driverRating(1);
        r.shrink(0.5);
        assertEquals(before * 0.5, r.driverRating(1), 1e-12);
        r.shrink(0.0);
        assertEquals(0.0, r.driverRating(1), 1e-12);
    }

    @Test
    void nonClassifiedCarsDoNotMoveRatings() {
        EloRatings r = new EloRatings(32);
        r.applyRace(List.of(new Entry(1, 10, 1, 25), new Entry(2, 20, null, 0)));
        assertEquals(0.0, r.driverRating(1), 1e-12);
        assertEquals(0.0, r.driverRating(2), 1e-12);
    }

    @Test
    void spearmanIsOneForPerfectOrderAndBrierZeroForPerfectForecast() {
        List<Entry> field = List.of(
                new Entry(1, 1, 1, 0), new Entry(2, 2, 2, 0), new Entry(3, 3, 3, 0), new Entry(4, 4, 4, 0));
        assertEquals(1.0, Backtest.spearman(field, new double[] {4, 3, 2, 1}), 1e-12);
        assertEquals(0.0, Backtest.brier(new double[] {1, 0, 0, 0}, 0), 1e-12);
    }
}
