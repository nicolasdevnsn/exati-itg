package com.exati.itg.api;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.TicketQuery;
import com.exati.itg.api.dto.TicketQueryResponse;
import com.exati.itg.api.dto.TicketResponse;
import com.exati.itg.api.dto.TicketStatus;
import com.exati.itg.service.SolicitacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Edge for solicitações (tickets). Delegates to {@link SolicitacaoService},
 * which forwards to the Exati IoT Hub Solicitações API
 * (https://iothub-solicitacoes.apidog.io). Protected by the app's JWT —
 * callers authenticate against this API; the outbound Exati identity is
 * configured separately via {@code exati.tickets.*}.
 */
@RestController
@RequestMapping("/api/v1/solicitacoes")
@RequiredArgsConstructor
@Validated
@Tag(name = "Solicitações", description = "Create, query and cancel Exati IoT Hub tickets")
@SecurityRequirement(name = "bearerAuth")
public class SolicitacaoController {

    private final SolicitacaoService solicitacaoService;

    @PostMapping
    @Operation(summary = "Create a solicitação (ticket) on the Exati IoT Hub",
            description = "Mirrors the upstream status: 201 created, 200 idempotent repeat of an existing ticket.")
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        ResponseEntity<TicketResponse> upstream = solicitacaoService.create(request);
        return ResponseEntity.status(upstream.getStatusCode()).body(upstream.getBody());
    }

    @GetMapping
    @Operation(summary = "Query solicitações (tickets) on the Exati IoT Hub")
    public ResponseEntity<TicketQueryResponse> query(
            @RequestParam(required = false) @Min(1) @Max(100) Integer limit,
            @RequestParam(required = false) @Min(1) Integer page,
            @RequestParam(required = false) String deviceUuid,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(solicitacaoService.query(
                new TicketQuery(limit, page, deviceUuid, status, dateFrom, dateTo)));
    }

    @DeleteMapping
    @Operation(summary = "Cancel a solicitação (ticket) on the Exati IoT Hub")
    public ResponseEntity<TicketResponse> cancel(@Valid @RequestBody CancelTicketRequest request) {
        return ResponseEntity.ok(solicitacaoService.cancel(request));
    }
}
