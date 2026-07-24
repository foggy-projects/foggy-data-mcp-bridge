package com.foggyframework.dataset.db.model.engine.compose;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.port.ComposeSemanticPlanningPort;
import com.foggyframework.dataset.db.model.semantic.port.ComposeSqlGeneration;
import com.foggyframework.dataset.db.model.semantic.port.SemanticQueryExecutionPort;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class DslQueryFunctionPortTest {

    @Test
    void acceptsIndependentExecutionAndPlanningPorts() {
        AtomicInteger executionCount = new AtomicInteger();
        AtomicInteger planningCount = new AtomicInteger();
        SemanticQueryExecutionPort executionPort = (model, request, mode, context) -> {
            executionCount.incrementAndGet();
            SemanticQueryResponse response = new SemanticQueryResponse();
            response.setItems(List.of(Map.of("amount", 100)));
            return response;
        };
        ComposeSemanticPlanningPort planningPort = (model, request, context) -> {
            planningCount.incrementAndGet();
            return new ComposeSqlGeneration("select 1", List.of(), List.of(), Map.of());
        };

        DslQueryFunction function = new DslQueryFunction(
                executionPort,
                planningPort,
                SemanticRequestContext.empty(),
                mock(DataSource.class));

        DataSetResult result = (DataSetResult) function.executeFunction(null, Map.of(
                "model", "SalesQM",
                "columns", List.of("amount")));

        assertEquals(List.of(Map.of("amount", 100)), result.toList());
        assertEquals(1, executionCount.get());
        assertEquals(0, planningCount.get());
        assertNull(result.getComposeContext().getQueryService());
        assertSame(planningPort, result.getComposeContext().getPlanningPort());
    }
}
