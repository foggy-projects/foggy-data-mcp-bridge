package com.foggyframework.analytics.console;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Rejects cross-site form submissions before they reach host-managed Console APIs. */
final class AnalyticsConsoleRequestGuardFilter extends HttpFilter {

    static final String REQUEST_HEADER = "X-Foggy-Analytics-Console-Request";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected void doFilter(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (SAFE_METHODS.contains(request.getMethod())
                || "1".equals(request.getHeader(REQUEST_HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"success\":false,\"data\":null,\"error\":{"
                        + "\"code\":\"ANALYTICS_CONSOLE_REQUEST_FORBIDDEN\","
                        + "\"message\":\"Analytics Console request header is required\"}}"
        );
    }
}
