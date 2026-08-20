package com.exati.itg.api.dto;

import java.time.Instant;

/**
 * Response payload for {@code GET /api/v1/ping}.
 *
 * <p>DTOs in this project are Java records — immutable, with auto-generated
 * accessors, {@code equals}, {@code hashCode}, and {@code toString}.
 */
public record PingResponse(String message, Instant timestamp) {
}
