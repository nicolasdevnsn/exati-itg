package com.exati.itg.mirror;

import com.exati.itg.api.dto.CancelTicketRequest;
import com.exati.itg.api.dto.CreateTicketRequest;
import com.exati.itg.api.dto.TicketQuery;
import com.exati.itg.api.dto.TicketQueryResponse;
import com.exati.itg.api.dto.TicketResponse;
import com.exati.itg.api.dto.TicketStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirror behaviour against the REAL SIP MySQL (test environment): upserts,
 * cancels, the retry queue and the listing query. Each test removes the rows
 * it created.
 */
class SipTicketMirrorTest {

    private static final String DEVICE = "93c751e8-2c06-460e-8d68-31f5e4774b43";
    private static final long PROTOCOL_A = 990000001L;
    private static final long PROTOCOL_B = 990000002L;
    private static final long TICKET_A = 991000001L;
    private static final long TICKET_B = 991000002L;

    private SipDatabase db;
    private SipTicketMirror mirror;

    @BeforeEach
    void setUp() {
        db = SipTestDatabase.get();
        SipTestDatabase.cleanUp(PROTOCOL_A, PROTOCOL_B);
        mirror = new SipTicketMirror(db, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (mirror != null) {
            mirror.close();
        }
        SipTestDatabase.cleanUp(PROTOCOL_A, PROTOCOL_B);
    }

    private static CreateTicketRequest createRequest(long protocol) {
        return new CreateTicketRequest(DEVICE, protocol, "PROTO-" + protocol,
                "ILUMINACAO_FALHA", null, "teste automatizado", null, null, null, null);
    }

    private static TicketResponse response(long protocol, long ticket, String status) {
        return new TicketResponse(protocol, ticket, DEVICE, status);
    }

    private Map<String, Object> row(long protocol) {
        List<Map<String, Object>> rows = db.jdbc()
                .sql("SELECT * FROM exati_itg_ticket WHERE id_external_protocol = ?")
                .param(protocol).query().listOfRows();
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** The mirror writes asynchronously; give the flusher a moment. */
    private Map<String, Object> awaitRow(long protocol) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        Map<String, Object> row = row(protocol);
        while (row == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            row = row(protocol);
        }
        return row;
    }

    @Test
    void recordCreated_writesTheTicket() throws Exception {
        mirror.recordCreated(createRequest(PROTOCOL_A), response(PROTOCOL_A, TICKET_A, "PENDING"));

        Map<String, Object> row = awaitRow(PROTOCOL_A);
        assertThat(row).isNotNull();
        assertThat(((Number) row.get("id_ticket")).longValue()).isEqualTo(TICKET_A);
        assertThat(row.get("device_uuid")).isEqualTo(DEVICE);
        assertThat(row.get("service_code")).isEqualTo("ILUMINACAO_FALHA");
        assertThat(row.get("ticket_status")).isEqualTo("PENDING");
        assertThat(row.get("submitted_at")).isNotNull();
        assertThat(row.get("request_payload").toString()).contains("teste automatizado");
    }

    @Test
    void recordCreated_twice_updatesInsteadOfDuplicating() throws Exception {
        mirror.recordCreated(createRequest(PROTOCOL_A), response(PROTOCOL_A, TICKET_A, "PENDING"));
        assertThat(awaitRow(PROTOCOL_A)).isNotNull();

        // Exati answers 200 with the same ticket on an idempotent repeat.
        mirror.recordCreated(createRequest(PROTOCOL_A),
                response(PROTOCOL_A, TICKET_A, "IN_PROGRESS"));

        long deadline = System.currentTimeMillis() + 10_000;
        while (!"IN_PROGRESS".equals(row(PROTOCOL_A).get("ticket_status"))
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        Long count = db.jdbc()
                .sql("SELECT COUNT(*) FROM exati_itg_ticket WHERE id_external_protocol = ?")
                .param(PROTOCOL_A).query(Long.class).single();
        assertThat(count).isEqualTo(1);
        assertThat(row(PROTOCOL_A).get("ticket_status")).isEqualTo("IN_PROGRESS");
    }

    @Test
    void recordCancelled_updatesStatusAndJustification() throws Exception {
        mirror.recordCreated(createRequest(PROTOCOL_A), response(PROTOCOL_A, TICKET_A, "PENDING"));
        assertThat(awaitRow(PROTOCOL_A)).isNotNull();

        mirror.recordCancelled(new CancelTicketRequest(PROTOCOL_A, "cancelado no teste"),
                response(PROTOCOL_A, TICKET_A, "CANCELED"));

        long deadline = System.currentTimeMillis() + 10_000;
        while (!"CANCELED".equals(row(PROTOCOL_A).get("ticket_status"))
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        Map<String, Object> row = row(PROTOCOL_A);
        assertThat(row.get("ticket_status")).isEqualTo("CANCELED");
        assertThat(row.get("cancel_justification")).isEqualTo("cancelado no teste");
        assertThat(row.get("cancelled_at")).isNotNull();
    }

    @Test
    void recordCancelled_unknownProtocol_isDroppedWithoutError() throws Exception {
        mirror.recordCancelled(new CancelTicketRequest(PROTOCOL_B, "sem espelho"),
                response(PROTOCOL_B, TICKET_B, "CANCELED"));

        Thread.sleep(1_000);
        assertThat(row(PROTOCOL_B)).isNull();
        // A dropped cancel must not block later writes.
        mirror.recordCreated(createRequest(PROTOCOL_A), response(PROTOCOL_A, TICKET_A, "PENDING"));
        assertThat(awaitRow(PROTOCOL_A)).isNotNull();
    }

    @Test
    void writeFailure_isRetriedUntilItSucceeds() throws Exception {
        // A mirror pointed at a dead endpoint: the write must queue, not vanish.
        SipDatabase broken = new SipDatabase(
                new com.exati.itg.config.ItgProperties.Dev.Db("127.0.0.1", 1, "ami", "ami", "x"),
                new DirectConnectivity(new com.exati.itg.config.ItgProperties.Dev.Db(
                        "127.0.0.1", 1, "ami", "ami", "x")));
        try (SipTicketMirror failing = new SipTicketMirror(broken, new ObjectMapper())) {
            failing.recordCreated(createRequest(PROTOCOL_A),
                    response(PROTOCOL_A, TICKET_A, "PENDING"));
            Thread.sleep(1_500);
            assertThat(row(PROTOCOL_A)).as("nothing written while the database is down").isNull();
            assertThat(failing.pendingWrites()).as("write kept for retry").isEqualTo(1);
        } finally {
            broken.close();
        }
    }

    @Test
    void query_filtersAndPaginates() throws Exception {
        mirror.recordCreated(createRequest(PROTOCOL_A), response(PROTOCOL_A, TICKET_A, "PENDING"));
        assertThat(awaitRow(PROTOCOL_A)).isNotNull();

        Optional<TicketQueryResponse> byDevice = mirror.query(
                new TicketQuery(50, 1, DEVICE, null, null, null));
        assertThat(byDevice).isPresent();
        assertThat(byDevice.get().items())
                .extracting(TicketQueryResponse.Item::idExternalProtocol)
                .contains(PROTOCOL_A);

        Optional<TicketQueryResponse> byStatus = mirror.query(
                new TicketQuery(50, 1, DEVICE, TicketStatus.RESOLVED, null, null));
        assertThat(byStatus).isPresent();
        assertThat(byStatus.get().items())
                .extracting(TicketQueryResponse.Item::idExternalProtocol)
                .doesNotContain(PROTOCOL_A);

        Optional<TicketQueryResponse> byFutureDate = mirror.query(
                new TicketQuery(50, 1, DEVICE, null, LocalDate.now().plusDays(1), null));
        assertThat(byFutureDate).isPresent();
        assertThat(byFutureDate.get().items())
                .extracting(TicketQueryResponse.Item::idExternalProtocol)
                .doesNotContain(PROTOCOL_A);

        Optional<TicketQueryResponse> paged = mirror.query(
                new TicketQuery(1, 1, null, null, null, null));
        assertThat(paged).isPresent();
        assertThat(paged.get().items()).hasSizeLessThanOrEqualTo(1);
        assertThat(paged.get().limit()).isEqualTo(1);
        assertThat(paged.get().total()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void query_whenDatabaseUnreachable_isEmptySoCallerFallsBack() {
        SipDatabase broken = new SipDatabase(
                new com.exati.itg.config.ItgProperties.Dev.Db("127.0.0.1", 1, "ami", "ami", "x"),
                new DirectConnectivity(new com.exati.itg.config.ItgProperties.Dev.Db(
                        "127.0.0.1", 1, "ami", "ami", "x")));
        try (SipTicketMirror failing = new SipTicketMirror(broken, new ObjectMapper())) {
            assertThat(failing.query(new TicketQuery(10, 1, null, null, null, null))).isEmpty();
        } finally {
            broken.close();
        }
    }
}
