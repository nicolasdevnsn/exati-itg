package com.exati.itg.integration;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.TicketQuery;
import com.exati.itg.api.dto.TicketQueryResponse;
import com.exati.itg.api.dto.TicketResponse;
import com.exati.itg.config.ExatiProperties;
import com.exati.itg.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Optional;

/**
 * Client for the Exati IoT Hub <b>Solicitações</b> (tickets) API —
 * https://iothub-solicitacoes.apidog.io (criar / consultar / cancelar). Owns
 * wire concerns (URI, headers, content-type, error translation); orchestration
 * lives in the service layer.
 *
 * <p>The configured base URL already ends at the tickets resource — on the
 * certifier the product token is part of the path ({@code /tickets/<token>}) —
 * so every call targets the base URL itself; only the query string varies.
 * Bodies are snake_case, query parameters camelCase, per the published spec.
 *
 * <p>Create and cancel identify the caller via the {@code client-address}
 * header (the announced TALQ gateway UUID); the query operation declares no
 * headers in the spec, so none are sent.
 *
 * <p>Error bodies ({@link BusinessErrorResponse}) are mapped onto
 * {@link ApiException} so failures surface through the app's RFC 7807 path with
 * a status reflecting the Exati {@code error_code}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExatiTicketsClient {

    private static final String CLIENT_ADDRESS_HEADER = "client-address";

    private final RestClient exatiTicketsRestClient;
    private final ExatiProperties props;
    private final ObjectMapper objectMapper;

    /**
     * Create a solicitação ({@code POST /tickets}). Returns the full entity so
     * the edge can mirror the upstream status — 201 created vs 200 idempotent
     * repeat of an existing ticket.
     */
    public ResponseEntity<TicketResponse> createTicket(CreateTicketRequest request) {
        return call(() -> exatiTicketsRestClient.post()
                .uri("")
                .headers(this::clientAddress)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw translate(res);
                })
                .toEntity(TicketResponse.class));
    }

    /** Cancel a solicitação ({@code DELETE /tickets}) — DELETE with a body. */
    public TicketResponse cancelTicket(CancelTicketRequest request) {
        return call(() -> exatiTicketsRestClient.method(HttpMethod.DELETE)
                .uri("")
                .headers(this::clientAddress)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw translate(res);
                })
                .body(TicketResponse.class));
    }

    /** Query solicitações ({@code GET /tickets}) with optional paging/filters. */
    public TicketQueryResponse queryTickets(TicketQuery query) {
        return call(() -> exatiTicketsRestClient.get()
                .uri(b -> b
                        .queryParamIfPresent("limit", Optional.ofNullable(query.limit()))
                        .queryParamIfPresent("page", Optional.ofNullable(query.page()))
                        .queryParamIfPresent("deviceUuid", Optional.ofNullable(query.deviceUuid()))
                        .queryParamIfPresent("status", Optional.ofNullable(query.status()))
                        .queryParamIfPresent("dateFrom", Optional.ofNullable(query.dateFrom()))
                        .queryParamIfPresent("dateTo", Optional.ofNullable(query.dateTo()))
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw translate(res);
                })
                .body(TicketQueryResponse.class));
    }

    private void clientAddress(HttpHeaders headers) {
        ExatiProperties.Tickets tickets = props.tickets();
        if (tickets != null && StringUtils.hasText(tickets.clientAddress())) {
            headers.add(CLIENT_ADDRESS_HEADER, tickets.clientAddress());
        }
    }

    /** Wraps connectivity failures (timeout / refused) as 502. */
    private <T> T call(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (ResourceAccessException ex) {
            log.error("Exati Solicitações API unreachable at {}",
                    props.tickets() != null ? props.tickets().baseUrl() : null, ex);
            throw ApiException.badGateway("Exati IoT Hub is unreachable.");
        }
    }

    /** Maps a Solicitações error response onto the matching {@link ApiException}. */
    private ApiException translate(ClientHttpResponse res) throws IOException {
        HttpStatusCode status = res.getStatusCode();
        BusinessErrorResponse body = parse(res);
        BusinessErrorResponse.Error err = body != null ? body.error() : null;
        String code = err != null ? err.errorCode() : null;
        String detail = err != null && err.message() != null
                ? err.message()
                : "Exati request failed (HTTP " + status.value() + ").";

        log.warn("Exati Solicitações error: http={} code={} detail={}", status.value(), code, detail);

        if (status.is5xxServerError()) {
            return ApiException.badGateway("Exati IoT Hub error: " + detail);
        }
        if (code == null) {
            return fromStatus(status, detail);
        }
        return switch (code) {
            case "INVALID_HEADER" -> ApiException.badRequest(detail);
            case "INVALID_PARAMETERS" -> ApiException.unprocessable(detail);
            case "ENTITY_NOT_FOUND" -> ApiException.notFound(detail);
            case "CLIENT_IDENTIFICATION_MISSING",
                 "CLIENT_OR_VENDOR_IDENTIFICATION_MISSING",
                 "GATEWAY_IDENTIFICATION_MISSING",
                 "OPERATION_NOT_ALLOWED_FOR_VENDOR" -> ApiException.forbidden(detail);
            case "ENTITY_ALREADY_EXISTS",
                 "RESOURCE_CONFLICT",
                 "DEVICE_IS_NOT_AVAILABLE",
                 "INVALID_STATE" -> ApiException.conflict(detail);
            case "TOO_MANY_REQUESTS" -> ApiException.tooManyRequests(detail);
            case "VENDOR_REQUEST_ERROR", "INTERNAL_SERVER_ERROR" ->
                    ApiException.badGateway("Exati IoT Hub error: " + detail);
            default -> fromStatus(status, detail);
        };
    }

    /** Fallback for unrecognized (or absent) error codes: mirror the HTTP status. */
    private ApiException fromStatus(HttpStatusCode status, String detail) {
        return switch (status.value()) {
            case 400 -> ApiException.badRequest(detail);
            case 403 -> ApiException.forbidden(detail);
            case 404 -> ApiException.notFound(detail);
            case 409 -> ApiException.conflict(detail);
            case 429 -> ApiException.tooManyRequests(detail);
            default -> ApiException.unprocessable(detail);
        };
    }

    private BusinessErrorResponse parse(ClientHttpResponse res) {
        try {
            byte[] body = res.getBody().readAllBytes();
            if (body.length == 0) {
                return null;
            }
            return objectMapper.readValue(body, BusinessErrorResponse.class);
        } catch (IOException e) {
            return null;
        }
    }
}
