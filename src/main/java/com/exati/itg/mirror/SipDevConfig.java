package com.exati.itg.mirror;

import com.exati.itg.config.DevEnvironmentCondition;
import com.exati.itg.config.ItgProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Dev-only SIP infrastructure: the transport to the SIP database and the
 * MySQL access on top of it. None of these beans exist in qa/prod — there is
 * no access path there, and there must be no way to accidentally reach the
 * dev tunnel.
 *
 * <p>This is the ONLY place that knows which {@link SipDatabaseConnectivity}
 * implementations exist; everything else depends on the interface.
 */
@Configuration
@Conditional(DevEnvironmentCondition.class)
@EnableConfigurationProperties(ItgProperties.class)
@Slf4j
public class SipDevConfig {

    @Bean(destroyMethod = "close")
    public SipDatabaseConnectivity sipConnectivity(ItgProperties props) {
        if (!StringUtils.hasText(props.dev().db().password())) {
            log.warn("ITG_DEV_DB_PASSWORD is not set — the SIP mirror database "
                    + "will refuse connections until it is provided");
        }
        ItgProperties.Dev.Access access = props.dev().access();
        if (access == null) {
            throw new IllegalStateException(
                    "itg.dev.access is not set — declare the SIP database transport "
                            + "(ITG_DEV_DB_ACCESS=tunnel|direct)");
        }
        return switch (access) {
            case TUNNEL -> new SshTunnelConnectivity(props.dev());
            case DIRECT -> new DirectConnectivity(props.dev().db());
        };
    }

    @Bean(destroyMethod = "close")
    public SipDatabase sipDatabase(ItgProperties props, SipDatabaseConnectivity connectivity) {
        return new SipDatabase(props.dev().db(), connectivity);
    }

    @Bean(destroyMethod = "close")
    public TicketRecheckJob ticketRecheckJob(ItgProperties props, SipDatabase db,
                                             com.exati.itg.integration.ExatiTicketsClient exatiClient) {
        return new TicketRecheckJob(db, exatiClient, props.dev().recheck());
    }

    /** Shows up under /actuator/health as "sipMirror". */
    @Bean
    public HealthIndicator sipMirrorHealthIndicator(SipDatabaseConnectivity connectivity,
                                                    SipDatabase db) {
        return () -> {
            if (!connectivity.isUp()) {
                return Health.down().withDetail("transport", "down").build();
            }
            try {
                db.ping();
                return Health.up().withDetail("transport", "up").build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("transport", "up")
                        .withDetail("database", e.getMessage())
                        .build();
            }
        };
    }
}
