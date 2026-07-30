package com.foggyframework.runtime.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.runtime.api.RuntimeApiRoutes;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
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

    private static final String PHASE = "runtime.auth";
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final FoggyRuntimeApiProperties properties;
    private final RuntimeApiResponseFactory responses;
    private final ObjectMapper objectMapper;

    public RuntimeApiAuthInterceptor(
            FoggyRuntimeApiProperties properties,
            RuntimeApiResponseFactory responses,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.responses = responses;
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
                    "Pass the management auth code through X-Foggy-Runtime-Code."
            );
            return false;
        }
        return true;
    }

    private boolean isProtectedOperation(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = normalizedRequestPath(request);
        if (RuntimeApiRoutes.Full.ACCESS_CHECK.equals(path)) {
            return true;
        }
        if (properties.isManagementAllAuthScope()
                && (RuntimeApiRoutes.API_V1.equals(path) || pathMatcher.match(RuntimeApiRoutes.API_V1_PATTERN, path))) {
            return true;
        }
        String pattern = bestMatchingPattern(request);
        if (isPatternProtected(pattern, method)) {
            return true;
        }
        return isPathProtected(path, method);
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
            case RuntimeApiRoutes.Full.BUNDLES -> "POST".equals(method);
            case RuntimeApiRoutes.Full.BUNDLE_BY_NAME -> "PUT".equals(method) || "DELETE".equals(method);
            case RuntimeApiRoutes.Full.DATASOURCES -> "POST".equals(method);
            case RuntimeApiRoutes.Full.DATASOURCE_BY_NAME -> "PUT".equals(method) || "DELETE".equals(method);
            case RuntimeApiRoutes.Full.DATASOURCE_TEST -> "POST".equals(method);
            case RuntimeApiRoutes.Full.NAMESPACE_DATASOURCE -> "PUT".equals(method);
            case RuntimeApiRoutes.Full.RESOURCES_SAVE,
                 RuntimeApiRoutes.Full.MODELS_VALIDATE,
                 RuntimeApiRoutes.Full.MODELS_REFRESH,
                 RuntimeApiRoutes.Full.FSSCRIPT_EXECUTE -> "POST".equals(method);
            case RuntimeApiRoutes.Full.LEGACY_BUNDLE_ADD,
                 RuntimeApiRoutes.Full.LEGACY_BUNDLE_REMOVE -> "POST".equals(method) || "DELETE".equals(method);
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
        if (RuntimeApiRoutes.Full.BUNDLES.equals(path)) {
            return "POST".equals(method);
        }
        if (pathMatcher.match(RuntimeApiRoutes.Full.BUNDLE_BY_NAME, path)) {
            return "PUT".equals(method) || "DELETE".equals(method);
        }
        if (RuntimeApiRoutes.Full.DATASOURCES.equals(path)) {
            return "POST".equals(method);
        }
        if (pathMatcher.match(RuntimeApiRoutes.Full.DATASOURCE_BY_NAME, path)) {
            return "PUT".equals(method) || "DELETE".equals(method);
        }
        if (pathMatcher.match(RuntimeApiRoutes.Full.DATASOURCE_TEST, path)) {
            return "POST".equals(method);
        }
        if (pathMatcher.match(RuntimeApiRoutes.Full.NAMESPACE_DATASOURCE, path)) {
            return "PUT".equals(method);
        }
        if (RuntimeApiRoutes.Full.RESOURCES_SAVE.equals(path)
                || RuntimeApiRoutes.Full.MODELS_VALIDATE.equals(path)
                || RuntimeApiRoutes.Full.MODELS_REFRESH.equals(path)
                || RuntimeApiRoutes.Full.FSSCRIPT_EXECUTE.equals(path)) {
            return "POST".equals(method);
        }
        if (RuntimeApiRoutes.Full.LEGACY_BUNDLE_ADD.equals(path)) {
            return "POST".equals(method);
        }
        if (pathMatcher.match(RuntimeApiRoutes.Full.LEGACY_BUNDLE_REMOVE, path)) {
            return "DELETE".equals(method);
        }
        return false;
    }

    private String extractSubmittedCode(HttpServletRequest request) {
        return request.getHeader(AUTH_CODE_HEADER);
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
        if (RuntimeApiRoutes.Full.ACCESS_CHECK.equals(normalizedRequestPath(request))) {
            response.setHeader("Cache-Control", "no-store");
        }
        RuntimeEnvelope<Object> envelope = responses.fail(
                code,
                PHASE,
                message,
                null,
                null,
                normalizedRequestPath(request),
                suggestedNextAction,
                false
        );
        objectMapper.writeValue(response.getWriter(), envelope);
    }
}
