package com.exati.itg.mirror;

import com.exati.itg.api.dto.TicketQueryResponse;
import com.exati.itg.config.ItgProperties;
import com.exati.itg.integration.ExatiTicketsClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Recheck job against the REAL SIP MySQL (test environment), with the Exati
 * client mocked so upstream answers are controllable.
 */
class TicketRecheckJobTest {

    private static final String DEVICE = "93c751e8-2c06-460e-8d68-31f5e4774b43";
    private static final long PROTOCOL = 990000010L;
    private static final long TICKET = 991000010L;

    private SipDatabase db;
    private ExatiTicketsClient exatiClient;
    private TicketRecheckJob job;

    @BeforeEach
    void setUp() {
        db = SipTestDatabase.get();
        SipTestDatabase.cleanUp(PROTOCOL);
        exatiClient = mock(ExatiTicketsClient.class);
        // Default: the listing answers empty (today's real behaviour). Tests
        // that care about upstream content override it.
        upstreamReturns();
    }

    @AfterEach
    void tearDown() {
        if (job != null) {
            job.close();
        }
        SipTestDatabase.cleanUp(PROTOCOL);
    }

    private TicketRecheckJob job(long expireDays) {
        job = new TicketRecheckJob(db, exatiClient,
                new ItgProperties.Dev.Recheck(15, Set.of("RESOLVED", "CANCELED"), expireDays));
        return job;
    }

    private void insertTicket(String status, LocalDateTime submittedAt) {
        db.jdbc().sql("""
                INSERT INTO exati_itg_ticket
                    (id_ticket, id_external_protocol, device_uuid, external_protocol,
                     service_code, request_payload, ticket_status, submitted_at)
                VALUES (?, ?, ?, ?, ?, '{}', ?, ?)
                """)
                .params(TICKET, PROTOCOL, DEVICE, "PROTO-" + PROTOCOL,
                        "ILUMINACAO_FALHA", status, submittedAt)
                .update();
    }

    private Map<String, Object> row() {
        List<Map<String, Object>> rows = db.jdbc()
                .sql("SELECT * FROM exati_itg_ticket WHERE id_external_protocol = ?")
                .param(PROTOCOL).query().listOfRows();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void upstreamReturns(TicketQueryResponse.Item... items) {
        when(exatiClient.queryTickets(any())).thenReturn(
                new TicketQueryResponse(1, 100, (long) items.length, List.of(items)));
    }

    @Test
    void syncsStatusChangedUpstream() {
        insertTicket("PENDING", LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        upstreamReturns(new TicketQueryResponse.Item(PROTOCOL, TICKET, DEVICE,
                "RESOLVED", null, null, "2026-09-02T10:00:00", "concluido"));

        job(60).runOnce();

        Map<String, Object> row = row();
        assertThat(row.get("ticket_status")).isEqualTo("RESOLVED");
        assertThat(row.get("closing_reason")).isEqualTo("concluido");
        assertThat(row.get("closed_at")).isNotNull();
        assertThat(row.get("last_status_at")).as("status changed").isNotNull();
        assertThat(row.get("last_checked_at")).isNotNull();
    }

    @Test
    void ticketAbsentFromListing_onlyStampsLastChecked() {
        insertTicket("PENDING", LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        upstreamReturns(); // today's reality: the listing answers empty

        job(60).runOnce();

        Map<String, Object> row = row();
        assertThat(row.get("ticket_status")).as("untouched").isEqualTo("PENDING");
        assertThat(row.get("last_status_at")).isNull();
        assertThat(row.get("last_checked_at")).isNotNull();
    }

    @Test
    void terminalTicketsAreNeverChecked() {
        insertTicket("RESOLVED", LocalDateTime.now(ZoneOffset.UTC).minusHours(1));

        job(60).runOnce();

        assertThat(row().get("last_checked_at")).isNull();
    }

    @Test
    void ticketsOlderThanTheExpiryWindowAreGivenUp() {
        insertTicket("PENDING", LocalDateTime.now(ZoneOffset.UTC).minusDays(61));

        job(60).runOnce();

        assertThat(row().get("last_checked_at")).as("outside the 60-day window").isNull();
    }

    @Test
    void upstreamFailure_doesNotBreakTheJob() {
        insertTicket("PENDING", LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(exatiClient.queryTickets(any())).thenThrow(new RuntimeException("Exati 502"));

        TicketRecheckJob j = job(60);
        assertThatCode(j::runSafely).doesNotThrowAnyException();
        assertThat(row().get("ticket_status")).isEqualTo("PENDING");
    }
}
