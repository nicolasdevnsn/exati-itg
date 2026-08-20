package com.exati.itg.integration;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@code /staging} path segment in the configured base URL
 * survives URI resolution for BOTH call styles used by the clients:
 *  - template string form  (Tier 1 tickets, modifyDevice)
 *  - UriBuilder callback    (Tier 2 device-classes/devices)
 * DefaultUriBuilderFactory is what RestClient.baseUrl(...) uses under the hood.
 */
class ExatiUriResolutionTest {

    private static final String BASE = "https://iot.exati.com.br/staging";

    @Test
    void templateForm_preservesStagingPrefix() {
        DefaultUriBuilderFactory f = new DefaultUriBuilderFactory(BASE);
        URI u = f.expand("/vendors/talq/clients/{idInstance}/tickets", "69");
        assertThat(u.toString())
                .isEqualTo("https://iot.exati.com.br/staging/vendors/talq/clients/69/tickets");
    }

    @Test
    void callbackForm_preservesStagingPrefix() {
        DefaultUriBuilderFactory f = new DefaultUriBuilderFactory(BASE);
        URI u = f.builder().path("/talq/device-classes").build();
        assertThat(u.toString())
                .isEqualTo("https://iot.exati.com.br/staging/talq/device-classes");
    }
}
