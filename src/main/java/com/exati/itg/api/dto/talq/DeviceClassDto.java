package com.exati.itg.api.dto.talq;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A TALQ device class (Tier 2) — {@code POST /talq/device-classes} accepts and
 * returns an array of these. A class is a named set of functions the ODN
 * supports; per the spec a class may grow but never shrink.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceClassDto(

        @NotBlank @Size(max = 50)
        String name,

        @NotEmpty @Valid
        List<ClassFunctionDto> functions
) {
}
