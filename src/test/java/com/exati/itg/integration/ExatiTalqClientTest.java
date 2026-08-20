package com.exati.itg.integration;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.CreateTicketResponse;
import com.exati.itg.config.ExatiProperties;
import com.exati.itg.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ExatiTalqClientTest {

    private static final String BASE = "http://exati.test";
    private static final String INSTANCE = "69";
    private static final String URL = BASE + "/vendors/talq/clients/" + INSTANCE + "/tickets";

    private MockRestServiceServer server;
    private ExatiTalqClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        ExatiProperties props = new ExatiProperties(
                BASE, INSTANCE, null,
                new ExatiProperties.Auth("none", null, null, null),
                new ExatiProperties.Timeout(5_000, 10_000));

        client = new ExatiTalqClient(restClient, props, new ObjectMapper());
    }

    @Test
    void createTicket_success_returnsResponse() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "id_demanda": 123, "operacao": "cria",
                                  "data_recebido": "2026-07-01T10:00:00Z", "status": "ok" }
                                """));

        CreateTicketResponse res = client.createTicket(minimalCreate());

        assertThat(res.idDemanda()).isEqualTo(123L);
        assertThat(res.operacao()).isEqualTo("cria");
        assertThat(res.status()).isEqualTo("ok");
        server.verify();
    }

    @Test
    void cancelTicket_success_returnsResponse() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "id_demanda": 123, "operacao": "cancela",
                                  "data_recebido": "2026-07-01T11:00:00Z", "status": "ok" }
                                """));

        CreateTicketResponse res = client.cancelTicket(new CancelTicketRequest(1L, null, "duplicada"));

        assertThat(res.operacao()).isEqualTo("cancela");
        server.verify();
    }

    @Test
    void createTicket_invalidParameters_mapsToBadRequest() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "status": "error",
                                  "error": { "error_code": "INVALID_PARAMETERS",
                                             "message": "service_code is unknown", "details": {} } }
                                """));

        assertThatThrownBy(() -> client.createTicket(minimalCreate()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("service_code is unknown")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        server.verify();
    }

    @Test
    void createTicket_clientIdentificationMissing_mapsToForbidden() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "status": "error",
                                  "error": { "error_code": "CLIENT_IDENTIFICATION_MISSING",
                                             "message": "missing client id", "details": {} } }
                                """));

        assertThatThrownBy(() -> client.createTicket(minimalCreate()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createTicket_upstream5xx_mapsToBadGateway() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.createTicket(minimalCreate()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    private static CreateTicketRequest minimalCreate() {
        return new CreateTicketRequest(
                1L, "SVC-01", 42L,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }
}
