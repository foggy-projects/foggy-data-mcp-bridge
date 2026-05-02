package com.foggyframework.dataset.db.model.engine.compose.capability;

import com.foggyframework.dataset.db.model.engine.compose.runtime.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real integration tests for ObjectFacadeProxy + ComposePause.
 *
 * <p>These tests go through the actual ObjectFacadeProxy dispatch path
 * (executor child thread) to verify that ScriptRunContext and
 * SuspensionManager are correctly propagated.</p>
 *
 * @since 8.5.0 (P2.5-fix)
 */
class ObjectFacadePauseIntegrationTest {

    private SuspensionManager manager;
    private ScriptRunContext runCtx;
    private ObjectFacadeProxy proxy;
    private ScriptRunContextHolder.Token ctxToken;

    /**
     * A service whose method calls ComposePause.pause() —
     * this is the real handler-internal pause path.
     */
    static class PausingService {
        public Map<String, Object> reviewAndApprove() {
            return ComposePause.pause("business.review", Map.of("action", "approve"), 5000);
        }
    }

    @BeforeEach
    void setUp() {
        manager = new SuspensionManager();
        runCtx = new ScriptRunContext();
        manager.registerRun(runCtx);

        // Set up ThreadLocal context (simulating ScriptRuntime.runScript)
        ctxToken = ScriptRunContextHolder.set(runCtx);
        ComposePause.setManager(manager);

        // Build proxy for PausingService
        ObjectFacadeDescriptor descriptor = new ObjectFacadeDescriptor("pausing_svc", List.of(
                new MethodDescriptor("reviewAndApprove", List.of(), "dict",
                        "none", "write", 10_000, "p.review")
        ));
        CapabilityPolicy policy = new CapabilityPolicy(
                Set.of(),
                Map.of("pausing_svc", Set.of("reviewAndApprove")),
                Set.of("write")
        );
        proxy = new ObjectFacadeProxy(descriptor, new PausingService(), policy);
    }

    @AfterEach
    void tearDown() {
        ComposePause.removeManager();
        ScriptRunContextHolder.pop(ctxToken);
        ScriptRunContextHolder.clearForTesting();
        ComposePause.clearForTesting();
    }

    // -----------------------------------------------------------------------
    // Positive: facade method pauses, then resume delivers payload
    // -----------------------------------------------------------------------

    @Test
    void facadeMethodPauseAndResume() throws Exception {
        AtomicReference<Object> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch invokeStarted = new CountDownLatch(1);

        // Run the proxy.invoke in a separate thread (simulating script execution)
        Thread scriptThread = new Thread(() -> {
            // Re-establish ThreadLocal context in this thread
            ScriptRunContextHolder.Token t = ScriptRunContextHolder.set(runCtx);
            ComposePause.setManager(manager);
            invokeStarted.countDown();
            try {
                resultRef.set(proxy.invoke("reviewAndApprove"));
            } catch (Throwable ex) {
                errorRef.set(ex);
            } finally {
                ComposePause.removeManager();
                ScriptRunContextHolder.pop(t);
            }
        });
        scriptThread.start();
        assertTrue(invokeStarted.await(2, TimeUnit.SECONDS));
        Thread.sleep(100); // let handler block in pause

        // Verify run is SUSPENDED
        assertEquals(ScriptRunState.SUSPENDED, runCtx.getState());
        SuspensionResult suspension = runCtx.getSuspension();
        assertNotNull(suspension, "suspension result should be set");

        // Resume with payload
        manager.resume(new ResumeCommand(
                runCtx.getRunId(), suspension.getSuspendId(),
                Map.of("approved", true, "reviewer", "admin")));

        scriptThread.join(5000);
        assertFalse(scriptThread.isAlive(), "script thread should have completed");
        assertNull(errorRef.get(), () -> "unexpected error: " + errorRef.get());

        // The facade method returns the resume payload
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resultRef.get();
        assertEquals(true, result.get("approved"));
        assertEquals("admin", result.get("reviewer"));

        // Run should be back to RUNNING (resume transitions SUSPENDED → RUNNING)
        assertEquals(ScriptRunState.RUNNING, runCtx.getState());
        assertEquals(0, manager.activeSlotCount());
    }

    // -----------------------------------------------------------------------
    // Negative: facade method pauses, then reject — exception passes through
    // -----------------------------------------------------------------------

    @Test
    void facadeMethodPauseAndReject() throws Exception {
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch invokeStarted = new CountDownLatch(1);

        Thread scriptThread = new Thread(() -> {
            ScriptRunContextHolder.Token t = ScriptRunContextHolder.set(runCtx);
            ComposePause.setManager(manager);
            invokeStarted.countDown();
            try {
                proxy.invoke("reviewAndApprove");
            } catch (Throwable ex) {
                errorRef.set(ex);
            } finally {
                ComposePause.removeManager();
                ScriptRunContextHolder.pop(t);
            }
        });
        scriptThread.start();
        assertTrue(invokeStarted.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);

        SuspensionResult suspension = runCtx.getSuspension();
        assertNotNull(suspension);

        // Reject
        manager.reject(new RejectCommand(
                runCtx.getRunId(), suspension.getSuspendId(), "not authorized"));

        scriptThread.join(5000);

        // ScriptSuspendException.Rejected must pass through ObjectFacadeProxy
        assertNotNull(errorRef.get(), "should have caught an exception");
        assertInstanceOf(ScriptSuspendException.Rejected.class, errorRef.get());
        assertEquals(ScriptSuspendErrorCodes.SUSPEND_REJECTED,
                ((ScriptSuspendException) errorRef.get()).getCode());
        assertEquals(0, manager.activeSlotCount());
    }

    // -----------------------------------------------------------------------
    // Negative: facade method pauses, then timeout — exception passes through
    // -----------------------------------------------------------------------

    @Test
    void facadeMethodPauseAndTimeout() throws Exception {
        // Use a service with a very short timeout
        ObjectFacadeDescriptor descriptor = new ObjectFacadeDescriptor("short_svc", List.of(
                new MethodDescriptor("reviewAndApprove", List.of(), "dict",
                        "none", "write", 10_000, "p.review")
        ));
        CapabilityPolicy policy = new CapabilityPolicy(
                Set.of(),
                Map.of("short_svc", Set.of("reviewAndApprove")),
                Set.of("write")
        );

        // Override with a service that uses 100ms timeout
        PausingService shortTimeoutSvc = new PausingService() {
            @Override
            public Map<String, Object> reviewAndApprove() {
                return ComposePause.pause("business.review",
                        Map.of("action", "approve"), 150);
            }
        };
        ObjectFacadeProxy shortProxy = new ObjectFacadeProxy(
                descriptor, shortTimeoutSvc, policy);

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        Thread scriptThread = new Thread(() -> {
            ScriptRunContextHolder.Token t = ScriptRunContextHolder.set(runCtx);
            ComposePause.setManager(manager);
            try {
                shortProxy.invoke("reviewAndApprove");
            } catch (Throwable ex) {
                errorRef.set(ex);
            } finally {
                ComposePause.removeManager();
                ScriptRunContextHolder.pop(t);
            }
        });
        scriptThread.start();
        scriptThread.join(5000);

        // Timeout exception must pass through
        assertNotNull(errorRef.get(), "should have caught a timeout exception");
        assertInstanceOf(ScriptSuspendException.Timeout.class, errorRef.get());
        assertEquals(ScriptSuspendErrorCodes.SUSPEND_TIMEOUT,
                ((ScriptSuspendException) errorRef.get()).getCode());
        assertEquals(0, manager.activeSlotCount());
    }
}
