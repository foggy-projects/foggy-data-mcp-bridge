package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryGridEngineSpiFailClosedTest {

    private final SemanticQueryServiceV3Impl service = new SemanticQueryServiceV3Impl();

    @Test
    @DisplayName("MEMORY_GRID route requires an explicitly configured engine")
    void memoryGridRouteRequiresConfiguredEngine() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("MEMORY_GRID");
        request.setMemoryGridPlan(Map.of(
                "inputs", List.of(Map.of(
                        "handle", "mgr_actual_sales",
                        "governed", true,
                        "row_limit", 100
                )),
                "output", Map.of("limit", 100)
        ));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", request, SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("MEMORY_GRID_ENGINE_NOT_CONFIGURED"));
    }
}
