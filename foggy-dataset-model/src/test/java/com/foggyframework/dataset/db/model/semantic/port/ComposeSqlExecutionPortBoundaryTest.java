package com.foggyframework.dataset.db.model.semantic.port;

import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ComposeSqlExecutionPortBoundaryTest {

    @Test
    void legacySemanticServiceProvidesNarrowComposeSqlExecutionPort() {
        assertTrue(ComposeSqlExecutionPort.class.isAssignableFrom(SemanticQueryServiceV3.class));
    }
}
