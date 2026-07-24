package com.foggyframework.dataset.model.engine.compose.runtime;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

/**
 * In-process registry of active FSScript runs with blocking pause support.
 *
 * <p>Thread-safe.  All mutations are {@code synchronized} on the manager
 * instance.  Uses {@link CountDownLatch} for one-shot blocking wait per
 * suspension.</p>
 *
 * <p>Mirrors Python {@code SuspensionManager}.</p>
 *
 * @since 8.5.0
 */
public final class SuspensionManager {

    /** Default maximum concurrent active suspensions. */
    public static final int MAX_SUSPEND_COUNT = 100;

    private final int maxSuspendCount;
    private final ConcurrentHashMap<String, ScriptRunContext> runs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WaitSlot> slots = new ConcurrentHashMap<>();

    public SuspensionManager() {
        this(MAX_SUSPEND_COUNT);
    }

    public SuspensionManager(int maxSuspendCount) {
        this.maxSuspendCount = maxSuspendCount;
    }

    // -- run lifecycle ------------------------------------------------------

    /**
     * Register a new run context.
     *
     * @throws IllegalArgumentException if already registered
     */
    public synchronized void registerRun(ScriptRunContext ctx) {
        if (runs.containsKey(ctx.getRunId())) {
            throw new IllegalArgumentException("run " + ctx.getRunId() + " is already registered");
        }
        runs.put(ctx.getRunId(), ctx);
    }

    /** Look up a run by ID.  Returns null if not found. */
    public ScriptRunContext getRun(String runId) {
        return runs.get(runId);
    }

    /**
     * Complete a run (RUNNING → COMPLETED) and remove from active map.
     */
    public synchronized void completeRun(String runId) {
        ScriptRunContext ctx = runs.get(runId);
        if (ctx == null) return;
        ctx.transition(ScriptRunState.COMPLETED);
        runs.remove(runId);
    }

    /**
     * Abort a run and remove from active map.  If suspended, wake the
     * blocked thread with an abort error.
     */
    public synchronized void abortRun(String runId) {
        ScriptRunContext ctx = runs.get(runId);
        if (ctx == null) return;
        ctx.transition(ScriptRunState.ABORTED);
        // Wake any blocked thread
        SuspensionResult suspension = ctx.getSuspension();
        if (suspension != null) {
            WaitSlot slot = slots.remove(suspension.getSuspendId());
            if (slot != null) {
                slot.error = new ScriptSuspendException.StateInvalid("run was aborted");
                cancelTimer(slot);
                slot.latch.countDown();
            }
        }
        runs.remove(runId);
    }

    // -- blocking pause -----------------------------------------------------

    /**
     * Suspend a RUNNING run and block until resume, reject, or timeout.
     *
     * <p>This is the handler-thread entry point.  Under a single
     * {@code synchronized} block it transitions to SUSPENDED, creates
     * the {@link SuspensionResult}, registers the {@link WaitSlot},
     * and enforces {@code MAX_SUSPEND_COUNT}.</p>
     *
     * @return the resume payload
     * @throws ScriptSuspendException.Rejected on explicit reject
     * @throws ScriptSuspendException.Timeout  on timeout
     * @throws ScriptSuspendException.LimitExceeded if too many concurrent suspensions
     */
    public Map<String, Object> pauseAndWait(String runId, PauseRequest request) {
        String suspendId;
        WaitSlot slot = new WaitSlot();

        // Atomic: check limit, transition, register slot — all under lock
        synchronized (this) {
            if (slots.size() >= maxSuspendCount) {
                throw new ScriptSuspendException.LimitExceeded(
                        "concurrent suspension limit (" + maxSuspendCount + ") exceeded");
            }

            ScriptRunContext ctx = runs.get(runId);
            if (ctx == null) {
                throw new ScriptSuspendException.ResumeTokenInvalid(
                        "run " + runId + " is not registered");
            }
            ctx.transition(ScriptRunState.SUSPENDED);

            suspendId = ScriptRunContext.generateSuspendId();
            Instant timeoutAt = Instant.now().plusMillis(request.getTimeoutMs());
            SuspensionResult result = new SuspensionResult(
                    runId, suspendId, request.getReason(),
                    request.getSummary(), timeoutAt);
            ctx.setSuspension(result);
            slots.put(suspendId, slot);
        }

        // Start auto-timeout timer
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "suspend-timer-" + suspendId);
            t.setDaemon(true);
            return t;
        });
        slot.timer = timer;
        timer.schedule(() -> autoTimeout(runId, suspendId),
                request.getTimeoutMs(), TimeUnit.MILLISECONDS);

        // Block — guaranteed cleanup in finally
        try {
            slot.latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScriptSuspendException.Timeout("handler thread was interrupted");
        } finally {
            slots.remove(suspendId);
            cancelTimer(slot);
        }

        // Check result
        if (slot.error != null) {
            if (slot.error instanceof ScriptSuspendException sse) {
                throw sse;
            }
            throw new ScriptSuspendException(
                    ScriptSuspendErrorCodes.SUSPEND_STATE_INVALID,
                    "unexpected error: " + slot.error.getMessage(), slot.error);
        }
        return slot.payload;
    }

    // -- resume / reject / timeout ------------------------------------------

    /**
     * Resume a suspended run with a payload.
     */
    public synchronized void resume(ResumeCommand cmd) {
        ScriptRunContext ctx = runs.get(cmd.getScriptRunId());
        if (ctx == null) {
            throw new ScriptSuspendException.ResumeTokenInvalid(
                    "run " + cmd.getScriptRunId() + " is not registered");
        }
        SuspensionResult suspension = ctx.getSuspension();
        if (suspension == null || !suspension.getSuspendId().equals(cmd.getSuspendId())) {
            throw new ScriptSuspendException.ResumeTokenInvalid(
                    "suspend_id mismatch");
        }
        ctx.transition(ScriptRunState.RUNNING);
        ctx.setSuspension(null);

        WaitSlot slot = slots.get(cmd.getSuspendId());
        if (slot != null) {
            slot.payload = cmd.getPayload();
            cancelTimer(slot);
            slot.latch.countDown();
        }
    }

    /**
     * Reject a suspended run.
     */
    public synchronized void reject(RejectCommand cmd) {
        ScriptRunContext ctx = runs.get(cmd.getScriptRunId());
        if (ctx == null) {
            throw new ScriptSuspendException.ResumeTokenInvalid(
                    "run " + cmd.getScriptRunId() + " is not registered");
        }
        SuspensionResult suspension = ctx.getSuspension();
        if (suspension == null || !suspension.getSuspendId().equals(cmd.getSuspendId())) {
            throw new ScriptSuspendException.ResumeTokenInvalid(
                    "suspend_id mismatch");
        }
        ctx.transition(ScriptRunState.REJECTED);
        ctx.setSuspension(null);

        WaitSlot slot = slots.get(cmd.getSuspendId());
        if (slot != null) {
            String msg = cmd.getReason() != null ? cmd.getReason() : "suspend rejected";
            slot.error = new ScriptSuspendException.Rejected(msg);
            cancelTimer(slot);
            slot.latch.countDown();
        }
    }

    /**
     * Timeout a suspended run.
     */
    public synchronized void timeout(String runId, String suspendId) {
        ScriptRunContext ctx = runs.get(runId);
        if (ctx == null) return;  // already completed / aborted
        SuspensionResult suspension = ctx.getSuspension();
        if (suspension == null || !suspension.getSuspendId().equals(suspendId)) {
            return;  // stale timeout
        }
        if (ctx.getState() != ScriptRunState.SUSPENDED) return;

        ctx.transition(ScriptRunState.TIMED_OUT);
        ctx.setSuspension(null);

        WaitSlot slot = slots.get(suspendId);
        if (slot != null) {
            slot.error = new ScriptSuspendException.Timeout("suspend timed out");
            slot.latch.countDown();
        }
    }

    // -- internal -----------------------------------------------------------

    private void autoTimeout(String runId, String suspendId) {
        timeout(runId, suspendId);
    }

    private static void cancelTimer(WaitSlot slot) {
        if (slot.timer != null) {
            slot.timer.shutdownNow();
            slot.timer = null;
        }
    }

    /** Number of active wait slots (for testing). */
    public int activeSlotCount() {
        return slots.size();
    }

    /** Number of active runs (for testing). */
    public int activeRunCount() {
        return runs.size();
    }

    // -- WaitSlot -----------------------------------------------------------

    private static final class WaitSlot {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile Map<String, Object> payload;
        volatile Throwable error;
        volatile ScheduledExecutorService timer;
    }
}
