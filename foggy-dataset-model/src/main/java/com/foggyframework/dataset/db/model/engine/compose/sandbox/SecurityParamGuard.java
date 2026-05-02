package com.foggyframework.dataset.db.model.engine.compose.sandbox;

import java.util.Map;
import java.util.Set;

/**
 * Layer A runtime guard — validates that security-sensitive parameters
 * are not injected through the DSL body ({@code from()} / {@code plan.query()}).
 *
 * <p>Security parameters (authorization, userId, tenantId, roles, namespace,
 * deniedColumns, systemSlice, fieldAccess, policySnapshotId) are bound
 * exclusively by {@code ComposeQueryContext}. Any attempt to pass them
 * through the script is rejected fail-closed.</p>
 *
 * @since 8.2.0.beta
 */
public final class SecurityParamGuard {

    private SecurityParamGuard() { /* utility */ }

    /**
     * Parameter names that must never appear in a DSL options map.
     */
    private static final Set<String> BLOCKED_PARAMS = Set.of(
            "authorization",
            "userId",
            "tenantId",
            "roles",
            "namespace",
            "deniedColumns",
            "systemSlice",
            "fieldAccess",
            "policySnapshotId"
    );

    /**
     * Check the options map for blocked security parameters.
     *
     * @param args  the DSL options map from {@code from({...})} or {@code plan.query({...})}
     * @param phase the pipeline phase (e.g. "script-eval" or "plan-build")
     * @throws ComposeSandboxViolationException if any blocked parameter is found
     */
    public static void validate(Map<String, Object> args, String phase) {
        if (args == null) return;
        for (String key : args.keySet()) {
            if (BLOCKED_PARAMS.contains(key)) {
                throw new ComposeSandboxViolationException(
                        ComposeSandboxErrorCodes.LAYER_A_SECURITY_PARAM,
                        "Security parameters cannot be passed through DSL body; "
                                + "they are bound by ComposeQueryContext. "
                                + "Blocked parameter: '" + key + "'",
                        phase);
            }
        }
    }
}
