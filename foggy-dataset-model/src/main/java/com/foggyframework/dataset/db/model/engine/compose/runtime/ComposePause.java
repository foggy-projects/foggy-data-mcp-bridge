package com.foggyframework.dataset.db.model.engine.compose.runtime;

import java.util.Map;

/**
 * Static entry point for handler-internal pause.
 *
 * <p>Reads the current {@link ScriptRunContextHolder} and the bound
 * {@link SuspensionManager} to perform a blocking pause.  Throws
 * {@link ScriptSuspendException.PauseNotInRun} when called outside
 * a script run context.</p>
 *
 * <p>Mirrors Python {@code compose_pause()}.</p>
 *
 * @since 8.5.0
 */
public final class ComposePause {

    private ComposePause() { /* utility */ }

    /**
     * Thread-local holder for the SuspensionManager bound to the
     * current runScript invocation.
     */
    static final ThreadLocal<SuspensionManager> CURRENT_MANAGER = new ThreadLocal<>();

    /**
     * Pause the current script run and block until resume, reject, or timeout.
     *
     * @param reason    stable string identifying the pause reason
     * @param summary   JSON-safe context for upstream display
     * @param timeoutMs how long to wait (0 &lt; timeoutMs &lt;= MAX_TIMEOUT_MS)
     * @return the resume payload
     */
    public static Map<String, Object> pause(String reason,
                                            Map<String, Object> summary,
                                            int timeoutMs) {
        ScriptRunContext ctx = ScriptRunContextHolder.current();
        if (ctx == null) {
            throw new ScriptSuspendException.PauseNotInRun();
        }
        SuspensionManager manager = CURRENT_MANAGER.get();
        if (manager == null) {
            throw new ScriptSuspendException.PauseNotInRun(
                    "no SuspensionManager is bound to the current run");
        }

        PauseRequest request = PauseRequest.builder()
                .reason(reason)
                .summary(summary)
                .timeoutMs(timeoutMs)
                .build();

        return manager.pauseAndWait(ctx.getRunId(), request);
    }

    /** Clear the ThreadLocal (test-only). */
    public static void clearForTesting() {
        CURRENT_MANAGER.remove();
    }
}
