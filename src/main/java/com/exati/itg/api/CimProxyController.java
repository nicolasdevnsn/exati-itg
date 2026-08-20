package com.exati.itg.api;

import com.exati.itg.config.CimProperties;
import com.exati.itg.exception.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Enumeration;
import java.util.Set;

/**
 * Transparent access layer over the external CIM gateway ({@code ami-cim}).
 *
 * <p>Everything under {@code /api/v1/cim/**} is forwarded verbatim to
 * {@code ${cim.base-url}/<rest-of-path>} — same HTTP method, query string, body,
 * and (non-hop-by-hop) headers, including {@code Authorization} so the gateway's
 * own auth (e.g. UDIL JWT) still applies. The upstream status, body and headers
 * are returned unchanged; nothing here interprets the CIM contract, so all
 * ~28 documented routes (and any future ones) are covered by this one handler.
 *
 * <p>Route catalog (from {@code ami-cim-design-doc-en} §7.10) lives in the
 * Postman collection / docs; this class deliberately stays contract-agnostic.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "CIM Gateway (proxy)", description = "Transparent passthrough to the ami-cim gateway under /api/v1/cim/**")
@SecurityRequirement(name = "bearerAuth")
public class CimProxyController {

    private static final String PREFIX = "/api/v1/cim/";

    /** Hop-by-hop / connection-management headers not to forward on the request. */
    private static final Set<String> REQUEST_SKIP = Set.of(
            "host", "content-length", "connection", "keep-alive", "transfer-encoding",
            "te", "trailer", "upgrade", "proxy-authenticate", "proxy-authorization",
            "accept-encoding");

    /** Response headers the servlet container must set itself. */
    private static final Set<String> RESPONSE_SKIP = Set.of(
            "content-length", "connection", "keep-alive", "transfer-encoding");

    private final RestClient cimRestClient;
    private final CimProperties props;

    @RequestMapping("/api/v1/cim/**")
    @Operation(summary = "Forward any request to the CIM gateway (method/path/query/body/headers preserved)")
    public ResponseEntity<byte[]> proxy(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {

        String targetUrl = buildTargetUrl(request);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        RestClient.RequestBodySpec spec = cimRestClient.method(method)
                .uri(URI.create(targetUrl));
        spec.headers(h -> copyRequestHeaders(request, h));
        if (body != null && body.length > 0) {
            spec.body(body);
        }

        try {
            return spec.exchange((req, res) -> {
                byte[] responseBody = StreamUtils.copyToByteArray(res.getBody());
                HttpHeaders out = new HttpHeaders();
                res.getHeaders().forEach((name, values) -> {
                    if (!RESPONSE_SKIP.contains(name.toLowerCase())) {
                        out.put(name, values);
                    }
                });
                return ResponseEntity.status(res.getStatusCode()).headers(out).body(responseBody);
            });
        } catch (ResourceAccessException ex) {
            log.error("CIM gateway unreachable at {}", props.baseUrl(), ex);
            throw ApiException.badGateway("CIM gateway is unreachable.");
        }
    }

    private String buildTargetUrl(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int idx = uri.indexOf(PREFIX);
        String rest = idx >= 0 ? uri.substring(idx + PREFIX.length()) : "";
        String query = request.getQueryString();
        return props.baseUrl()
                + (rest.isEmpty() ? "" : "/" + rest)
                + (query != null ? "?" + query : "");
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (REQUEST_SKIP.contains(name.toLowerCase())) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
    }
}
