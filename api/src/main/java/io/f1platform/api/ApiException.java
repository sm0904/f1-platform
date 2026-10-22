package io.f1platform.api;

/** An error with an HTTP status, mapped to an RFC 9457 problem response. */
public class ApiException extends RuntimeException {

    private final int status;
    private final String title;

    public ApiException(int status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    public static ApiException notFound(String detail) {
        return new ApiException(404, "Not found", detail);
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(400, "Bad request", detail);
    }
}
