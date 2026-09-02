package com.exati.itg.mirror;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.TicketQuery;
import com.exati.itg.api.dto.TicketQueryResponse;
import com.exati.itg.api.dto.TicketResponse;

import java.util.Optional;

/**
 * Environment-specific copy of the tickets submitted to the Exati IoT Hub.
 *
 * <p>Exati remains the source of truth; the mirror is written best-effort
 * AFTER Exati has accepted an operation and must never fail or delay the
 * caller — implementations queue and retry internally. Selected per
 * environment by {@code MirrorConfig}: dev writes to the SIP {@code ami}
 * database, every other environment is a no-op.
 */
public interface TicketMirror {

    /** Record a ticket Exati accepted (201 created or 200 idempotent repeat). */
    void recordCreated(CreateTicketRequest request, TicketResponse response);

    /** Record a cancellation Exati accepted. */
    void recordCancelled(CancelTicketRequest request, TicketResponse response);

    /**
     * Answer the listing from the mirror, or {@link Optional#empty()} when this
     * environment's mirror can't (no wired database, or it is unreachable) —
     * the caller then falls back to querying Exati directly.
     */
    Optional<TicketQueryResponse> query(TicketQuery query);
}
