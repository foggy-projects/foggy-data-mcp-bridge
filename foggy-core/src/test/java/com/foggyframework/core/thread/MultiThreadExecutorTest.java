package com.foggyframework.core.thread;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MultiThreadExecutorTest {

    private static final long TIMEOUT_SECONDS = 5;

    @Test
    void waitWithoutShutdownUsesTaskSnapshotAndKeepsExecutorReusable() throws Exception {
        MultiThreadExecutor executor = new MultiThreadExecutor(1);
        CountDownLatch firstTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstTask = new CountDownLatch(1);
        CountDownLatch firstTaskFinished = new CountDownLatch(1);
        CountDownLatch waiterStarted = new CountDownLatch(1);
        CountDownLatch waiterFinished = new CountDownLatch(1);
        AtomicReference<Throwable> waiterFailure = new AtomicReference<>();

        executor.execute(() -> {
            firstTaskStarted.countDown();
            awaitTaskRelease(releaseFirstTask);
            firstTaskFinished.countDown();
        });

        Thread waiter = new Thread(() -> {
            waiterStarted.countDown();
            try {
                executor.waitAllCompleted(false);
            } catch (Throwable error) {
                waiterFailure.set(error);
            } finally {
                waiterFinished.countDown();
            }
        }, "multi-thread-executor-no-shutdown-waiter");

        try {
            assertLatch(firstTaskStarted, "first task did not start");
            waiter.start();
            assertLatch(waiterStarted, "waiter did not start");
            awaitCondition(() -> isWaiting(waiter), waiter,
                    "waitAllCompleted(false) returned before the task snapshot completed");
            assertFalse(firstTaskFinished.await(0, TimeUnit.MILLISECONDS));

            releaseFirstTask.countDown();
            assertLatch(waiterFinished, "waitAllCompleted(false) did not return");
            assertNull(waiterFailure.get());

            CountDownLatch secondTaskFinished = new CountDownLatch(1);
            executor.execute(secondTaskFinished::countDown);
            assertLatch(secondTaskFinished, "executor was not reusable after shutdown=false");
        } finally {
            releaseFirstTask.countDown();
            stopThread(waiter);
            shutdownNow(executor);
        }
    }

    @Test
    void stopIfHasErrorPropagatesFailureAndStopsRemainingTasks() throws Exception {
        MultiThreadExecutor executor = new MultiThreadExecutor(1);
        CountDownLatch blockedTaskStarted = new CountDownLatch(1);
        CountDownLatch blockedTaskRelease = new CountDownLatch(1);
        CountDownLatch blockedTaskInterrupted = new CountDownLatch(1);
        IllegalStateException taskFailure = new IllegalStateException("expected task failure");

        executor.execute(() -> {
            blockedTaskStarted.countDown();
            try {
                blockedTaskRelease.await();
            } catch (InterruptedException interrupted) {
                blockedTaskInterrupted.countDown();
                Thread.currentThread().interrupt();
            }
        });

        try {
            assertLatch(blockedTaskStarted, "blocking task did not start");
            executor.setError(taskFailure);
            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> executor.waitAllCompleted(true, true));

            assertSame(taskFailure, thrown.getCause());
            assertLatch(blockedTaskInterrupted, "stopIfHasError did not interrupt the remaining task");
            assertTrue(executor.executorService.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            blockedTaskRelease.countDown();
            shutdownNow(executor);
        }
    }

    @Test
    void waiterInterruptRestoresInterruptFlagAndStopsShutdownExecutor() throws Exception {
        MultiThreadExecutor executor = new MultiThreadExecutor(1);
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch taskRelease = new CountDownLatch(1);
        CountDownLatch taskInterrupted = new CountDownLatch(1);
        CountDownLatch waiterStarted = new CountDownLatch(1);
        CountDownLatch waiterFinished = new CountDownLatch(1);
        AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
        AtomicBoolean waiterInterruptRestored = new AtomicBoolean();

        executor.execute(() -> {
            taskStarted.countDown();
            try {
                taskRelease.await();
            } catch (InterruptedException interrupted) {
                taskInterrupted.countDown();
                Thread.currentThread().interrupt();
            }
        });

        Thread waiter = new Thread(() -> {
            waiterStarted.countDown();
            try {
                executor.waitAllCompleted(true);
            } catch (Throwable error) {
                waiterFailure.set(error);
                waiterInterruptRestored.set(Thread.currentThread().isInterrupted());
            } finally {
                waiterFinished.countDown();
            }
        }, "multi-thread-executor-interrupted-waiter");

        try {
            assertLatch(taskStarted, "task did not start");
            waiter.start();
            assertLatch(waiterStarted, "waiter did not start");
            awaitCondition(() -> executor.executorService.isShutdown() && isWaiting(waiter), waiter,
                    "waiter did not enter awaitTermination");

            waiter.interrupt();

            assertLatch(waiterFinished, "interrupted waiter did not return");
            assertTrue(waiterInterruptRestored.get(), "waiter interrupt flag was not restored");
            assertTrue(hasCause(waiterFailure.get(), InterruptedException.class),
                    "waiter failure did not retain InterruptedException");
            assertLatch(taskInterrupted, "interrupt did not stop the running task");
            assertTrue(executor.executorService.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            taskRelease.countDown();
            stopThread(waiter);
            shutdownNow(executor);
        }
    }

    private static void awaitTaskRelease(CountDownLatch release) {
        try {
            release.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void assertLatch(CountDownLatch latch, String message) throws InterruptedException {
        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), message);
    }

    private static void awaitCondition(BooleanSupplier condition, Thread thread, String message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (thread.isAlive() && System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        fail(message + "; state=" + thread.getState());
    }

    private static boolean isWaiting(Thread thread) {
        return thread.getState() == Thread.State.WAITING
                || thread.getState() == Thread.State.TIMED_WAITING;
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void stopThread(Thread thread) {
        if (thread.isAlive()) {
            thread.interrupt();
            try {
                thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void shutdownNow(MultiThreadExecutor executor) {
        executor.executorService.shutdownNow();
        try {
            executor.executorService.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
