package com.foggyframework.dataset.db.model.engine.compose.runtime;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * What the engine returns when a run is suspended.
 *
 * <p>Immutable.  Mirrors Python {@code SuspensionResult}.</p>
 *
 * @since 8.5.0
 */
public final class SuspensionResult {

    private final String scriptRunId;
    private final String suspendId;
    private final String reason;
    private final Map<String, Object> summary;
    private final Instant timeoutAt;

    public SuspensionResult(
            String scriptRunId,
            String suspendId,
            String reason,
            Map<String, Object> summary,
            Instant timeoutAt) {
        this.scriptRunId = scriptRunId;
        this.suspendId = suspendId;
        this.reason = reason;
        this.summary = summary == null ? Map.of() : Collections.unmodifiableMap(summary);
        this.timeoutAt = timeoutAt;
    }

    public String getScriptRunId() { return scriptRunId; }
    public String getSuspendId() { return suspendId; }
    public String getReason() { return reason; }
    public Map<String, Object> getSummary() { return summary; }
    public Instant getTimeoutAt() { return timeoutAt; }

    @Override
    public String toString() {
        return "SuspensionResult{runId='" + scriptRunId
                + "', suspendId='" + suspendId
                + "', reason='" + reason + "'}";
    }
}
