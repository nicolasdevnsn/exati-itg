package com.exati.itg.mirror;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.TicketQuery;
import com.exati.itg.api.dto.TicketQueryResponse;
import com.exati.itg.api.dto.TicketResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mirrors tickets accepted by Exati into {@code ami.exati_itg_ticket},
 * through whatever transport the active {@link SipDatabaseConnectivity}
 * provides. Prod-reusable: only the wiring (connectivity + credentials) is
 * environment-specific.
 *
 * <p>Best-effort by design: {@code record*} only enqueues (the caller never
 * waits on the SIP database) and a single-threaded flusher upserts pending
 * entries, retrying every few seconds while the database is unreachable.
 * Pending entries live in memory only — a restart during an outage loses
 * them (accepted trade-off; no local staging table).
 */
@Slf4j
public class SipTicketMirror implements TicketMirror, AutoCloseable {

    private static final long FLUSH_PERIOD_SECONDS = 5;
    /** After the first failure, only every Nth is logged at WARN. */
    private static final int LOG_EVERY_N_FAILURES = 20;

    private static final String UPSERT_SQL = """
            INSERT INTO exati_itg_ticket
                (id_ticket, id_external_protocol, device_uuid, external_protocol,
                 service_code, request_payload, ticket_status, submitted_at, last_status_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                ticket_status  = VALUES(ticket_status),
                last_status_at = VALUES(last_status_at)
            """;

    private static final String CANCEL_SQL = """
            UPDATE exati_itg_ticket
               SET ticket_status = ?, cancel_justification = ?,
                   cancelled_at = ?, last_status_at = ?
             WHERE id_external_protocol = ?
            """;

    private final SipDatabase db;
    private final ObjectMapper objectMapper;
    private final Queue<MirrorWrite> pending = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService flusher;
    private int consecutiveFailures;

    private enum Kind { CREATE, CANCEL }

    private record MirrorWrite(
            Kind kind,
            Long idTicket,
            Long idExternalProtocol,
            String deviceUuid,
            String externalProtocol,
            String serviceCode,
            String payloadJson,
            String status,
            String justification,
            LocalDateTime at
    ) {
    }

    public SipTicketMirror(SipDatabase db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
        this.flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sip-ticket-mirror");
            t.setDaemon(true);
            return t;
        });
        this.flusher.scheduleWithFixedDelay(this::flush,
                FLUSH_PERIOD_SECONDS, FLUSH_PERIOD_SECONDS, TimeUnit.SECONDS);
        log.info("Ticket mirror active: SIP ami.exati_itg_ticket");
    }

    @Override
    public void recordCreated(CreateTicketRequest request, TicketResponse response) {
        pending.add(new MirrorWrite(Kind.CREATE,
                response.idTicket(),
                response.idExternalProtocol() != null
                        ? response.idExternalProtocol() : request.idExternalProtocol(),
                response.deviceUuid() != null ? response.deviceUuid() : request.deviceUuid(),
                request.externalProtocol(),
                request.serviceCode(),
                toJson(request),
                response.ticketStatus() != null ? response.ticketStatus() : "PENDING",
                null,
                LocalDateTime.now(ZoneOffset.UTC)));
        flusher.execute(this::flush);
    }

    @Override
    public void recordCancelled(CancelTicketRequest request, TicketResponse response) {
        pending.add(new MirrorWrite(Kind.CANCEL,
                response.idTicket(),
                request.idExternalProtocol(),
                response.deviceUuid(),
                null, null, null,
                response.ticketStatus() != null ? response.ticketStatus() : "CANCELED",
                request.justification(),
                LocalDateTime.now(ZoneOffset.UTC)));
        flusher.execute(this::flush);
    }

    /**
     * Drain the queue in order; on the first failure stop and leave the rest
     * for the next cycle (if the database is unreachable they would all fail).
     */
    private synchronized void flush() {
        MirrorWrite write;
        while ((write = pending.peek()) != null) {
            try {
                apply(write);
                pending.poll();
                consecutiveFailures = 0;
            } catch (Exception e) {
                consecutiveFailures++;
                if (consecutiveFailures == 1 || consecutiveFailures % LOG_EVERY_N_FAILURES == 0) {
                    log.warn("SIP mirror write failed (attempt {}, {} pending): {} — will retry",
                            consecutiveFailures, pending.size(), e.getMessage());
                }
                return;
            }
        }
    }

    private void apply(MirrorWrite w) {
        switch (w.kind()) {
            case CREATE -> {
                db.jdbc().sql(UPSERT_SQL)
                        .params(w.idTicket(), w.idExternalProtocol(), w.deviceUuid(),
                                w.externalProtocol(), w.serviceCode(), w.payloadJson(),
                                w.status(), w.at(), w.at())
                        .update();
                log.debug("Mirrored ticket {} ({})", w.idTicket(), w.status());
            }
            case CANCEL -> {
                int rows = db.jdbc().sql(CANCEL_SQL)
                        .params(w.status(), w.justification(), w.at(), w.at(),
                                w.idExternalProtocol())
                        .update();
                if (rows == 0) {
                    // Unknown to the mirror (created outside this app?) — not retryable.
                    log.warn("Cancel of protocol {} matched no mirror row — dropped",
                            w.idExternalProtocol());
                } else {
                    log.debug("Mirrored cancel of protocol {}", w.idExternalProtocol());
                }
            }
        }
    }

    /** Answer the listing from the mirror; empty on any failure (caller falls back to Exati). */
    @Override
    public Optional<TicketQueryResponse> query(TicketQuery query) {
        try {
            int limit = query.limit() != null ? query.limit() : 20;
            int page = query.page() != null ? query.page() : 1;

            StringBuilder where = new StringBuilder(" WHERE 1=1");
            List<Object> params = new ArrayList<>();
            if (query.deviceUuid() != null) {
                where.append(" AND device_uuid = ?");
                params.add(query.deviceUuid());
            }
            if (query.status() != null) {
                where.append(" AND ticket_status = ?");
                params.add(query.status().name());
            }
            if (query.dateFrom() != null) {
                where.append(" AND submitted_at >= ?");
                params.add(query.dateFrom().atStartOfDay());
            }
            if (query.dateTo() != null) {
                // inclusive end date
                where.append(" AND submitted_at < ?");
                params.add(query.dateTo().plusDays(1).atStartOfDay());
            }

            Long total = db.jdbc().sql("SELECT COUNT(*) FROM exati_itg_ticket" + where)
                    .params(params.toArray())
                    .query(Long.class).single();

            List<Object> pageParams = new ArrayList<>(params);
            pageParams.add(limit);
            pageParams.add((page - 1) * limit);
            List<TicketQueryResponse.Item> items = db.jdbc().sql("""
                            SELECT id_external_protocol, id_ticket, device_uuid, ticket_status,
                                   reported_at, cancel_justification, closed_at, closing_reason
                              FROM exati_itg_ticket""" + where
                            + " ORDER BY submitted_at DESC LIMIT ? OFFSET ?")
                    .params(pageParams.toArray())
                    .query((rs, i) -> new TicketQueryResponse.Item(
                            rs.getLong("id_external_protocol"),
                            rs.getLong("id_ticket"),
                            rs.getString("device_uuid"),
                            rs.getString("ticket_status"),
                            toIso(rs.getTimestamp("reported_at")),
                            rs.getString("cancel_justification"),
                            toIso(rs.getTimestamp("closed_at")),
                            rs.getString("closing_reason")))
                    .list();

            return Optional.of(new TicketQueryResponse(page, limit, total, items));
        } catch (Exception e) {
            log.warn("Mirror query failed, falling back to Exati: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String toIso(Timestamp t) {
        return t == null ? null : t.toLocalDateTime().toString();
    }

    private String toJson(CreateTicketRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            log.warn("Could not serialize create payload for mirror: {}", e.getMessage());
            return "{}";
        }
    }

    @Override
    public void close() {
        flush();
        flusher.shutdownNow();
        if (!pending.isEmpty()) {
            log.warn("Shutting down with {} unmirrored ticket write(s) — lost", pending.size());
        }
    }
}
