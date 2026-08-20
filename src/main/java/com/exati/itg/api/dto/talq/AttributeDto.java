package com.exati.itg.api.dto.talq;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Standard TALQ-function attribute (Tier 2 device-class model). The regex field
 * is {@code regEx} here (capital E) — matching the Exati contract exactly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttributeDto(

        @NotBlank @Size(max = 50)
        String name,

        String description,
        Double minValue,
        Double maxValue,

        @Size(max = 500)
        String regEx,

        Boolean readOnly,
        List<String> enumValues,
        String unit,
        Map<String, Object> commands,
        Map<String, Object> types
) {
}
