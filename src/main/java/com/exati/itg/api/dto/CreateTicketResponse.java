package com.exati.itg.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Successful (HTTP 201) response from the Exati Tier&nbsp;1 create-ticket call.
 *
 * <p>{@code operacao} is one of {@code cria | cancela | encerra | reabre |
 * atualiza | altera} and {@code status} is {@code ok | error}. Kept as strings
 * (rather than enums) and {@code ignoreUnknown} so an upstream addition never
 * breaks deserialization — consistent with TALQ's silent-accept rule for
 * unknown fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateTicketResponse(
        Long idDemanda,
        String operacao,
        String dataRecebido,
        String status
) {
}
