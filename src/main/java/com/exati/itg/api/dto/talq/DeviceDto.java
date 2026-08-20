package com.exati.itg.api.dto.talq;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A concrete TALQ device (Tier 2) — {@code POST /talq/devices} accepts and
 * returns an array of these.
 *
 * <p>The JSON key is {@code class} (a Java reserved word), so the record
 * component is {@code deviceClass} mapped via {@link JsonProperty}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceDto(

        @NotBlank
        String address,

        @NotBlank @Size(max = 50)
        String name,

        @NotBlank
        @JsonProperty("class")
        String deviceClass,

        @Valid
        List<DeviceFunctionDto> functions
) {
}
