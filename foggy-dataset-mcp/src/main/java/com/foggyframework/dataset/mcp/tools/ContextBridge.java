package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeRuntimeBundle;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeRuntimeHolder;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.mcp.spi.ToolExecutionContext;

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
            return bundle.ctx();
        }
        throw new UnsupportedOperationException(
                "ComposeScriptTool header-mode not implemented in M7; "
                        + "host must pre-set ComposeRuntimeBundle via "
                        + "ComposeRuntimeHolder.setBundle(...) before invoking. "
                        + "JWT / header-based Principal parsing scoped to M8+.");
    }
}
