-- ============================================================================
-- exati_itg_ticket — copy of every solicitação (ticket) submitted to the
-- Exati IoT Hub by exati-itg, kept in the SIP `ami` schema.
--
-- Exati is the source of truth; this table is a best-effort mirror written by
-- the app AFTER Exati accepts a create/cancel, and re-synced by the periodic
-- recheck job (non-terminal rows only). See docs/INTEGRATION.md.
--
-- Run MANUALLY (the app never executes DDL). The mysql client lives on the
-- SSH VM, not on this laptop — pipe the script through ssh (password: see
-- Projects/EXATI/dump_schema.sh; quote it single-quoted, no space after -p):
--
--   ssh -i ../EXATI/hong_baiyi.pem hong_baiyi@3.88.22.232 \
--       "mysql -h 34.232.210.135 -u ami -p'<password>' ami" \
--       < docs/sql/exati_itg_ticket.sql
--
-- MySQL 5.7.23 (JSON type available). Safe to re-run: CREATE ... IF NOT EXISTS.
-- ============================================================================

CREATE TABLE IF NOT EXISTS exati_itg_ticket (
    -- Identity (both from the Exati create response)
    id_ticket            BIGINT UNSIGNED NOT NULL COMMENT 'Exati id_ticket',
    id_external_protocol BIGINT UNSIGNED NOT NULL COMMENT 'our protocol id, unique per ticket',

    -- What was requested (from the create body)
    device_uuid          CHAR(36)     NOT NULL COMMENT 'TALQ device address (e.g. ZENIX)',
    external_protocol    VARCHAR(128) NOT NULL,
    service_code         VARCHAR(64)  NOT NULL,
    request_payload      JSON         NOT NULL COMMENT 'full create body as sent, for audit',

    -- Lifecycle
    ticket_status        VARCHAR(32)  NOT NULL COMMENT 'DRAFT|PENDING|IN_PROGRESS|PARTIALLY_RESOLVED|RESOLVED|CANCELED (kept open for upstream additions)',
    cancel_justification VARCHAR(200) NULL,
    submitted_at         DATETIME     NOT NULL COMMENT 'when Exati accepted the create',
    cancelled_at         DATETIME     NULL     COMMENT 'when Exati accepted the cancel',
    last_status_at       DATETIME     NULL     COMMENT 'when ticket_status last changed',
    last_checked_at      DATETIME     NULL     COMMENT 'last recheck poll against Exati',

    -- Row bookkeeping
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id_ticket),
    UNIQUE KEY uk_exati_itg_ticket_ext_protocol (id_external_protocol),

    -- Recheck job: WHERE ticket_status NOT IN (terminal set)
    KEY idx_exati_itg_ticket_status (ticket_status),
    -- Query endpoint filters: deviceUuid, dateFrom/dateTo
    KEY idx_exati_itg_ticket_device (device_uuid),
    KEY idx_exati_itg_ticket_submitted (submitted_at)
)
ENGINE = InnoDB
DEFAULT CHARSET = utf8mb4
COLLATE = utf8mb4_unicode_ci
COMMENT = 'exati-itg: mirror of tickets submitted to the Exati IoT Hub (dev env)';
