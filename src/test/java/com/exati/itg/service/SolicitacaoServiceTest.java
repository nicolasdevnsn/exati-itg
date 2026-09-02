package com.exati.itg.service;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.TicketQuery;
import com.exati.itg.api.dto.TicketQueryResponse;
import com.exati.itg.api.dto.TicketResponse;
import com.exati.itg.integration.ExatiTicketsClient;
import com.exati.itg.mirror.TicketMirror;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The service's contract with the mirror: it is fed only after Exati accepts,
 * it can never affect what the caller receives, and the listing prefers the
 * mirror but falls back to Exati.
 */
class SolicitacaoServiceTest {

    private static final String DEVICE = "93c751e8-2c06-460e-8d68-31f5e4774b43";

    private ExatiTicketsClient exatiClient;
    private TicketMirror mirror;
    private SolicitacaoService service;

    @BeforeEach
    void setUp() {
        exatiClient = mock(ExatiTicketsClient.class);
        mirror = mock(TicketMirror.class);
        service = new SolicitacaoService(exatiClient, mirror);
    }

    private static CreateTicketRequest createRequest() {
        return new CreateTicketRequest(DEVICE, 1L, "PROTO-1", "ILUMINACAO_FALHA",
                null, null, null, null, null, null);
    }

    private static TicketResponse response(String status) {
        return new TicketResponse(1L, 100L, DEVICE, status);
    }

    @Test
    void create_accepted_isMirrored() {
        TicketResponse body = response("PENDING");
        when(exatiClient.createTicket(any()))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(body));

        ResponseEntity<TicketResponse> result = service.create(createRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(mirror).recordCreated(any(), any());
    }

    @Test
    void create_notAccepted_isNotMirrored() {
        when(exatiClient.createTicket(any()))
                .thenReturn(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build());

        service.create(createRequest());

        verify(mirror, never()).recordCreated(any(), any());
    }

    @Test
    void create_mirrorFailure_doesNotAffectTheCaller() {
        TicketResponse body = response("PENDING");
        when(exatiClient.createTicket(any()))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(body));
        org.mockito.Mockito.doThrow(new RuntimeException("mirror down"))
                .when(mirror).recordCreated(any(), any());

        ResponseEntity<TicketResponse> result = service.create(createRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(body);
    }

    @Test
    void cancel_isMirroredAndSurvivesMirrorFailure() {
        TicketResponse body = response("CANCELED");
        when(exatiClient.cancelTicket(any())).thenReturn(body);
        org.mockito.Mockito.doThrow(new RuntimeException("mirror down"))
                .when(mirror).recordCancelled(any(), any());

        CancelTicketRequest request = new CancelTicketRequest(1L, "justificativa");
        assertThatCode(() -> assertThat(service.cancel(request)).isEqualTo(body))
                .doesNotThrowAnyException();
        verify(mirror).recordCancelled(any(), any());
    }

    @Test
    void query_prefersTheMirror() {
        TicketQueryResponse mirrored = new TicketQueryResponse(1, 20, 1L,
                List.of(new TicketQueryResponse.Item(1L, 100L, DEVICE, "PENDING",
                        null, null, null, null)));
        when(mirror.query(any())).thenReturn(Optional.of(mirrored));

        TicketQueryResponse result = service.query(new TicketQuery(20, 1, null, null, null, null));

        assertThat(result).isEqualTo(mirrored);
        verifyNoInteractions(exatiClient);
    }

    @Test
    void query_fallsBackToExatiWhenTheMirrorHasNoAnswer() {
        TicketQueryResponse upstream = new TicketQueryResponse(1, 20, 0L, List.of());
        when(mirror.query(any())).thenReturn(Optional.empty());
        when(exatiClient.queryTickets(any())).thenReturn(upstream);

        TicketQueryResponse result = service.query(new TicketQuery(20, 1, null, null, null, null));

        assertThat(result).isEqualTo(upstream);
        verify(exatiClient).queryTickets(any());
    }
}
