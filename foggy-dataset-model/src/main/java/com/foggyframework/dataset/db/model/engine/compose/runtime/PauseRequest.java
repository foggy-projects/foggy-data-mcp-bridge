package com.foggyframework.dataset.db.model.engine.compose.runtime;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * What a handler passes to the pause primitive.
 *
 * <p>Immutable.  Mirrors Python {@code PauseRequest}.</p>
 *
 * @since 8.5.0
 */
public final class PauseRequest {

    /** System-level maximum timeout (milliseconds). Default 5 minutes. */
    public static final int MAX_TIMEOUT_MS = 300_000;

    private final String reason;
    private final Map<String, Object> summary;
    private final int timeoutMs;
    private final Map<String, Object> resumeSchema;  // nullable
    private final String auditTag;                    // nullable

    private PauseRequest(Builder b) {
        if (b.reason == null || b.reason.isBlank()) {
            throw new IllegalArgumentException("reason must be a non-empty string");
        }
        if (b.timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be > 0");
        }
        if (b.timeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException(
                    "timeoutMs must be <= " + MAX_TIMEOUT_MS + ", got " + b.timeoutMs);
        }
        this.reason = b.reason;
        this.summary = b.summary == null ? Map.of() : Collections.unmodifiableMap(b.summary);
        this.timeoutMs = b.timeoutMs;
        this.resumeSchema = b.resumeSchema;
        this.auditTag = b.auditTag;
    }

    public String getReason() { return reason; }
    public Map<String, Object> getSummary() { return summary; }
    public int getTimeoutMs() { return timeoutMs; }
    public Map<String, Object> getResumeSchema() { return resumeSchema; }
    public String getAuditTag() { return auditTag; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String reason;
        private Map<String, Object> summary;
        private int timeoutMs;
        private Map<String, Object> resumeSchema;
        private String auditTag;

        public Builder reason(String v) { this.reason = v; return this; }
        public Builder summary(Map<String, Object> v) { this.summary = v; return this; }
        public Builder timeoutMs(int v) { this.timeoutMs = v; return this; }
        public Builder resumeSchema(Map<String, Object> v) { this.resumeSchema = v; return this; }
        public Builder auditTag(String v) { this.auditTag = v; return this; }
        public PauseRequest build() { return new PauseRequest(this); }
    }

    @Override
    public String toString() {
        return "PauseRequest{reason='" + reason + "', timeoutMs=" + timeoutMs + '}';
    }
}
