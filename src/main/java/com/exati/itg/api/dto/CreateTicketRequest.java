package com.exati.itg.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating a solicitação (ticket / demanda) on the Exati IoT Hub
 * TALQ Tier&nbsp;1 API — {@code POST /vendors/talq/clients/{idInstance}/tickets}.
 *
 * <p>Field names are the exact snake_case keys from the Tier&nbsp;1 OpenAPI; the
 * {@link JsonNaming} strategy maps these camelCase record components to them, so
 * the same record serves both the inbound edge request and the outbound call.
 * Constraints mirror the spec so invalid input fails fast as a 400 (RFC 7807)
 * rather than round-tripping to Exati.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateTicketRequest(

        // ── Required ────────────────────────────────────────────────────────
        @NotNull @Min(1)
        Long idExternalProtocol,

        @NotBlank
        String serviceCode,

        @NotNull @Min(1)
        Long idWorksite,

        // ── Optional: origin / protocol ──────────────────────────────────────
        @Size(max = 4)
        String codExternalTicketOrigin,

        @Min(1)
        Integer ticketOriginTypeId,

        @Size(max = 20)
        String externalProtocol,

        @Size(max = 200)
        String justification,

        @Size(max = 200)
        String description,

        // ── Optional: location ───────────────────────────────────────────────
        StateAbbreviation stateAbbreviation,

        @Size(max = 72)
        String municipality,

        @Size(max = 72)
        String neighborhood,

        String address,

        @Min(0)
        Integer addressNumber,

        @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "must match #####-### or ########")
        String zipCode,

        @Pattern(regexp = "^[-+]?([1-8]?\\d(\\.\\d+)?|90(\\.0+)?)$", message = "invalid latitude")
        String latitude,

        @Pattern(regexp = "^[-+]?((1[0-7]\\d|[1-9]?\\d)(\\.\\d+)?|180(\\.0+)?)$", message = "invalid longitude")
        String longitude,

        String referencePoint,

        // ── Optional: reporter ───────────────────────────────────────────────
        @Size(max = 150)
        String reporter,

        @Size(max = 150)
        String reporterPhone,

        @Pattern(regexp = "^\\d{2}/\\d{2}/\\d{4}$", message = "must be dd/mm/yyyy")
        String reporterBirthDate,

        @Size(max = 100)
        String nameplateNum,

        @Min(0) @Max(1)
        Integer ageMajorityCheckbox
) {
}
