package com.foggyframework.dataset.db.model.semantic.controller;

import com.foggyframework.dataset.db.model.engine.pivot.PivotOuterCacheInvalidationBroadcaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

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
        assertEquals("namespace/model", response.getBody().get("scope"));
    }
}
