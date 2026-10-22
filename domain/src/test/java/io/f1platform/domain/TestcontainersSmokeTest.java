package io.f1platform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class TestcontainersSmokeTest {

    @Test
    void postgresContainerStartsAndAnswersQuery() throws Exception {
        try (var pg = new PostgreSQLContainer("postgres:16-alpine")) {
            pg.start();
            try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT 1")) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
        }
    }
}
