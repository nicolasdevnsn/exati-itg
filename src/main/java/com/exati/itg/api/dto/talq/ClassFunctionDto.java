package com.exati.itg.api.dto.talq;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A function declared on a device class (Tier 2). {@code type} is one of the
 * TALQ {@code *Function} types (e.g. {@code LampActuatorFunction}); kept as a
 * string for forward-compat with vendor/spec additions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClassFunctionDto(

        @NotBlank @Size(max = 50)
        String functionId,

        @NotBlank
        String type,

        @Valid
        List<VendorAttributeDto> vendorAttributes,

        @Valid
        List<AttributeDto> attributes,

        @Valid
        List<EventDto> events
) {
}
