package com.exati.itg.service;

import com.exati.itg.api.dto.PingResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PingService {

    public PingResponse ping() {
        return new PingResponse("pong", Instant.now());
    }
}
