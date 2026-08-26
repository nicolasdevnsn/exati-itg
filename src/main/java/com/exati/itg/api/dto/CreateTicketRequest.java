package com.exati.itg.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Payload for creating a solicitação (ticket) on the Exati IoT Hub
 * Solicitações API — {@code POST /tickets}
 * (https://iothub-solicitacoes.apidog.io/criar-solicitação-41253468e0).
 *
 * <p>Field names are the exact snake_case keys from the published spec; the
 * {@link JsonNaming} strategy maps these camelCase record components to them, so
 * the same record serves both the inbound edge request and the outbound call.
 * Constraints mirror the spec so invalid input fails fast as a 400 (RFC 7807)
 * rather than round-tripping to Exati.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateTicketRequest(

        // ── Required ────────────────────────────────────────────────────────
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "must be a UUID")
        String deviceUuid,

        @NotNull @Min(1)
        Long idExternalProtocol,

        @NotBlank
        String externalProtocol,

        @NotBlank
        String serviceCode,

        // ── Optional ─────────────────────────────────────────────────────────
        String nameplateNum,

        String description,

        String justification,

        String address,

        @Pattern(regexp = "^[-+]?([1-8]?\\d(\\.\\d+)?|90(\\.0+)?)$", message = "invalid latitude")
        String latitude,

        @Pattern(regexp = "^[-+]?((1[0-7]\\d|[1-9]?\\d)(\\.\\d+)?|180(\\.0+)?)$", message = "invalid longitude")
        String longitude
) {
}
