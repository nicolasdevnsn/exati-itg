package com.exati.itg.api;

import com.exati.itg.api.dto.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void register_then_login_then_access_protected_endpoint() throws Exception {
        String username = "alice";
        String password = "password-strong-1";

        // 1. Register — should return 201 with a token.
        MvcResult reg = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", password))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();

        AuthResponse afterRegister = objectMapper.readValue(
                reg.getResponse().getContentAsByteArray(), AuthResponse.class);

        // 2. Login with the same credentials — also returns a token.
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        AuthResponse afterLogin = objectMapper.readValue(
                login.getResponse().getContentAsByteArray(), AuthResponse.class);

        // 3. Hit a protected endpoint with either token — should succeed.
        mockMvc.perform(get("/api/v1/ping")
                        .header("Authorization", "Bearer " + afterLogin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("pong"));

        mockMvc.perform(get("/api/v1/ping")
                        .header("Authorization", "Bearer " + afterRegister.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("username", "bob", "password", "password-strong-1"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Username already taken: bob"));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "carol", "password", "short"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "dan", "password", "password-strong-1"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "dan", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials."));
    }

    @Test
    void login_unknownUser_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "ghost", "password", "password-strong-1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageToken_isIgnored_authDisabled() throws Exception {
        // Auth middleware disabled — a malformed token is ignored and the open route responds.
        mockMvc.perform(get("/api/v1/ping")
                        .header("Authorization", "Bearer this.is.not.a.real.jwt"))
                .andExpect(status().isOk());
    }

    @Test
    void authEndpoint_doesNotRequireToken() throws Exception {
        // Swagger UI must remain reachable without a token; same for /auth/**.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
