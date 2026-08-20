package com.exati.itg.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

/**
 * Error body returned by the Exati <b>Tier&nbsp;1</b> (Solicitações) API — the
 * {@code BusinessErrorResponse} schema. Note the {@code error_code} is nested
 * under {@code error}, not top-level.
 *
 * <p>Distinct from Tier&nbsp;2, which returns an array of {@link TalqErrorMessage}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BusinessErrorResponse(
        String status,
        Error error
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Error(
            String errorCode,
            String message,
            Map<String, Object> details
    ) {
    }
}
