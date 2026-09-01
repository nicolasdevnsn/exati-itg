package com.exati.itg.mirror;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.TicketResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Dev-environment mirror: copies every ticket accepted by Exati into the SIP
 * {@code ami} database ({@code ami.itg_ticket_mirror}), reached through the
 * app-managed SSH tunnel. Writes are best-effort — queued locally and retried,
 * never surfacing to the caller.
 *
 * <p>Skeleton: the outbox queue, SSH tunnel and JDBC writes land with the
 * mirror datasource work (plan phases 2 and 4).
 */
@Slf4j
public class DevTicketMirror implements TicketMirror {

    public DevTicketMirror() {
        log.info("Ticket mirror active: dev — SIP ami.itg_ticket_mirror via SSH tunnel");
    }

    @Override
    public void recordCreated(CreateTicketRequest request, TicketResponse response) {
        log.debug("Mirror recordCreated: idTicket={} (persistence pending phases 2/4)",
                response.idTicket());
    }

    @Override
    public void recordCancelled(CancelTicketRequest request, TicketResponse response) {
        log.debug("Mirror recordCancelled: idTicket={} (persistence pending phases 2/4)",
                response.idTicket());
    }
}
