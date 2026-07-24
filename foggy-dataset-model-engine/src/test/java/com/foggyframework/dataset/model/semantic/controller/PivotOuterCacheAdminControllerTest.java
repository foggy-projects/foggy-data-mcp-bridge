package com.foggyframework.dataset.model.semantic.controller;

import com.foggyframework.dataset.model.engine.pivot.PivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.model.engine.pivot.PivotOuterCacheInvalidationEvent;
import com.foggyframework.dataset.model.engine.pivot.PivotOuterCacheInvalidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PivotOuterCacheAdminControllerTest {

    @Test
    @DisplayName("admin evict endpoint delegates namespace/model scope")
    void testAdminEvictDelegatesNamespaceAndModel() {
        PivotOuterCacheInvalidationBroadcaster broadcaster = (namespace, model) -> {
            assertEquals("ns-a", namespace);
            assertEquals("SalesQM", model);
            return 3;
        };
        PivotOuterCacheAdminController controller = new PivotOuterCacheAdminController(broadcaster);

        ResponseEntity<Map<String, Object>> response = controller.evict("ns-a", "SalesQM");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().get("success"));
        assertEquals("ns-a", response.getBody().get("namespace"));
        assertEquals("SalesQM", response.getBody().get("model"));
        assertEquals(3, response.getBody().get("removed"));
        assertEquals(1, response.getBody().get("attemptedNodes"));
        assertEquals(1, response.getBody().get("succeededNodes"));
        assertEquals(0, response.getBody().get("failedNodes"));
        assertEquals("namespace/model", response.getBody().get("scope"));
    }

    @Test
    @DisplayName("admin evict endpoint surfaces broadcaster partial failure diagnostics")
    void testAdminEvictSurfacesPartialFailureDiagnostics() {
        PivotOuterCacheInvalidationBroadcaster broadcaster = new PivotOuterCacheInvalidationBroadcaster() {
            @Override
            public int evict(String namespace, String model) {
                return evict(PivotOuterCacheInvalidationEvent.of(namespace, model)).removed();
            }

            @Override
            public PivotOuterCacheInvalidationResult evict(PivotOuterCacheInvalidationEvent event) {
                return new PivotOuterCacheInvalidationResult(
                        2, 3, 2, 1, List.of("node-c publish failed"));
            }
        };
        PivotOuterCacheAdminController controller = new PivotOuterCacheAdminController(broadcaster);

        ResponseEntity<Map<String, Object>> response = controller.evict("ns-a", null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(false, response.getBody().get("success"));
        assertEquals(2, response.getBody().get("removed"));
        assertEquals(3, response.getBody().get("attemptedNodes"));
        assertEquals(2, response.getBody().get("succeededNodes"));
        assertEquals(1, response.getBody().get("failedNodes"));
        assertEquals(List.of("node-c publish failed"), response.getBody().get("errors"));
        assertEquals("namespace/all-models", response.getBody().get("scope"));
    }

    @Test
    @DisplayName("admin evict endpoint returns failure payload when broadcaster throws")
    void testAdminEvictReturnsFailurePayloadWhenBroadcasterThrows() {
        PivotOuterCacheInvalidationBroadcaster broadcaster = new PivotOuterCacheInvalidationBroadcaster() {
            @Override
            public int evict(String namespace, String model) {
                throw new IllegalStateException("publish unavailable");
            }
        };
        PivotOuterCacheAdminController controller = new PivotOuterCacheAdminController(broadcaster);

        ResponseEntity<Map<String, Object>> response = controller.evict(null, "SalesQM");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(false, response.getBody().get("success"));
        assertEquals(0, response.getBody().get("removed"));
        assertEquals(1, response.getBody().get("attemptedNodes"));
        assertEquals(0, response.getBody().get("succeededNodes"));
        assertEquals(1, response.getBody().get("failedNodes"));
        assertEquals(List.of("broadcaster failed: publish unavailable"), response.getBody().get("errors"));
        assertEquals("all-namespaces/model", response.getBody().get("scope"));
    }
}
