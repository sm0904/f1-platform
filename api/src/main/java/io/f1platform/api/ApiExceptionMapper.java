package io.f1platform.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps ApiException to an application/problem+json body (RFC 9457). */
@Provider
public final class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    @Override
    public Response toResponse(ApiException e) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", e.title());
        problem.put("status", e.status());
        problem.put("detail", e.getMessage());
        return Response.status(e.status()).type("application/problem+json").entity(problem).build();
    }
}
