-- device_integration_status — TALQ integration state per luminaire.
-- Target: AMI MySQL 5.7, schema `ami` (run manually there; this app itself only
-- reaches that DB through ami-cim, so this is NOT a Flyway migration).
--
-- FK: archive_meter.meter_no is varchar(32) with a UNIQUE key
-- (UK_qutm4wdjyilyv7tpdda8g6b0k), so InnoDB accepts it as an FK target.
-- No explicit charset here on purpose — the table inherits the schema default,
-- which matches the vendor tables. If the FK fails with errno 150, align the
-- charset/collation with `SHOW CREATE TABLE archive_meter` and retry.

USE ami;

CREATE TABLE device_integration_status (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    meter_no      VARCHAR(32) NOT NULL,
    integrated    TINYINT(1)  NOT NULL DEFAULT 0,
    integrated_at DATETIME    NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_devintstat_meter_no (meter_no),
    CONSTRAINT fk_devintstat_meter_no FOREIGN KEY (meter_no)
        REFERENCES archive_meter (meter_no)
) ENGINE=InnoDB;
