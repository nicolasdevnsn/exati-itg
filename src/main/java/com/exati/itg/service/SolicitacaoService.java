package com.exati.itg.service;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.TicketQuery;
import com.exati.itg.api.dto.TicketQueryResponse;
import com.exati.itg.api.dto.TicketResponse;
import com.exati.itg.integration.ExatiTicketsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the solicitação (ticket) lifecycle against the Exati IoT Hub
 * Solicitações API (criar / consultar / cancelar). For now a pass-through;
 * it's the seam where persistence (id_ticket correlation) would land.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SolicitacaoService {

    private final ExatiTicketsClient exatiClient;

    /** Create — the entity is kept so the edge can mirror 201 created vs 200 idempotent. */
    public ResponseEntity<TicketResponse> create(CreateTicketRequest request) {
        log.info("Creating solicitação: externalProtocol={} serviceCode={} device={}",
                request.idExternalProtocol(), request.serviceCode(), request.deviceUuid());
        return exatiClient.createTicket(request);
    }

    public TicketResponse cancel(CancelTicketRequest request) {
        log.info("Cancelling solicitação: externalProtocol={}", request.idExternalProtocol());
        return exatiClient.cancelTicket(request);
    }

    public TicketQueryResponse query(TicketQuery query) {
        log.info("Querying solicitações: device={} status={} page={} limit={}",
                query.deviceUuid(), query.status(), query.page(), query.limit());
        return exatiClient.queryTickets(query);
    }
}
