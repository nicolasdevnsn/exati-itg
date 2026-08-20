package com.exati.itg.integration;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.CreateTicketResponse;
import com.exati.itg.config.ExatiProperties;
import com.exati.itg.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;

/**
 * Client for the Exati IoT Hub TALQ <b>Tier&nbsp;1</b> (Solicitações) API. Owns
 * wire concerns (URI, content-type, error translation); orchestration lives in
 * the service layer.
 *
 * <p>Tier 1 error bodies ({@link BusinessErrorResponse}) are mapped onto
 * {@link ApiException} so failures surface through the app's RFC 7807 path with
 * a status reflecting the Exati {@code error_code}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExatiTalqClient {

    private static final String TICKETS_PATH = "/vendors/talq/clients/{idInstance}/tickets";

    private final RestClient exatiRestClient;
    private final ExatiProperties props;
    private final ObjectMapper objectMapper;

    /** Create a solicitação (operacao {@code cria}). */
    public CreateTicketResponse createTicket(CreateTicketRequest request) {
        return call(() -> exatiRestClient.post()
                .uri(TICKETS_PATH, props.idInstance())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw translate(res);
                })
                .body(CreateTicketResponse.class));
    }

    /** Cancel a solicitação (operacao {@code cancela}) — DELETE with a body. */
    public CreateTicketResponse cancelTicket(CancelTicketRequest request) {
        return call(() -> exatiRestClient.method(HttpMethod.DELETE)
                .uri(TICKETS_PATH, props.idInstance())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw translate(res);
                })
                .body(CreateTicketResponse.class));
    }

    /** Wraps connectivity failures (timeout / refused) as 502. */
    private <T> T call(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (ResourceAccessException ex) {
            log.error("Exati Tier 1 unreachable at {}", props.baseUrl(), ex);
            throw ApiException.badGateway("Exati IoT Hub is unreachable.");
        }
    }

    /** Maps a Tier 1 error response onto the matching {@link ApiException}. */
    private ApiException translate(ClientHttpResponse res) throws IOException {
        HttpStatusCode status = res.getStatusCode();
        BusinessErrorResponse body = parse(res);
        BusinessErrorResponse.Error err = body != null ? body.error() : null;
        String code = err != null ? err.errorCode() : null;
        String detail = err != null && err.message() != null
                ? err.message()
                : "Exati request failed (HTTP " + status.value() + ").";

        log.warn("Exati Tier 1 error: http={} code={} detail={}", status.value(), code, detail);

        if (status.is5xxServerError()) {
            return ApiException.badGateway("Exati IoT Hub error: " + detail);
        }
        if (code == null) {
            return ApiException.unprocessable(detail);
        }
        return switch (code) {
            case "INVALID_PARAMETERS", "INVALID_HEADER" -> ApiException.badRequest(detail);
            case "ENTITY_NOT_FOUND" -> ApiException.notFound(detail);
            case "CLIENT_IDENTIFICATION_MISSING", "OPERATION_NOT_ALLOWED_FOR_VENDOR" -> ApiException.forbidden(detail);
            case "ENTITY_ALREADY_EXISTS", "RESOURCE_CONFLICT" -> ApiException.conflict(detail);
            case "INTERNAL_SERVER_ERROR" -> ApiException.badGateway("Exati IoT Hub error: " + detail);
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
