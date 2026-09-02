package com.exati.itg.service;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.TicketQuery;
import com.exati.itg.api.dto.TicketQueryResponse;
import com.exati.itg.api.dto.TicketResponse;
import com.exati.itg.integration.ExatiTicketsClient;
import com.exati.itg.mirror.TicketMirror;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the solicitação (ticket) lifecycle against the Exati IoT Hub
 * Solicitações API (criar / consultar / cancelar). Accepted operations are
 * copied to the environment's {@link TicketMirror} strictly after the fact —
 * a mirror problem must never alter what the caller receives.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SolicitacaoService {

    private final ExatiTicketsClient exatiClient;
    private final TicketMirror ticketMirror;

    /** Create — the entity is kept so the edge can mirror 201 created vs 200 idempotent. */
    public ResponseEntity<TicketResponse> create(CreateTicketRequest request) {
        log.info("Creating solicitação: externalProtocol={} serviceCode={} device={}",
                request.idExternalProtocol(), request.serviceCode(), request.deviceUuid());
        ResponseEntity<TicketResponse> upstream = exatiClient.createTicket(request);
        if (upstream.getStatusCode().is2xxSuccessful() && upstream.getBody() != null) {
            mirrorSafely(() -> ticketMirror.recordCreated(request, upstream.getBody()));
        }
        return upstream;
    }

    public TicketResponse cancel(CancelTicketRequest request) {
        log.info("Cancelling solicitação: externalProtocol={}", request.idExternalProtocol());
        TicketResponse response = exatiClient.cancelTicket(request);
        mirrorSafely(() -> ticketMirror.recordCancelled(request, response));
        return response;
    }

    private void mirrorSafely(Runnable mirrorCall) {
        try {
            mirrorCall.run();
        } catch (Exception e) {
            log.warn("Ticket mirror call failed (ticket unaffected): {}", e.getMessage());
        }
    }

    /** Mirror-first: environments with a wired mirror answer locally, others ask Exati. */
    public TicketQueryResponse query(TicketQuery query) {
        log.info("Querying solicitações: device={} status={} page={} limit={}",
                query.deviceUuid(), query.status(), query.page(), query.limit());
        return ticketMirror.query(query)
                .orElseGet(() -> exatiClient.queryTickets(query));
    }
}
