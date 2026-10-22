package io.f1platform.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.f1platform.ingestion.StatusMapper.Status;
import org.junit.jupiter.api.Test;

class StatusMapperTest {

    @Test
    void numericPositionIsClassifiedEvenWithRetirementReason() {
        assertEquals(Status.CLASSIFIED, StatusMapper.map("5", "+2 Laps"));
        assertEquals(Status.CLASSIFIED, StatusMapper.map("17", "Engine"));
        assertEquals(Status.CLASSIFIED, StatusMapper.map("1", "Finished"));
    }

    @Test
    void retiredWithNotClassifiedStatusIsNotClassified() {
        assertEquals(Status.NOT_CLASSIFIED, StatusMapper.map("R", "Not classified"));
        assertEquals(Status.RETIRED, StatusMapper.map("R", "Engine"));
    }

    @Test
    void disqualifiedAndExcluded() {
        assertEquals(Status.EXCLUDED, StatusMapper.map("D", "Excluded"));
        assertEquals(Status.DISQUALIFIED, StatusMapper.map("D", "Underweight"));
    }

    @Test
    void withdrawnAndDidNotStart() {
        assertEquals(Status.NOT_CLASSIFIED, StatusMapper.map("W", "Did not start"));
        assertEquals(Status.WITHDRAWN, StatusMapper.map("W", "Withdrew"));
    }

    @Test
    void unknownPositionTextFailsLoudly() {
        assertThrows(IllegalArgumentException.class, () -> StatusMapper.map("F", "Did not qualify"));
        assertThrows(IllegalArgumentException.class, () -> StatusMapper.map("", ""));
    }
}
