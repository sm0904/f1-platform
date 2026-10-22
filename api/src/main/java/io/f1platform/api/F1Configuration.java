package io.f1platform.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;

public class F1Configuration extends Configuration {

    @JsonProperty
    private DatabaseConfig database = new DatabaseConfig();

    public DatabaseConfig getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfig database) {
        this.database = database;
    }
}
