package com.exati.itg.service;

import com.exati.itg.api.dto.AuthResponse;
import com.exati.itg.api.dto.LoginRequest;
import com.exati.itg.api.dto.RegisterRequest;
import com.exati.itg.domain.User;
import com.exati.itg.exception.ApiException;
import com.exati.itg.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByUsername(req.username())) {
            throw ApiException.conflict("Username already taken: " + req.username());
        }

        User saved = users.save(User.builder()
                .username(req.username())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role("USER")
                .enabled(true)
                .createdAt(Instant.now())
                .build());

        log.info("Registered user '{}' (id={})", saved.getUsername(), saved.getId());
        return issueToken(saved.getUsername());
    }

    public AuthResponse login(LoginRequest req) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        } catch (BadCredentialsException ex) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid credentials.");
        } catch (AuthenticationException ex) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication failed.");
        }
        return issueToken(req.username());
    }

    private AuthResponse issueToken(String username) {
        JwtService.IssuedToken issued = jwt.issue(username);
        return new AuthResponse(TOKEN_TYPE, issued.token(), issued.expiresAt(), username);
    }
}
