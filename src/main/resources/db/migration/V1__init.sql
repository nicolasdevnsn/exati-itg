-- V1: bootstrap schema. Replace / extend as the domain takes shape.
-- Flyway is the source of truth — Hibernate ddl-auto is "none" in application.yml.

CREATE TABLE IF NOT EXISTS app_metadata (
    id      BIGINT       NOT NULL PRIMARY KEY,
    version VARCHAR(64)  NOT NULL,
    created TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
