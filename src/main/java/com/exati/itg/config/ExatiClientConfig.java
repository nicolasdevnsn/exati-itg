package com.exati.itg.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds the {@link RestClient}s used to call the Exati IoT Hub.
 *
 * <p>{@code exatiTicketsRestClient} targets the Solicitações (tickets) API —
 * on the certifier the base URL already embeds the product token
 * ({@code /tickets/<token>}) and the transport requires mTLS with the pinned
 * gateway leaf cert, supplied via a {@code spring.ssl.bundle} named by
 * {@code exati.tickets.ssl-bundle}.
 *
 * <p>{@code exatiRestClient} targets the DEPRECATED Tier&nbsp;2 staging resource
 * API and remains only for {@code TalqResourceClient}.
 *
 * <p>Base URLs, timeouts and auth all come from {@link ExatiProperties}. Auth is
 * applied as a single interceptor so swapping schemes later is a config change,
 * not a code change. {@code cache-control: no-transform} is set by default per
 * the TALQ spec (§3.5) so intermediaries never mutate payloads.
 */
@Configuration
@EnableConfigurationProperties(ExatiProperties.class)
@RequiredArgsConstructor
public class ExatiClientConfig {

    private final ExatiProperties props;

    /** Solicitações (tickets) API client — certifier, token-in-path, optional mTLS. */
    @Bean
    public RestClient exatiTicketsRestClient(SslBundles sslBundles) {
        ExatiProperties.Tickets tickets = props.tickets();

        HttpClient.Builder httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectMs()));
        if (tickets != null && StringUtils.hasText(tickets.sslBundle())) {
            httpClient.sslContext(sslBundles.getBundle(tickets.sslBundle()).createSslContext());
        }
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient.build());
        factory.setReadTimeout(Duration.ofMillis(readMs()));

        return RestClient.builder()
                .baseUrl(tickets != null ? tickets.baseUrl() : null)
                .requestFactory(factory)
                .requestInterceptor(authInterceptor())
                .defaultHeader("Cache-Control", "no-transform")
                .build();
    }

    /** DEPRECATED Tier 2 staging client — kept only for {@code TalqResourceClient}. */
    @Bean
    public RestClient exatiRestClient() {
        // JDK HttpClient factory — unlike SimpleClientHttpRequestFactory it allows a
        // request body on DELETE, which the tickets cancel endpoint requires.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readMs()));

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .requestInterceptor(authInterceptor())
                .defaultHeader("Cache-Control", "no-transform")
                .build();
    }

    private long connectMs() {
        return props.timeout() != null ? props.timeout().connectMs() : 5_000;
    }

    private long readMs() {
        return props.timeout() != null ? props.timeout().readMs() : 10_000;
    }

    /**
     * Attaches credentials based on {@code exati.auth.type}. A {@code none} (or
     * unset) type is a no-op — matching the published, security-less Solicitações
     * spec (identification travels as the token in the path plus the
     * {@code client-address} header), while leaving bearer / api-key ready to enable.
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
