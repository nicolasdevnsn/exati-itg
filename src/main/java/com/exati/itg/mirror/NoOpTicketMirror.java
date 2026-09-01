package com.exati.itg.mirror;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.TicketResponse;
import com.exati.itg.config.ItgEnvironment;
import lombok.extern.slf4j.Slf4j;

/**
 * Mirror for environments without an implementation (qa, prod). Intentionally
 * empty — it must never fall back to the dev SSH tunnel; the access path for
 * these environments is still to be defined.
 */
@Slf4j
public class NoOpTicketMirror implements TicketMirror {

    public NoOpTicketMirror(ItgEnvironment env) {
        log.info("Ticket mirror inactive: no implementation for environment {}", env);
    }

    @Override
    public void recordCreated(CreateTicketRequest request, TicketResponse response) {
        // no-op
    }

    @Override
    public void recordCancelled(CancelTicketRequest request, TicketResponse response) {
        // no-op
    }
}
