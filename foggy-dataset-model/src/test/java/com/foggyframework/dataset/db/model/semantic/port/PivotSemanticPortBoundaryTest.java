package com.foggyframework.dataset.db.model.semantic.port;

import com.foggyframework.dataset.db.model.engine.pivot.LocalPivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.db.model.engine.pivot.PivotPipeline;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PivotSemanticPortBoundaryTest {

    @Test
    void legacySemanticServiceProvidesNarrowPivotPorts() {
        assertTrue(PivotRollupExecutionPort.class.isAssignableFrom(SemanticQueryServiceV3.class));
        assertTrue(PivotOuterCacheEvictionPort.class.isAssignableFrom(SemanticQueryServiceV3.class));
    }

    @Test
    void pivotInternalsDependOnNarrowSemanticPorts() throws Exception {
        Field execution = PivotPipeline.class.getDeclaredField("pivotRollupExecutionPort");
        assertEquals(PivotRollupExecutionPort.class, execution.getType());

        Field eviction = LocalPivotOuterCacheInvalidationBroadcaster.class
                .getDeclaredField("evictionPortProvider");
        ParameterizedType providerType = (ParameterizedType) eviction.getGenericType();
        assertEquals(PivotOuterCacheEvictionPort.class, providerType.getActualTypeArguments()[0]);
    }

    @Test
    void legacyServiceBridgesRollupSqlAndExecutionWithoutEngineTypesInPort() {
        SemanticQueryServiceV3 service = mock(SemanticQueryServiceV3.class, CALLS_REAL_METHODS);
        SemanticQueryRequest request = new SemanticQueryRequest();
        SemanticRequestContext context = SemanticRequestContext.empty();
        SqlGenerationResult generated = new SqlGenerationResult("select ?", List.of("p1"), null);
        List<Map<String, Object>> rows = List.of(Map.of("value", 7));

        when(service.generateSql("SalesQM", request, context)).thenReturn(generated);
        when(service.executeSql("select ?", List.of("p1"), "SalesQM")).thenReturn(rows);

        assertEquals(new SemanticSqlGeneration("select ?", List.of("p1")),
                service.generateRollupSql("SalesQM", request, context));
        assertSame(rows, service.executeRollupSql("select ?", List.of("p1"), "SalesQM"));
    }
}
