package io.f1platform.prediction;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * One completed race. sprintPoints holds the sprint points each driver scored in this weekend's sprint
 * (empty when there was no sprint). Those points count before the race in the championship baseline.
 */
public record RaceRecord(int season, int round, String name, LocalDate date,
                         List<Entry> entries, Map<Long, Double> sprintPoints) {

    public Entry winner() {
        return entries.stream().filter(e -> e.position() != null && e.position() == 1).findFirst().orElse(null);
    }
}
