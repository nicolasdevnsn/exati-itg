package com.exati.itg.api;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.CreateTicketResponse;
import com.exati.itg.service.SolicitacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Edge for creating solicitações. Delegates to {@link SolicitacaoService}, which
 * forwards to the Exati IoT Hub TALQ Tier&nbsp;1 API. Protected by the app's JWT —
 * callers authenticate against this API; the outbound Exati credentials are
 * configured separately via {@code exati.auth.*}.
 */
@RestController
@RequestMapping("/api/v1/solicitacoes")
@RequiredArgsConstructor
@Tag(name = "Solicitações", description = "Create and manage Exati Tier 1 tickets (demandas)")
@SecurityRequirement(name = "bearerAuth")
public class SolicitacaoController {

    private final SolicitacaoService solicitacaoService;

    @PostMapping
    @Operation(summary = "Create a solicitação (ticket) on the Exati IoT Hub")
    public ResponseEntity<CreateTicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitacaoService.create(request));
    }

    @DeleteMapping
    @Operation(summary = "Cancel a solicitação (ticket) on the Exati IoT Hub")
    public ResponseEntity<CreateTicketResponse> cancel(@Valid @RequestBody CancelTicketRequest request) {
        return ResponseEntity.ok(solicitacaoService.cancel(request));
    }
}
