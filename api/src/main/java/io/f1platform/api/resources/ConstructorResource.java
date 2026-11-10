package io.f1platform.api.resources;

import io.f1platform.api.ApiException;
import io.f1platform.api.Dtos.ConstructorProfile;
import io.f1platform.api.Dtos.ConstructorSeason;
import io.f1platform.api.Queries;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Path("/api/v1/constructors/{name}")
@Produces(MediaType.APPLICATION_JSON)
public class ConstructorResource {

    private final Queries queries;

    public ConstructorResource(Queries queries) {
        this.queries = queries;
    }

    @GET
    public Response profile(@PathParam("name") String name) throws SQLException {
        ConstructorProfile team = queries.constructorByName(name.replace("-", " "));
        if (team == null) throw ApiException.notFound("no constructor named " + name);
        return Response.ok(team).build();
    }

    @GET
    @Path("seasons")
    public Response seasons(@PathParam("name") String name) throws SQLException {
        ConstructorProfile team = queries.constructorByName(name.replace("-", " "));
        if (team == null) throw ApiException.notFound("no constructor named " + name);
        List<ConstructorSeason> seasons = queries.constructorSeasons(team.id());
        return Response.ok(Map.of("constructor", team, "seasons", seasons)).build();
    }
}
