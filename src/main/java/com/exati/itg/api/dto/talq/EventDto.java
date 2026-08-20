package com.exati.itg.api.dto.talq;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Event exposed by a TALQ function (Tier 2 device-class model).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventDto(
        String name,
        String description,
        String uuid,
        String functionUuid,

        @NotBlank
        String type
) {
}
