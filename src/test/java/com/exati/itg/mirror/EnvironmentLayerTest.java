package com.exati.itg.mirror;

import org.junit.jupiter.api.Test;
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

    @Test
    void dev_wiresDevMirror() {
        runner.withPropertyValues("itg.env=dev")
                .run(ctx -> assertThat(ctx).getBean(TicketMirror.class)
                        .isInstanceOf(DevTicketMirror.class));
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
        runner.withPropertyValues("itg.env=DEV")
                .run(ctx -> assertThat(ctx).getBean(TicketMirror.class)
                        .isInstanceOf(DevTicketMirror.class));
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
