package com.foggyframework.dataset.db.model.plugins.query_execution;

import com.foggyframework.dataset.db.model.plugins.pipeline.LoopDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class QueryExecutionStepExecutorLoopHookTest {

    @Test
    void defaultMaxLoopCountDisablesLoopHook() {
        QueryExecutionStep step = mock(QueryExecutionStep.class);
        when(step.order()).thenReturn(100);
        when(step.supports(eq(QueryExecutionPhase.NORMAL_QUERY), any())).thenReturn(true);
        when(step.beforeExecute(eq(QueryExecutionPhase.NORMAL_QUERY), any()))
                .thenReturn(QueryExecutionStep.CONTINUE);

        QueryExecutionContext ctx = new QueryExecutionContext();
        QueryExecutionStepExecutor executor = new QueryExecutionStepExecutor(List.of(step));

        int result = executor.executeBeforeExecute(QueryExecutionPhase.NORMAL_QUERY, ctx);

        assertEquals(QueryExecutionStep.CONTINUE, result);
        verify(step, never()).supportsLoop(any(), any());
        verify(step, never()).runLoop(any(), any());
        assertEquals(0, ctx.getLoopTrace().size());
    }

    @Test
    void unsupportedLoopStepDoesNotRunHook() {
        QueryExecutionStep step = mock(QueryExecutionStep.class);
        when(step.order()).thenReturn(100);
        when(step.supports(eq(QueryExecutionPhase.NORMAL_QUERY), any())).thenReturn(true);
        when(step.beforeExecute(eq(QueryExecutionPhase.NORMAL_QUERY), any()))
                .thenReturn(QueryExecutionStep.CONTINUE);
        when(step.supportsLoop(eq(QueryExecutionPhase.NORMAL_QUERY), any())).thenReturn(false);

        QueryExecutionContext ctx = new QueryExecutionContext();
        ctx.setMaxLoopCount(3);
        QueryExecutionStepExecutor executor = new QueryExecutionStepExecutor(List.of(step));

        int result = executor.executeBeforeExecute(QueryExecutionPhase.NORMAL_QUERY, ctx);

        assertEquals(QueryExecutionStep.CONTINUE, result);
        verify(step, never()).runLoop(any(), any());
        assertEquals(0, ctx.getLoopTrace().size());
    }

    @Test
    void loopHookStopsWhenIterationIsUnchanged() {
        CountingLoopStep step = new CountingLoopStep(
                LoopDecision.changed("normalized"),
                LoopDecision.unchanged("stable"));
        QueryExecutionContext ctx = new QueryExecutionContext();
        ctx.setMaxLoopCount(4);
        QueryExecutionStepExecutor executor = new QueryExecutionStepExecutor(List.of(step));

        int result = executor.executeBeforeExecute(QueryExecutionPhase.NORMAL_QUERY, ctx);

        assertEquals(QueryExecutionStep.CONTINUE, result);
        assertEquals(1, step.beforeCount);
        assertEquals(2, step.loopCount);
        assertEquals(1, ctx.getLoopIndex());
        assertEquals(2, ctx.getLoopTrace().size());
        assertEquals("normalized", ctx.getLoopTrace().get(0).getReason());
        assertEquals("stable", ctx.getLoopTrace().get(1).getReason());
    }

    @Test
    void loopHookHonorsExplicitStopDecision() {
        CountingLoopStep step = new CountingLoopStep(LoopDecision.stop("done"));
        QueryExecutionContext ctx = new QueryExecutionContext();
        ctx.setMaxLoopCount(4);
        QueryExecutionStepExecutor executor = new QueryExecutionStepExecutor(List.of(step));

        int result = executor.executeBeforeExecute(QueryExecutionPhase.NORMAL_QUERY, ctx);

        assertEquals(QueryExecutionStep.CONTINUE, result);
        assertEquals(1, step.loopCount);
        assertEquals(true, ctx.isLoopStopRequested());
        assertEquals("done", ctx.getLoopStopReason());
        assertEquals(1, ctx.getLoopTrace().size());
    }

    @Test
    void loopHookFailsClosedWhenMaxLoopCountIsExceeded() {
        CountingLoopStep step = new CountingLoopStep(
                LoopDecision.changed("pass-1"),
                LoopDecision.changed("pass-2"));
        QueryExecutionContext ctx = new QueryExecutionContext();
        ctx.setMaxLoopCount(2);
        QueryExecutionStepExecutor executor = new QueryExecutionStepExecutor(List.of(step));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> executor.executeBeforeExecute(QueryExecutionPhase.NORMAL_QUERY, ctx));

        assertEquals("Pipeline loop hook exceeded maxLoopCount=2 for phase=NORMAL_QUERY", ex.getMessage());
        assertEquals(2, step.loopCount);
        assertEquals(2, ctx.getLoopTrace().size());
    }

    @Test
    void abortedMainPipelineDoesNotRunLoopHook() {
        QueryExecutionStep abortingStep = mock(QueryExecutionStep.class);
        when(abortingStep.order()).thenReturn(100);
        when(abortingStep.supports(eq(QueryExecutionPhase.NORMAL_QUERY), any())).thenReturn(true);
        when(abortingStep.beforeExecute(eq(QueryExecutionPhase.NORMAL_QUERY), any()))
                .thenReturn(QueryExecutionStep.ABORT);

        QueryExecutionContext ctx = new QueryExecutionContext();
        ctx.setMaxLoopCount(4);
        QueryExecutionStepExecutor executor = new QueryExecutionStepExecutor(List.of(abortingStep));

        int result = executor.executeBeforeExecute(QueryExecutionPhase.NORMAL_QUERY, ctx);

        assertEquals(QueryExecutionStep.ABORT, result);
        verify(abortingStep, never()).supportsLoop(any(), any());
        verify(abortingStep, never()).runLoop(any(), any());
    }

    @Test
    void afterExecuteDoesNotRunLoopHook() {
        QueryExecutionStep step = mock(QueryExecutionStep.class);
        when(step.order()).thenReturn(100);
        when(step.supports(eq(QueryExecutionPhase.NORMAL_QUERY), any())).thenReturn(true);
        when(step.afterExecute(eq(QueryExecutionPhase.NORMAL_QUERY), any()))
                .thenReturn(QueryExecutionStep.CONTINUE);
        when(step.supportsLoop(eq(QueryExecutionPhase.NORMAL_QUERY), any())).thenReturn(true);

        QueryExecutionContext ctx = new QueryExecutionContext();
        ctx.setMaxLoopCount(4);
        QueryExecutionStepExecutor executor = new QueryExecutionStepExecutor(List.of(step));

        int result = executor.executeAfterExecute(QueryExecutionPhase.NORMAL_QUERY, ctx);

        assertEquals(QueryExecutionStep.CONTINUE, result);
        verify(step, times(1)).afterExecute(eq(QueryExecutionPhase.NORMAL_QUERY), any());
        verify(step, never()).runLoop(any(), any());
        assertEquals(0, ctx.getLoopTrace().size());
    }

    @Test
    void loopHookClearsStaleStopBeforeSupportCheck() {
        StopSensitiveLoopStep step = new StopSensitiveLoopStep();
        QueryExecutionContext ctx = new QueryExecutionContext();
        ctx.setMaxLoopCount(4);
        QueryExecutionStepExecutor executor = new QueryExecutionStepExecutor(List.of(step));

        assertEquals(QueryExecutionStep.CONTINUE,
                executor.executeBeforeExecute(QueryExecutionPhase.NORMAL_QUERY, ctx));
        assertEquals(true, ctx.isLoopStopRequested());

        assertEquals(QueryExecutionStep.CONTINUE,
                executor.executeBeforeExecute(QueryExecutionPhase.NORMAL_QUERY, ctx));

        assertEquals(2, step.loopCount);
        assertEquals(true, ctx.isLoopStopRequested());
    }

    private static final class CountingLoopStep implements QueryExecutionStep {

        private final LoopDecision[] decisions;
        private int beforeCount;
        private int loopCount;

        private CountingLoopStep(LoopDecision... decisions) {
            this.decisions = decisions;
        }

        @Override
        public int order() {
            return 100;
        }

        @Override
        public boolean supports(QueryExecutionPhase phase, QueryExecutionContext ctx) {
            return phase == QueryExecutionPhase.NORMAL_QUERY;
        }

        @Override
        public int beforeExecute(QueryExecutionPhase phase, QueryExecutionContext ctx) {
            beforeCount++;
            return CONTINUE;
        }

        @Override
        public boolean supportsLoop(QueryExecutionPhase phase, QueryExecutionContext ctx) {
            return phase == QueryExecutionPhase.NORMAL_QUERY;
        }

        @Override
        public LoopDecision runLoop(QueryExecutionPhase phase, QueryExecutionContext ctx) {
            LoopDecision decision = decisions[Math.min(loopCount, decisions.length - 1)];
            loopCount++;
            return decision;
        }
    }

    private static final class StopSensitiveLoopStep implements QueryExecutionStep {

        private int loopCount;

        @Override
        public int order() {
            return 100;
        }

        @Override
        public boolean supports(QueryExecutionPhase phase, QueryExecutionContext ctx) {
            return phase == QueryExecutionPhase.NORMAL_QUERY;
        }

        @Override
        public boolean supportsLoop(QueryExecutionPhase phase, QueryExecutionContext ctx) {
            return phase == QueryExecutionPhase.NORMAL_QUERY && !ctx.isLoopStopRequested();
        }

        @Override
        public LoopDecision runLoop(QueryExecutionPhase phase, QueryExecutionContext ctx) {
            loopCount++;
            return LoopDecision.stop("done");
        }
    }
}
