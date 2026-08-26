package com.exati.itg.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for cancelling a solicitação on the Exati Solicitações API —
 * {@code DELETE /tickets}
 * (https://iothub-solicitacoes.apidog.io/cancelar-solicitação-41253467e0).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CancelTicketRequest(

        @NotNull @Min(1)
        Long idExternalProtocol,

        @NotBlank @Size(max = 200)
        String justification
) {
}
