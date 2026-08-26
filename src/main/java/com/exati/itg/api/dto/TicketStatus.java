package com.exati.itg.api.dto;

/**
 * Ticket lifecycle status — enum from the Exati IoT Hub Solicitações API
 * (https://iothub-solicitacoes.apidog.io). Used to validate the inbound
 * {@code status} query filter; responses keep the status as a plain string so
 * an upstream addition never breaks deserialization.
 */
public enum TicketStatus {
    DRAFT,
    PENDING,
    IN_PROGRESS,
    PARTIALLY_RESOLVED,
    RESOLVED,
    CANCELED
}
