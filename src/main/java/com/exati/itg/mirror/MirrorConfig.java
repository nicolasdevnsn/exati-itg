package com.exati.itg.mirror;

import com.exati.itg.config.ItgEnvironment;
import com.exati.itg.config.ItgProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the {@link TicketMirror} implementation for the configured
 * {@code itg.env}. Exhaustive switch: a new {@link ItgEnvironment} constant
 * won't compile until it is mapped here — the deliberate extension point for
 * future environments.
 */
@Configuration
@EnableConfigurationProperties(ItgProperties.class)
public class MirrorConfig {

    @Bean
    public TicketMirror ticketMirror(ItgProperties props) {
        ItgEnvironment env = props.env();
        if (env == null) {
            throw new IllegalStateException(
                    "itg.env is not set — declare the environment (ITG_ENV=dev|qa|prod)");
        }
        return switch (env) {
            case DEV -> new DevTicketMirror();
            case QA, PROD -> new NoOpTicketMirror(env);
        };
    }
}
