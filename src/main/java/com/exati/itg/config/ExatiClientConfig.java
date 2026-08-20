package com.exati.itg.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds the {@link RestClient} used to call the Exati IoT Hub TALQ Tier&nbsp;1 API.
 *
 * <p>Base URL, timeouts and auth all come from {@link ExatiProperties}. Auth is
 * applied as a single interceptor so swapping schemes later is a config change,
 * not a code change. {@code cache-control: no-transform} is set by default per
 * the TALQ spec (§3.5) so intermediaries never mutate payloads.
 */
@Configuration
@EnableConfigurationProperties(ExatiProperties.class)
@RequiredArgsConstructor
public class ExatiClientConfig {

    private final ExatiProperties props;

    @Bean
    public RestClient exatiRestClient() {
        ExatiProperties.Timeout t = props.timeout();
        long connectMs = t != null ? t.connectMs() : 5_000;
        long readMs = t != null ? t.readMs() : 10_000;

        // JDK HttpClient factory — unlike SimpleClientHttpRequestFactory it allows a
        // request body on DELETE, which the Tier 1 cancel endpoint requires.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readMs));

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .requestInterceptor(authInterceptor())
                .defaultHeader("Cache-Control", "no-transform")
                .build();
    }

    /**
     * Attaches credentials based on {@code exati.auth.type}. A {@code none} (or
     * unset) type is a no-op — matching the currently-published, security-less
     * Tier&nbsp;1 spec, while leaving bearer / api-key ready to enable.
     */
    private ClientHttpRequestInterceptor authInterceptor() {
        ExatiProperties.Auth auth = props.auth();
        return (request, body, execution) -> {
            if (auth != null && auth.type() != null) {
                switch (auth.type().toLowerCase()) {
                    case "bearer" -> {
                        if (StringUtils.hasText(auth.token())) {
                            request.getHeaders().setBearerAuth(auth.token());
                        }
                    }
                    case "apikey" -> {
                        if (StringUtils.hasText(auth.headerName()) && StringUtils.hasText(auth.apiKey())) {
                            request.getHeaders().add(auth.headerName(), auth.apiKey());
                        }
                    }
                    default -> {
                        // "none" — send no credentials.
                    }
                }
            }
            return execution.execute(request, body);
        };
    }
}
