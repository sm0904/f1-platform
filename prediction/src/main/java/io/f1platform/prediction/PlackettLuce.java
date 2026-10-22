package io.f1platform.prediction;

import java.util.Random;

/**
 * Plackett-Luce ordering model: each car has weight exp(strength * ln10 / 400). Cars finish one at a time,
 * each drawn in proportion to its weight among those still running. The win probability has a closed form;
 * podium probabilities and expected positions come from Monte Carlo simulation with a fixed seed.
 */
public final class PlackettLuce {

    private PlackettLuce() {}

    public static double[] weights(double[] strengths) {
        double[] w = new double[strengths.length];
        for (int i = 0; i < strengths.length; i++) {
            w[i] = Math.pow(10.0, strengths[i] / EloRatings.SCALE);
        }
        return w;
    }

    public static double[] winProbabilities(double[] strengths) {
        double[] w = weights(strengths);
        double total = 0;
        for (double x : w) {
            total += x;
        }
        double[] p = new double[w.length];
        for (int i = 0; i < w.length; i++) {
            p[i] = w[i] / total;
        }
        return p;
    }

    /** Returns {winProb, podiumProb, expectedPosition} per car, from the given number of simulated races. */
    public static double[][] simulate(double[] strengths, int simulations, long seed) {
        int n = strengths.length;
        double[] w = weights(strengths);
        double[] wins = new double[n];
        double[] podium = new double[n];
        double[] posSum = new double[n];
        Random rnd = new Random(seed);
        int[] order = new int[n];
        for (int s = 0; s < simulations; s++) {
            boolean[] taken = new boolean[n];
            double remaining = 0;
            for (double x : w) {
                remaining += x;
            }
            for (int place = 0; place < n; place++) {
                double draw = rnd.nextDouble() * remaining;
                int chosen = -1;
                for (int i = 0; i < n; i++) {
                    if (taken[i]) {
                        continue;
                    }
                    draw -= w[i];
                    if (draw <= 0) {
                        chosen = i;
                        break;
                    }
                }
                if (chosen == -1) {
                    // Floating-point edge: take the last untaken car.
                    for (int i = n - 1; i >= 0; i--) {
                        if (!taken[i]) {
                            chosen = i;
                            break;
                        }
                    }
                }
                taken[chosen] = true;
                remaining -= w[chosen];
                order[chosen] = place + 1;
            }
            for (int i = 0; i < n; i++) {
                posSum[i] += order[i];
                if (order[i] == 1) {
                    wins[i]++;
                }
                if (order[i] <= 3) {
                    podium[i]++;
                }
            }
        }
        double[][] out = new double[3][n];
        for (int i = 0; i < n; i++) {
            out[0][i] = wins[i] / simulations;
            out[1][i] = podium[i] / simulations;
            out[2][i] = posSum[i] / simulations;
        }
        return out;
    }
}
