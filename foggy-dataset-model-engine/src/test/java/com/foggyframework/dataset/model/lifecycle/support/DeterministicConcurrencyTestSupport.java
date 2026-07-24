package com.foggyframework.dataset.model.lifecycle.support;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class DeterministicConcurrencyTestSupport {

    public static final long STEP_TIMEOUT_SECONDS = 5;

    private DeterministicConcurrencyTestSupport() {
    }

    public static void await(CountDownLatch latch, String label) {
        try {
            assertTrue(
                    latch.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    () -> "Timed out waiting for " + label + ", remaining=" + latch.getCount()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for " + label, e);
        }
    }

    public static void rendezvous(Phaser phaser, String label) {
        int phase = phaser.arrive();
        try {
            phaser.awaitAdvanceInterruptibly(phase, STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted at phaser " + label, e);
        } catch (TimeoutException e) {
            throw new AssertionError("Timed out at phaser " + label + ", phase=" + phase, e);
        }
    }

    public static <T> T get(Future<T> future, String label) {
        try {
            return future.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted waiting for future " + label, e);
        } catch (ExecutionException e) {
            throw new AssertionError("Future failed: " + label, e.getCause());
        } catch (TimeoutException e) {
            throw new AssertionError("Timed out waiting for future " + label, e);
        }
    }

    public static void cancelIncomplete(Iterable<? extends Future<?>> futures) {
        for (Future<?> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    public static void shutdownAndAssertTerminated(ExecutorService executor, String label) {
        executor.shutdown();
        boolean interrupted = awaitTerminationPreservingInterrupt(executor);
        if (!executor.isTerminated()) {
            executor.shutdownNow();
            interrupted |= awaitTerminationPreservingInterrupt(executor);
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        assertTrue(executor.isTerminated(), () -> "Executor is not terminated: " + label);
    }

    private static boolean awaitTerminationPreservingInterrupt(ExecutorService executor) {
        boolean interrupted = false;
        long timeoutNanos = TimeUnit.SECONDS.toNanos(STEP_TIMEOUT_SECONDS);
        long deadline = System.nanoTime() + timeoutNanos;
        while (!executor.isTerminated()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            try {
                executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        return interrupted;
    }

    public static final class ControlledSingleFlight<K, V> {

        private final Map<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();
        private final CountDownLatch winnerEntered = new CountDownLatch(1);
        private final CountDownLatch waitersJoined;
        private final CountDownLatch releaseWinner = new CountDownLatch(1);
        private final AtomicInteger buildCount = new AtomicInteger();

        public ControlledSingleFlight(int expectedWaiters) {
            if (expectedWaiters < 0) {
                throw new IllegalArgumentException("expectedWaiters must be >= 0");
            }
            this.waitersJoined = new CountDownLatch(expectedWaiters);
        }

        public V execute(K key, Callable<V> builder) {
            CompletableFuture<V> candidate = new CompletableFuture<>();
            CompletableFuture<V> shared = inFlight.putIfAbsent(key, candidate);
            boolean winner = shared == null;
            CompletableFuture<V> result = winner ? candidate : shared;

            if (winner) {
                buildCount.incrementAndGet();
                winnerEntered.countDown();
                try {
                    await(releaseWinner, "winner release");
                    result.complete(builder.call());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                } finally {
                    inFlight.remove(key, result);
                }
            } else {
                waitersJoined.countDown();
            }

            try {
                return result.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for controlled single-flight", e);
            } catch (ExecutionException e) {
                throw new AssertionError("Controlled builder failed", e.getCause());
            } catch (TimeoutException e) {
                throw new AssertionError("Timed out waiting for controlled single-flight", e);
            }
        }

        public void awaitWinnerAndWaiters() {
            await(winnerEntered, "single-flight winner");
            await(waitersJoined, "single-flight waiters");
        }

        public void releaseWinner() {
            releaseWinner.countDown();
        }

        public int buildCount() {
            return buildCount.get();
        }

        public int inFlightCount() {
            return inFlight.size();
        }
    }
}
