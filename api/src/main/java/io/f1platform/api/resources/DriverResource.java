package io.f1platform.api.resources;

import io.f1platform.api.ApiException;
import io.f1platform.api.Dtos.DriverProfile;
import io.f1platform.api.Dtos.DriverResult;
import io.f1platform.api.Queries;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Path("/api/v1/drivers/{code}")
@Produces(MediaType.APPLICATION_JSON)
public class DriverResource {

    private final Queries queries;

    public DriverResource(Queries queries) {
        this.queries = queries;
    }

    @GET
    public Response profile(@PathParam("code") String code) throws SQLException {
        DriverProfile driver = queries.driverByCode(code);
        if (driver == null) throw ApiException.notFound("no driver with code " + code);
        return Response.ok(driver).build();
    }

    @GET
    @Path("results")
    public Response results(@PathParam("code") String code, @QueryParam("season") Integer season) throws SQLException {
        DriverProfile driver = queries.driverByCode(code);
        if (driver == null) throw ApiException.notFound("no driver with code " + code);
        List<DriverResult> results = queries.driverResults(driver.id(), season);
        return Response.ok(Map.of("driver", driver, "results", results)).build();
    }
}
