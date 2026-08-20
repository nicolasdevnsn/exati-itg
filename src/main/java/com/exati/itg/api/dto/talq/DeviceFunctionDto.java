package com.exati.itg.api.dto.talq;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A function instance on a concrete device (Tier 2). {@code id} must match a
 * {@code functionId} declared on the device's class.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceFunctionDto(

        @NotBlank @Size(max = 50)
        String id,

        @NotBlank
        String type
) {
}
