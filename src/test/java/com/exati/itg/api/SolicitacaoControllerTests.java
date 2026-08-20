package com.exati.itg.api;

import com.exati.itg.api.dto.CreateTicketResponse;
import com.exati.itg.integration.ExatiTalqClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.exati.itg.api.dto.AuthResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SolicitacaoControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExatiTalqClient exatiTalqClient;

    @Test
    void create_withoutToken_succeeds_authDisabled() throws Exception {
        // Auth middleware disabled — no token required to reach the endpoint.
        when(exatiTalqClient.createTicket(any()))
                .thenReturn(new CreateTicketResponse(1L, "cria", "2026-07-02T13:00:00Z", "ok"));

        mockMvc.perform(post("/api/v1/solicitacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated());
    }

    @Test
    void create_validRequest_returns201WithDemanda() throws Exception {
        when(exatiTalqClient.createTicket(any()))
                .thenReturn(new CreateTicketResponse(123L, "cria", "2026-07-01T10:00:00Z", "ok"));

        String token = registerAndGetToken("sol-user-ok", "supersecret123");

        mockMvc.perform(post("/api/v1/solicitacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id_demanda").value(123))
                .andExpect(jsonPath("$.operacao").value("cria"))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void create_missingRequiredField_returns400() throws Exception {
        String token = registerAndGetToken("sol-user-bad", "supersecret123");

        // id_worksite omitted → bean validation should reject before any Exati call.
        String body = "{ \"id_external_protocol\": 1, \"service_code\": \"SVC-01\" }";

        mockMvc.perform(post("/api/v1/solicitacoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private static String validBody() {
        return "{ \"id_external_protocol\": 1, \"service_code\": \"SVC-01\", \"id_worksite\": 42 }";
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
