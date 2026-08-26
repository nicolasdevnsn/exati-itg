package com.exati.itg.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Paged result of {@code GET /tickets} on the Exati Solicitações API
 * (https://iothub-solicitacoes.apidog.io/consultar-solicitações-41254435e0).
 *
 * <p>Envelope fields ({@code page/limit/total/items}) are plain camelCase in the
 * spec; item fields are snake_case — hence the naming strategy only on the item.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketQueryResponse(
        Integer page,
        Integer limit,
        Long total,
        List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Item(
            Long idExternalProtocol,
            Long idTicket,
            String deviceUuid,
            String ticketStatus,
            String reportedAt,
            String justification,
            String closedAt,
            String closingReason
    ) {
    }
}
