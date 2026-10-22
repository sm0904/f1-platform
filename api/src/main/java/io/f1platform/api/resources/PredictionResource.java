package io.f1platform.api.resources;

import io.f1platform.api.ApiException;
import io.f1platform.api.Dtos.PredictionRun;
import io.f1platform.api.Queries;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.List;

/**
 * Stored prediction runs for one target race. Runs are frozen once stored, so a response can be cached
 * briefly. A new run (for example, post-qualifying) appears as a new entry, never as a change to an old one.
 */
@Path("/api/v1/predictions/{year}/{round}")
@Produces(MediaType.APPLICATION_JSON)
public class PredictionResource {

    private final Queries queries;

    public PredictionResource(Queries queries) {
        this.queries = queries;
    }

    @GET
    public Response runs(@PathParam("year") int year, @PathParam("round") int round) throws SQLException {
        List<PredictionRun> runs = queries.predictionRuns(year, round);
        if (runs.isEmpty()) {
            throw ApiException.notFound("no stored prediction for round " + round + " of " + year);
        }
        return Response.ok(runs).header("Cache-Control", "public, max-age=60").build();
    }
}
