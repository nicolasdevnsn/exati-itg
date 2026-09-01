package com.exati.itg.config;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches only when {@code itg.env} binds to {@link ItgEnvironment#DEV}.
 *
 * <p>Used instead of {@code @ConditionalOnProperty(havingValue = "dev")}
 * because that compares raw strings case-sensitively, while the enum binding
 * accepts {@code DEV}/{@code dev} — the two must never disagree about which
 * environment is active.
 */
public class DevEnvironmentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return Binder.get(context.getEnvironment())
                .bind("itg.env", ItgEnvironment.class)
                .map(env -> env == ItgEnvironment.DEV)
                .orElse(false);
    }
}
