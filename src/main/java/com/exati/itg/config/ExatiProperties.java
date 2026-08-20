package com.exati.itg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the outbound Exati IoT Hub (TALQ Tier&nbsp;1) client.
 *
 * <p>Bound from the {@code exati.*} tree in {@code application.yml}. All values
 * are environment-overridable so the same jar runs against staging and prod.
 *
 * <p><b>Auth note:</b> the published Tier&nbsp;1 OpenAPI declares {@code security: []}
 * (no scheme). That almost certainly does not reflect production, where auth is
 * expected at a gateway or via a global scheme not rendered per-endpoint. The
 * {@link Auth} block is therefore pluggable — flip {@code exati.auth.type} to
 * {@code bearer} or {@code apikey} once Exati confirms the real scheme.
 */
@ConfigurationProperties(prefix = "exati")
public record ExatiProperties(
        String baseUrl,
        String idInstance,
        String clientAddress,
        Auth auth,
        Timeout timeout
) {

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
