package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small local cache for E1b Pivot response-cache verification.
 */
final class PivotOuterResponseCache {

    static final String CACHE_NAME = "pivot_outer_response_local";

    private final boolean enabled;
    private final long ttlMillis;
    private final int maximumSize;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    PivotOuterResponseCache(PivotPipeline.OuterCacheOptions options) {
        PivotPipeline.OuterCacheOptions safeOptions = options == null
                ? PivotPipeline.OuterCacheOptions.disabled()
                : options.normalized();
        this.enabled = safeOptions.enabled();
        this.ttlMillis = safeOptions.ttlMillis();
        this.maximumSize = safeOptions.maximumSize();
    }

    boolean isEnabled() {
        return enabled;
    }

    long ttlMillis() {
        return ttlMillis;
    }

    LookupResult lookup(String keyHash, long nowMillis) {
        if (!enabled || keyHash == null || keyHash.isBlank()) {
            return LookupResult.miss();
        }
        Entry entry = entries.get(keyHash);
        if (entry == null) {
            return LookupResult.miss();
        }
        long ageMs = Math.max(0L, nowMillis - entry.storedAtMillis());
        if (entry.expiresAtMillis() <= nowMillis) {
            entries.remove(keyHash, entry);
            return LookupResult.expired(ageMs);
        }
        return LookupResult.hit(copyResponse(entry.response()), ageMs);
    }

    void store(String keyHash, SemanticQueryResponse response, long nowMillis) {
        if (!enabled || keyHash == null || keyHash.isBlank() || response == null) {
            return;
        }
        evictExpired(nowMillis);
        if (entries.size() >= maximumSize) {
            evictOldest();
        }
        entries.put(keyHash, new Entry(copyResponse(response), nowMillis, nowMillis + ttlMillis));
    }

    int estimatePayloadBytes(SemanticQueryResponse response) {
        if (response == null) {
            return 0;
        }
        int bytes = 0;
        bytes += String.valueOf(response.getItems()).getBytes(StandardCharsets.UTF_8).length;
        bytes += String.valueOf(response.getWarnings()).getBytes(StandardCharsets.UTF_8).length;
        Object contract = response.getDebug() != null && response.getDebug().getExtra() != null
                ? response.getDebug().getExtra().get("pivotEngineContract")
                : null;
        bytes += String.valueOf(contract).getBytes(StandardCharsets.UTF_8).length;
        return bytes;
    }

    static SemanticQueryResponse copyResponse(SemanticQueryResponse source) {
        if (source == null) {
            return null;
        }
        SemanticQueryResponse copy = new SemanticQueryResponse();
        copy.setItems(copyListOfMaps(source.getItems()));
        copy.setSchema(source.getSchema());
        copy.setPagination(source.getPagination());
        copy.setTotal(source.getTotal());
        copy.setTotalData(deepCopyValue(source.getTotalData()));
        copy.setHasNext(source.getHasNext());
        copy.setCursor(source.getCursor());
        copy.setWarnings(source.getWarnings() == null ? null : new ArrayList<>(source.getWarnings()));
        copy.setDebug(copyDebug(source.getDebug()));
        copy.setTruncationInfo(copyMap(source.getTruncationInfo()));
        copy.setSemantic(source.getSemantic());
        copy.setExecution(source.getExecution());
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> copyListOfMaps(List<Map<String, Object>> source) {
        if (source == null) {
            return null;
        }
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> item : source) {
            copy.add((Map<String, Object>) deepCopyValue(item));
        }
        return copy;
    }

    private static SemanticQueryResponse.DebugInfo copyDebug(SemanticQueryResponse.DebugInfo source) {
        if (source == null) {
            return null;
        }
        SemanticQueryResponse.DebugInfo copy = new SemanticQueryResponse.DebugInfo();
        copy.setNormalized(source.getNormalized());
        copy.setDurationMs(source.getDurationMs());
        copy.setExtra(copyMap(source.getExtra()));
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        return (Map<String, Object>) deepCopyValue(source);
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(deepCopyValue(item));
            }
            return copy;
        }
        return value;
    }

    private void evictExpired(long nowMillis) {
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= nowMillis);
    }

    private void evictOldest() {
        String oldestKey = null;
        long oldestStoredAt = Long.MAX_VALUE;
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            if (entry.getValue().storedAtMillis() < oldestStoredAt) {
                oldestStoredAt = entry.getValue().storedAtMillis();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            entries.remove(oldestKey);
        }
    }

    private record Entry(SemanticQueryResponse response, long storedAtMillis, long expiresAtMillis) {}

    record LookupResult(SemanticQueryResponse response, long ageMs, boolean hit, boolean expired) {
        static LookupResult hit(SemanticQueryResponse response, long ageMs) {
            return new LookupResult(response, ageMs, true, false);
        }

        static LookupResult expired(long ageMs) {
            return new LookupResult(null, ageMs, false, true);
        }

        static LookupResult miss() {
            return new LookupResult(null, 0L, false, false);
        }
    }
}
