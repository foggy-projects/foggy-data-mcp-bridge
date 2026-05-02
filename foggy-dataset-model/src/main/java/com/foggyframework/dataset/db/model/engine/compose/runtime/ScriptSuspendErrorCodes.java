package com.foggyframework.dataset.db.model.engine.compose.runtime;

/**
 * Error code constants for v1.9 script suspend / resume.
 *
 * <p>Lives in the {@code script/*} namespace, separate from
 * {@code capability/*} codes.  Must match Python frozen codes exactly.</p>
 *
 * @since 8.5.0
 */
public final class ScriptSuspendErrorCodes {

    private ScriptSuspendErrorCodes() { /* constants */ }

    public static final String PAUSE_NOT_IN_RUN = "script/pause-not-in-run";
    public static final String PAUSE_NOT_ALLOWED = "script/pause-not-allowed";
    public static final String SUSPEND_LIMIT_EXCEEDED = "script/suspend-limit-exceeded";
    public static final String SUSPEND_TIMEOUT = "script/suspend-timeout";
    public static final String SUSPEND_REJECTED = "script/suspend-rejected";
    public static final String RESUME_TOKEN_INVALID = "script/resume-token-invalid";
    public static final String RESUME_PAYLOAD_INVALID = "script/resume-payload-invalid";
    public static final String SUSPEND_STATE_INVALID = "script/suspend-state-invalid";
}
