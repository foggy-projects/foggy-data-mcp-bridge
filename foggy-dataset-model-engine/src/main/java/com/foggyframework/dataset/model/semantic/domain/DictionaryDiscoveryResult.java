package com.foggyframework.dataset.model.semantic.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Runtime observed dictionary values for a semantic field.
 *
 * <p>The result is intentionally small and serializable so metadata builders can
 * expose it without binding to the query execution implementation.</p>
 */
public class DictionaryDiscoveryResult {

    public static final String STATUS_SAMPLED = "sampled";
    public static final String STATUS_FAILED = "failed";

    private final String status;
    private final List<ValueEntry> values;
    private final boolean truncated;
    private final Instant sampledAt;
    private final String error;

    private DictionaryDiscoveryResult(String status, List<ValueEntry> values, boolean truncated,
                                      Instant sampledAt, String error) {
        this.status = status;
        this.values = values == null ? List.of() : Collections.unmodifiableList(values);
        this.truncated = truncated;
        this.sampledAt = sampledAt;
        this.error = error;
    }

    public static DictionaryDiscoveryResult sampled(List<ValueEntry> values, boolean truncated, Instant sampledAt) {
        return new DictionaryDiscoveryResult(STATUS_SAMPLED, values, truncated, sampledAt, null);
    }

    public static DictionaryDiscoveryResult failed(String error) {
        return new DictionaryDiscoveryResult(STATUS_FAILED, List.of(), false, Instant.now(), error);
    }

    public String getStatus() {
        return status;
    }

    public List<ValueEntry> getValues() {
        return values;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public Instant getSampledAt() {
        return sampledAt;
    }

    public String getError() {
        return error;
    }

    public static class ValueEntry {
        private final Object value;
        private final Long count;

        public ValueEntry(Object value, Long count) {
            this.value = value;
            this.count = count;
        }

        public Object getValue() {
            return value;
        }

        public Long getCount() {
            return count;
        }
    }
}
