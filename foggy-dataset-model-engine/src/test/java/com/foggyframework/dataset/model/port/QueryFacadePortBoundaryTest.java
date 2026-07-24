package com.foggyframework.dataset.model.port;

import com.foggyframework.dataset.model.engine.pivot.PivotPipeline;
import com.foggyframework.dataset.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryFacadePortBoundaryTest {

    @Test
    void advancedFacadeProvidesInternalPortsWithoutExpandingStableFacade() {
        Class<com.foggyframework.dataset.model.service.AdvancedQueryFacade> advanced =
                com.foggyframework.dataset.model.service.AdvancedQueryFacade.class;

        assertTrue(AdvancedQueryExecutionPort.class.isAssignableFrom(advanced));
        assertTrue(InternalQueryExecutionPort.class.isAssignableFrom(AdvancedQueryExecutionPort.class));
        assertTrue(ManagedRelationExecutionPort.class.isAssignableFrom(AdvancedQueryExecutionPort.class));
        assertEquals(2, InternalQueryExecutionPort.class.getDeclaredMethods().length);
        assertEquals(2, ManagedRelationExecutionPort.class.getDeclaredMethods().length);
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
