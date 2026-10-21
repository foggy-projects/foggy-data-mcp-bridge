package com.foggyframework.dataset.model.engine.compose.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SuspensionManagerConcurrencyTest {
    enum Completion { RESUME, REJECT, ABORT }

    @ParameterizedTest
    @EnumSource(Completion.class)
    void completionWaitsUntilTimeoutIsScheduled(Completion completion) throws Exception {
        CountDownLatch scheduling = new CountDownLatch(1);
        CountDownLatch releaseSchedule = new CountDownLatch(1);
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1) {
            @Override public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
                scheduling.countDown();
                try {
                    if (!releaseSchedule.await(5, TimeUnit.SECONDS)) throw new AssertionError("schedule gate timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                return super.schedule(task, delay, unit);
            }
        };
        SuspensionManager manager = new SuspensionManager(1, id -> timer);
        ScriptRunContext context = new ScriptRunContext();
        manager.registerRun(context);
        AtomicReference<Throwable> pauseError = new AtomicReference<>();
        AtomicReference<Throwable> completionError = new AtomicReference<>();
        AtomicReference<Map<String, Object>> payload = new AtomicReference<>();
        Thread handler = new Thread(() -> {
            try { payload.set(manager.pauseAndWait(context.getRunId(), request())); }
            catch (Throwable failure) { pauseError.set(failure); }
        });
        Thread completer = new Thread(() -> {
            try {
                String token = context.getSuspension().getSuspendId();
                switch (completion) {
                    case RESUME -> manager.resume(new ResumeCommand(context.getRunId(), token, Map.of("ok", true)));
                    case REJECT -> manager.reject(new RejectCommand(context.getRunId(), token, "denied"));
                    case ABORT -> manager.abortRun(context.getRunId());
                }
            } catch (Throwable failure) { completionError.set(failure); }
        });
        try {
            handler.start();
            assertTrue(scheduling.await(5, TimeUnit.SECONDS));
            completer.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (completer.getState() != Thread.State.BLOCKED && completer.isAlive()
                    && System.nanoTime() < deadline) Thread.sleep(1);
            // The old implementation completes here and closes the executor before schedule.
            assertEquals(Thread.State.BLOCKED, completer.getState());
            assertFalse(timer.isShutdown());
            releaseSchedule.countDown();
            completer.join(5000);
            handler.join(5000);
            assertFalse(completer.isAlive());
            assertFalse(handler.isAlive());
            assertNull(completionError.get());
            switch (completion) {
                case RESUME -> { assertNull(pauseError.get()); assertEquals(Map.of("ok", true), payload.get()); }
                case REJECT -> assertInstanceOf(ScriptSuspendException.Rejected.class, pauseError.get());
                case ABORT -> assertInstanceOf(ScriptSuspendException.StateInvalid.class, pauseError.get());
            }
            assertEquals(0, manager.activeSlotCount());
            assertTrue(timer.isShutdown());
            assertTrue(timer.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            releaseSchedule.countDown();
            handler.interrupt();
            handler.join(5000);
            completer.join(5000);
            timer.shutdownNow();
        }
    }

    @Test void rejectedSchedulingRollsBackSuspension() {
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
        timer.shutdownNow();
        SuspensionManager manager = new SuspensionManager(1, id -> timer);
        assertInitializationFailure(manager, RejectedExecutionException.class);
        assertTrue(timer.isShutdown());
    }

    @Test void failedTimerCreationRollsBackSuspension() {
        SuspensionManager manager = new SuspensionManager(1, id -> { throw new IllegalStateException("unavailable"); });
        assertInitializationFailure(manager, IllegalStateException.class);
    }

    private static void assertInitializationFailure(SuspensionManager manager, Class<? extends Throwable> type) {
        ScriptRunContext context = new ScriptRunContext();
        manager.registerRun(context);
        assertThrows(type, () -> manager.pauseAndWait(context.getRunId(), request()));
        assertEquals(ScriptRunState.RUNNING, context.getState());
        assertNull(context.getSuspension());
        assertEquals(0, manager.activeSlotCount());
        assertEquals(1, manager.activeRunCount());
    }

    private static PauseRequest request() {
        return PauseRequest.builder().reason("concurrent completion").summary(Map.of()).timeoutMs(60_000).build();
    }
}
