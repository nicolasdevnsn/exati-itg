package com.exati.itg.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Domain-level exception raised by service code to map a failure to a specific
 * HTTP status. Caught by {@link GlobalExceptionHandler} and serialised as
 * RFC&nbsp;7807 {@code application/problem+json}.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException unprocessable(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    /** Upstream (e.g. Exati) failed or was unreachable — surfaces as 502. */
    public static ApiException badGateway(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, message);
    }
}
