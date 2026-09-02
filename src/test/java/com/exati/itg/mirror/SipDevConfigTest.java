package com.exati.itg.mirror;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SIP infrastructure beans (tunnel, database, health) must exist only in
 * the dev environment — in qa/prod there is no access path and none may be
 * created accidentally.
 */
class SipDevConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SipDevConfig.class)
            .withBean(com.exati.itg.integration.ExatiTicketsClient.class,
                    () -> org.mockito.Mockito.mock(com.exati.itg.integration.ExatiTicketsClient.class))
            .withPropertyValues(
                    "itg.dev.recheck.period-minutes=15",
                    "itg.dev.recheck.terminal-statuses=RESOLVED,CANCELED",
                    "itg.dev.recheck.expire-days=60",
                    // Unreachable dummy endpoints: bean creation must succeed
                    // without any live SSH/DB (connections are lazy/retried).
                    "itg.dev.access=tunnel",
                    "itg.dev.ssh.host=127.0.0.1",
                    "itg.dev.ssh.port=1",
                    "itg.dev.ssh.user=test",
                    "itg.dev.ssh.key-path=does-not-exist.pem",
                    "itg.dev.ssh.local-port=0",
                    "itg.dev.db.host=127.0.0.1",
                    "itg.dev.db.port=1",
                    "itg.dev.db.schema=ami",
                    "itg.dev.db.username=ami",
                    "itg.dev.db.password=");

    @Test
    void dev_tunnelMode_createsSipInfrastructure() {
        runner.withPropertyValues("itg.env=dev").run(ctx -> {
            assertThat(ctx).getBean(SipDatabaseConnectivity.class)
                    .isInstanceOf(SshTunnelConnectivity.class);
            assertThat(ctx).hasSingleBean(SipDatabase.class);
            assertThat(ctx).hasSingleBean(HealthIndicator.class);
        });
    }

    @Test
    void dev_directMode_wiresDirectConnectivity() {
        runner.withPropertyValues("itg.env=dev", "itg.dev.access=direct").run(ctx -> {
            assertThat(ctx).getBean(SipDatabaseConnectivity.class)
                    .isInstanceOf(DirectConnectivity.class);
            assertThat(ctx).hasSingleBean(SipDatabase.class);
        });
    }

    @Test
    void qa_createsNoSipInfrastructure() {
        runner.withPropertyValues("itg.env=qa").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(SipDatabaseConnectivity.class);
            assertThat(ctx).doesNotHaveBean(SipDatabase.class);
        });
    }

    @Test
    void prod_createsNoSipInfrastructure() {
        runner.withPropertyValues("itg.env=prod").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(SipDatabaseConnectivity.class);
            assertThat(ctx).doesNotHaveBean(SipDatabase.class);
        });
    }

    @Test
    void conditionIsCaseInsensitive() {
        runner.withPropertyValues("itg.env=DEV").run(ctx ->
                assertThat(ctx).hasSingleBean(SipDatabaseConnectivity.class));
    }
}
