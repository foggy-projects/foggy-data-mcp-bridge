package com.foggyframework.dataset.db.model.engine.compose.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ComposePause} static entry point.
 * Mirrors Python test_handler_pause.py.
 */
class ComposePauseTest {

    @AfterEach
    void cleanup() {
        ScriptRunContextHolder.clearForTesting();
        ComposePause.clearForTesting();
    }

    @Test
    void pauseOutsideRunThrows() {
        ScriptSuspendException.PauseNotInRun ex = assertThrows(
                ScriptSuspendException.PauseNotInRun.class,
                () -> ComposePause.pause("r", java.util.Map.of(), 1000));
        assertEquals(ScriptSuspendErrorCodes.PAUSE_NOT_IN_RUN, ex.getCode());
    }

    @Test
    void pauseWithoutManagerThrows() {
        ScriptRunContext ctx = new ScriptRunContext();
        ScriptRunContextHolder.Token token = ScriptRunContextHolder.set(ctx);
        try {
            assertThrows(ScriptSuspendException.PauseNotInRun.class,
                    () -> ComposePause.pause("r", java.util.Map.of(), 1000));
        } finally {
            ScriptRunContextHolder.pop(token);
        }
    }

    @Test
    void pauseAndResumeViaComposePause() throws Exception {
        SuspensionManager mgr = new SuspensionManager();
        ScriptRunContext ctx = new ScriptRunContext();
        mgr.registerRun(ctx);

        ScriptRunContextHolder.Token token = ScriptRunContextHolder.set(ctx);
        ComposePause.CURRENT_MANAGER.set(mgr);

        java.util.concurrent.atomic.AtomicReference<java.util.Map<String, Object>> resultRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> errorRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);

        // Handler calls ComposePause.pause in a child thread (simulating facade dispatch)
        Thread handler = new Thread(() -> {
            // Propagate context to child thread
            ScriptRunContextHolder.Token childToken = ScriptRunContextHolder.set(ctx);
            ComposePause.CURRENT_MANAGER.set(mgr);
            started.countDown();
            try {
                resultRef.set(ComposePause.pause("test", java.util.Map.of(), 5000));
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                ScriptRunContextHolder.pop(childToken);
                ComposePause.CURRENT_MANAGER.remove();
            }
        });
        handler.start();
        assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
        Thread.sleep(50);

        // Resume
        SuspensionResult suspension = ctx.getSuspension();
        assertNotNull(suspension);
        mgr.resume(new ResumeCommand(
                ctx.getRunId(), suspension.getSuspendId(),
                java.util.Map.of("status", "ok")));

        handler.join(5000);
        assertNull(errorRef.get(), () -> "handler error: " + errorRef.get());
        assertEquals(java.util.Map.of("status", "ok"), resultRef.get());

        // Cleanup
        ScriptRunContextHolder.pop(token);
        ComposePause.CURRENT_MANAGER.remove();
    }
}
