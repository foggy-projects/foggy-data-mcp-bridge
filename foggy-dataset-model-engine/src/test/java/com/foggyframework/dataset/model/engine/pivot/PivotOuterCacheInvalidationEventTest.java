package com.foggyframework.dataset.model.engine.pivot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PivotOuterCacheInvalidationEventTest {

    @Test
    @DisplayName("event normalizes transport scope without losing default namespace semantics")
    void testEventNormalizesTransportScope() {
        PivotOuterCacheInvalidationEvent all =
                PivotOuterCacheInvalidationEvent.of(null, " ");
        PivotOuterCacheInvalidationEvent defaultNamespace =
                PivotOuterCacheInvalidationEvent.of(" ", "SalesQM");

        assertTrue(all.allNamespaces());
        assertTrue(all.allModels());
        assertEquals("all-namespaces/all-models", all.scope());
        assertNull(all.model());

        assertTrue(defaultNamespace.defaultNamespace());
        assertFalse(defaultNamespace.allNamespaces());
        assertEquals("", defaultNamespace.namespace());
        assertEquals("SalesQM", defaultNamespace.model());
        assertEquals("namespace/model", defaultNamespace.scope());
    }

    @Test
    @DisplayName("event metadata is optional but validates issued time")
    void testEventMetadataValidation() {
        PivotOuterCacheInvalidationEvent event =
                PivotOuterCacheInvalidationEvent.of("ns-a", "SalesQM")
                        .withMetadata("evt-1", "node-a", 123L);

        assertEquals("evt-1", event.eventId());
        assertEquals("node-a", event.sourceNodeId());
        assertEquals(123L, event.issuedAtMillis());
        assertThrows(IllegalArgumentException.class,
                () -> PivotOuterCacheInvalidationEvent.of("ns-a", "SalesQM")
                        .withMetadata("evt-1", "node-a", -1L));
    }

    @Test
    @DisplayName("event replay deduplication requires explicit event id")
    void testReplayDeduplicationRequiresExplicitEventId() {
        PivotOuterCacheInvalidationEvent scoped =
                PivotOuterCacheInvalidationEvent.of("ns-a", "SalesQM");
        PivotOuterCacheInvalidationEvent replayA =
                scoped.withMetadata("evt-1", "node-a", 123L);
        PivotOuterCacheInvalidationEvent replayB =
                PivotOuterCacheInvalidationEvent.of(null, null)
                        .withMetadata("evt-1", "node-b", 456L);
        PivotOuterCacheInvalidationEvent different =
                scoped.withMetadata("evt-2", "node-a", 123L);

        assertTrue(scoped.replayDeduplicationKey().isEmpty());
        assertEquals("pivot-outer-cache-invalidation:evt-1",
                replayA.replayDeduplicationKey().orElseThrow());
        assertTrue(replayA.sameReplayEvent(replayB));
        assertFalse(replayA.sameReplayEvent(different));
        assertFalse(replayA.sameReplayEvent(scoped));
    }

    @Test
    @DisplayName("result aggregates node counts and protects error list")
    void testResultAggregatesAndProtectsErrors() {
        List<String> errors = new ArrayList<>();
        errors.add("node-b timeout");
        PivotOuterCacheInvalidationResult partial =
                new PivotOuterCacheInvalidationResult(0, 1, 0, 1, errors);
        errors.add("mutated");

        PivotOuterCacheInvalidationResult result = PivotOuterCacheInvalidationResult.aggregate(List.of(
                PivotOuterCacheInvalidationResult.local(2),
                partial
        ));

        assertEquals(2, result.removed());
        assertEquals(2, result.attemptedNodes());
        assertEquals(1, result.succeededNodes());
        assertEquals(1, result.failedNodes());
        assertEquals(List.of("node-b timeout"), result.errors());
        assertFalse(result.success());
        assertEquals(List.of("invalidation target is unavailable"),
                PivotOuterCacheInvalidationResult.unavailable(null).errors());
    }
}
