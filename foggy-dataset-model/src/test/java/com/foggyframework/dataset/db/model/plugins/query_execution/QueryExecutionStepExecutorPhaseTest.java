package com.foggyframework.dataset.db.model.plugins.query_execution;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class QueryExecutionStepExecutorPhaseTest {

    @Test
    public void testPhaseFiltering() {
        QueryExecutionStep stepSupported = mock(QueryExecutionStep.class);
        when(stepSupported.supports(eq(QueryExecutionPhase.PREPARE_MANAGED_RELATION), any())).thenReturn(true);
        when(stepSupported.beforeExecute(eq(QueryExecutionPhase.PREPARE_MANAGED_RELATION), any())).thenReturn(QueryExecutionStep.CONTINUE);
        when(stepSupported.order()).thenReturn(100);

        QueryExecutionStep stepNotSupported = mock(QueryExecutionStep.class);
        when(stepNotSupported.supports(eq(QueryExecutionPhase.PREPARE_MANAGED_RELATION), any())).thenReturn(false);
        when(stepNotSupported.order()).thenReturn(200);

        QueryExecutionStepExecutor executor = new QueryExecutionStepExecutor(Arrays.asList(stepSupported, stepNotSupported));

        QueryExecutionContext ctx = new QueryExecutionContext();
        
        int result = executor.executeBeforeExecute(QueryExecutionPhase.PREPARE_MANAGED_RELATION, ctx);

        assertEquals(QueryExecutionStep.CONTINUE, result);
        
        // Ensure supported step was called in beforeExecute
        verify(stepSupported, times(1)).beforeExecute(eq(QueryExecutionPhase.PREPARE_MANAGED_RELATION), any());
        
        // Ensure unsupported step was NOT called
        verify(stepNotSupported, never()).beforeExecute(any(), any());
    }
}
