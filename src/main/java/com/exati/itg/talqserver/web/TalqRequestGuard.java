package com.exati.itg.talqserver.web;

import com.exati.itg.talqserver.TalqApiException;
import com.exati.itg.talqserver.TalqServerProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.UUID;

/**
 * Cross-cutting TALQ request rules (spec §3.3): every CMS request carries the
 * {@code talq-api-version} header and a {@code clientAddress} equal to the CMS
 * address; every state-changing request additionally carries a
 * {@code talqRequestId} UUID. Violations answer 400 with a TALQ error array
 * (via {@link TalqServerAdvice}).
 */
@Component
@RequiredArgsConstructor
public class TalqRequestGuard implements HandlerInterceptor {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final TalqServerProperties props;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var version = request.getHeader("talq-api-version");
        if (version == null || version.isBlank()) {
            throw TalqApiException.badRequest("required header 'talq-api-version' is missing");
        }
        var clientAddress = request.getParameter("clientAddress");
        if (clientAddress == null || clientAddress.isBlank()) {
            throw TalqApiException.badRequest("required parameter 'clientAddress' is missing");
        }
        if (!props.cmsAddress().equals(clientAddress)) {
            throw TalqApiException.badRequest(
                    "'clientAddress' must be the CMS address this gateway is bootstrapped to");
        }
        if (MUTATING.contains(request.getMethod())) {
            var requestId = request.getParameter("talqRequestId");
            if (requestId == null || requestId.isBlank()) {
                throw TalqApiException.badRequest("required parameter 'talqRequestId' is missing");
            }
            try {
                UUID.fromString(requestId);
            } catch (IllegalArgumentException e) {
                throw TalqApiException.badRequest("'talqRequestId' must be a UUID");
            }
        }
        return true;
    }
}
