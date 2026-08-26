package com.exati.itg.integration;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that path segments baked into the configured base URLs survive URI
 * resolution for the call styles used by the clients. DefaultUriBuilderFactory
 * is what RestClient.baseUrl(...) uses under the hood.
 *
 * <p>Critical for the Solicitações client: the certifier embeds the product
 * token in the path ({@code /tickets/<token>}), and every operation targets
 * that base URL directly — an empty template and a query-only builder must
 * both preserve it.
 */
class ExatiUriResolutionTest {

    private static final String TICKETS_BASE = "https://iotcertifier.exati.com.br:8443/tickets/TOKEN123";
    private static final String STAGING_BASE = "https://iot.exati.com.br/staging";

    @Test
    void emptyTemplate_resolvesToTokenBase() {
        DefaultUriBuilderFactory f = new DefaultUriBuilderFactory(TICKETS_BASE);
        URI u = f.expand("");
        assertThat(u.toString()).isEqualTo(TICKETS_BASE);
    }

    @Test
    void queryOnlyBuilder_preservesTokenPath() {
        DefaultUriBuilderFactory f = new DefaultUriBuilderFactory(TICKETS_BASE);
        URI u = f.builder().queryParam("limit", 50).queryParam("status", "PENDING").build();
        assertThat(u.toString()).isEqualTo(TICKETS_BASE + "?limit=50&status=PENDING");
    }

    @Test
    void callbackForm_preservesStagingPrefix() {
        // Legacy Tier 2 client (TalqResourceClient) — UriBuilder callback style.
        DefaultUriBuilderFactory f = new DefaultUriBuilderFactory(STAGING_BASE);
        URI u = f.builder().path("/talq/device-classes").build();
        assertThat(u.toString())
                .isEqualTo("https://iot.exati.com.br/staging/talq/device-classes");
    }
}
