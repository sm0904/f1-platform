package io.f1platform.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response shapes. They are separate from the database rows, so the API can change without a schema change. */
public final class Dtos {

    private Dtos() {}

    public record SeasonSummary(int year, int races) {}

    public record RaceSummary(int season, int round, String name, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate date,
                              String circuit, String locality, String country) {}

    public record ResultRow(Integer position, String positionText, String status, String statusDetail,
                            Integer carNumber, Integer grid, Integer laps, String timeText, BigDecimal points,
                            String driverCode, String givenName, String familyName, String constructor,
                            String q1, String q2, String q3) {}

    public record DriverStanding(int position, String driverCode, String givenName, String familyName,
                                 BigDecimal points, int wins) {}

    public record ConstructorStanding(int position, String name, BigDecimal points, int wins) {}

    public record PredictedDriver(String givenName, String familyName, String constructor,
                                  double ratingStrength, double winProb, double podiumProb,
                                  double expectedPosition) {}

    /** Dates are ISO strings (YYYY-MM-DD) and the creation time is ISO 8601 UTC, whatever the mapper defaults are. */
    public record PredictionRun(long id, String modelName, String modelVersion, int targetSeason, int targetRound,
                                String targetName,
                                @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate targetDate,
                                @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate dataCutoffDate,
                                int inputRaces, boolean gridKnown, int simulations, long randomSeed,
                                @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
                                JsonNode parameters, List<PredictedDriver> predictions) {}
}
