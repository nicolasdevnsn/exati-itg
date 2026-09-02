package com.exati.itg.mirror;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the environment layer: itg.env selects the {@link TicketMirror}
 * implementation, and anything other than a declared environment fails
 * startup — a deploy must never silently inherit dev behavior (and with it
 * the dev SSH tunnel).
 */
class EnvironmentLayerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MirrorConfig.class);

    /** Dev needs the SIP beans (SipDevConfig) plus an ObjectMapper. */
    private final ApplicationContextRunner devRunner = runner
            .withUserConfiguration(SipDevConfig.class, JacksonAutoConfiguration.class)
            .withBean(com.exati.itg.integration.ExatiTicketsClient.class,
                    () -> org.mockito.Mockito.mock(com.exati.itg.integration.ExatiTicketsClient.class))
            .withPropertyValues(
                    "itg.dev.recheck.period-minutes=15",
                    "itg.dev.recheck.terminal-statuses=RESOLVED,CANCELED",
                    "itg.dev.recheck.expire-days=60",
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
                    "itg.dev.db.password=x");

    @Test
    void dev_wiresSipMirror() {
        devRunner.withPropertyValues("itg.env=dev")
                .run(ctx -> assertThat(ctx).getBean(TicketMirror.class)
                        .isInstanceOf(SipTicketMirror.class));
    }

    @Test
    void qa_wiresNoOpMirror() {
        runner.withPropertyValues("itg.env=qa")
                .run(ctx -> assertThat(ctx).getBean(TicketMirror.class)
                        .isInstanceOf(NoOpTicketMirror.class));
    }

    @Test
    void prod_wiresNoOpMirror() {
        runner.withPropertyValues("itg.env=prod")
                .run(ctx -> assertThat(ctx).getBean(TicketMirror.class)
                        .isInstanceOf(NoOpTicketMirror.class));
    }

    @Test
    void bindingIsCaseInsensitive() {
        devRunner.withPropertyValues("itg.env=DEV")
                .run(ctx -> assertThat(ctx).getBean(TicketMirror.class)
                        .isInstanceOf(SipTicketMirror.class));
    }

    @Test
    void unknownEnvironment_failsStartup() {
        runner.withPropertyValues("itg.env=staging")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void missingEnvironment_failsStartup() {
        runner.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("itg.env is not set");
        });
    }
}
