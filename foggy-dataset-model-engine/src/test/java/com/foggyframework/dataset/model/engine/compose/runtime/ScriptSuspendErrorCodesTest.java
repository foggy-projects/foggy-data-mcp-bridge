package com.foggyframework.dataset.model.engine.compose.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for error code constants parity with Python.
 */
class ScriptSuspendErrorCodesTest {

    @Test
    void allCodesMatchPython() {
        assertEquals("script/pause-not-in-run", ScriptSuspendErrorCodes.PAUSE_NOT_IN_RUN);
        assertEquals("script/pause-not-allowed", ScriptSuspendErrorCodes.PAUSE_NOT_ALLOWED);
        assertEquals("script/suspend-limit-exceeded", ScriptSuspendErrorCodes.SUSPEND_LIMIT_EXCEEDED);
        assertEquals("script/suspend-timeout", ScriptSuspendErrorCodes.SUSPEND_TIMEOUT);
        assertEquals("script/suspend-rejected", ScriptSuspendErrorCodes.SUSPEND_REJECTED);
        assertEquals("script/resume-token-invalid", ScriptSuspendErrorCodes.RESUME_TOKEN_INVALID);
        assertEquals("script/resume-payload-invalid", ScriptSuspendErrorCodes.RESUME_PAYLOAD_INVALID);
        assertEquals("script/suspend-state-invalid", ScriptSuspendErrorCodes.SUSPEND_STATE_INVALID);
    }

    @Test
    void allCodesInScriptNamespace() {
        assertTrue(ScriptSuspendErrorCodes.PAUSE_NOT_IN_RUN.startsWith("script/"));
        assertTrue(ScriptSuspendErrorCodes.PAUSE_NOT_ALLOWED.startsWith("script/"));
        assertTrue(ScriptSuspendErrorCodes.SUSPEND_LIMIT_EXCEEDED.startsWith("script/"));
        assertTrue(ScriptSuspendErrorCodes.SUSPEND_TIMEOUT.startsWith("script/"));
        assertTrue(ScriptSuspendErrorCodes.SUSPEND_REJECTED.startsWith("script/"));
        assertTrue(ScriptSuspendErrorCodes.RESUME_TOKEN_INVALID.startsWith("script/"));
        assertTrue(ScriptSuspendErrorCodes.RESUME_PAYLOAD_INVALID.startsWith("script/"));
        assertTrue(ScriptSuspendErrorCodes.SUSPEND_STATE_INVALID.startsWith("script/"));
    }

    @Test
    void exceptionHierarchyCarriesCode() {
        var e1 = new ScriptSuspendException.PauseNotInRun();
        assertEquals(ScriptSuspendErrorCodes.PAUSE_NOT_IN_RUN, e1.getCode());

        var e2 = new ScriptSuspendException.LimitExceeded();
        assertEquals(ScriptSuspendErrorCodes.SUSPEND_LIMIT_EXCEEDED, e2.getCode());

        var e3 = new ScriptSuspendException.Timeout();
        assertEquals(ScriptSuspendErrorCodes.SUSPEND_TIMEOUT, e3.getCode());

        var e4 = new ScriptSuspendException.Rejected();
        assertEquals(ScriptSuspendErrorCodes.SUSPEND_REJECTED, e4.getCode());

        var e5 = new ScriptSuspendException.ResumeTokenInvalid();
        assertEquals(ScriptSuspendErrorCodes.RESUME_TOKEN_INVALID, e5.getCode());

        var e6 = new ScriptSuspendException.StateInvalid();
        assertEquals(ScriptSuspendErrorCodes.SUSPEND_STATE_INVALID, e6.getCode());

        var e7 = new ScriptSuspendException.PauseNotAllowed();
        assertEquals(ScriptSuspendErrorCodes.PAUSE_NOT_ALLOWED, e7.getCode());
    }

    @Test
    void allExceptionsExtendBase() {
        assertInstanceOf(ScriptSuspendException.class, new ScriptSuspendException.PauseNotInRun());
        assertInstanceOf(ScriptSuspendException.class, new ScriptSuspendException.LimitExceeded());
        assertInstanceOf(ScriptSuspendException.class, new ScriptSuspendException.Timeout());
        assertInstanceOf(ScriptSuspendException.class, new ScriptSuspendException.Rejected());
        assertInstanceOf(ScriptSuspendException.class, new ScriptSuspendException.ResumeTokenInvalid());
        assertInstanceOf(ScriptSuspendException.class, new ScriptSuspendException.StateInvalid());
        assertInstanceOf(ScriptSuspendException.class, new ScriptSuspendException.PauseNotAllowed());
    }

    @Test
    void allExceptionsAreRuntimeExceptions() {
        assertInstanceOf(RuntimeException.class, new ScriptSuspendException.PauseNotInRun());
    }
}
