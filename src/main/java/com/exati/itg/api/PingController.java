package com.exati.itg.api;

import com.exati.itg.api.dto.PingResponse;
import com.exati.itg.service.PingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Ping", description = "Trivial health-check style endpoints")
@SecurityRequirement(name = "bearerAuth")
public class PingController {

    private final PingService pingService;

    @GetMapping("/ping")
    @Operation(summary = "Returns 'pong' plus the server's current timestamp")
    public PingResponse ping() {
        return pingService.ping();
    }
}
