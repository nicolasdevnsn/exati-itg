package com.exati.itg.api.dto;

import java.time.Instant;

public record AuthResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        String username
) {
}
