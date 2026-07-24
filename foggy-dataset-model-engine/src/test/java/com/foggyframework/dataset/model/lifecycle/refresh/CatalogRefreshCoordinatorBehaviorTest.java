package com.foggyframework.dataset.model.lifecycle.refresh;

import com.foggyframework.dataset.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogAdmissionState;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogCandidate;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.model.lifecycle.port.CommittedSourceRevisionGuard;
import com.foggyframework.dataset.model.lifecycle.port.DatasourceBindingResolver;
import com.foggyframework.dataset.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.model.lifecycle.port.StaleDatasourceBindingException;
import com.foggyframework.dataset.model.spi.NamespaceContext;
import com.foggyframework.dataset.model.spi.NamespaceScope;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogRefreshCoordinatorBehaviorTest {

    private static final String NAMESPACE = "tenant-refresh-behavior";
    private static final String QUERY_X = "RefreshXQueryModel";
    private static final String QUERY_Y = "RefreshYQueryModel";

    @Test
    void modelRefreshMustPublishOnceRebaseProvenanceAndPreserveSiblingObject() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot before = seed(store, NAMESPACE, Set.of(QUERY_X, QUERY_Y));
        store.advanceSourceRevision(NAMESPACE);

        CatalogRefreshRequest request = CatalogRefreshRequest.models(
                NAMESPACE,
                Set.of(CatalogModelKey.query(QUERY_X)),
                CatalogRefreshTrigger.EXPLICIT_RECOVERY);
        CatalogRefreshPlan plan = CatalogRefreshPlan.models(
                request.targets(), Set.of(QUERY_X, QUERY_Y));
        CatalogRefreshResult result = new CatalogRefreshCoordinator(store).refresh(
                request,
                plan,
                context -> stageQuery(context.candidate(), QUERY_X));

        CatalogSnapshot after = store.readCurrent(NAMESPACE).orElseThrow();
        assertSame(after, store.current(NAMESPACE).orElseThrow());
        assertNotEquals(before.identity().generation(), after.identity().generation());
        assertEquals(result.afterIdentity(), after.identity());
        assertEquals(Set.of(CatalogModelKey.query(QUERY_X)), result.refreshedModels());
        assertEquals(Set.of(CatalogModelKey.query(QUERY_Y)), result.preservedModels());
        assertSame(before.queryModels().get(QUERY_Y), after.queryModels().get(QUERY_Y));
        after.provenance().values().forEach(provenance ->
                assertEquals(after.identity().sourceRevision(), provenance.sourceRevision()));
        assertEquals(CatalogAdmissionState.ACTIVE, result.catalogState());
    }

    @Test
    void failedKnownScopeRefreshWithUntrackedBindingMustKeepTheOldCatalogAdmissible() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot before = seedUntracked(store, NAMESPACE, QUERY_X);
        CatalogRefreshRequest request = CatalogRefreshRequest.namespace(
                NAMESPACE, CatalogRefreshTrigger.EXPLICIT_RECOVERY);

        CatalogRefreshException failure = assertThrows(
                CatalogRefreshException.class,
                () -> new CatalogRefreshCoordinator(store).refresh(
                        request,
                        CatalogRefreshPlan.namespace(Set.of(QUERY_X)),
                        ignored -> {
                            throw new IllegalStateException("controlled build failure");
                        }));

        assertEquals("CATALOG_REFRESH_FAILED", failure.code());
        assertEquals(CatalogAdmissionState.ACTIVE_OLD_PRESERVED,
                failure.catalogState());
        assertEquals(before.identity(), failure.beforeIdentity());
        assertSame(before, store.current(NAMESPACE).orElseThrow());
        assertSame(before, store.readCurrent(NAMESPACE).orElseThrow());
    }

    @Test
    void failedRefreshWithMixedProvenanceMustCheckKnownBindingsAndPreserveCurrentOld() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        DatasourceBindingIdentity tracked = new DatasourceBindingIdentity(
                "test:tracked",
                "test:sqlite",
                new DatasourceBindingGeneration("generation-one"));
        CatalogSnapshot before = seedMixed(store, NAMESPACE, QUERY_X, tracked);
        RejectingBindingResolver resolver = new RejectingBindingResolver(tracked);
        CatalogRefreshRequest request = CatalogRefreshRequest.namespace(
                NAMESPACE, CatalogRefreshTrigger.EXPLICIT_RECOVERY);

        CatalogRefreshException failure = assertThrows(
                CatalogRefreshException.class,
                () -> new CatalogRefreshCoordinator(store, resolver).refresh(
                        request,
                        CatalogRefreshPlan.namespace(Set.of(QUERY_X)),
                        ignored -> {
                            throw new IllegalStateException("controlled mixed build failure");
                        }));

        assertEquals("CATALOG_REFRESH_FAILED", failure.code());
        assertEquals(CatalogAdmissionState.ACTIVE_OLD_PRESERVED,
                failure.catalogState());
        assertSame(before, store.current(NAMESPACE).orElseThrow());
        assertSame(before, store.readCurrent(NAMESPACE).orElseThrow());
    }

    @Test
    void sourceChangeDuringBuildMustRejectTheCandidateWithoutPublication() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot before = seed(store, NAMESPACE, Set.of(QUERY_X));
        CatalogRefreshRequest request = CatalogRefreshRequest.namespace(
                NAMESPACE, CatalogRefreshTrigger.EXPLICIT_RECOVERY);

        CatalogRefreshException failure = assertThrows(
                CatalogRefreshException.class,
                () -> new CatalogRefreshCoordinator(store).refresh(
                        request,
                        CatalogRefreshPlan.namespace(Set.of(QUERY_X)),
                        context -> {
                            stageQuery(context.candidate(), QUERY_X);
                            store.advanceSourceRevision(NAMESPACE);
                        }));

        assertEquals("SOURCE_REVISION_STALE", failure.code());
        assertSame(before, store.current(NAMESPACE).orElseThrow());
        assertEquals(before.identity(), store.readCurrent(NAMESPACE)
                .orElseThrow().identity());
        assertEquals(CatalogAdmissionState.ACTIVE_OLD_PRESERVED,
                store.admissionState(NAMESPACE));
    }

    @Test
    void discoveryFailureMustPreserveTheCapturedOldCatalog() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot before = seed(store, NAMESPACE, Set.of(QUERY_X));
        TableModelLoaderManagerImpl tableLoader =
                mock(TableModelLoaderManagerImpl.class);
        QueryModelLoaderImpl queryLoader = mock(QueryModelLoaderImpl.class);
        when(queryLoader.discoverQueryModelNames(NAMESPACE))
                .thenThrow(new IllegalStateException("controlled discovery failure"));
        CatalogRefreshCoordinator coordinator = new CatalogRefreshCoordinator(
                store, tableLoader, queryLoader);

        CatalogRefreshException failure = assertThrows(
                CatalogRefreshException.class,
                () -> coordinator.refresh(
                        CatalogRefreshRequest.namespace(
                                NAMESPACE,
                                CatalogRefreshTrigger.FILE),
                        ignored -> {
                        }));

        assertEquals("CATALOG_REFRESH_FAILED", failure.code());
        assertEquals(before.identity(), failure.beforeIdentity());
        assertSame(before, store.readCurrent(NAMESPACE).orElseThrow());
        assertEquals(CatalogAdmissionState.ACTIVE_OLD_PRESERVED,
                store.admissionState(NAMESPACE));
        assertConfiguredDiscoveryAndPlanBoundariesFailClosed();
        assertUnstableConfiguredDiscoveryFailsAfterBoundedCaptureAttempts();
    }

    @Test
    void failedAttemptMustNotMarkAConcurrentWinnerAsOldOrBlocked()
            throws Exception {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot before = seed(store, NAMESPACE, Set.of(QUERY_X));
        CatalogRefreshCoordinator coordinator = new CatalogRefreshCoordinator(store);
        CatalogRefreshRequest request = CatalogRefreshRequest.namespace(
                NAMESPACE, CatalogRefreshTrigger.EXPLICIT_RECOVERY);
        CatalogRefreshPlan plan = CatalogRefreshPlan.namespace(Set.of(QUERY_X));
        CountDownLatch failedBuildEntered = new CountDownLatch(1);
        CountDownLatch releaseFailedBuild = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<CatalogRefreshResult> failed = executor.submit(() ->
                    coordinator.refresh(request, plan, ignored -> {
                        failedBuildEntered.countDown();
                        assertTrue(releaseFailedBuild.await(5, TimeUnit.SECONDS));
                        throw new IllegalStateException("controlled late failure");
                    }));
            assertTrue(failedBuildEntered.await(5, TimeUnit.SECONDS));

            CatalogSnapshot winner;
            try (CatalogSnapshotStore.CandidateScope scope =
                         store.openCandidate(store.capture(NAMESPACE))) {
                scope.candidate().discoverQueryModels(Set.of(QUERY_X, QUERY_Y));
                stageQuery(scope.candidate(), QUERY_Y);
                winner = scope.commit();
            }
            assertNotEquals(before.identity(), winner.identity());
            releaseFailedBuild.countDown();

            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> failed.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof CatalogRefreshException);
            assertSame(winner, store.readCurrent(NAMESPACE).orElseThrow());
            assertEquals(CatalogAdmissionState.ACTIVE,
                    store.admissionState(NAMESPACE));
        } finally {
            releaseFailedBuild.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void mixedTrackedAndUntrackedCandidateMustGuardTheKnownBindingAtPublish() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot before = seedUntracked(store, NAMESPACE, QUERY_X);
        DatasourceBindingIdentity tracked = new DatasourceBindingIdentity(
                "test:tracked",
                "test:sqlite",
                new DatasourceBindingGeneration("generation-one"));
        RejectingBindingResolver resolver = new RejectingBindingResolver(tracked);
        CatalogRefreshRequest request = CatalogRefreshRequest.namespace(
                NAMESPACE, CatalogRefreshTrigger.EXPLICIT_RECOVERY);

        CatalogRefreshException failure = assertThrows(
                CatalogRefreshException.class,
                () -> new CatalogRefreshCoordinator(store, resolver).refresh(
                        request,
                        CatalogRefreshPlan.namespace(Set.of(QUERY_X, QUERY_Y)),
                        context -> {
                            stageUntrackedQuery(context.candidate(), QUERY_X);
                            stageTrackedQuery(
                                    context.candidate(), QUERY_Y, tracked);
                        }));

        assertEquals("DATASOURCE_BINDING_NOT_CURRENT", failure.code());
        assertEquals(1, resolver.publicationGuards);
        assertSame(before, store.current(NAMESPACE).orElseThrow());
        assertSame(before, store.readCurrent(NAMESPACE).orElseThrow());
        assertEquals(CatalogAdmissionState.ACTIVE_OLD_PRESERVED,
                store.admissionState(NAMESPACE));
    }

    @Test
    void snapshotPreparationMustCompleteBeforeBindingMutationMonitorIsEntered() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot before = seedUntracked(store, NAMESPACE, QUERY_X);
        DatasourceBindingIdentity tracked = new DatasourceBindingIdentity(
                "test:tracked",
                "test:sqlite",
                new DatasourceBindingGeneration("generation-one"));
        MonitorTrackingBindingResolver resolver =
                new MonitorTrackingBindingResolver(tracked, false);
        CatalogRefreshRequest request = CatalogRefreshRequest.namespace(
                NAMESPACE, CatalogRefreshTrigger.EXPLICIT_RECOVERY);

        CatalogRefreshResult result = new CatalogRefreshCoordinator(
                store, resolver).refresh(
                request,
                CatalogRefreshPlan.namespace(Set.of(QUERY_X, QUERY_Y)),
                context -> {
                    stageUntrackedQuery(context.candidate(), QUERY_X);
                    stageTrackedQuery(
                            context.candidate(),
                            QUERY_Y,
                            tracked,
                            resolver.observedQueryModel(QUERY_Y,
                                    context.candidate().aliasFor(QUERY_Y)));
                });

        CatalogSnapshot after = store.readCurrent(NAMESPACE).orElseThrow();
        assertNotEquals(before.identity(), after.identity());
        assertEquals(after.identity(), result.afterIdentity());
        assertEquals(1, resolver.publicationGuards);
        assertTrue(resolver.snapshotValidationCalls.get() > 0);
    }

    @Test
    void staleBindingAfterSnapshotPreparationMustStillRejectAtomicPublication() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot before = seedUntracked(store, NAMESPACE, QUERY_X);
        DatasourceBindingIdentity tracked = new DatasourceBindingIdentity(
                "test:tracked",
                "test:sqlite",
                new DatasourceBindingGeneration("generation-one"));
        MonitorTrackingBindingResolver resolver =
                new MonitorTrackingBindingResolver(tracked, true);
        CatalogRefreshRequest request = CatalogRefreshRequest.namespace(
                NAMESPACE, CatalogRefreshTrigger.EXPLICIT_RECOVERY);

        CatalogRefreshException failure = assertThrows(
                CatalogRefreshException.class,
                () -> new CatalogRefreshCoordinator(store, resolver).refresh(
                        request,
                        CatalogRefreshPlan.namespace(Set.of(QUERY_X, QUERY_Y)),
                        context -> {
                            stageUntrackedQuery(context.candidate(), QUERY_X);
                            stageTrackedQuery(
                                    context.candidate(),
                                    QUERY_Y,
                                    tracked,
                                    resolver.observedQueryModel(QUERY_Y,
                                            context.candidate().aliasFor(QUERY_Y)));
                        }));

        assertEquals("DATASOURCE_BINDING_NOT_CURRENT", failure.code());
        assertEquals(1, resolver.publicationGuards);
        assertTrue(resolver.snapshotValidationCalls.get() > 0);
        assertSame(before, store.current(NAMESPACE).orElseThrow());
        assertSame(before, store.readCurrent(NAMESPACE).orElseThrow());
    }

    @Test
    void blockedAdmissionMustNotBeDowngradedByANoOpOrLateFailureMarker() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot before = seed(store, NAMESPACE, Set.of(QUERY_X));
        store.markStaleAdmissionBlocked(
                NAMESPACE, "REFRESH_SCOPE_UNKNOWN: controlled mutation");

        try (CatalogSnapshotStore.CandidateScope scope =
                     store.openCandidate(store.capture(NAMESPACE))) {
            assertSame(before, scope.commit());
        }
        store.markActiveOldPreserved(
                NAMESPACE, "known refresh failed after a newer block");

        assertEquals(CatalogAdmissionState.STALE_ADMISSION_BLOCKED,
                store.admissionState(NAMESPACE));
        assertSame(before, store.current(NAMESPACE).orElseThrow());
        assertThrows(RuntimeException.class,
                () -> store.readCurrent(NAMESPACE));
    }

    @Test
    void sameNamespaceCallbacksMustBeSerialized() throws Exception {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogRefreshCoordinator coordinator = new CatalogRefreshCoordinator(store);
        CatalogRefreshRequest request = CatalogRefreshRequest.namespace(
                NAMESPACE, CatalogRefreshTrigger.EXPLICIT_RECOVERY);
        CatalogRefreshPlan plan = CatalogRefreshPlan.namespace(Set.of());
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondReady = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CatalogRefreshResult> first = executor.submit(() ->
                    coordinator.refresh(request, plan, ignored -> {
                        firstEntered.countDown();
                        assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                    }));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
            Future<CatalogRefreshResult> second = executor.submit(() -> {
                secondReady.countDown();
                return coordinator.refresh(request, plan,
                        ignored -> secondEntered.countDown());
            });

            assertTrue(secondReady.await(5, TimeUnit.SECONDS));
            assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(0L, secondEntered.getCount());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void differentNamespaceCallbacksMustOverlap() throws Exception {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogRefreshCoordinator coordinator = new CatalogRefreshCoordinator(store);
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CatalogRefreshResult> first = executor.submit(() ->
                    coordinator.refresh(
                            CatalogRefreshRequest.namespace(
                                    "tenant-a", CatalogRefreshTrigger.EXPLICIT_RECOVERY),
                            CatalogRefreshPlan.namespace(Set.of()),
                            ignored -> awaitRelease(bothEntered, release)));
            Future<CatalogRefreshResult> second = executor.submit(() ->
                    coordinator.refresh(
                            CatalogRefreshRequest.namespace(
                                    "tenant-b", CatalogRefreshTrigger.EXPLICIT_RECOVERY),
                            CatalogRefreshPlan.namespace(Set.of()),
                            ignored -> awaitRelease(bothEntered, release)));

            assertTrue(bothEntered.await(5, TimeUnit.SECONDS));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void refreshCallbackMustUseRequestNamespaceAndRestoreTheOuterScope() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogRefreshCoordinator coordinator = new CatalogRefreshCoordinator(store);
        CatalogRefreshRequest request = CatalogRefreshRequest.namespace(
                NAMESPACE, CatalogRefreshTrigger.EXPLICIT_RECOVERY);
        CatalogRefreshPlan plan = CatalogRefreshPlan.namespace(Set.of());

        try (NamespaceScope ignored = NamespaceContext.open("outer-namespace")) {
            coordinator.refresh(request, plan, context ->
                    assertEquals(NAMESPACE, NamespaceContext.getNamespace()));
            assertEquals("outer-namespace", NamespaceContext.getNamespace());

            assertThrows(CatalogRefreshException.class,
                    () -> coordinator.refresh(request, plan, context -> {
                        assertEquals(NAMESPACE, NamespaceContext.getNamespace());
                        throw new IllegalStateException("controlled refresh failure");
                    }));
            assertEquals("outer-namespace", NamespaceContext.getNamespace());
        }
        assertNull(NamespaceContext.getNamespace());
        assertColdNestedFatalAndCheckedFailuresRemainFailClosed();
    }

    private static void assertConfiguredDiscoveryAndPlanBoundariesFailClosed() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogRefreshCoordinator coordinator = new CatalogRefreshCoordinator(store);
        CatalogRefreshRequest namespaceRequest = CatalogRefreshRequest.namespace(
                "boundary-namespace", CatalogRefreshTrigger.EXPLICIT_RECOVERY);

        IllegalStateException missingDiscovery = assertThrows(
                IllegalStateException.class,
                () -> coordinator.refresh(namespaceRequest, ignored -> {
                }));
        assertEquals("CATALOG_REFRESH_DISCOVERY_CALLBACK_REQUIRED",
                missingDiscovery.getMessage());

        CatalogRefreshPlan wrongScope = CatalogRefreshPlan.models(
                Set.of(CatalogModelKey.query(QUERY_X)), Set.of(QUERY_X));
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.refresh(namespaceRequest, wrongScope, ignored -> {
                }));

        CatalogRefreshRequest modelRequest = CatalogRefreshRequest.models(
                "boundary-namespace",
                Set.of(CatalogModelKey.query(QUERY_X)),
                CatalogRefreshTrigger.EXPLICIT_RECOVERY);
        CatalogRefreshPlan wrongTargets = CatalogRefreshPlan.models(
                Set.of(CatalogModelKey.query(QUERY_Y)), Set.of(QUERY_X, QUERY_Y));
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.refresh(modelRequest, wrongTargets, ignored -> {
                }));
    }

    private static void assertColdNestedFatalAndCheckedFailuresRemainFailClosed() {
        CatalogRefreshRequest request = CatalogRefreshRequest.namespace(
                "failure-boundary", CatalogRefreshTrigger.EXPLICIT_RECOVERY);
        CatalogRefreshPlan emptyPlan = CatalogRefreshPlan.namespace(Set.of());

        CatalogSnapshotStore coldStore = new CatalogSnapshotStore();
        CatalogRefreshResult coldResult = new CatalogRefreshCoordinator(coldStore).refresh(
                request, emptyPlan, ignored -> {
                });
        CatalogSnapshot coldSnapshot = coldStore.current(request.namespace()).orElseThrow();
        assertEquals(coldSnapshot.identity(), coldResult.afterIdentity());
        assertTrue(coldSnapshot.provenance().isEmpty());

        CatalogSnapshotStore nestedStore = new CatalogSnapshotStore();
        try (CatalogSnapshotStore.CandidateScope ignored =
                     nestedStore.openCandidate(request.namespace())) {
            CatalogRefreshException nestedFailure = assertThrows(
                    CatalogRefreshException.class,
                    () -> new CatalogRefreshCoordinator(nestedStore).refresh(
                            request, emptyPlan, context -> {
                            }));
            assertTrue(nestedFailure.getCause() instanceof IllegalStateException);
            assertEquals("CATALOG_REFRESH_NESTED_OWNER_REQUIRED",
                    nestedFailure.getCause().getMessage());
        }

        CatalogSnapshotStore seededStore = new CatalogSnapshotStore();
        CatalogSnapshot active = seed(
                seededStore, request.namespace(), Set.of(QUERY_X));
        CatalogRefreshPlan seededPlan = CatalogRefreshPlan.namespace(Set.of(QUERY_X));
        CatalogRefreshCoordinator seededCoordinator =
                new CatalogRefreshCoordinator(seededStore);
        AssertionError fatal = new AssertionError("controlled fatal refresh failure");
        assertSame(fatal, assertThrows(AssertionError.class,
                () -> seededCoordinator.refresh(
                        request, seededPlan, ignored -> {
                            throw fatal;
                        })));
        assertSame(active, seededStore.readCurrent(request.namespace()).orElseThrow());
        assertEquals(CatalogAdmissionState.ACTIVE_OLD_PRESERVED,
                seededStore.admissionState(request.namespace()));

        IOException checked = new IOException("controlled checked refresh failure");
        CatalogRefreshException checkedFailure = assertThrows(
                CatalogRefreshException.class,
                () -> seededCoordinator.refresh(
                        request, seededPlan, ignored -> {
                            throw checked;
                        }));
        assertEquals("CATALOG_REFRESH_FAILED", checkedFailure.code());
        assertSame(checked, checkedFailure.getCause().getCause());
        assertSame(active, seededStore.readCurrent(request.namespace()).orElseThrow());

        CatalogRefreshException normalized = new CatalogRefreshException(
                "CONTROLLED_REFRESH_FAILURE",
                request,
                active.identity(),
                CatalogAdmissionState.ACTIVE_OLD_PRESERVED,
                List.of(),
                new IllegalStateException("controlled normalized failure"));
        assertSame(normalized, assertThrows(CatalogRefreshException.class,
                () -> seededCoordinator.refresh(
                        request, seededPlan, ignored -> {
                            throw normalized;
                        })));

        CatalogSnapshotStore guardedStore = new CatalogSnapshotStore();
        DatasourceBindingIdentity tracked = new DatasourceBindingIdentity(
                "test:guard-required",
                "test:sqlite",
                new DatasourceBindingGeneration("generation-one"));
        CatalogRefreshException guardFailure = assertThrows(
                CatalogRefreshException.class,
                () -> new CatalogRefreshCoordinator(guardedStore).refresh(
                        CatalogRefreshRequest.namespace(
                                "guard-required",
                                CatalogRefreshTrigger.EXPLICIT_RECOVERY),
                        CatalogRefreshPlan.namespace(Set.of(QUERY_Y)),
                        context -> stageTrackedQuery(
                                context.candidate(), QUERY_Y, tracked)));
        assertEquals("CATALOG_REFRESH_FAILED", guardFailure.code());
        assertEquals("DATASOURCE_BINDING_PUBLICATION_GUARD_UNAVAILABLE",
                guardFailure.getCause().getMessage());
        assertTrue(guardedStore.current("guard-required").isEmpty());
    }

    private static void assertUnstableConfiguredDiscoveryFailsAfterBoundedCaptureAttempts() {
        CatalogSnapshotStore store = new CatalogSnapshotStore(
                new StepwiseSourceRevisionGuard());
        TableModelLoaderManagerImpl tableLoader =
                mock(TableModelLoaderManagerImpl.class);
        QueryModelLoaderImpl queryLoader = mock(QueryModelLoaderImpl.class);
        AtomicInteger discoveries = new AtomicInteger();
        when(queryLoader.discoverQueryModelNames("unstable-discovery"))
                .thenAnswer(ignored -> {
                    discoveries.incrementAndGet();
                    return Set.of();
                });
        CatalogRefreshCoordinator coordinator = new CatalogRefreshCoordinator(
                store, tableLoader, queryLoader);
        CatalogRefreshRequest request = CatalogRefreshRequest.namespace(
                "unstable-discovery", CatalogRefreshTrigger.FILE);

        CatalogRefreshException failure = assertThrows(
                CatalogRefreshException.class,
                () -> coordinator.refresh(request, ignored -> {
                }));

        assertEquals("SOURCE_REVISION_STALE", failure.code());
        assertEquals(3, discoveries.get());
        assertTrue(store.current(request.namespace()).isEmpty());
    }

    private static void awaitRelease(
            CountDownLatch bothEntered,
            CountDownLatch release
    ) throws InterruptedException {
        bothEntered.countDown();
        assertTrue(release.await(5, TimeUnit.SECONDS));
    }

    private static CatalogSnapshot seed(
            CatalogSnapshotStore store,
            String namespace,
            Set<String> names
    ) {
        try (CatalogSnapshotStore.CandidateScope scope =
                     store.openCandidate(namespace)) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(names);
            for (String name : names) {
                stageQuery(candidate, name);
            }
            return scope.commit();
        }
    }

    private static CatalogSnapshot seedUntracked(
            CatalogSnapshotStore store,
            String namespace,
            String name
    ) {
        try (CatalogSnapshotStore.CandidateScope scope =
                     store.openCandidate(namespace)) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(Set.of(name));
            candidate.putQueryModel(
                    name,
                    queryModel(name, candidate.aliasFor(name)),
                    new ModelProvenance(
                            name,
                            ModelProvenance.ModelKind.QUERY,
                            candidate.sourceRevision(),
                            Set.of(),
                            Map.of(),
                            false,
                            List.of()));
            return scope.commit();
        }
    }

    private static CatalogSnapshot seedMixed(
            CatalogSnapshotStore store,
            String namespace,
            String name,
            DatasourceBindingIdentity tracked
    ) {
        try (CatalogSnapshotStore.CandidateScope scope =
                     store.openCandidate(namespace)) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(Set.of(name));
            candidate.putQueryModel(
                    name,
                    queryModel(name, candidate.aliasFor(name)),
                    new ModelProvenance(
                            name,
                            ModelProvenance.ModelKind.QUERY,
                            candidate.sourceRevision(),
                            Set.of(),
                            Map.of(tracked.bindingKey(), tracked),
                            false,
                            List.of()));
            return scope.commit();
        }
    }

    private static void stageQuery(CatalogCandidate candidate, String name) {
        candidate.putQueryModel(
                name,
                queryModel(name, candidate.aliasFor(name)),
                new ModelProvenance(
                        name,
                        ModelProvenance.ModelKind.QUERY,
                        candidate.sourceRevision(),
                        Set.of(),
                        Map.of(),
                        true,
                        List.of()));
    }

    private static void stageUntrackedQuery(
            CatalogCandidate candidate,
            String name
    ) {
        candidate.putQueryModel(
                name,
                queryModel(name, candidate.aliasFor(name)),
                new ModelProvenance(
                        name,
                        ModelProvenance.ModelKind.QUERY,
                        candidate.sourceRevision(),
                        Set.of(),
                        Map.of(),
                        false,
                        List.of()));
    }

    private static void stageTrackedQuery(
            CatalogCandidate candidate,
            String name,
            DatasourceBindingIdentity identity
    ) {
        stageTrackedQuery(
                candidate,
                name,
                identity,
                queryModel(name, candidate.aliasFor(name)));
    }

    private static void stageTrackedQuery(
            CatalogCandidate candidate,
            String name,
            DatasourceBindingIdentity identity,
            QueryModel model
    ) {
        candidate.putQueryModel(
                name,
                model,
                new ModelProvenance(
                        name,
                        ModelProvenance.ModelKind.QUERY,
                        candidate.sourceRevision(),
                        Set.of(),
                        Map.of(identity.bindingKey(), identity),
                        true,
                        List.of()));
    }

    private static QueryModel queryModel(String name, String alias) {
        QueryModel model = mock(QueryModel.class);
        when(model.getName()).thenReturn(name);
        when(model.getShortAlias()).thenReturn(alias);
        return model;
    }

    private static final class RejectingBindingResolver
            implements DatasourceBindingResolver {

        private final DatasourceBindingIdentity tracked;
        private int publicationGuards;

        private RejectingBindingResolver(DatasourceBindingIdentity tracked) {
            this.tracked = tracked;
        }

        @Override
        public ResolvedDatasourceBinding resolveBinding(String name) {
            return null;
        }

        @Override
        public BindingCurrentness currentness(
                DatasourceBindingIdentity identity
        ) {
            return tracked.equals(identity)
                    ? BindingCurrentness.CURRENT
                    : BindingCurrentness.STALE;
        }

        @Override
        public <T> T publishIfCurrent(
                Collection<DatasourceBindingIdentity> identities,
                Supplier<T> publication
        ) {
            publicationGuards++;
            if (!List.copyOf(identities).equals(List.of(tracked))) {
                throw new IllegalStateException(
                        "unexpected publication binding set");
            }
            throw new StaleDatasourceBindingException(tracked.bindingKey());
        }
    }

    private static final class StepwiseSourceRevisionGuard
            implements CommittedSourceRevisionGuard {

        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public SourceRevision currentSourceRevision(String namespace) {
            int capture = reads.getAndIncrement() / 2 + 1;
            return new SourceRevision("stepwise-source-" + capture);
        }

        @Override
        public <T> T publishIfCurrent(
                String namespace,
                SourceRevision expected,
                Supplier<T> publication
        ) {
            return publication.get();
        }
    }

    private static final class MonitorTrackingBindingResolver
            implements DatasourceBindingResolver {

        private final Object mutationMonitor = new Object();
        private final DatasourceBindingIdentity tracked;
        private final boolean rejectPublication;
        private final AtomicInteger snapshotValidationCalls = new AtomicInteger();
        private int publicationGuards;

        private MonitorTrackingBindingResolver(
                DatasourceBindingIdentity tracked,
                boolean rejectPublication
        ) {
            this.tracked = tracked;
            this.rejectPublication = rejectPublication;
        }

        private QueryModel observedQueryModel(String name, String alias) {
            QueryModel model = mock(QueryModel.class);
            when(model.getName()).thenAnswer(ignored -> {
                assertFalse(Thread.holdsLock(mutationMonitor),
                        "snapshot validation must not hold the binding mutation monitor");
                snapshotValidationCalls.incrementAndGet();
                return name;
            });
            when(model.getShortAlias()).thenReturn(alias);
            return model;
        }

        @Override
        public ResolvedDatasourceBinding resolveBinding(String name) {
            return null;
        }

        @Override
        public BindingCurrentness currentness(
                DatasourceBindingIdentity identity
        ) {
            return tracked.equals(identity)
                    ? BindingCurrentness.CURRENT
                    : BindingCurrentness.STALE;
        }

        @Override
        public <T> T publishIfCurrent(
                Collection<DatasourceBindingIdentity> identities,
                Supplier<T> publication
        ) {
            synchronized (mutationMonitor) {
                publicationGuards++;
                assertEquals(List.of(tracked), List.copyOf(identities));
                assertTrue(snapshotValidationCalls.get() > 0,
                        "snapshot must be prepared before entering the binding guard");
                if (rejectPublication) {
                    throw new StaleDatasourceBindingException(
                            tracked.bindingKey());
                }
                return publication.get();
            }
        }
    }
}
