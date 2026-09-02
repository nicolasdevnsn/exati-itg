package com.exati.itg.mirror;

import com.exati.itg.api.dto.TicketQuery;
import com.exati.itg.api.dto.TicketQueryResponse;
import com.exati.itg.config.ItgProperties;
import com.exati.itg.integration.ExatiTicketsClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Keeps mirrored tickets in sync with Exati: periodically re-reads the Exati
 * listing and updates the known fields (status, reported_at, closed_at,
 * closing_reason) of every non-terminal row in {@code exati_itg_ticket}.
 * Terminal tickets are never re-checked.
 *
 * <p>Known blocker: the certifier's listing currently answers total=0 even
 * for existing tickets — the job runs correctly but updates nothing until
 * Exati fixes it (open question with them).
 */
@Slf4j
public class TicketRecheckJob implements AutoCloseable {

    private static final long INITIAL_DELAY_SECONDS = 60;
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 50;

    private static final String PENDING_SQL = """
            SELECT id_ticket, ticket_status
              FROM exati_itg_ticket
             WHERE ticket_status NOT IN (:terminal)
               AND submitted_at > :cutoff
            """;

    private static final String SYNC_SQL = """
            UPDATE exati_itg_ticket
               SET ticket_status = ?, reported_at = ?, closed_at = ?,
                   closing_reason = ?,
                   last_status_at = COALESCE(?, last_status_at),
                   last_checked_at = ?
             WHERE id_ticket = ?
            """;

    private static final String TOUCH_SQL = """
            UPDATE exati_itg_ticket SET last_checked_at = ? WHERE id_ticket = ?
            """;

    private final SipDatabase db;
    private final ExatiTicketsClient exatiClient;
    private final Set<String> terminalStatuses;
    private final long expireDays;
    private final ScheduledExecutorService scheduler;

    public TicketRecheckJob(SipDatabase db, ExatiTicketsClient exatiClient,
                            ItgProperties.Dev.Recheck recheck) {
        this.db = db;
        this.exatiClient = exatiClient;
        this.terminalStatuses = recheck.terminalStatuses();
        this.expireDays = recheck.expireDays();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sip-ticket-recheck");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleWithFixedDelay(this::runSafely,
                INITIAL_DELAY_SECONDS, recheck.periodMinutes() * 60, TimeUnit.SECONDS);
        log.info("Ticket recheck scheduled every {} min (terminal: {}, expire: {} days)",
                recheck.periodMinutes(), terminalStatuses, expireDays);
    }

    /** One pass, never throwing — what the scheduler runs. */
    void runSafely() {
        try {
            runOnce();
        } catch (Exception e) {
            log.warn("Ticket recheck failed: {} — next cycle retries", e.getMessage());
        }
    }

    /** One full pass; also callable on demand (tests, manual trigger). */
    public synchronized void runOnce() {
        List<Map<String, Object>> pending = db.jdbc().sql(PENDING_SQL)
                .param("terminal", List.copyOf(terminalStatuses))
                .param("cutoff", LocalDateTime.now(ZoneOffset.UTC).minusDays(expireDays))
                .query().listOfRows();
        if (pending.isEmpty()) {
            log.debug("Ticket recheck: nothing non-terminal to check");
            return;
        }

        Map<Long, TicketQueryResponse.Item> upstream = fetchUpstream();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int synced = 0;
        int missing = 0;

        for (Map<String, Object> row : pending) {
            long idTicket = ((Number) row.get("id_ticket")).longValue();
            String currentStatus = (String) row.get("ticket_status");
            TicketQueryResponse.Item item = upstream.get(idTicket);
            if (item == null) {
                // Not in the listing (today: always, due to the total=0 bug).
                db.jdbc().sql(TOUCH_SQL).params(now, idTicket).update();
                missing++;
                continue;
            }
            boolean statusChanged = !Objects.equals(currentStatus, item.ticketStatus());
            db.jdbc().sql(SYNC_SQL)
                    .params(item.ticketStatus(), parseDate(item.reportedAt()),
                            parseDate(item.closedAt()), item.closingReason(),
                            statusChanged ? now : null, now, idTicket)
                    .update();
            synced++;
        }
        log.info("Ticket recheck: {} checked, {} synced from Exati, {} not in listing",
                pending.size(), synced, missing);
    }

    /** Page through the Exati listing and index the items by id_ticket. */
    private Map<Long, TicketQueryResponse.Item> fetchUpstream() {
        Map<Long, TicketQueryResponse.Item> byId = new HashMap<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            TicketQueryResponse response = exatiClient.queryTickets(
                    new TicketQuery(PAGE_SIZE, page, null, null, null, null));
            if (response == null) {
                break;
            }
            List<TicketQueryResponse.Item> items =
                    response.items() != null ? response.items() : List.of();
            items.forEach(i -> byId.put(i.idTicket(), i));
            long total = response.total() != null ? response.total() : 0;
            if (items.isEmpty() || (long) page * PAGE_SIZE >= total) {
                break;
            }
        }
        return byId;
    }

    /** The listing's date format is not documented — parse defensively. */
    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
            } catch (DateTimeParseException e2) {
                log.debug("Unparseable date from Exati listing: '{}'", value);
                return null;
            }
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
