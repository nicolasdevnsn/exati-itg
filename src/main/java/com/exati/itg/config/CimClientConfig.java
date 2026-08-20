package com.exati.itg.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds the {@link RestClient} the CIM proxy uses to forward requests to the
 * external gateway. No base URL is set here — the proxy forwards absolute URIs
 * it builds from {@link CimProperties}, so nothing gets re-encoded. HTTP/1.1 is
 * pinned to match the gateway's stack.
 */
@Configuration
@EnableConfigurationProperties(CimProperties.class)
@RequiredArgsConstructor
public class CimClientConfig {

    private final CimProperties props;

    @Bean
    public RestClient cimRestClient() {
        CimProperties.Timeout t = props.timeout();
        long connectMs = t != null ? t.connectMs() : 5_000;
        long readMs = t != null ? t.readMs() : 30_000;

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readMs));

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
