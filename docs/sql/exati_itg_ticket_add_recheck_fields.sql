-- Adds the fields the recheck job syncs from the Exati listing (phase 5).
-- For tables created before 2026-09-01; the canonical exati_itg_ticket.sql
-- already includes them. Run like the create script:
--
--   ssh -i ../EXATI/hong_baiyi.pem hong_baiyi@3.88.22.232 \
--       "mysql -h 34.232.210.135 -u ami -p'<password>' ami" \
--       < docs/sql/exati_itg_ticket_add_recheck_fields.sql

ALTER TABLE exati_itg_ticket
    ADD COLUMN reported_at    DATETIME     NULL COMMENT 'reported_at from the Exati listing'    AFTER cancelled_at,
    ADD COLUMN closed_at      DATETIME     NULL COMMENT 'closed_at from the Exati listing'      AFTER reported_at,
    ADD COLUMN closing_reason VARCHAR(255) NULL COMMENT 'closing_reason from the Exati listing' AFTER closed_at;
