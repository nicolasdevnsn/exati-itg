package com.exati.itg.talqserver.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Collapses duplicate slashes in the request path and forwards to the clean
 * path. The EXATI certifier's CMS stores our {@code gatewayUri} with a
 * trailing slash regardless of what we announce (verified 2026-08-19: calls
 * arrive as {@code //devices}, {@code //seed/…}) and Spring Security's strict
 * firewall rejects un-normalized paths with 400 before routing. Must run
 * BEFORE the security chain; the forwarded request skips it, which is
 * acceptable while every TALQ route is permitAll (transport auth is mTLS).
 */
public class DoubleSlashNormalizerFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var uri = request.getRequestURI();
        if (uri.contains("//")) {
            var normalized = uri.replaceAll("/{2,}", "/");
            var query = request.getQueryString();
            request.getRequestDispatcher(query == null ? normalized : normalized + "?" + query)
                    .forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}
