package com.exati.itg.api;

import com.exati.itg.api.dto.AuthResponse;
import com.exati.itg.api.dto.LoginRequest;
import com.exati.itg.api.dto.RegisterRequest;
import com.exati.itg.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Register, log in, and obtain JWT bearer tokens")
@SecurityRequirements // open endpoints — bypass the global Bearer requirement
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Create a new user account and return an access token")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with username + password and obtain an access token")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }
}
