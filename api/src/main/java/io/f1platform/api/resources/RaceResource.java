package io.f1platform.api.resources;

import io.f1platform.api.ApiException;
import io.f1platform.api.Dtos.ResultRow;
import io.f1platform.api.Queries;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

@Path("/api/v1/seasons/{year}/races/{round}")
@Produces(MediaType.APPLICATION_JSON)
public class RaceResource {

    private static final Set<String> SESSIONS = Set.of("QUALIFYING", "SPRINT_QUALIFYING", "SPRINT", "RACE");

    private final Queries queries;

    public RaceResource(Queries queries) {
        this.queries = queries;
    }

    @GET
    @Path("results")
    public Response results(@PathParam("year") int year, @PathParam("round") int round,
                            @QueryParam("session") @DefaultValue("RACE") String session) throws SQLException {
        if (!SESSIONS.contains(session)) {
            throw ApiException.badRequest("session must be one of " + SESSIONS);
        }
        if (!queries.raceExists(year, round)) {
            throw ApiException.notFound("no race in round " + round + " of " + year);
        }
        List<ResultRow> rows = queries.results(year, round, session);
        return Response.ok(rows).header("Cache-Control", SeasonResource.HISTORICAL_CACHE).build();
    }
}
