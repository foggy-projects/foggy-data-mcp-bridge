package com.foggyframework.dataset.model.engine.pivot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PivotOuterCacheInvalidationReplayWindowTest {

    @Test
    @DisplayName("replay window deduplicates explicit event ids within the window")
    void replayWindowDeduplicatesExplicitEventIdsWithinWindow() {
        PivotOuterCacheInvalidationReplayWindow window =
                new PivotOuterCacheInvalidationReplayWindow(100L, 8);
        PivotOuterCacheInvalidationEvent event =
                PivotOuterCacheInvalidationEvent.of("ns-a", "ModelA")
                        .withMetadata("evt-1", "node-a", 10L);

        assertTrue(window.shouldConsume(event, "node-b", 10L));
        assertFalse(window.shouldConsume(event.withMetadata("evt-1", "node-c", 11L), "node-b", 11L));
        assertTrue(window.shouldConsume(event.withMetadata("evt-1", "node-c", 111L), "node-b", 111L));
    }

    @Test
    @DisplayName("replay window keeps scope-only manual cleanup repeatable")
    void replayWindowKeepsScopeOnlyManualCleanupRepeatable() {
        PivotOuterCacheInvalidationReplayWindow window =
                new PivotOuterCacheInvalidationReplayWindow(100L, 8);
        PivotOuterCacheInvalidationEvent event = PivotOuterCacheInvalidationEvent.of("ns-a", null);

        assertTrue(window.shouldConsume(event, "node-b", 10L));
        assertTrue(window.shouldConsume(event, "node-b", 11L));
        assertEquals(0, window.size());
    }

    @Test
    @DisplayName("replay window filters local self-loop consumption")
    void replayWindowFiltersLocalSelfLoopConsumption() {
        PivotOuterCacheInvalidationReplayWindow window =
                new PivotOuterCacheInvalidationReplayWindow(100L, 8);
        PivotOuterCacheInvalidationEvent event =
                PivotOuterCacheInvalidationEvent.of("ns-a", "ModelA")
                        .withMetadata("evt-2", "node-a", 10L);

        assertFalse(window.shouldConsume(event, " node-a ", 10L));
        assertTrue(window.shouldConsume(event, "node-b", 10L));
    }

    @Test
    @DisplayName("replay window evicts oldest replay keys when capacity is exceeded")
    void replayWindowEvictsOldestReplayKeysWhenCapacityExceeded() {
        PivotOuterCacheInvalidationReplayWindow window =
                new PivotOuterCacheInvalidationReplayWindow(1_000L, 1);

        assertTrue(window.shouldConsume(event("evt-1"), "node-z", 10L));
        assertTrue(window.shouldConsume(event("evt-2"), "node-z", 11L));
        assertEquals(1, window.size());
        assertTrue(window.shouldConsume(event("evt-1"), "node-z", 12L));
    }

    @Test
    @DisplayName("replay window rejects invalid configuration")
    void replayWindowRejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new PivotOuterCacheInvalidationReplayWindow(0L, 8));
        assertThrows(IllegalArgumentException.class,
                () -> new PivotOuterCacheInvalidationReplayWindow(100L, 0));
    }

    private PivotOuterCacheInvalidationEvent event(String eventId) {
        return PivotOuterCacheInvalidationEvent.of("ns-a", "ModelA")
                .withMetadata(eventId, "node-a", 10L);
    }
}
