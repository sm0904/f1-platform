package io.f1platform.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CarNumberTest {

    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void positiveNumberIsKept() throws Exception {
        assertEquals(17, PostgresLoader.carNumberOrNull(M.readTree("{\"number\":\"17\"}")));
    }

    @Test
    void zeroPlaceholderAndMissingNumberBecomeNull() throws Exception {
        assertNull(PostgresLoader.carNumberOrNull(M.readTree("{\"number\":\"0\"}")));
        assertNull(PostgresLoader.carNumberOrNull(M.readTree("{}")));
        assertNull(PostgresLoader.carNumberOrNull(M.readTree("{\"number\":null}")));
    }
}
