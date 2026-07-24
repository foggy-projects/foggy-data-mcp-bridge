package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeBundle;
import com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeHolder;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.mcp.spi.ToolExecutionContext;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bridge from MCP {@link ToolExecutionContext} to Compose Query
 * {@link ComposeQueryContext}.
 *
 * <p><b>M7 scope — embedded mode only.</b> The host pre-sets a complete
 * {@link ComposeRuntimeBundle} via
 * {@link ComposeRuntimeHolder#setBundle} before invoking
 * {@code ComposeScriptTool.execute}. This bridge simply returns the
 * bundle's context.</p>
 *
 * <p>Header / JWT-based principal parsing deferred to M8+.</p>
 *
 * @since 8.2.0.beta
 */
final class ContextBridge {

    private ContextBridge() { /* utility */ }

    /**
     * Convert a {@link ToolExecutionContext} into a
     * {@link ComposeQueryContext}.
     *
     * @param toolCtx   the MCP tool execution context (may be null in
     *                  embedded mode since we use the bundle instead)
     * @param resolver  the authority resolver (must not be null);
     *                  reserved for M8+ header mode — currently validated
     *                  but unused in embedded mode (the bundle's ctx
     *                  already carries its own resolver)
     * @return the pre-set {@link ComposeQueryContext}
     * @throws IllegalArgumentException if resolver is null
     * @throws UnsupportedOperationException if no bundle is pre-set
     */
    static ComposeQueryContext toComposeContext(
            ToolExecutionContext toolCtx,
            AuthorityResolver resolver) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null");
        }
        ComposeRuntimeBundle bundle = ComposeRuntimeHolder.currentBundle();
        if (bundle != null) {
            ComposeQueryContext current = bundle.ctx();
            return ComposeQueryContext.builder()
                    .principal(current.principal())
                    .namespace(current.namespace())
                    .traceId(current.traceId())
                    .params(current.params())
                    .extensions(current.extensions())
                    .authorityResolver(resolver)
                    .build();
        }
        if (toolCtx == null) {
            throw new IllegalArgumentException(
                    "ToolExecutionContext is required for dataset.compose_script header mode");
        }

        Principal principal = Principal.builder()
                .userId(requiredHeader(toolCtx, "X-User-Id"))
                .tenantId(blankToNull(toolCtx.getHeader("X-Tenant-Id")))
                .roles(parseRoles(toolCtx.getHeader("X-Roles")))
                .deptId(blankToNull(toolCtx.getHeader("X-Dept-Id")))
                .authorizationHint(blankToNull(toolCtx.getHeader("Authorization")))
                .policySnapshotId(blankToNull(toolCtx.getHeader("X-Policy-Snapshot-Id")))
                .build();

        return ComposeQueryContext.builder()
                .principal(principal)
                .namespace(resolveNamespace(toolCtx))
                .traceId(firstNonBlank(toolCtx.getTraceId(), toolCtx.getHeader("X-Trace-Id")))
                .authorityResolver(resolver)
                .build();
    }

    private static String resolveNamespace(ToolExecutionContext toolCtx) {
        String namespace = firstNonBlank(
                toolCtx.getNamespace(),
                toolCtx.getHeader("X-Namespace"),
                toolCtx.getHeader("X-NS"));
        if (namespace == null) {
            throw new IllegalArgumentException(
                    "X-Namespace or X-NS header is required for dataset.compose_script header mode");
        }
        return namespace;
    }

    private static String requiredHeader(ToolExecutionContext toolCtx, String name) {
        String value = blankToNull(toolCtx.getHeader(name));
        if (value == null) {
            throw new IllegalArgumentException(name + " header is required for dataset.compose_script header mode");
        }
        return value;
    }

    private static List<String> parseRoles(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableList());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String cleaned = blankToNull(value);
            if (cleaned != null) {
                return cleaned;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
