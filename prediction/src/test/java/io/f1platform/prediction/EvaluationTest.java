package io.f1platform.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationTest {

    @Test
    void equalStrengthsScoreTheUniformLikelihood() {
        double[] equal = {0, 0, 0, 0};
        assertEquals(Evaluation.uniformLogLikelihood(4), Evaluation.plLogLikelihood(equal), 1e-12);
        assertEquals(-Math.log(24), Evaluation.uniformLogLikelihood(4), 1e-12);
    }

    @Test
    void plLikelihoodRewardsStrengthsThatMatchTheOrder() {
        double right = Evaluation.plLogLikelihood(new double[] {200, 100, 0, -100});
        double wrong = Evaluation.plLogLikelihood(new double[] {-100, 0, 100, 200});
        assertTrue(right > Evaluation.uniformLogLikelihood(4));
        assertTrue(wrong < Evaluation.uniformLogLikelihood(4));
    }

    @Test
    void plLikelihoodMatchesTwoCarClosedForm() {
        // With two cars, P(first ahead of second) = w1 / (w1 + w2), so the log likelihood is log of that.
        double s1 = 50, s2 = 0;
        double w1 = Math.pow(10, s1 / 400), w2 = Math.pow(10, s2 / 400);
        assertEquals(Math.log(w1 / (w1 + w2)), Evaluation.plLogLikelihood(new double[] {s1, s2}), 1e-12);
    }

    @Test
    void spearmanIsOneForPerfectAgreementAndMinusOneForReversal() {
        assertEquals(1.0, Evaluation.spearman(new double[] {3, 2, 1}, new double[] {30, 20, 10}), 1e-12);
        assertEquals(-1.0, Evaluation.spearman(new double[] {3, 2, 1}, new double[] {10, 20, 30}), 1e-12);
    }

    @Test
    void spearmanUsesAverageRanksForTies() {
        // x has a tie between the first two values; the correlation is still well defined.
        double rho = Evaluation.spearman(new double[] {1, 1, 2}, new double[] {1, 2, 3});
        assertTrue(rho > 0.8 && rho < 1.0, "rho was " + rho);
    }

    @Test
    void retirementsDoNotChangeRatings() {
        EloRatings r = new EloRatings(32);
        r.applyRace(List.of(new Entry(1, 10, 1, 25, "CLASSIFIED", null),
                new Entry(2, 20, null, 0, "RETIRED", "Engine")));
        assertEquals(0.0, r.driverRating(2), 1e-12);
        assertEquals(0.0, r.driverRating(1), 1e-12);
    }

    @Test
    void reasonGroupingKeepsRetirementDetailBroad() {
        assertEquals(DnfReason.ENGINE, DnfReason.of("Engine"));
        assertEquals(DnfReason.ENGINE, DnfReason.of("Turbo"));
        assertEquals(DnfReason.ACCIDENT, DnfReason.of("Accident"));
        assertEquals(DnfReason.ACCIDENT, DnfReason.of("Spun off"));
        assertEquals(DnfReason.TRANSMISSION, DnfReason.of("Gearbox"));
        assertEquals(DnfReason.COOLING_FUEL_OIL, DnfReason.of("Oil leak"));
        assertEquals(DnfReason.CHASSIS_WHEELS, DnfReason.of("Puncture"));
        assertEquals(DnfReason.DRIVER_HEALTH, DnfReason.of("Driver unwell"));
        assertEquals(DnfReason.UNSPECIFIED, DnfReason.of("Retired"));
        assertEquals(DnfReason.UNSPECIFIED, DnfReason.of("+11 Laps"));
        assertEquals(DnfReason.OTHER, DnfReason.of("Mystery"));
    }

    @Test
    void retiredIsNotReadAsTyre() {
        // "Retired" contains "tire" as a substring. Whole-word matching must not treat it as a tyre failure.
        assertEquals(DnfReason.UNSPECIFIED, DnfReason.of("Retired"));
    }

    @Test
    void bootstrapIntervalContainsTheMeanForASimpleSample() {
        List<Double> values = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
        List<Integer> seasons = List.of(2000, 2000, 2001, 2001, 2002, 2002);
        Evaluation.Interval iv = Evaluation.seasonBlockBootstrap(values, seasons, 2000, 1L);
        assertEquals(3.5, iv.mean(), 1e-12);
        assertTrue(iv.lower() <= iv.mean() && iv.mean() <= iv.upper());
    }
}
