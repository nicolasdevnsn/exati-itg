package com.exati.itg.service;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.CreateTicketResponse;
import com.exati.itg.integration.ExatiTalqClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the solicitação (ticket / demanda) lifecycle against Exati
 * Tier&nbsp;1. For now this is a pass-through create; it's the seam where
 * persistence (id_demanda correlation) and the remaining operations
 * (cancela / encerra / reabre / atualiza / altera) will land.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SolicitacaoService {

    private final ExatiTalqClient exatiClient;

    public CreateTicketResponse create(CreateTicketRequest request) {
        log.info("Creating solicitação: externalProtocol={} serviceCode={} worksite={}",
                request.idExternalProtocol(), request.serviceCode(), request.idWorksite());
        return exatiClient.createTicket(request);
    }

    public CreateTicketResponse cancel(CancelTicketRequest request) {
        log.info("Cancelling solicitação: externalProtocol={}", request.idExternalProtocol());
        return exatiClient.cancelTicket(request);
    }
}
