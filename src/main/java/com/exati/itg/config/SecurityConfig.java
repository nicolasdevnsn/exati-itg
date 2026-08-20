package com.exati.itg.config;

import com.exati.itg.api.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        // Auth endpoints — open
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Ops — health/info/prometheus stay open; secure the rest if needed later
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        // API docs
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        // H2 console (dev only — frame-options also relaxed below)
                        .requestMatchers("/h2-console/**").permitAll()
                        // Everything else requires a valid JWT.
                        // TODO(auth): TEMPORARILY DISABLED — all routes are currently open (no JWT
                        // required). The JwtAuthFilter still runs and honours a token if one is sent;
                        // this line is what enforced auth. To re-enable, restore the line below and
                        // delete the `.anyRequest().permitAll()` line that replaces it.
                        // .anyRequest().authenticated())
                        .anyRequest().permitAll())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> writeProblem(res,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "Authentication required."))
                        .accessDeniedHandler((req, res, ex) -> writeProblem(res,
                                HttpServletResponse.SC_FORBIDDEN,
                                "Access denied.")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeProblem(HttpServletResponse res, int status, String detail) throws java.io.IOException {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.valueOf(status), detail);
        body.setProperty("timestamp", Instant.now());

        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(res.getWriter(), body);
    }
}
