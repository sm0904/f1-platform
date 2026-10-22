package io.f1platform.api;

import com.codahale.metrics.health.HealthCheck;
import io.dropwizard.core.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.f1platform.api.resources.PredictionResource;
import io.f1platform.api.resources.RaceResource;
import io.f1platform.api.resources.SeasonResource;

/**
 * Read-only HTTP API over the F1 database. The database must already be loaded and migrated by the
 * ingestion command; this service never writes.
 */
public final class F1Application extends Application<F1Configuration> {

    public static void main(String[] args) throws Exception {
        new F1Application().run(args);
    }

    /** Lets the YAML refer to environment variables, such as ${F1_DB_PASSWORD}. Unset variables fail at startup. */
    @Override
    public void initialize(Bootstrap<F1Configuration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
                bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    }

    @Override
    public void run(F1Configuration configuration, Environment environment) {
        Database db = new Database(configuration.getDatabase());
        environment.lifecycle().manage(db);

        Queries queries = new Queries(db);
        environment.jersey().register(new SeasonResource(queries));
        environment.jersey().register(new RaceResource(queries));
        environment.jersey().register(new PredictionResource(queries));
        environment.jersey().register(new ApiExceptionMapper());

        environment.healthChecks().register("database", new HealthCheck() {
            @Override
            protected Result check() {
                return db.ping() ? Result.healthy() : Result.unhealthy("cannot reach the database");
            }
        });
    }
}
