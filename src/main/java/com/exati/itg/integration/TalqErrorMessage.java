package com.exati.itg.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One entry of the error array returned by the Exati <b>Tier&nbsp;2</b> (TALQ
 * resource) API. {@code key} is a TALQ error code such as {@code payloadError},
 * {@code resourceNotFound}, {@code resourceConflict}.
 *
 * <p>Distinct from Tier&nbsp;1, which returns a single {@link BusinessErrorResponse}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TalqErrorMessage(
        String key,
        String description,
        List<Reference> references
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reference(ResourceAddress address, String tag) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResourceAddress(String resource, String address) {
    }
}
