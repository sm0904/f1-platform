package io.f1platform.api.resources;

import io.f1platform.api.ApiException;
import io.f1platform.api.Dtos.ConstructorStanding;
import io.f1platform.api.Dtos.DriverStanding;
import io.f1platform.api.Dtos.RaceSummary;
import io.f1platform.api.Dtos.SeasonSummary;
import io.f1platform.api.Queries;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.List;

@Path("/api/v1/seasons")
@Produces(MediaType.APPLICATION_JSON)
public class SeasonResource {

    /** Historical data does not change after a race, so it can be cached for an hour. */
    static final String HISTORICAL_CACHE = "public, max-age=3600";

    private final Queries queries;

    public SeasonResource(Queries queries) {
        this.queries = queries;
    }

    @GET
    public Response seasons() throws SQLException {
        List<SeasonSummary> seasons = queries.seasons();
        return Response.ok(seasons).header("Cache-Control", HISTORICAL_CACHE).build();
    }

    @GET
    @Path("{year}/races")
    public Response races(@PathParam("year") int year) throws SQLException {
        requireSeason(year);
        List<RaceSummary> races = queries.races(year);
        return Response.ok(races).header("Cache-Control", HISTORICAL_CACHE).build();
    }

    @GET
    @Path("{year}/standings/drivers")
    public Response driverStandings(@PathParam("year") int year) throws SQLException {
        requireSeason(year);
        List<DriverStanding> standings = queries.driverStandings(year);
        return Response.ok(standings).header("Cache-Control", HISTORICAL_CACHE).build();
    }

    @GET
    @Path("{year}/standings/constructors")
    public Response constructorStandings(@PathParam("year") int year) throws SQLException {
        requireSeason(year);
        List<ConstructorStanding> standings = queries.constructorStandings(year);
        return Response.ok(standings).header("Cache-Control", HISTORICAL_CACHE).build();
    }

    private void requireSeason(int year) throws SQLException {
        if (!queries.seasonExists(year)) {
            throw ApiException.notFound("no season " + year + " in the database");
        }
    }
}
