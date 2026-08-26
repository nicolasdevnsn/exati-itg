package com.exati.itg.api;

import com.exati.itg.api.dto.TicketQueryResponse;
import com.exati.itg.api.dto.TicketResponse;
import com.exati.itg.integration.ExatiTicketsClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.exati.itg.api.dto.AuthResponse;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SolicitacaoControllerTests {

    private static final String DEVICE = "6f7f4c6d-0d6e-4f2b-8d58-3c0d3d92f1a1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExatiTicketsClient exatiTicketsClient;

    @Test
    void create_withoutToken_succeeds_authDisabled() throws Exception {
        // Auth middleware disabled — no token required to reach the endpoint.
        when(exatiTicketsClient.createTicket(any()))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED)
                        .body(new TicketResponse(987654L, 123456789L, DEVICE, "PENDING")));

        mockMvc.perform(post("/api/v1/solicitacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated());
    }

    @Test
    void create_validRequest_returns201WithTicket() throws Exception {
        when(exatiTicketsClient.createTicket(any()))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED)
                        .body(new TicketResponse(987654L, 123456789L, DEVICE, "PENDING")));

        String token = registerAndGetToken("sol-user-ok", "supersecret123");

        mockMvc.perform(post("/api/v1/solicitacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id_ticket").value(123456789L))
                .andExpect(jsonPath("$.device_uuid").value(DEVICE))
                .andExpect(jsonPath("$.ticket_status").value("PENDING"));
    }

    @Test
    void create_idempotentUpstream200_isMirrored() throws Exception {
        when(exatiTicketsClient.createTicket(any()))
                .thenReturn(ResponseEntity.ok(new TicketResponse(987654L, 123456789L, DEVICE, "PENDING")));

        String token = registerAndGetToken("sol-user-idem", "supersecret123");

        mockMvc.perform(post("/api/v1/solicitacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk());
    }

    @Test
    void create_missingRequiredField_returns400() throws Exception {
        String token = registerAndGetToken("sol-user-bad", "supersecret123");

        // device_uuid and external_protocol omitted → bean validation rejects
        // before any Exati call.
        String body = "{ \"id_external_protocol\": 1, \"service_code\": \"ILUMINACAO_FALHA\" }";

        mockMvc.perform(post("/api/v1/solicitacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void query_returnsPagedTickets() throws Exception {
        when(exatiTicketsClient.queryTickets(any()))
                .thenReturn(new TicketQueryResponse(1, 50, 1L, List.of(
                        new TicketQueryResponse.Item(10001L, 1177048L, DEVICE, "PENDING",
                                "2026-08-01T10:00:00Z", "abertura automatica", null, null))));

        String token = registerAndGetToken("sol-user-query", "supersecret123");

        mockMvc.perform(get("/api/v1/solicitacoes")
                        .header("Authorization", "Bearer " + token)
                        .queryParam("status", "PENDING")
                        .queryParam("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id_ticket").value(1177048L))
                .andExpect(jsonPath("$.items[0].ticket_status").value("PENDING"));
    }

    @Test
    void query_limitOutOfRange_returns400() throws Exception {
        String token = registerAndGetToken("sol-user-limit", "supersecret123");

        mockMvc.perform(get("/api/v1/solicitacoes")
                        .header("Authorization", "Bearer " + token)
                        .queryParam("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    private static String validBody() {
        return "{ \"device_uuid\": \"" + DEVICE + "\", \"id_external_protocol\": 1,"
                + " \"external_protocol\": \"PROTOCOLO-1\", \"service_code\": \"ILUMINACAO_FALHA\" }";
    }

    private String registerAndGetToken(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("username", username, "password", password));

        MvcResult res = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(res.getResponse().getContentAsByteArray(), AuthResponse.class);
        return auth.accessToken();
    }
}
