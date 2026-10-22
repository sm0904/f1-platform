package io.f1platform.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.f1platform.ingestion.JolpicaReader.RaceKey;
import io.f1platform.ingestion.JolpicaReader.SessionKind;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JolpicaReaderTest {

    static Path fixtureRoot() throws Exception {
        return Path.of(JolpicaReaderTest.class.getResource("/fixtures/jolpica").toURI());
    }

    @Test
    void readsFixtureRacesAndSessions() throws Exception {
        Map<RaceKey, JolpicaReader.RaceBundle> races = JolpicaReader.read(fixtureRoot());
        assertEquals(3, races.size());
        assertTrue(races.containsKey(new RaceKey(1950, 3)));
        assertTrue(races.containsKey(new RaceKey(1950, 7)));
        assertTrue(races.containsKey(new RaceKey(2024, 5)));

        JolpicaReader.RaceBundle china = races.get(new RaceKey(2024, 5));
        assertEquals(20, china.results().get(SessionKind.RACE).size());
        assertEquals(20, china.results().get(SessionKind.SPRINT).size());
        assertEquals(20, china.results().get(SessionKind.QUALIFYING).size());
    }

    @Test
    void indianapolis1950KeepsBothCarsOfBettenhausen() throws Exception {
        JolpicaReader.RaceBundle indy = JolpicaReader.read(fixtureRoot()).get(new RaceKey(1950, 3));
        long bettenhausenEntries = indy.results().get(SessionKind.RACE).stream()
                .filter(r -> "bettenhausen".equals(r.path("Driver").path("driverId").asText()))
                .count();
        assertEquals(2, bettenhausenEntries);
    }
}
