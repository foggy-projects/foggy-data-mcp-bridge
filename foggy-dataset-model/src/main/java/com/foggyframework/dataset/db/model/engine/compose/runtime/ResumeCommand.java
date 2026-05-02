package com.foggyframework.dataset.db.model.engine.compose.runtime;

import java.util.Collections;
import java.util.Map;

/**
 * What upstream submits to resume a suspended run.
 *
 * <p>Immutable.  Mirrors Python {@code ResumeCommand}.</p>
 *
 * @since 8.5.0
 */
public final class ResumeCommand {

    private final String scriptRunId;
    private final String suspendId;
    private final Map<String, Object> payload;

    public ResumeCommand(String scriptRunId, String suspendId, Map<String, Object> payload) {
        this.scriptRunId = scriptRunId;
        this.suspendId = suspendId;
        this.payload = payload == null ? Map.of() : Collections.unmodifiableMap(payload);
    }

    public String getScriptRunId() { return scriptRunId; }
    public String getSuspendId() { return suspendId; }
    public Map<String, Object> getPayload() { return payload; }
}
