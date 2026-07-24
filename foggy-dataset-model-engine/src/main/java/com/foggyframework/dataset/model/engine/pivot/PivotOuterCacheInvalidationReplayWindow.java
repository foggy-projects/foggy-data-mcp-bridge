package com.foggyframework.dataset.model.engine.pivot;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local replay/self-loop guard for event-bus backed invalidation consumers.
 *
 * <p>This is intentionally process-local. A real distributed event bus can use
 * it per consumer, or replace it with a bus-native deduplication store.</p>
 */
public final class PivotOuterCacheInvalidationReplayWindow {

    private final long windowMillis;
    private final int maximumEntries;
    private final LinkedHashMap<String, Long> seenExpiresAt = new LinkedHashMap<>();

    public PivotOuterCacheInvalidationReplayWindow(long windowMillis, int maximumEntries) {
        if (windowMillis <= 0L) {
            throw new IllegalArgumentException("windowMillis must be positive");
        }
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        this.windowMillis = windowMillis;
        this.maximumEntries = maximumEntries;
    }

    public synchronized boolean shouldConsume(PivotOuterCacheInvalidationEvent event,
                                              String localNodeId,
                                              long nowMillis) {
        PivotOuterCacheInvalidationEvent scoped =
                event == null ? PivotOuterCacheInvalidationEvent.all() : event;
        if (sameNode(localNodeId, scoped.sourceNodeId())) {
            return false;
        }
        cleanupExpired(nowMillis);
        if (scoped.replayDeduplicationKey().isEmpty()) {
            return true;
        }
        String replayKey = scoped.replayDeduplicationKey().orElseThrow();
        Long expiresAt = seenExpiresAt.get(replayKey);
        if (expiresAt != null && expiresAt > nowMillis) {
            return false;
        }
        seenExpiresAt.put(replayKey, nowMillis + windowMillis);
        evictOverflow();
        return true;
    }

    public synchronized int size() {
        return seenExpiresAt.size();
    }

    private boolean sameNode(String localNodeId, String sourceNodeId) {
        return localNodeId != null
                && !localNodeId.isBlank()
                && sourceNodeId != null
                && localNodeId.trim().equals(sourceNodeId.trim());
    }

    private void cleanupExpired(long nowMillis) {
        Iterator<Map.Entry<String, Long>> iterator = seenExpiresAt.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= nowMillis) {
                iterator.remove();
            }
        }
    }

    private void evictOverflow() {
        while (seenExpiresAt.size() > maximumEntries) {
            Iterator<String> iterator = seenExpiresAt.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }
}
