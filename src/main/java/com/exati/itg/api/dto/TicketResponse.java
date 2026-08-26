package com.exati.itg.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Ticket returned by the Exati Solicitações API for create (201, or 200 on an
 * idempotent repeat) and cancel (200) operations.
 *
 * <p>{@code ticketStatus} is one of {@link TicketStatus} but kept as a string
 * (plus {@code ignoreUnknown}) so an upstream addition never breaks
 * deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TicketResponse(
        Long idExternalProtocol,
        Long idTicket,
        String deviceUuid,
        String ticketStatus
) {
}
