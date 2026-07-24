package com.foggyframework.dataset.db.model.port;

import com.foggyframework.dataset.db.model.engine.pivot.PivotPipeline;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryFacadePortBoundaryTest {

    @Test
    void legacyFacadeProvidesInternalPortsWithoutExpandingStableFacade() {
        Class<com.foggyframework.dataset.db.model.service.QueryFacade> legacy =
                com.foggyframework.dataset.db.model.service.QueryFacade.class;

        assertTrue(AdvancedQueryExecutionPort.class.isAssignableFrom(legacy));
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
