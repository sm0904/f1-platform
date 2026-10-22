package io.f1platform.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads the raw Jolpica pages written by scripts/fetch_jolpica.sh and merges them by race.
 * A race can span two pages, so results are merged by (season, round), never by page.
 *
 * Layout: root/{race|sprint|qualifying}/{year}/results-{offset}.json
 */
public final class JolpicaReader {

    /** The three competitive session kinds the fetch script produces. */
    public enum SessionKind {
        RACE("race", "Results", "RACE"),
        SPRINT("sprint", "SprintResults", "SPRINT"),
        QUALIFYING("qualifying", "QualifyingResults", "QUALIFYING");

        public final String directory;
        public final String jsonKey;
        public final String dbType;

        SessionKind(String directory, String jsonKey, String dbType) {
            this.directory = directory;
            this.jsonKey = jsonKey;
            this.dbType = dbType;
        }
    }

    public record RaceKey(int season, int round) {}

    /** One race with its metadata and the raw result nodes for each session kind present. */
    public record RaceBundle(RaceKey key, String raceName, String date, JsonNode circuit,
                             Map<SessionKind, List<JsonNode>> results) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JolpicaReader() {}

    public static Map<RaceKey, RaceBundle> read(Path root) throws IOException {
        Map<RaceKey, RaceBundle> races = new LinkedHashMap<>();
        for (SessionKind kind : SessionKind.values()) {
            Path kindDir = root.resolve(kind.directory);
            if (!Files.isDirectory(kindDir)) {
                continue;
            }
            for (Path yearDir : sortedChildren(kindDir)) {
                int year = Integer.parseInt(yearDir.getFileName().toString());
                readSeason(yearDir, year, kind, races);
            }
        }
        return races;
    }

    private static void readSeason(Path yearDir, int year, SessionKind kind,
                                   Map<RaceKey, RaceBundle> races) throws IOException {
        long expectedTotal = -1;
        long rowsRead = 0;
        for (Path page : sortedChildren(yearDir)) {
            JsonNode mrData = MAPPER.readTree(page.toFile()).path("MRData");
            long total = mrData.path("total").asLong(-1);
            if (expectedTotal == -1) {
                expectedTotal = total;
            } else if (total != expectedTotal) {
                throw new IllegalStateException(
                        kind.directory + " " + year + ": pages disagree on total (" + expectedTotal + " vs " + total + ")");
            }
            for (JsonNode race : mrData.path("RaceTable").path("Races")) {
                int season = race.path("season").asInt();
                if (season != year) {
                    throw new IllegalStateException("page for " + year + " contains season " + season);
                }
                RaceKey key = new RaceKey(season, race.path("round").asInt());
                RaceBundle bundle = races.computeIfAbsent(key, k -> new RaceBundle(
                        k,
                        race.path("raceName").asText(),
                        race.path("date").asText(),
                        race.path("Circuit"),
                        new LinkedHashMap<>()));
                List<JsonNode> rows = bundle.results().computeIfAbsent(kind, k -> new ArrayList<>());
                for (JsonNode row : race.path(kind.jsonKey)) {
                    rows.add(row);
                    rowsRead++;
                }
            }
        }
        if (expectedTotal == -1) {
            throw new IllegalStateException(kind.directory + " " + year + ": no pages found");
        }
        if (rowsRead != expectedTotal) {
            throw new IllegalStateException(
                    kind.directory + " " + year + ": read " + rowsRead + " rows but the API reported " + expectedTotal
                            + " (incomplete download?)");
        }
    }

    private static List<Path> sortedChildren(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.sorted().toList();
        }
    }
}
