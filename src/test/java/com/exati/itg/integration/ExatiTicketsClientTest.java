package com.exati.itg.integration;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.TicketQuery;
import com.exati.itg.api.dto.TicketQueryResponse;
import com.exati.itg.api.dto.TicketResponse;
import com.exati.itg.api.dto.TicketStatus;
import com.exati.itg.config.ExatiProperties;
import com.exati.itg.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ExatiTicketsClientTest {

    /** Certifier shape: the product token is part of the base URL path. */
    private static final String BASE = "http://exati.test/tickets/TOKEN123";
    private static final String GATEWAY_UUID = "6df4b4cd-da48-4448-bfd7-bba3f5216bf2";

    private MockRestServiceServer server;
    private ExatiTicketsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        ExatiProperties props = new ExatiProperties(
                null, null,
                new ExatiProperties.Tickets(BASE, GATEWAY_UUID, null),
                new ExatiProperties.Auth("none", null, null, null),
                new ExatiProperties.Timeout(5_000, 10_000));

        client = new ExatiTicketsClient(restClient, props, new ObjectMapper());
    }

    @Test
    void createTicket_success_returns201Entity() {
        server.expect(requestTo(BASE))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("client-address", GATEWAY_UUID))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "id_external_protocol": 987654, "id_ticket": 123456789,
                                  "device_uuid": "6f7f4c6d-0d6e-4f2b-8d58-3c0d3d92f1a1",
                                  "ticket_status": "PENDING" }
                                """));

        ResponseEntity<TicketResponse> res = client.createTicket(minimalCreate());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().idTicket()).isEqualTo(123456789L);
        assertThat(res.getBody().ticketStatus()).isEqualTo("PENDING");
        server.verify();
    }

    @Test
    void createTicket_idempotentRepeat_returns200Entity() {
        server.expect(requestTo(BASE))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "id_external_protocol": 987654, "id_ticket": 123456789,
                                  "device_uuid": "6f7f4c6d-0d6e-4f2b-8d58-3c0d3d92f1a1",
                                  "ticket_status": "PENDING" }
                                """));

        ResponseEntity<TicketResponse> res = client.createTicket(minimalCreate());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        server.verify();
    }

    @Test
    void cancelTicket_success_returnsCanceledTicket() {
        server.expect(requestTo(BASE))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("client-address", GATEWAY_UUID))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "id_external_protocol": 987654, "id_ticket": 123456789,
                                  "device_uuid": "6f7f4c6d-0d6e-4f2b-8d58-3c0d3d92f1a1",
                                  "ticket_status": "CANCELED" }
                                """));

        TicketResponse res = client.cancelTicket(
                new CancelTicketRequest(987654L, "Solicitação cancelada pelo operador"));

        assertThat(res.ticketStatus()).isEqualTo("CANCELED");
        server.verify();
    }

    @Test
    void queryTickets_buildsCamelCaseQueryString_andParsesPage() {
        server.expect(requestTo(BASE + "?limit=50&page=1&deviceUuid=550e8400-e29b-41d4-a716-446655440000"
                        + "&status=PENDING&dateFrom=2026-01-01&dateTo=2026-12-31"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "page": 1, "limit": 50, "total": 1,
                                  "items": [ { "id_external_protocol": 10001, "id_ticket": 1177048,
                                               "device_uuid": "550e8400-e29b-41d4-a716-446655440000",
                                               "ticket_status": "PENDING",
                                               "reported_at": "2026-08-01T10:00:00Z",
                                               "justification": "abertura automatica" } ] }
                                """));

        TicketQueryResponse res = client.queryTickets(new TicketQuery(
                50, 1, "550e8400-e29b-41d4-a716-446655440000", TicketStatus.PENDING,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        assertThat(res.total()).isEqualTo(1L);
        assertThat(res.items()).hasSize(1);
        assertThat(res.items().get(0).idTicket()).isEqualTo(1177048L);
        assertThat(res.items().get(0).ticketStatus()).isEqualTo("PENDING");
        server.verify();
    }

    @Test
    void queryTickets_noFilters_hitsBareBaseUrl() {
        server.expect(requestTo(BASE))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{ \"page\": 1, \"limit\": 50, \"total\": 0, \"items\": [] }"));

        TicketQueryResponse res = client.queryTickets(new TicketQuery(null, null, null, null, null, null));

        assertThat(res.items()).isEmpty();
        server.verify();
    }

    @Test
    void createTicket_invalidParameters_mapsTo422() {
        server.expect(requestTo(BASE))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "status": "error",
                                  "error": { "error_code": "INVALID_PARAMETERS",
                                             "message": "The ticket request could not be created", "details": {} } }
                                """));

        assertThatThrownBy(() -> client.createTicket(minimalCreate()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("could not be created")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        server.verify();
    }

    @Test
    void createTicket_clientOrVendorIdentificationMissing_mapsToForbidden() {
        server.expect(requestTo(BASE))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "status": "error",
                                  "error": { "error_code": "CLIENT_OR_VENDOR_IDENTIFICATION_MISSING",
                                             "message": "Client or vendor identification is missing", "details": {} } }
                                """));

        assertThatThrownBy(() -> client.createTicket(minimalCreate()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createTicket_deviceNotAvailable_mapsToConflict() {
        server.expect(requestTo(BASE))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "status": "error",
                                  "error": { "error_code": "DEVICE_IS_NOT_AVAILABLE",
                                             "message": "Device is in replacement", "details": {} } }
                                """));

        assertThatThrownBy(() -> client.createTicket(minimalCreate()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createTicket_tooManyRequests_mapsTo429() {
        server.expect(requestTo(BASE))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "status": "error",
                                  "error": { "error_code": "TOO_MANY_REQUESTS",
                                             "message": "Another request for this id_external_protocol is already being processed",
                                             "details": {} } }
                                """));

        assertThatThrownBy(() -> client.createTicket(minimalCreate()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void createTicket_upstream502VendorError_mapsToBadGateway() {
        server.expect(requestTo(BASE))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "status": "error",
                                  "error": { "error_code": "VENDOR_REQUEST_ERROR",
                                             "message": "The ticket request could not be processed at this time",
                                             "details": {} } }
                                """));

        assertThatThrownBy(() -> client.createTicket(minimalCreate()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    private static CreateTicketRequest minimalCreate() {
        return new CreateTicketRequest(
                "6f7f4c6d-0d6e-4f2b-8d58-3c0d3d92f1a1", 987654L, "PROTOCOLO-987654", "ILUMINACAO_FALHA",
                null, null, null, null, null, null);
    }
}
