package com.exati.itg.api.dto.talq;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Vendor-defined attribute inside a TALQ function (Tier 2 device-class model).
 * Note the field is {@code regex} here — the standard {@link AttributeDto} uses
 * {@code regEx}; the casing difference is intentional per the Exati contract.
 *
 * <p>{@code scope} ∈ measurement|configuration|event|operational and {@code type}
 * is one of the {@code Attribute*} types — kept as strings for forward-compat.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VendorAttributeDto(

        @NotBlank @Size(max = 50)
        String name,

        String description,

        @NotBlank
        String scope,

        @NotBlank
        String type,

        Double minValue,
        Double maxValue,

        @Size(max = 500)
        String regex,

        Boolean readOnly,
        List<String> enumValues,
        String unit,
        String vendorUuid,
        Map<String, Object> commands,
        Map<String, Object> types
) {
}
