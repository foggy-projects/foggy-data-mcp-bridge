package com.foggyframework.dataset.model.port;

import com.foggyframework.dataset.model.engine.pivot.PivotPipeline;
import com.foggyframework.dataset.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryFacadePortBoundaryTest {

    @Test
    void advancedFacadeProvidesInternalPortsWithoutExpandingStableFacade() throws Exception {
        Class<com.foggyframework.dataset.model.service.AdvancedQueryFacade> advanced =
                com.foggyframework.dataset.model.service.AdvancedQueryFacade.class;

        assertTrue(AdvancedQueryExecutionPort.class.isAssignableFrom(advanced));
        assertTrue(InternalQueryExecutionPort.class.isAssignableFrom(AdvancedQueryExecutionPort.class));
        assertTrue(ManagedRelationExecutionPort.class.isAssignableFrom(AdvancedQueryExecutionPort.class));
        assertEquals(java.util.Set.of("queryModelResult", "buildSqlOnly", "buildComposeSqlOnly"),
                java.util.Arrays.stream(InternalQueryExecutionPort.class.getDeclaredMethods())
                        .map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet()));
        assertTrue(InternalQueryExecutionPort.class.getMethod("buildComposeSqlOnly",
                com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext.class).isDefault());
        assertEquals(1, com.foggyframework.dataset.model.api.QueryFacade.class.getDeclaredMethods().length);
        assertEquals(2, ManagedRelationExecutionPort.class.getDeclaredMethods().length);
    }

    @Test
    void legacyExecutionPortKeepsItsMethodsAndRejectsUnprovenCompose() {
        InternalQueryExecutionPort legacy = new InternalQueryExecutionPort() {
            public com.foggyframework.dataset.model.engine.query.DbQueryResult queryModelResult(
                    com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext context) {
                throw new AssertionError("No data query is allowed");
            }
            public com.foggyframework.dataset.model.engine.compose.SqlGenerationResult buildSqlOnly(
                    com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext context) {
                throw new AssertionError("Unproven SQL must not be returned");
            }
        };
        var failure=org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> legacy.buildComposeSqlOnly(new com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext()));
        assertEquals("COMPOSE_RESULT_GOVERNANCE_UNPROVEN",failure.getMessage());
    }

    @Test
    void semanticAndPivotDependOnNarrowExecutionPorts() throws Exception {
        assertFieldType(SemanticQueryServiceV3Impl.class, "queryFacade", AdvancedQueryExecutionPort.class);
        assertFieldType(PivotPipeline.class, "queryFacade", ManagedRelationExecutionPort.class);
    }

    private void assertFieldType(Class<?> owner, String fieldName, Class<?> expectedType) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        assertEquals(expectedType, field.getType());
    }
}
