package com.foggyframework.dataset.model.semantic.port;

import com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeBundle;
import com.foggyframework.dataset.model.engine.compose.runtime.ComposeScriptService;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComposeSqlExecutionPortBoundaryTest {

    @Test
    void legacySemanticServiceProvidesNarrowComposeSqlExecutionPort() {
        assertTrue(ComposeSqlExecutionPort.class.isAssignableFrom(SemanticQueryServiceV3.class));
    }

    @Test
    void composeRuntimeStoresOnlyTheNarrowSqlExecutionPort() throws Exception {
        assertEquals(ComposeSqlExecutionPort.class,
                ComposeRuntimeBundle.class.getDeclaredField("executionPort").getType());
        assertEquals(ComposeSqlExecutionPort.class,
                ComposeScriptService.ComposeScriptRequest.class
                        .getDeclaredField("executionPort").getType());
    }
}
