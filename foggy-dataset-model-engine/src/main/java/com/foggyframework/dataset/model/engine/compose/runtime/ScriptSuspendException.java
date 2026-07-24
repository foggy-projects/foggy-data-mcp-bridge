package com.foggyframework.dataset.model.engine.compose.runtime;

/**
 * Base exception for all script suspend / resume errors.
 *
 * <p>Carries a structured {@link #getCode() code} from
 * {@link ScriptSuspendErrorCodes}.  Callers can catch this family
 * in a single handler.</p>
 *
 * <p>Mirrors Python {@code ScriptSuspendError}.</p>
 *
 * @since 8.5.0
 */
public class ScriptSuspendException extends RuntimeException {

    private final String code;

    public ScriptSuspendException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ScriptSuspendException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** One of the {@code script/*} constants from {@link ScriptSuspendErrorCodes}. */
    public String getCode() {
        return code;
    }

    // -- Concrete subclasses ------------------------------------------------

    /** pause() called outside a FSScript run context. */
    public static class PauseNotInRun extends ScriptSuspendException {
        public PauseNotInRun(String message) {
            super(ScriptSuspendErrorCodes.PAUSE_NOT_IN_RUN, message);
        }
        public PauseNotInRun() {
            this("pause is not allowed outside a script run");
        }
    }

    /** Current policy does not allow script-visible pause. */
    public static class PauseNotAllowed extends ScriptSuspendException {
        public PauseNotAllowed(String message) {
            super(ScriptSuspendErrorCodes.PAUSE_NOT_ALLOWED, message);
        }
        public PauseNotAllowed() {
            this("pause is not allowed by current policy");
        }
    }

    /** Suspend count or resource quota exceeded. */
    public static class LimitExceeded extends ScriptSuspendException {
        public LimitExceeded(String message) {
            super(ScriptSuspendErrorCodes.SUSPEND_LIMIT_EXCEEDED, message);
        }
        public LimitExceeded() {
            this("suspend limit exceeded");
        }
    }

    /** Pause timed out — auto-rejected. */
    public static class Timeout extends ScriptSuspendException {
        public Timeout(String message) {
            super(ScriptSuspendErrorCodes.SUSPEND_TIMEOUT, message);
        }
        public Timeout() {
            this("suspend timed out");
        }
    }

    /** Upstream explicitly rejected the suspension. */
    public static class Rejected extends ScriptSuspendException {
        public Rejected(String message) {
            super(ScriptSuspendErrorCodes.SUSPEND_REJECTED, message);
        }
        public Rejected() {
            this("suspend rejected");
        }
    }

    /** Resume command does not match the current suspension. */
    public static class ResumeTokenInvalid extends ScriptSuspendException {
        public ResumeTokenInvalid(String message) {
            super(ScriptSuspendErrorCodes.RESUME_TOKEN_INVALID, message);
        }
        public ResumeTokenInvalid() {
            this("resume token does not match active suspension");
        }
    }

    /** Suspension is in a terminal or invalid state. */
    public static class StateInvalid extends ScriptSuspendException {
        public StateInvalid(String message) {
            super(ScriptSuspendErrorCodes.SUSPEND_STATE_INVALID, message);
        }
        public StateInvalid() {
            this("suspension is in a terminal or invalid state");
        }
    }
}
