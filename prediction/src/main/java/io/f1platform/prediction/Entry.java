package io.f1platform.prediction;

/**
 * One car entry in a race. position is null when the car was not classified.
 * Points are the race points; sprint points are kept separately on RaceRecord.
 */
public record Entry(long driverId, long constructorId, Integer position, double points) {

    public boolean classified() {
        return position != null;
    }
}
