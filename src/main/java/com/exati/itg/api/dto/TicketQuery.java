package com.exati.itg.api.dto;

import java.time.LocalDate;

/**
 * Filters for {@code GET /tickets} (consultar solicitações). All fields are
 * optional; names match the upstream camelCase query parameters verbatim.
 * Range/enum validation happens at the controller edge.
 */
public record TicketQuery(
        Integer limit,
        Integer page,
        String deviceUuid,
        TicketStatus status,
        LocalDate dateFrom,
        LocalDate dateTo
) {
}
