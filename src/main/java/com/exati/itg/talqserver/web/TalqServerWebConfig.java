package com.exati.itg.talqserver.web;

import com.exati.itg.talqserver.TalqServerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Mounts the TALQ gateway-server surface. The routes live at the context root
 * because that is what the announced {@code gatewayUri} points at — the CMS
 * composes {@code <gatewayUri>/devices}, {@code <gatewayUri>/groups}, etc.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TalqServerProperties.class)
public class TalqServerWebConfig implements WebMvcConfigurer {

    /** Every TALQ resource root served by this gateway. */
    public static final List<String> TALQ_PATHS = List.of(
            "/devices/**", "/device-classes/**", "/services/**",
            "/groups/**", "/calendars/**", "/control-programs/**",
            "/assign-commands/**", "/override-commands/**",
            "/logger-configs/**", "/log-reports/**", "/data-packages/**",
            "/lamp-types/**", "/luminaire-types/**", "/driver-types/**",
            "/controller-types/**", "/bracket-types/**");

    private final TalqRequestGuard guard;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guard).addPathPatterns(TALQ_PATHS);
    }

    @Bean
    public FilterRegistrationBean<DoubleSlashNormalizerFilter> doubleSlashNormalizer() {
        var registration = new FilterRegistrationBean<>(new DoubleSlashNormalizerFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
