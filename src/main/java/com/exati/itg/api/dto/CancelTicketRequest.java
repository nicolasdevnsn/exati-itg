package com.exati.itg.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for cancelling a solicitação on the Exati Tier&nbsp;1 API —
 * {@code DELETE /vendors/talq/clients/{idInstance}/tickets} ({@code CancelTicketDTO}).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CancelTicketRequest(

        @NotNull @Min(1)
        Long idExternalProtocol,

        @Size(max = 4)
        String codExternalTicketOrigin,

        @Size(max = 200)
        String justification
) {
}
