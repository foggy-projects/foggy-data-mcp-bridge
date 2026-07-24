package com.foggyframework.dataset.model.engine.compose.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * State machine tests for {@link ScriptRunState}.
 * Mirrors Python test_suspension_state_machine.py.
 */
class ScriptRunStateTest {

    // -- enum completeness --------------------------------------------------

    @Test
    void allStatesPresent() {
        Set<ScriptRunState> expected = EnumSet.of(
                ScriptRunState.RUNNING,
                ScriptRunState.SUSPENDED,
                ScriptRunState.REJECTED,
                ScriptRunState.TIMED_OUT,
                ScriptRunState.ABORTED,
                ScriptRunState.COMPLETED
        );
        assertEquals(expected, EnumSet.allOf(ScriptRunState.class),
                "ScriptRunState must have exactly 6 states");
    }

    @Test
    void noResumedState() {
        assertTrue(Arrays.stream(ScriptRunState.values())
                        .noneMatch(s -> s.name().equals("RESUMED")),
                "RESUMED state must NOT exist");
    }

    // -- valid transitions --------------------------------------------------

    @Test
    void runningCanSuspend() {
        assertTrue(ScriptRunState.RUNNING.canTransitionTo(ScriptRunState.SUSPENDED));
    }

    @Test
    void runningCanComplete() {
        assertTrue(ScriptRunState.RUNNING.canTransitionTo(ScriptRunState.COMPLETED));
    }

    @Test
    void runningCanAbort() {
        assertTrue(ScriptRunState.RUNNING.canTransitionTo(ScriptRunState.ABORTED));
    }

    @Test
    void suspendedCanResume() {
        assertTrue(ScriptRunState.SUSPENDED.canTransitionTo(ScriptRunState.RUNNING));
    }

    @Test
    void suspendedCanReject() {
        assertTrue(ScriptRunState.SUSPENDED.canTransitionTo(ScriptRunState.REJECTED));
    }

    @Test
    void suspendedCanTimeout() {
        assertTrue(ScriptRunState.SUSPENDED.canTransitionTo(ScriptRunState.TIMED_OUT));
    }

    @Test
    void suspendedCanAbort() {
        assertTrue(ScriptRunState.SUSPENDED.canTransitionTo(ScriptRunState.ABORTED));
    }

    // -- terminal states ----------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = ScriptRunState.class,
            names = {"REJECTED", "TIMED_OUT", "ABORTED", "COMPLETED"})
    void terminalStatesAreTerminal(ScriptRunState state) {
        assertTrue(state.isTerminal());
    }

    @ParameterizedTest
    @EnumSource(value = ScriptRunState.class,
            names = {"RUNNING", "SUSPENDED"})
    void nonTerminalStates(ScriptRunState state) {
        assertFalse(state.isTerminal());
    }

    @ParameterizedTest
    @EnumSource(value = ScriptRunState.class,
            names = {"REJECTED", "TIMED_OUT", "ABORTED", "COMPLETED"})
    void terminalStatesBlockAllTransitions(ScriptRunState terminal) {
        for (ScriptRunState target : ScriptRunState.values()) {
            assertFalse(terminal.canTransitionTo(target),
                    terminal + " should not transition to " + target);
        }
    }

    // -- ScriptRunContext transition tests -----------------------------------

    @Test
    void contextTransitionSucceeds() {
        ScriptRunContext ctx = new ScriptRunContext();
        assertEquals(ScriptRunState.RUNNING, ctx.getState());

        ctx.transition(ScriptRunState.SUSPENDED);
        assertEquals(ScriptRunState.SUSPENDED, ctx.getState());

        ctx.transition(ScriptRunState.RUNNING);
        assertEquals(ScriptRunState.RUNNING, ctx.getState());

        ctx.transition(ScriptRunState.COMPLETED);
        assertEquals(ScriptRunState.COMPLETED, ctx.getState());
        assertTrue(ctx.isTerminal());
    }

    @Test
    void contextTransitionFails() {
        ScriptRunContext ctx = new ScriptRunContext();
        assertThrows(ScriptSuspendException.StateInvalid.class,
                () -> ctx.transition(ScriptRunState.REJECTED));
    }

    @Test
    void suspendResumeComplete() {
        ScriptRunContext ctx = new ScriptRunContext();
        ctx.transition(ScriptRunState.SUSPENDED);
        ctx.transition(ScriptRunState.RUNNING);
        ctx.transition(ScriptRunState.COMPLETED);
        assertTrue(ctx.isTerminal());
    }
}
