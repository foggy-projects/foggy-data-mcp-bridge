package com.foggyframework.runtime.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.dto.RuntimeError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

@Component
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeApiAuthInterceptor implements HandlerInterceptor {

    public static final String AUTH_CODE_HEADER = "X-Foggy-Runtime-Code";

    private static final String ENGINE = "java";
    private static final String PHASE = "runtime.auth";
    private static final String BEARER_PREFIX = "Bearer ";

    private final FoggyRuntimeApiProperties properties;
    private final ObjectMapper objectMapper;

    public RuntimeApiAuthInterceptor(FoggyRuntimeApiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!isProtectedOperation(request) || !properties.isAuthCodeRequired()) {
            return true;
        }
        if (!properties.isAuthCodeConfigured()) {
            writeRejected(
                    request,
                    response,
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "RUNTIME_AUTH_CODE_NOT_CONFIGURED",
                    "Runtime API auth-code mode is enabled, but no auth code is configured.",
                    "Set foggy.runtime-api.auth-code before calling runtime management operations."
            );
            return false;
        }
        if (!authCodeMatches(extractSubmittedCode(request))) {
            writeRejected(
                    request,
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "RUNTIME_AUTH_REQUIRED",
                    "Runtime API management operation requires a valid auth code.",
                    "Pass the auth code through X-Foggy-Runtime-Code or Authorization: Bearer."
            );
            return false;
        }
        return true;
    }

    private boolean isProtectedOperation(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String pattern = bestMatchingPattern(request);
        if (isPatternProtected(pattern, method)) {
            return true;
        }
        return isPathProtected(normalizedRequestPath(request), method);
    }

    private String bestMatchingPattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : null;
    }

    private boolean isPatternProtected(String pattern, String method) {
        if (!StringUtils.hasText(pattern)) {
            return false;
        }
        return switch (pattern) {
            case "/api/v1/bundles" -> "POST".equals(method);
            case "/api/v1/bundles/{name}" -> "PUT".equals(method) || "DELETE".equals(method);
            case "/api/v1/datasources" -> "POST".equals(method);
            case "/api/v1/datasources/{name}" -> "PUT".equals(method) || "DELETE".equals(method);
            case "/api/v1/datasources/{name}/test" -> "POST".equals(method);
            case "/api/v1/namespaces/{namespace}/datasource" -> "PUT".equals(method);
            case "/api/v1/resources/save", "/api/v1/models/validate", "/api/v1/models/refresh" -> "POST".equals(method);
            case "/api/bundles/add", "/api/bundles/remove/{bundleName}" -> "POST".equals(method) || "DELETE".equals(method);
            default -> false;
        };
    }

    private String normalizedRequestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private boolean isPathProtected(String path, String method) {
        if ("/api/v1/bundles".equals(path)) {
            return "POST".equals(method);
        }
        if (path.matches("^/api/v1/bundles/[^/]+$")) {
            return "PUT".equals(method) || "DELETE".equals(method);
        }
        if ("/api/v1/datasources".equals(path)) {
            return "POST".equals(method);
        }
        if (path.matches("^/api/v1/datasources/[^/]+$")) {
            return "PUT".equals(method) || "DELETE".equals(method);
        }
        if (path.matches("^/api/v1/datasources/[^/]+/test$")) {
            return "POST".equals(method);
        }
        if (path.matches("^/api/v1/namespaces/[^/]+/datasource$")) {
            return "PUT".equals(method);
        }
        if ("/api/v1/resources/save".equals(path)
                || "/api/v1/models/validate".equals(path)
                || "/api/v1/models/refresh".equals(path)) {
            return "POST".equals(method);
        }
        if ("/api/bundles/add".equals(path)) {
            return "POST".equals(method);
        }
        if (path.matches("^/api/bundles/remove/[^/]+$")) {
            return "DELETE".equals(method);
        }
        return false;
    }

    private String extractSubmittedCode(HttpServletRequest request) {
        String headerCode = request.getHeader(AUTH_CODE_HEADER);
        if (StringUtils.hasText(headerCode)) {
            return headerCode;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization)
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private boolean authCodeMatches(String submittedCode) {
        if (!StringUtils.hasText(submittedCode)) {
            return false;
        }
        byte[] expected = properties.getAuthCode().trim().getBytes(StandardCharsets.UTF_8);
        byte[] actual = submittedCode.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private void writeRejected(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message,
            String suggestedNextAction
    ) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        RuntimeError error = new RuntimeError(
                code,
                PHASE,
                message,
                null,
                null,
                normalizedRequestPath(request),
                suggestedNextAction,
                false
        );
        RuntimeEnvelope<Object> envelope = RuntimeEnvelope.fail(
                ENGINE,
                properties.getRuntimeApiVersion(),
                error,
                RuntimeDiagnostics.empty()
        );
        objectMapper.writeValue(response.getWriter(), envelope);
    }
}
