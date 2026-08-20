package com.exati.itg.talqserver;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Carries a TALQ error array + HTTP status from anywhere in the gateway-server
 * stack to {@link TalqServerAdvice}. Prefer the static factories so the
 * status/key pairing stays consistent with the certifier's expectations.
 */
@Getter
public class TalqApiException extends RuntimeException {

    private final HttpStatus status;
    private final TalqError error;

    public TalqApiException(HttpStatus status, TalqError error) {
        super(error.key() + ": " + error.description());
        this.status = status;
        this.error = error;
    }

    public static TalqApiException badRequest(String description) {
        return new TalqApiException(HttpStatus.BAD_REQUEST, TalqError.payload(description));
    }

    public static TalqApiException unprocessable(String description) {
        return new TalqApiException(HttpStatus.UNPROCESSABLE_ENTITY, TalqError.payload(description));
    }

    public static TalqApiException notFound(String description) {
        return new TalqApiException(HttpStatus.NOT_FOUND, TalqError.notFound(description));
    }

    public static TalqApiException relatedNotFound(String description) {
        return new TalqApiException(HttpStatus.NOT_FOUND, TalqError.relatedNotFound(description));
    }

    public static TalqApiException conflict(String description) {
        return new TalqApiException(HttpStatus.CONFLICT, TalqError.conflict(description));
    }

    public static TalqApiException methodNotAllowed(String description) {
        return new TalqApiException(HttpStatus.METHOD_NOT_ALLOWED,
                new TalqError(TalqError.METHOD_NOT_ALLOWED, description));
    }
}
