package com.foggyframework.dataset.model.semantic.explain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request-local, append-only collector used exclusively by semantic explain.
 *
 * <p>The collector is deliberately in-memory. It is never installed for an
 * ordinary query and must only receive already-sanitized structured details.
 * This makes it suitable for passing through the existing query contexts
 * without creating a shared log file or a second SQL inference pipeline.</p>
 */
public final class ExplainTraceCollector {

    private final List<Event> events = new ArrayList<>();
    private final Map<Object, SemanticExplainResponse.ConditionOrigin> conditionOrigins =
            new IdentityHashMap<>();

    /**
     * Associate one compiler-owned condition node with its provenance.
     *
     * <p>Identity semantics are intentional: values may be equal while coming
     * from different governance stages. The map is request-local and is never
     * serialized as part of the public response.</p>
     */
    public synchronized void registerConditionOrigin(
            Object condition,
            SemanticExplainResponse.ConditionOrigin origin
    ) {
        if (condition != null && origin != null) {
            conditionOrigins.putIfAbsent(condition, origin);
        }
    }

    public synchronized SemanticExplainResponse.ConditionOrigin conditionOrigin(Object condition) {
        return conditionOrigins.get(condition);
    }

    public synchronized void record(
            String stage,
            String event,
            SemanticExplainResponse.StageStatus status,
            String decision,
            String reasonCode,
            SemanticExplainResponse.Confidence confidence,
            Map<String, ?> details
    ) {
        Map<String, Object> safeDetails = new LinkedHashMap<>();
        if (details != null) {
            details.forEach((key, value) -> {
                if (key != null && value != null) {
                    safeDetails.put(key, freeze(value));
                }
            });
        }
        events.add(new Event(
                events.size() + 1L,
                Instant.now(),
                stage,
                event,
                status,
                decision,
                reasonCode,
                confidence,
                Collections.unmodifiableMap(safeDetails)
        ));
    }

    public synchronized List<Event> snapshot() {
        return List.copyOf(events);
    }

    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> frozen = new LinkedHashMap<>();
            map.forEach((key, item) -> frozen.put(String.valueOf(key), freeze(item)));
            return Collections.unmodifiableMap(frozen);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> frozen = new ArrayList<>();
            iterable.forEach(item -> frozen.add(freeze(item)));
            return Collections.unmodifiableList(frozen);
        }
        return value;
    }

    public record Event(
            long sequence,
            Instant observedAt,
            String stage,
            String event,
            SemanticExplainResponse.StageStatus status,
            String decision,
            String reasonCode,
            SemanticExplainResponse.Confidence confidence,
            Map<String, Object> details
    ) {
    }
}
