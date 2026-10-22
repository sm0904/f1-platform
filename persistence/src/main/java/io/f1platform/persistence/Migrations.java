package io.f1platform.persistence;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/** Applies the versioned SQL migrations under db/migration on the classpath. */
public final class Migrations {

    private Migrations() {}

    public static void migrate(String jdbcUrl, String user, String password) {
        Flyway.configure()
                .dataSource(jdbcUrl, user, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
