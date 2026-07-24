package com.foggyframework.dataset.model.engine.compose.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SuspensionManager}.
 * Mirrors Python test_suspension_manager.py + test_suspend_limits.py.
 */
class SuspensionManagerTest {

    private final SuspensionManager mgr = new SuspensionManager();

    @AfterEach
    void cleanup() {
        ScriptRunContextHolder.clearForTesting();
    }

    // -- register / complete / abort ----------------------------------------

    @Test
    void registerAndComplete() {
        ScriptRunContext ctx = new ScriptRunContext();
        mgr.registerRun(ctx);
        assertEquals(1, mgr.activeRunCount());

        mgr.completeRun(ctx.getRunId());
        assertEquals(0, mgr.activeRunCount());
    }

    @Test
    void doubleRegisterFails() {
        ScriptRunContext ctx = new ScriptRunContext();
        mgr.registerRun(ctx);
        assertThrows(IllegalArgumentException.class, () -> mgr.registerRun(ctx));
    }

    @Test
    void abortCleansUp() {
        ScriptRunContext ctx = new ScriptRunContext();
        mgr.registerRun(ctx);
        mgr.abortRun(ctx.getRunId());
        assertEquals(0, mgr.activeRunCount());
        assertEquals(ScriptRunState.ABORTED, ctx.getState());
    }

    // -- pause and resume ---------------------------------------------------

    @Test
    void pauseAndResumeReturnsPayload() throws Exception {
        ScriptRunContext ctx = new ScriptRunContext();
        mgr.registerRun(ctx);

        PauseRequest req = PauseRequest.builder()
                .reason("test.reason")
                .summary(Map.of("key", "val"))
                .timeoutMs(5000)
                .build();

        AtomicReference<Map<String, Object>> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);

        Thread handler = new Thread(() -> {
            started.countDown();
            try {
                resultRef.set(mgr.pauseAndWait(ctx.getRunId(), req));
            } catch (Throwable t) {
                errorRef.set(t);
            }
        });
        handler.start();
        assertTrue(started.await(2, TimeUnit.SECONDS));
        Thread.sleep(50); // let handler block

        // Resume
        SuspensionResult suspension = ctx.getSuspension();
        assertNotNull(suspension);
        mgr.resume(new ResumeCommand(
                ctx.getRunId(), suspension.getSuspendId(),
                Map.of("approved", true)));

        handler.join(5000);
        assertNull(errorRef.get());
        assertEquals(Map.of("approved", true), resultRef.get());
        assertEquals(0, mgr.activeSlotCount());
    }

    // -- pause and reject ---------------------------------------------------

    @Test
    void pauseAndRejectThrows() throws Exception {
        ScriptRunContext ctx = new ScriptRunContext();
        mgr.registerRun(ctx);

        PauseRequest req = PauseRequest.builder()
                .reason("r").summary(Map.of()).timeoutMs(5000).build();

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);

        Thread handler = new Thread(() -> {
            started.countDown();
            try {
                mgr.pauseAndWait(ctx.getRunId(), req);
            } catch (Throwable t) {
                errorRef.set(t);
            }
        });
        handler.start();
        assertTrue(started.await(2, TimeUnit.SECONDS));
        Thread.sleep(50);

        SuspensionResult suspension = ctx.getSuspension();
        mgr.reject(new RejectCommand(ctx.getRunId(), suspension.getSuspendId(), "denied"));

        handler.join(5000);
        assertInstanceOf(ScriptSuspendException.Rejected.class, errorRef.get());
        assertEquals(0, mgr.activeSlotCount());
    }

    // -- pause and timeout --------------------------------------------------

    @Test
    void pauseAndTimeoutThrows() throws Exception {
        ScriptRunContext ctx = new ScriptRunContext();
        mgr.registerRun(ctx);

        PauseRequest req = PauseRequest.builder()
                .reason("r").summary(Map.of()).timeoutMs(100).build();

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        Thread handler = new Thread(() -> {
            try {
                mgr.pauseAndWait(ctx.getRunId(), req);
            } catch (Throwable t) {
                errorRef.set(t);
            }
        });
        handler.start();
        handler.join(5000);

        assertInstanceOf(ScriptSuspendException.Timeout.class, errorRef.get());
        assertEquals(0, mgr.activeSlotCount());
    }

    // -- double resume fails ------------------------------------------------

    @Test
    void doubleResumeFails() throws Exception {
        ScriptRunContext ctx = new ScriptRunContext();
        mgr.registerRun(ctx);

        PauseRequest req = PauseRequest.builder()
                .reason("r").summary(Map.of()).timeoutMs(5000).build();

        CountDownLatch started = new CountDownLatch(1);
        Thread handler = new Thread(() -> {
            started.countDown();
            try { mgr.pauseAndWait(ctx.getRunId(), req); } catch (Exception ignored) {}
        });
        handler.start();
        assertTrue(started.await(2, TimeUnit.SECONDS));
        Thread.sleep(50);

        SuspensionResult suspension = ctx.getSuspension();
        mgr.resume(new ResumeCommand(ctx.getRunId(), suspension.getSuspendId(), Map.of()));

        handler.join(5000);

        // Second resume should fail — no suspension active
        assertThrows(ScriptSuspendException.ResumeTokenInvalid.class,
                () -> mgr.resume(new ResumeCommand(
                        ctx.getRunId(), suspension.getSuspendId(), Map.of())));
    }

    // -- invalid run id -----------------------------------------------------

    @Test
    void resumeInvalidRunFails() {
        assertThrows(ScriptSuspendException.ResumeTokenInvalid.class,
                () -> mgr.resume(new ResumeCommand("no-such-run", "sp_xxx", Map.of())));
    }

    // -- max suspend count --------------------------------------------------

    @Test
    void exceedLimitFails() throws Exception {
        SuspensionManager small = new SuspensionManager(2);

        // Fill up 2 slots
        for (int i = 0; i < 2; i++) {
            ScriptRunContext ctx = new ScriptRunContext("sr_fill_" + i);
            small.registerRun(ctx);
            new Thread(() -> {
                try {
                    small.pauseAndWait(ctx.getRunId(),
                            PauseRequest.builder().reason("r").summary(Map.of())
                                    .timeoutMs(10000).build());
                } catch (Exception ignored) {}
            }).start();
        }
        Thread.sleep(100); // let handlers block

        // Third should fail
        ScriptRunContext ctx3 = new ScriptRunContext("sr_fill_2");
        small.registerRun(ctx3);

        ScriptSuspendException.LimitExceeded ex = assertThrows(
                ScriptSuspendException.LimitExceeded.class,
                () -> small.pauseAndWait(ctx3.getRunId(),
                        PauseRequest.builder().reason("r").summary(Map.of())
                                .timeoutMs(5000).build()));
        assertEquals(ScriptSuspendErrorCodes.SUSPEND_LIMIT_EXCEEDED, ex.getCode());

        // ctx3 should still be RUNNING (limit failure doesn't change state)
        assertEquals(ScriptRunState.RUNNING, ctx3.getState());
    }

    // -- slot cleanup on abort ----------------------------------------------

    @Test
    void abortCleansSlotsAndTimer() throws Exception {
        ScriptRunContext ctx = new ScriptRunContext();
        mgr.registerRun(ctx);

        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Thread handler = new Thread(() -> {
            started.countDown();
            try {
                mgr.pauseAndWait(ctx.getRunId(),
                        PauseRequest.builder().reason("r").summary(Map.of())
                                .timeoutMs(60_000).build());
            } catch (Throwable t) {
                errorRef.set(t);
            }
        });
        handler.start();
        assertTrue(started.await(2, TimeUnit.SECONDS));
        Thread.sleep(50);

        mgr.abortRun(ctx.getRunId());
        handler.join(5000);

        assertEquals(0, mgr.activeSlotCount());
        assertEquals(0, mgr.activeRunCount());
        assertNotNull(errorRef.get());
    }

    // -- PauseRequest validation --------------------------------------------

    @Test
    void pauseRequestRejectsEmptyReason() {
        assertThrows(IllegalArgumentException.class,
                () -> PauseRequest.builder().reason("").summary(Map.of()).timeoutMs(1000).build());
    }

    @Test
    void pauseRequestRejectsZeroTimeout() {
        assertThrows(IllegalArgumentException.class,
                () -> PauseRequest.builder().reason("r").summary(Map.of()).timeoutMs(0).build());
    }

    @Test
    void pauseRequestRejectsExcessiveTimeout() {
        assertThrows(IllegalArgumentException.class,
                () -> PauseRequest.builder().reason("r").summary(Map.of())
                        .timeoutMs(PauseRequest.MAX_TIMEOUT_MS + 1).build());
    }

    @Test
    void pauseRequestAcceptsMaxTimeout() {
        PauseRequest req = PauseRequest.builder().reason("r").summary(Map.of())
                .timeoutMs(PauseRequest.MAX_TIMEOUT_MS).build();
        assertEquals(PauseRequest.MAX_TIMEOUT_MS, req.getTimeoutMs());
    }
}
