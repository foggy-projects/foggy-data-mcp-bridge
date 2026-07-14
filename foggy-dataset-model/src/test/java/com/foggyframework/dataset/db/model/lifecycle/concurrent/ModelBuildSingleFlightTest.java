package com.foggyframework.dataset.db.model.lifecycle.concurrent;

import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.SourceRevision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.foggyframework.dataset.db.model.lifecycle.support.DeterministicConcurrencyTestSupport.STEP_TIMEOUT_SECONDS;
import static com.foggyframework.dataset.db.model.lifecycle.support.DeterministicConcurrencyTestSupport.await;
import static com.foggyframework.dataset.db.model.lifecycle.support.DeterministicConcurrencyTestSupport.cancelIncomplete;
import static com.foggyframework.dataset.db.model.lifecycle.support.DeterministicConcurrencyTestSupport.get;
import static com.foggyframework.dataset.db.model.lifecycle.support.DeterministicConcurrencyTestSupport.shutdownAndAssertTerminated;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ModelBuildSingleFlightTest {

    @Test
    void oneHundredSameKeyCallersMustShareOneCallerInlineWinner() {
        int callerCount = 100;
        RecordingObserver observer = new RecordingObserver(callerCount - 1);
        ModelBuildSingleFlight singleFlight = new ModelBuildSingleFlight(observer);
        ModelBuildKey key = trackedKey("FactOrderModel");
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        AtomicInteger buildCount = new AtomicInteger();
        Object built = new Object();
        ExecutorService executor = Executors.newFixedThreadPool(callerCount);
        List<Future<Object>> futures = new ArrayList<>();

        for (int index = 0; index < callerCount; index++) {
            futures.add(executor.submit(() -> {
                await(start, "same-key caller start");
                return singleFlight.execute(key, () -> {
                    buildCount.incrementAndGet();
                    await(releaseWinner, "same-key winner release");
                    return built;
                });
            }));
        }

        try {
            start.countDown();
            await(observer.winnerStarted, "single-flight winner start");
            await(observer.waitersJoined, "all 99 single-flight waiters");
            assertEquals(1, buildCount.get());
            assertEquals(1, singleFlight.inFlightCount());

            releaseWinner.countDown();
            for (int index = 0; index < futures.size(); index++) {
                assertSame(built, get(futures.get(index), "same-key result " + index));
            }
            assertEquals(1, buildCount.get());
            assertEquals(0, singleFlight.inFlightCount());
            assertEquals(1, observer.removedCount.get());
            assertEquals(ModelBuildFlightObserver.Completion.SUCCEEDED, observer.lastCompletion);
        } finally {
            releaseWinner.countDown();
            cancelIncomplete(futures);
            shutdownAndAssertTerminated(executor, "100-caller single-flight executor");
        }
    }

    @Test
    void observerErrorsMustNotChangeWinnerOrWaiterTerminalState() {
        CountDownLatch winnerEntered = new CountDownLatch(1);
        CountDownLatch waiterJoined = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        AtomicInteger builds = new AtomicInteger();
        ModelBuildSingleFlight flight = new ModelBuildSingleFlight(
                new ModelBuildFlightObserver() {
                    @Override
                    public void winnerStarted(ModelBuildKey key) {
                        throw new AssertionError("winner observer must be isolated");
                    }

                    @Override
                    public void waiterJoined(ModelBuildKey key, int waiterCount) {
                        waiterJoined.countDown();
                        throw new AssertionError("waiter observer must be isolated");
                    }

                    @Override
                    public void flightCompleted(ModelBuildKey key, Completion completion) {
                        throw new AssertionError("completion observer must be isolated");
                    }

                    @Override
                    public void flightRemoved(ModelBuildKey key) {
                        throw new AssertionError("removal observer must be isolated");
                    }
                });
        ModelBuildKey key = trackedKey(
                "orders",
                "OrderModel",
                "catalog-one",
                "source-one",
                List.of(binding("binding-one", "backend-one", "generation-one")));
        Object expected = new Object();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Object> winner = executor.submit(() -> flight.execute(key, () -> {
            builds.incrementAndGet();
            winnerEntered.countDown();
            await(releaseWinner, "observer-isolated winner release");
            return expected;
        }));
        Future<Object> waiter = null;

        try {
            await(winnerEntered, "observer-isolated winner entered");
            waiter = executor.submit(() -> flight.execute(key, () -> {
                throw new AssertionError("waiter must not build");
            }));
            await(waiterJoined, "observer-isolated waiter joined");
            releaseWinner.countDown();

            assertSame(expected, get(winner, "observer-isolated winner"));
            assertSame(expected, get(waiter, "observer-isolated waiter"));
            assertEquals(1, builds.get());
            assertEquals(0, flight.inFlightCount());
        } finally {
            releaseWinner.countDown();
            winner.cancel(true);
            if (waiter != null) {
                waiter.cancel(true);
            }
            shutdownAndAssertTerminated(executor, "observer-isolated executor");
        }
    }

    @Test
    void topLevelWaiterMustNotRetainEmptyBuildPathOnReusedPoolThread() {
        RecordingObserver observer = new RecordingObserver(1);
        ModelBuildSingleFlight singleFlight = new ModelBuildSingleFlight(observer);
        ModelBuildKey key = trackedKey("WaiterThreadStateModel");
        CountDownLatch releaseWinner = new CountDownLatch(1);
        AtomicInteger unexpectedWaiterBuilds = new AtomicInteger();
        ExecutorService winnerExecutor = Executors.newSingleThreadExecutor();
        ExecutorService waiterExecutor = Executors.newSingleThreadExecutor();
        Future<String> winner = winnerExecutor.submit(() -> singleFlight.execute(key, () -> {
            await(releaseWinner, "waiter-state winner release");
            return "shared";
        }));
        Future<String> waiter = null;

        try {
            await(observer.winnerStarted, "waiter-state winner start");
            assertFalse(get(waiterExecutor.submit(singleFlight::hasCurrentThreadBuildState),
                    "initial waiter thread state"));
            waiter = waiterExecutor.submit(() -> singleFlight.execute(key, () -> {
                unexpectedWaiterBuilds.incrementAndGet();
                return "unexpected";
            }));
            await(observer.waitersJoined, "waiter-state waiter joined");

            releaseWinner.countDown();
            assertEquals("shared", get(winner, "waiter-state winner result"));
            assertEquals("shared", get(waiter, "waiter-state shared result"));
            assertEquals(0, unexpectedWaiterBuilds.get());
            assertFalse(get(waiterExecutor.submit(singleFlight::hasCurrentThreadBuildState),
                    "reused waiter thread state"));
            assertEquals(0, singleFlight.inFlightCount());
        } finally {
            releaseWinner.countDown();
            winner.cancel(true);
            if (waiter != null) {
                waiter.cancel(true);
            }
            shutdownAndAssertTerminated(winnerExecutor, "waiter-state winner executor");
            shutdownAndAssertTerminated(waiterExecutor, "waiter-state waiter executor");
        }
    }

    @Test
    void winnerFailureMustBeSharedThenExactFlightRemovedForRetry() {
        int callerCount = 32;
        RecordingObserver observer = new RecordingObserver(callerCount - 1);
        ModelBuildSingleFlight singleFlight = new ModelBuildSingleFlight(observer);
        ModelBuildKey key = trackedKey("BrokenModel");
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        AtomicInteger buildCount = new AtomicInteger();
        IllegalStateException marker = new IllegalStateException("controlled winner failure");
        ExecutorService executor = Executors.newFixedThreadPool(callerCount);
        List<Future<String>> futures = new ArrayList<>();

        for (int index = 0; index < callerCount; index++) {
            futures.add(executor.submit(() -> {
                await(start, "failure caller start");
                return singleFlight.execute(key, () -> {
                    buildCount.incrementAndGet();
                    await(releaseWinner, "failing winner release");
                    throw marker;
                });
            }));
        }

        try {
            start.countDown();
            await(observer.winnerStarted, "failing winner start");
            await(observer.waitersJoined, "all failing-flight waiters");
            releaseWinner.countDown();

            for (int index = 0; index < futures.size(); index++) {
                assertSame(marker, failureCause(futures.get(index), "shared failure " + index));
            }
            assertEquals(1, buildCount.get());
            assertEquals(0, singleFlight.inFlightCount());
            assertEquals(ModelBuildFlightObserver.Completion.FAILED, observer.lastCompletion);

            String retried = singleFlight.execute(key, () -> {
                buildCount.incrementAndGet();
                return "recovered";
            });
            assertEquals("recovered", retried);
            assertEquals(2, buildCount.get());
            assertEquals(0, singleFlight.inFlightCount());
            assertEquals(2, observer.removedCount.get());
        } finally {
            releaseWinner.countDown();
            cancelIncomplete(futures);
            shutdownAndAssertTerminated(executor, "failure-sharing executor");
        }
    }

    @Test
    void cancellationMustBeSharedThenFlightRemovedForRetry() {
        assertTerminalFailureSharedAndRetryable(
                "CancelledModel",
                new CancellationException("controlled model build cancellation"));
    }

    @Test
    void checkedTimeoutMustBeSharedExactlyThenFlightRemovedForRetry() {
        assertTerminalFailureSharedAndRetryable(
                "TimedOutModel",
                new TimeoutException("controlled model build timeout"));
    }

    @Test
    void distinctNamespacesMustOverlapAndKeepResultsIsolated() {
        assertDistinctFlightsOverlap(
                trackedKey("tenant-a", "DimChannelModel", "catalog-1", "source-1",
                        List.of(binding("orders", "jdbc-orders", "binding-1"))),
                trackedKey("tenant-b", "DimChannelModel", "catalog-1", "source-1",
                        List.of(binding("orders", "jdbc-orders", "binding-1"))),
                "namespace");
    }

    @Test
    void distinctModelsMustOverlapAndKeepResultsIsolated() {
        assertDistinctFlightsOverlap(
                trackedKey("tenant-a", "DimChannelModel", "catalog-1", "source-1",
                        List.of(binding("orders", "jdbc-orders", "binding-1"))),
                trackedKey("tenant-a", "DimCustomerModel", "catalog-1", "source-1",
                        List.of(binding("orders", "jdbc-orders", "binding-1"))),
                "model");
    }

    @Test
    void distinctCatalogGenerationsMustOverlapAndKeepResultsIsolated() {
        assertDistinctFlightsOverlap(
                trackedKey("tenant-a", "DimChannelModel", "catalog-1", "source-1",
                        List.of(binding("orders", "jdbc-orders", "binding-1"))),
                trackedKey("tenant-a", "DimChannelModel", "catalog-2", "source-1",
                        List.of(binding("orders", "jdbc-orders", "binding-1"))),
                "catalog-generation");
    }

    @Test
    void distinctSourceRevisionsMustOverlapAndKeepResultsIsolated() {
        assertDistinctFlightsOverlap(
                trackedKey("tenant-a", "DimChannelModel", "catalog-1", "source-1",
                        List.of(binding("orders", "jdbc-orders", "binding-1"))),
                trackedKey("tenant-a", "DimChannelModel", "catalog-1", "source-2",
                        List.of(binding("orders", "jdbc-orders", "binding-1"))),
                "source-revision");
    }

    @Test
    void distinctBackendsMustOverlapAndKeepResultsIsolated() {
        assertDistinctFlightsOverlap(
                trackedKey("tenant-a", "DimChannelModel", "catalog-1", "source-1",
                        List.of(binding("orders", "jdbc-orders-a", "binding-1"))),
                trackedKey("tenant-a", "DimChannelModel", "catalog-1", "source-1",
                        List.of(binding("orders", "jdbc-orders-b", "binding-1"))),
                "backend");
    }

    @Test
    void distinctBindingSetsMustOverlapAndKeepResultsIsolated() {
        assertDistinctFlightsOverlap(
                trackedKey("tenant-a", "DimChannelModel", "catalog-1", "source-1",
                        List.of(binding("orders", "jdbc-orders", "binding-1"))),
                trackedKey("tenant-a", "DimChannelModel", "catalog-1", "source-1",
                        List.of(binding("payments", "jdbc-orders", "binding-1"))),
                "binding-set");
    }

    @Test
    void untrackedBuildMustBypassSharingEvenWhenCallerReusesTheSameKeyInstance() {
        ModelBuildSingleFlight singleFlight = new ModelBuildSingleFlight();
        ModelBuildKey untracked = ModelBuildKey.isolatedUntracked(
                CatalogModelKey.table("RoutingModel"),
                "tenant-a",
                new CatalogGeneration("catalog-1"),
                new SourceRevision("source-1"),
                List.of());
        CountDownLatch buildersEntered = new CountDownLatch(2);
        CountDownLatch releaseBuilders = new CountDownLatch(1);
        AtomicInteger buildCount = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = List.of(
                executor.submit(() -> singleFlight.execute(untracked,
                        () -> controlledCount(buildCount, buildersEntered, releaseBuilders))),
                executor.submit(() -> singleFlight.execute(untracked,
                        () -> controlledCount(buildCount, buildersEntered, releaseBuilders)))
        );

        try {
            await(buildersEntered, "both isolated untracked builders");
            assertEquals(0, singleFlight.inFlightCount());
            releaseBuilders.countDown();
            get(futures.get(0), "first untracked build");
            get(futures.get(1), "second untracked build");
            assertEquals(2, buildCount.get());
        } finally {
            releaseBuilders.countDown();
            cancelIncomplete(futures);
            shutdownAndAssertTerminated(executor, "untracked isolation executor");
        }
    }

    @Test
    void directSelfDependencyMustFailBeforeWaitingOnOwnFuture() {
        ModelBuildSingleFlight singleFlight = new ModelBuildSingleFlight();
        ModelBuildKey key = trackedKey("SelfModel");
        AtomicInteger recursiveBuilderCalls = new AtomicInteger();

        ModelBuildCyclicDependencyException failure = assertThrows(
                ModelBuildCyclicDependencyException.class,
                () -> singleFlight.execute(key, () -> singleFlight.execute(key, () -> {
                    recursiveBuilderCalls.incrementAndGet();
                    return "unreachable";
                })));

        assertEquals(List.of(key, key), failure.cyclePath());
        assertEquals("MODEL_BUILD_DEPENDENCY_CYCLE", failure.code());
        assertTrue(failure.getMessage().startsWith("MODEL_BUILD_DEPENDENCY_CYCLE:"));
        assertTrue(failure.getMessage().contains("SelfModel"));
        assertEquals(0, recursiveBuilderCalls.get());
        assertEquals(0, singleFlight.inFlightCount());
        assertEquals(0, singleFlight.currentThreadBuildDepth());
    }

    @Test
    void indirectBuildGraphCycleMustReportCanonicalPathAndCleanEveryFlight() {
        ModelBuildSingleFlight singleFlight = new ModelBuildSingleFlight();
        ModelBuildKey query = trackedKey(CatalogModelKey.query("OrderQueryModel"));
        ModelBuildKey table = trackedKey(CatalogModelKey.table("FactOrderModel"));

        ModelBuildCyclicDependencyException failure = assertThrows(
                ModelBuildCyclicDependencyException.class,
                () -> singleFlight.execute(query,
                        () -> singleFlight.execute(table,
                                () -> singleFlight.execute(query, () -> "unreachable"))));

        assertEquals(List.of(query, table, query), failure.cyclePath());
        assertTrue(failure.getMessage().contains("OrderQueryModel"));
        assertTrue(failure.getMessage().contains("FactOrderModel"));
        assertEquals(0, singleFlight.inFlightCount());
        assertEquals(0, singleFlight.currentThreadBuildDepth());
    }

    @Test
    void queryTableSyntheticCycleMustFailWithoutSelfWaitOrResidualFlight() {
        ModelBuildSingleFlight singleFlight = new ModelBuildSingleFlight();
        ModelBuildKey synthetic = trackedKey(
                CatalogModelKey.syntheticQuery("OrderQueryModel#customer"));
        ModelBuildKey sourceQuery = trackedKey(CatalogModelKey.query("OrderQueryModel"));
        ModelBuildKey table = trackedKey(CatalogModelKey.table("FactOrderModel"));

        ModelBuildCyclicDependencyException failure = assertThrows(
                ModelBuildCyclicDependencyException.class,
                () -> singleFlight.execute(synthetic,
                        () -> singleFlight.execute(sourceQuery,
                                () -> singleFlight.execute(table,
                                        () -> singleFlight.execute(synthetic,
                                                () -> "unreachable")))));

        assertEquals(List.of(synthetic, sourceQuery, table, synthetic), failure.cyclePath());
        assertTrue(failure.getMessage().startsWith("MODEL_BUILD_DEPENDENCY_CYCLE:"));
        assertEquals(0, singleFlight.inFlightCount());
        assertEquals(0, singleFlight.currentThreadBuildDepth());
    }

    @Test
    void untrackedCycleMustBeDetectedByLogicalNodeDespiteFreshIsolationNonce() {
        ModelBuildSingleFlight singleFlight = new ModelBuildSingleFlight();
        ModelBuildKey first = untrackedKey("SyntheticModel#customer");
        ModelBuildKey freshEquivalent = untrackedKey("SyntheticModel#customer");
        assertFalse(first.equals(freshEquivalent));

        ModelBuildCyclicDependencyException failure = assertThrows(
                ModelBuildCyclicDependencyException.class,
                () -> singleFlight.execute(first,
                        () -> singleFlight.execute(freshEquivalent, () -> "unreachable")));

        assertEquals(List.of(first, freshEquivalent), failure.cyclePath());
        assertEquals(0, singleFlight.inFlightCount());
        assertEquals(0, singleFlight.currentThreadBuildDepth());
    }

    private void assertDistinctFlightsOverlap(
            ModelBuildKey first,
            ModelBuildKey second,
            String dimension
    ) {
        assertNotEquals(first, second, dimension + " must participate in flight identity");
        ModelBuildSingleFlight singleFlight = new ModelBuildSingleFlight();
        CountDownLatch buildersEntered = new CountDownLatch(2);
        CountDownLatch releaseBuilders = new CountDownLatch(1);
        AtomicInteger firstBuilds = new AtomicInteger();
        AtomicInteger secondBuilds = new AtomicInteger();
        Object firstResult = new Object();
        Object secondResult = new Object();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Object>> futures = List.of(
                executor.submit(() -> singleFlight.execute(first, () -> {
                    firstBuilds.incrementAndGet();
                    return controlledBuild(firstResult, buildersEntered, releaseBuilders);
                })),
                executor.submit(() -> singleFlight.execute(second, () -> {
                    secondBuilds.incrementAndGet();
                    return controlledBuild(secondResult, buildersEntered, releaseBuilders);
                }))
        );

        try {
            await(buildersEntered, "both distinct " + dimension + " builders");
            assertEquals(2, singleFlight.inFlightCount());
            releaseBuilders.countDown();
            Object observedFirst = get(futures.get(0), dimension + " first build");
            Object observedSecond = get(futures.get(1), dimension + " second build");
            assertSame(firstResult, observedFirst);
            assertSame(secondResult, observedSecond);
            assertNotSame(observedFirst, observedSecond);
            assertEquals(1, firstBuilds.get());
            assertEquals(1, secondBuilds.get());
            assertEquals(0, singleFlight.inFlightCount());
        } finally {
            releaseBuilders.countDown();
            cancelIncomplete(futures);
            shutdownAndAssertTerminated(executor,
                    "distinct " + dimension + " single-flight executor");
        }
    }

    private <T> T controlledBuild(
            T value,
            CountDownLatch entered,
            CountDownLatch release
    ) {
        entered.countDown();
        await(release, "distinct-key builder release");
        return value;
    }

    private int controlledCount(
            AtomicInteger buildCount,
            CountDownLatch entered,
            CountDownLatch release
    ) {
        int count = buildCount.incrementAndGet();
        entered.countDown();
        await(release, "untracked builder release");
        return count;
    }

    private void assertTerminalFailureSharedAndRetryable(
            String modelName,
            Throwable terminalFailure
    ) {
        RecordingObserver observer = new RecordingObserver(1);
        ModelBuildSingleFlight singleFlight = new ModelBuildSingleFlight(observer);
        ModelBuildKey key = trackedKey(modelName);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        AtomicInteger buildCount = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> winner = executor.submit(() -> singleFlight.execute(key, () -> {
            buildCount.incrementAndGet();
            await(releaseWinner, modelName + " terminal winner release");
            throwUnchecked(terminalFailure);
            return "unreachable";
        }));
        Future<String> waiter = null;

        try {
            await(observer.winnerStarted, modelName + " terminal winner start");
            waiter = executor.submit(() -> singleFlight.execute(key, () -> {
                buildCount.incrementAndGet();
                return "unexpected waiter build";
            }));
            await(observer.waitersJoined, modelName + " terminal waiter joined");
            assertEquals(1, buildCount.get());

            releaseWinner.countDown();
            assertSame(terminalFailure,
                    failureCause(winner, modelName + " winner terminal failure"));
            assertSame(terminalFailure,
                    failureCause(waiter, modelName + " waiter terminal failure"));
            assertEquals(ModelBuildFlightObserver.Completion.FAILED, observer.lastCompletion);
            assertEquals(0, singleFlight.inFlightCount());
            assertEquals(1, observer.removedCount.get());

            String retried = singleFlight.execute(key, () -> {
                buildCount.incrementAndGet();
                return "recovered";
            });
            assertEquals("recovered", retried);
            assertEquals(2, buildCount.get());
            assertEquals(0, singleFlight.inFlightCount());
            assertEquals(2, observer.removedCount.get());
        } finally {
            releaseWinner.countDown();
            winner.cancel(true);
            if (waiter != null) {
                waiter.cancel(true);
            }
            shutdownAndAssertTerminated(executor, modelName + " terminal executor");
        }
    }

    private void throwUnchecked(Throwable failure) {
        ModelBuildSingleFlightTest.<RuntimeException>throwAny(failure);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwAny(Throwable failure) throws E {
        throw (E) failure;
    }

    private Throwable failureCause(Future<?> future, String label) {
        try {
            Object ignored = future.get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            fail("Expected future failure: " + label + ", result=" + ignored);
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted waiting for " + label, interrupted);
        } catch (TimeoutException timeout) {
            throw new AssertionError("Timed out waiting for " + label, timeout);
        } catch (ExecutionException expected) {
            return expected.getCause();
        }
    }

    private ModelBuildKey trackedKey(String modelName) {
        return trackedKey(CatalogModelKey.table(modelName));
    }

    private ModelBuildKey trackedKey(CatalogModelKey modelKey) {
        return trackedKey(
                modelKey,
                "tenant-a",
                "catalog-1",
                "source-1",
                List.of(binding("orders", "jdbc-orders", "binding-1")));
    }

    private ModelBuildKey trackedKey(
            String namespace,
            String modelName,
            String catalogGeneration,
            String sourceRevision,
            List<DatasourceBindingIdentity> bindings
    ) {
        return trackedKey(CatalogModelKey.table(modelName), namespace, catalogGeneration,
                sourceRevision, bindings);
    }

    private ModelBuildKey trackedKey(
            CatalogModelKey modelKey,
            String namespace,
            String catalogGeneration,
            String sourceRevision,
            List<DatasourceBindingIdentity> bindings
    ) {
        return ModelBuildKey.tracked(
                modelKey,
                namespace,
                new CatalogGeneration(catalogGeneration),
                new SourceRevision(sourceRevision),
                bindings);
    }

    private DatasourceBindingIdentity binding(
            String bindingKey,
            String backendId,
            String generation
    ) {
        return new DatasourceBindingIdentity(
                bindingKey,
                backendId,
                new DatasourceBindingGeneration(generation));
    }

    private ModelBuildKey untrackedKey(String modelName) {
        return ModelBuildKey.isolatedUntracked(
                CatalogModelKey.syntheticQuery(modelName),
                "tenant-a",
                new CatalogGeneration("catalog-1"),
                new SourceRevision("source-1"),
                List.of());
    }

    private static final class RecordingObserver implements ModelBuildFlightObserver {
        private final CountDownLatch winnerStarted = new CountDownLatch(1);
        private final CountDownLatch waitersJoined;
        private final AtomicInteger removedCount = new AtomicInteger();
        private volatile Completion lastCompletion;

        private RecordingObserver(int expectedWaiters) {
            this.waitersJoined = new CountDownLatch(expectedWaiters);
        }

        @Override
        public void winnerStarted(ModelBuildKey key) {
            winnerStarted.countDown();
        }

        @Override
        public void waiterJoined(ModelBuildKey key, int waiterCount) {
            waitersJoined.countDown();
        }

        @Override
        public void flightCompleted(ModelBuildKey key, Completion completion) {
            lastCompletion = completion;
        }

        @Override
        public void flightRemoved(ModelBuildKey key) {
            removedCount.incrementAndGet();
        }
    }
}
