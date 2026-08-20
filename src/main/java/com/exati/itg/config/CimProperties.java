package com.exati.itg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the outbound CIM gateway (the external {@code ami-cim}
 * microservice described in {@code ami-cim-design-doc-en}).
 *
 * <p>{@code baseUrl} includes the gateway's REST prefix so the proxy only has to
 * append the caller's sub-path. Default targets {@code ami-cim} directly on its
 * documented port 18084; point it at {@code ami-zuul} (18088) or any host by
 * overriding {@code CIM_BASE_URL}.
 */
@ConfigurationProperties(prefix = "cim")
public record CimProperties(
        String baseUrl,
        Timeout timeout
) {

    /** HTTP timeouts in milliseconds. Read timeout is generous — device ops can be slow. */
    public record Timeout(
            long connectMs,
            long readMs
    ) {
    }
}
