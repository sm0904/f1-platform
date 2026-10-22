package io.f1platform.api;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.lifecycle.Managed;
import java.sql.Connection;
import java.sql.SQLException;

/** Connection pool shared by the resources. Started and closed by the Dropwizard lifecycle. */
public final class Database implements Managed {

    private final HikariDataSource dataSource;

    public Database(DatabaseConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.getUrl());
        hc.setUsername(config.getUser());
        hc.setPassword(config.getPassword());
        hc.setMaximumPoolSize(config.getMaximumPoolSize());
        hc.setReadOnly(true);
        hc.setPoolName("f1-read");
        this.dataSource = new HikariDataSource(hc);
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    public boolean ping() {
        try (Connection c = dataSource.getConnection(); var st = c.createStatement()) {
            return st.execute("SELECT 1");
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void start() {}

    @Override
    public void stop() {
        dataSource.close();
    }
}
