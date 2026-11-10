package io.f1platform.prediction;

/**
 * One car entry in a race.
 *
 * position is the official classified position, or null when the car was not classified.
 * status is the schema status (CLASSIFIED, RETIRED, DISQUALIFIED, EXCLUDED, WITHDRAWN, NOT_CLASSIFIED).
 * statusDetail is the provider's raw text, such as "Engine" or "+2 Laps", kept for reason tracking.
 *
 * Retirements (status RETIRED) are tracked, but they never change driver or constructor strength.
 */
public record Entry(long driverId, long constructorId, Integer position, double points,
                    String status, String statusDetail) {

    /** Convenience constructor for tests: the status is inferred from the position. */
    public Entry(long driverId, long constructorId, Integer position, double points) {
        this(driverId, constructorId, position, points,
                position != null ? "CLASSIFIED" : "NOT_CLASSIFIED", null);
    }

    public boolean classified() {
        return position != null;
    }

    public boolean retired() {
        return "RETIRED".equals(status);
    }
}
