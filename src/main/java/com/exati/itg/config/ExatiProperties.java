package com.exati.itg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the outbound Exati IoT Hub clients.
 *
 * <p>Bound from the {@code exati.*} tree in {@code application.yml}. All values
 * are environment-overridable so the same jar runs against the certifier,
 * staging and prod.
 *
 * <p>Two independent upstreams live here:
 * <ul>
 *   <li>{@code exati.tickets.*} — the Solicitações (tickets) API
 *       (https://iothub-solicitacoes.apidog.io). On the certifier the product
 *       token is embedded in the base URL path ({@code /tickets/<token>}).</li>
 *   <li>{@code exati.base-url} — the DEPRECATED Tier&nbsp;2 staging resource API
 *       still referenced by {@link com.exati.itg.integration.TalqResourceClient};
 *       kept only until that client is removed.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "exati")
public record ExatiProperties(
        String baseUrl,
        String clientAddress,
        Tickets tickets,
        Auth auth,
        Timeout timeout
) {

    /**
     * Solicitações API. {@code clientAddress} is the announced TALQ gateway UUID,
     * sent as the {@code client-address} header on create/cancel. {@code sslBundle}
     * names a {@code spring.ssl.bundle} carrying the gateway leaf cert for mTLS
     * (the certifier requires it); empty disables client certs.
     */
    public record Tickets(
            String baseUrl,
            String clientAddress,
            String sslBundle
    ) {
    }

    /** Pluggable outbound auth. {@code type} is one of {@code none|bearer|apikey}. */
    public record Auth(
            String type,
            String token,
            String headerName,
            String apiKey
    ) {
    }

    /** HTTP timeouts in milliseconds. */
    public record Timeout(
            long connectMs,
            long readMs
    ) {
    }
}
