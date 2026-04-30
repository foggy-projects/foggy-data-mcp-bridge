package com.foggyframework.dataset.db.model.engine.compose.runtime;

/**
 * What upstream submits to reject a suspended run.
 *
 * <p>Immutable.  Mirrors Python {@code RejectCommand}.</p>
 *
 * @since 8.5.0
 */
public final class RejectCommand {

    private final String scriptRunId;
    private final String suspendId;
    private final String reason;  // nullable

    public RejectCommand(String scriptRunId, String suspendId, String reason) {
        this.scriptRunId = scriptRunId;
        this.suspendId = suspendId;
        this.reason = reason;
    }

    public RejectCommand(String scriptRunId, String suspendId) {
        this(scriptRunId, suspendId, null);
    }

    public String getScriptRunId() { return scriptRunId; }
    public String getSuspendId() { return suspendId; }
    public String getReason() { return reason; }
}
