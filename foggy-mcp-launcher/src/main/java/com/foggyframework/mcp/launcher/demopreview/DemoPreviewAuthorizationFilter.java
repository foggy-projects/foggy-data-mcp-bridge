package com.foggyframework.mcp.launcher.demopreview;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/** Converts the HttpOnly browser session to the opaque Runtime identity for its bound QM only. */
public final class DemoPreviewAuthorizationFilter extends OncePerRequestFilter {

    private final DemoPreviewSessionService sessions;
    private final DemoPreviewProperties properties;

    public DemoPreviewAuthorizationFilter(DemoPreviewSessionService sessions, DemoPreviewProperties properties) {
        this.sessions = sessions;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/data-viewer/api/preview/session")
                || path.equals("/data-viewer/api/preview/logout")
                || !path.startsWith("/data-viewer/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String sessionToken = cookie(request);
        if (sessionToken == null) {
            rejectUnauthorized(response);
            return;
        }
        String namespace = header(request, "X-NS");
        if (!sessions.allowsViewerRequest(sessionToken, request.getMethod(), request.getRequestURI(), namespace)) {
            rejectUnauthorized(response);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Vary", "Cookie");
        response.setHeader("Referrer-Policy", "no-referrer");
        filterChain.doFilter(withHeaders(request, sessions.toRuntimeAuthorization(sessionToken),
                sessions.getSession(sessionToken).namespace()), response);
    }

    private void rejectUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Vary", "Cookie");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"需要有效的预览登录会话\"}");
    }

    private HttpServletRequest withHeaders(HttpServletRequest request, String authorization, String namespace) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if (name.equalsIgnoreCase("Authorization")) return authorization;
                if (name.equalsIgnoreCase("X-NS") && namespace != null) return namespace;
                return authorization == null && name.equalsIgnoreCase("Authorization") ? null : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                String value = getHeader(name);
                return value == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(value));
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
                names.removeIf(value -> value.equalsIgnoreCase("Authorization") || value.equalsIgnoreCase("X-NS"));
                if (authorization != null) names.add("Authorization");
                if (namespace != null) names.add("X-NS");
                return Collections.enumeration(names);
            }
        };
    }

    private String cookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (properties.getCookieName().equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private String header(HttpServletRequest request, String name) {
        return request.getHeader(name);
    }
}
