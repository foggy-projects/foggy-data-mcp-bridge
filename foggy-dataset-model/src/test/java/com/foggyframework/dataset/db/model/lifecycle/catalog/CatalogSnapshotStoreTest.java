package com.foggyframework.dataset.db.model.lifecycle.catalog;

import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogSnapshotStoreTest {

    private void assertInvalidBootEpochAndAdmissionDiagnosticsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogSnapshotStore((String) null));
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogSnapshotStore("  "));

        CatalogSnapshotStore store = new CatalogSnapshotStore("diagnostic-boot");
        assertTrue(store.admissionDiagnostic("absent").isEmpty());

        store.markStaleAdmissionBlocked("null-diagnostic", null);
        store.markStaleAdmissionBlocked("blank-diagnostic", "  ");
        store.markStaleAdmissionBlocked("custom-diagnostic", "controlled source drift");

        assertEquals("catalog source scope is unknown",
                store.admissionDiagnostic("null-diagnostic").orElseThrow());
        assertEquals("catalog source scope is unknown",
                store.admissionDiagnostic("blank-diagnostic").orElseThrow());
        assertEquals("controlled source drift",
                store.admissionDiagnostic("custom-diagnostic").orElseThrow());
    }

    private void assertConditionalStaleMarkerOnlyMutatesTheExactCapturedView() {
        CatalogSnapshotStore store = new CatalogSnapshotStore("conditional-stale");
        CatalogBuildView captured = store.capture("tenant-a");

        assertTrue(store.markStaleAdmissionBlockedIfCurrent(captured, null));
        assertEquals(CatalogAdmissionState.STALE_ADMISSION_BLOCKED,
                store.admissionState("tenant-a"));
        assertEquals("catalog source scope is unknown",
                store.admissionDiagnostic("tenant-a").orElseThrow());

        CatalogBuildView blocked = store.capture("tenant-a");
        assertTrue(store.markStaleAdmissionBlockedIfCurrent(
                blocked, "must not replace the first diagnostic"));
        assertEquals("catalog source scope is unknown",
                store.admissionDiagnostic("tenant-a").orElseThrow());
        assertFalse(store.markStaleAdmissionBlockedIfCurrent(
                captured, "stale attempt must not win"));

        CatalogBuildView blank = store.capture("tenant-b");
        assertTrue(store.markStaleAdmissionBlockedIfCurrent(blank, "  "));
        assertEquals("catalog source scope is unknown",
                store.admissionDiagnostic("tenant-b").orElseThrow());

        CatalogBuildView custom = store.capture("tenant-c");
        assertTrue(store.markStaleAdmissionBlockedIfCurrent(
                custom, "controlled registry mismatch"));
        assertEquals("controlled registry mismatch",
                store.admissionDiagnostic("tenant-c").orElseThrow());
    }

    private void assertActiveOldMarkerRequiresAnExistingCatalogAndPreservesNewerBlocks() {
        CatalogSnapshotStore store = new CatalogSnapshotStore("active-old-marker");
        IllegalStateException absent = assertThrows(
                IllegalStateException.class,
                () -> store.markActiveOldPreserved("absent", null));
        assertTrue(absent.getMessage().startsWith("CATALOG_ACTIVE_OLD_ABSENT:"));

        CatalogSnapshot active = seed(store, "tenant-a", "ActiveQueryModel");
        store.markActiveOldPreserved("tenant-a", null);
        assertSame(active, store.readCurrent("tenant-a").orElseThrow());
        assertEquals("known-scope catalog refresh failed; active catalog preserved",
                store.admissionDiagnostic("tenant-a").orElseThrow());

        store.markActiveOldPreserved("tenant-a", "controlled refresh failure");
        assertEquals("controlled refresh failure",
                store.admissionDiagnostic("tenant-a").orElseThrow());

        seed(store, "tenant-b", "BlankDiagnosticQueryModel");
        store.markActiveOldPreserved("tenant-b", "  ");
        assertEquals("known-scope catalog refresh failed; active catalog preserved",
                store.admissionDiagnostic("tenant-b").orElseThrow());

        store.markStaleAdmissionBlocked("tenant-a", "newer fail-closed decision");
        store.markActiveOldPreserved("tenant-a", "late refresh failure");
        assertEquals(CatalogAdmissionState.STALE_ADMISSION_BLOCKED,
                store.admissionState("tenant-a"));
        assertEquals("newer fail-closed decision",
                store.admissionDiagnostic("tenant-a").orElseThrow());
    }

    @Test
    void plainReadAndNoOpMustNotAdvanceGenerationWhileMaterializationAdvancesExactlyOnce() {
        CatalogSnapshotStore store = new CatalogSnapshotStore("controlled-boot");
        assertFalse(store.current("tenant-a").isPresent());

        CatalogSnapshot first;
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("tenant-a")) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(Set.of("FactOrderQueryModel"));
            QueryModel model = queryModel("FactOrderQueryModel", candidate.aliasFor("FactOrderQueryModel"));
            candidate.putQueryModel("FactOrderQueryModel", model,
                    provenance(candidate, "FactOrderQueryModel", ModelProvenance.ModelKind.QUERY));
            first = scope.commit();
        }

        assertSame(first, store.current("tenant-a").orElseThrow());
        assertSame(first, store.current("tenant-a").orElseThrow());

        CatalogSnapshot noOp;
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("tenant-a")) {
            noOp = scope.commit();
        }
        assertSame(first, noOp);

        CatalogSnapshot second;
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("tenant-a")) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(Set.of("FactOrderQueryModel", "FactPaymentQueryModel"));
            QueryModel model = queryModel("FactPaymentQueryModel", candidate.aliasFor("FactPaymentQueryModel"));
            candidate.putQueryModel("FactPaymentQueryModel", model,
                    provenance(candidate, "FactPaymentQueryModel", ModelProvenance.ModelKind.QUERY));
            second = scope.commit();
        }
        assertNotEquals(first.identity().generation(), second.identity().generation());
        assertEquals(2, second.queryModels().size());
        assertInvalidBootEpochAndAdmissionDiagnosticsFailClosed();
    }

    @Test
    void failedCandidateMustLeaveActiveSnapshotAndGenerationUntouched() {
        CatalogSnapshotStore store = new CatalogSnapshotStore("controlled-failure-boot");
        CatalogSnapshot before;
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("")) {
            scope.candidate().discoverQueryModels(Set.of("GoodQueryModel"));
            before = scope.commit();
        }

        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("")) {
            scope.candidate().discoverQueryModels(Set.of("GoodQueryModel", "BrokenQueryModel"));
            scope.candidate().fail("controlled build failure");
            assertThrows(IllegalStateException.class, scope::commit);
        }

        CatalogSnapshot after = store.current("").orElseThrow();
        assertSame(before, after);
        assertEquals(before.identity(), after.identity());
        assertFalse(after.discoveredQueryModelNames().contains("BrokenQueryModel"));
    }

    @Test
    void failureWithoutOtherChangesMustNotBeTreatedAsNoOpPublication() {
        CatalogSnapshotStore store = new CatalogSnapshotStore("pure-failure-boot");
        CatalogSnapshot before;
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("tenant-a")) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(Set.of("GoodQueryModel"));
            candidate.putQueryModel(
                    "GoodQueryModel",
                    queryModel("GoodQueryModel", candidate.aliasFor("GoodQueryModel")),
                    provenance(candidate, "GoodQueryModel", ModelProvenance.ModelKind.QUERY));
            before = scope.commit();
        }

        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("tenant-a")) {
            scope.candidate().fail("controlled failure without directory mutation");
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class, scope::commit);
            assertTrue(failure.getMessage().contains(
                    "controlled failure without directory mutation"));
        }

        CatalogSnapshot after = store.current("tenant-a").orElseThrow();
        assertSame(before, after);
        assertEquals("catalog:pure-failure-boot:1",
                after.identity().generation().value());
        assertActiveOldMarkerRequiresAnExistingCatalogAndPreservesNewerBlocks();
    }

    @Test
    void nestedTableAndQueryBuildMustPublishOneSnapshot() {
        CatalogSnapshotStore store = new CatalogSnapshotStore("controlled-nested-boot");
        CatalogSnapshot published;
        try (CatalogSnapshotStore.CandidateScope outer = store.openCandidate("tenant-nested")) {
            CatalogCandidate candidate = outer.candidate();
            candidate.discoverQueryModels(Set.of("NestedQueryModel"));
            try (CatalogSnapshotStore.CandidateScope inner = store.openCandidate("tenant-nested")) {
                assertFalse(inner.isOwner());
                TableModel tableModel = mock(TableModel.class);
                when(tableModel.getName()).thenReturn("NestedTableModel");
                inner.candidate().putTableModel("NestedTableModel", tableModel,
                        provenance(candidate, "NestedTableModel", ModelProvenance.ModelKind.TABLE));
            }
            QueryModel queryModel = queryModel("NestedQueryModel", candidate.aliasFor("NestedQueryModel"));
            candidate.putQueryModel("NestedQueryModel", queryModel, new ModelProvenance(
                    "NestedQueryModel", ModelProvenance.ModelKind.QUERY, candidate.sourceRevision(),
                    Set.of(CatalogModelKey.table("NestedTableModel")), Map.of(), false, List.of()));
            published = outer.commit();
        }

        assertEquals("catalog:controlled-nested-boot:1", published.identity().generation().value());
        assertEquals(Set.of(CatalogModelKey.table("NestedTableModel")),
                published.provenance().get(CatalogModelKey.query("NestedQueryModel"))
                        .modelDependencies());
        assertEquals(1, published.tableModels().size());
        assertEquals(1, published.queryModels().size());
    }

    @Test
    void aliasPlanMustBeIndependentOfDiscoveryArrivalOrder() {
        CatalogSnapshotStore forwardStore = new CatalogSnapshotStore("forward");
        CatalogSnapshotStore reverseStore = new CatalogSnapshotStore("reverse");

        Map<String, String> forward = aliases(forwardStore,
                List.of("DimChannelQueryModel", "DimCustomerQueryModel"));
        Map<String, String> reverse = aliases(reverseStore,
                List.of("DimCustomerQueryModel", "DimChannelQueryModel"));

        assertEquals(forward, reverse);
        assertEquals(Map.of("DimChannelQueryModel", "DC", "DimCustomerQueryModel", "DC2"), forward);
    }

    @Test
    void sourceRevisionsMustBeOpaqueNonReusableAndNamespaceScoped() {
        CatalogSnapshotStore store = new CatalogSnapshotStore("source-identity-boot");

        assertSame(store.currentSourceRevision("tenant-a"),
                store.currentSourceRevision("tenant-a"));
        assertNotEquals(store.currentSourceRevision("tenant-a"),
                store.currentSourceRevision("tenant-b"));
        assertNotEquals(store.currentSourceRevision("tenant-a"),
                store.advanceSourceRevision("tenant-a"));
    }

    @Test
    void dynamicSyntheticAliasesMustRemainStableAcrossSequentialArrivalOrder() {
        List<String> forwardOrder = List.of(
                "FactSalesNestedDimQueryModel#product",
                "FactSalesNestedDimQueryModel#customer");
        List<String> reverseOrder = List.of(
                "FactSalesNestedDimQueryModel#customer",
                "FactSalesNestedDimQueryModel#product");

        Map<String, String> forward = syntheticAliases(
                new CatalogSnapshotStore("synthetic-forward"), forwardOrder);
        Map<String, String> reverse = syntheticAliases(
                new CatalogSnapshotStore("synthetic-reverse"), reverseOrder);

        assertEquals(forward, reverse);
        assertNotEquals(
                forward.get("FactSalesNestedDimQueryModel#product"),
                forward.get("FactSalesNestedDimQueryModel#customer"));
    }

    @Test
    void committedCandidateMustBeSealedAgainstPostPublishMutation() {
        CatalogSnapshotStore store = new CatalogSnapshotStore("sealed-candidate");
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("")) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(Set.of("StableQueryModel"));
            CatalogSnapshot published = scope.commit();

            assertThrows(IllegalStateException.class,
                    () -> candidate.discoverQueryModels(Set.of("LateQueryModel")));
            assertSame(published, store.current("").orElseThrow());
        }
    }

    @Test
    void wrongThreadCloseMustNotPoisonOwnerScopeOrNamespaceLock() throws Exception {
        CatalogSnapshotStore store = new CatalogSnapshotStore("owner-thread");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("tenant-a")) {
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> executor.submit(scope::close).get());
            assertTrue(failure.getCause() instanceof IllegalStateException);

            scope.candidate().discoverQueryModels(Set.of("OwnerQueryModel"));
            scope.commit();
        } finally {
            executor.shutdownNow();
        }

        try (CatalogSnapshotStore.CandidateScope next = store.openCandidate("tenant-a")) {
            assertSame(store.current("tenant-a").orElseThrow(), next.commit());
        }
    }

    @Test
    void capturedCandidateMustRejectDirectMutationFromAnotherThread() throws Exception {
        CatalogSnapshotStore store = new CatalogSnapshotStore("candidate-owner-thread");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("tenant-a")) {
            CatalogCandidate candidate = scope.candidate();
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> executor.submit(() -> candidate.discoverQueryModels(
                            Set.of("WrongThreadQueryModel"))).get());
            assertEquals("catalog candidate must be used by its owner thread",
                    failure.getCause().getMessage());

            candidate.discoverQueryModels(Set.of("OwnerQueryModel"));
            CatalogSnapshot published = scope.commit();
            assertFalse(published.discoveredQueryModelNames()
                    .contains("WrongThreadQueryModel"));
            assertTrue(published.discoveredQueryModelNames()
                    .contains("OwnerQueryModel"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void coldBuildViewMustRemainStableUntilFirstPublication() {
        CatalogSnapshotStore store = new CatalogSnapshotStore("stable-cold-view");

        CatalogBuildView first = store.capture(" tenant-a ");
        CatalogBuildView second = store.capture("tenant-a");
        assertEquals(first, second);
        assertTrue(first.cold());
        assertTrue(first.catalogGeneration().isEmpty());

        CatalogSnapshot published;
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(first)) {
            scope.candidate().discoverQueryModels(Set.of("FirstQueryModel"));
            published = scope.commit();
        }

        CatalogBuildView after = store.capture("tenant-a");
        assertFalse(after.cold());
        assertNotEquals(first, after);
        assertSame(published, after.baseSnapshot());
        assertEquals(published.identity().generation(), after.catalogGeneration().orElseThrow());
        assertEquals(first.sourceRevision(), after.sourceRevision());
    }

    @Test
    void detachedCandidatesMustBuildConcurrentlyAndRejectTheStaleBasePublisher()
            throws Exception {
        CatalogSnapshotStore store = new CatalogSnapshotStore("detached-overlap");
        CatalogBuildView captured = store.capture("tenant-a");
        CountDownLatch bothCandidatesReady = new CountDownLatch(2);
        CountDownLatch releasePublication = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<CommitAttempt> first = executor.submit(() -> detachedCommitAttempt(
                store, captured, "FirstQueryModel", bothCandidatesReady, releasePublication));
        Future<CommitAttempt> second = executor.submit(() -> detachedCommitAttempt(
                store, captured, "SecondQueryModel", bothCandidatesReady, releasePublication));

        try {
            assertTrue(bothCandidatesReady.await(5, TimeUnit.SECONDS),
                    "different detached candidates must overlap before publication");
            releasePublication.countDown();

            CommitAttempt firstResult = first.get(5, TimeUnit.SECONDS);
            CommitAttempt secondResult = second.get(5, TimeUnit.SECONDS);
            assertEquals(1, (firstResult.published() ? 1 : 0)
                    + (secondResult.published() ? 1 : 0));
            assertEquals(1, (firstResult.staleReason() != null ? 1 : 0)
                    + (secondResult.staleReason() != null ? 1 : 0));
            StaleCatalogBuildException.Reason staleReason = firstResult.staleReason() != null
                    ? firstResult.staleReason()
                    : secondResult.staleReason();
            assertEquals(StaleCatalogBuildException.Reason.BASE_CATALOG_CHANGED, staleReason);
            assertEquals(1, store.current("tenant-a").orElseThrow()
                    .discoveredQueryModelNames().size());
        } finally {
            releasePublication.countDown();
            first.cancel(true);
            second.cancel(true);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void staleBaseMustFailWithoutReplacingTheWinningSnapshot() {
        CatalogSnapshotStore store = new CatalogSnapshotStore("stale-base");
        CatalogBuildView stale = store.capture("tenant-a");
        CatalogSnapshot winner;
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(stale)) {
            scope.candidate().discoverQueryModels(Set.of("WinnerQueryModel"));
            winner = scope.commit();
        }

        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(stale)) {
            scope.candidate().discoverQueryModels(Set.of("StaleQueryModel"));
            StaleCatalogBuildException failure = assertThrows(
                    StaleCatalogBuildException.class, scope::commit);
            assertEquals(StaleCatalogBuildException.Reason.BASE_CATALOG_CHANGED,
                    failure.reason());
            assertEquals("tenant-a", failure.namespace());
        }

        assertSame(winner, store.current("tenant-a").orElseThrow());
        assertFalse(winner.discoveredQueryModelNames().contains("StaleQueryModel"));
    }

    @Test
    void sourceAdvanceMustRejectADetachedCandidateWithoutPublishingIt() {
        CatalogSnapshotStore store = new CatalogSnapshotStore("stale-source");
        CatalogBuildView stale = store.capture("tenant-a");
        assertNotEquals(stale.sourceRevision(), store.advanceSourceRevision("tenant-a"));

        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(stale)) {
            scope.candidate().discoverQueryModels(Set.of("StaleSourceQueryModel"));
            StaleCatalogBuildException failure = assertThrows(
                    StaleCatalogBuildException.class, scope::commit);
            assertEquals(StaleCatalogBuildException.Reason.SOURCE_REVISION_CHANGED,
                    failure.reason());
        }

        assertTrue(store.current("tenant-a").isEmpty());
        CatalogBuildView current = store.capture("tenant-a");
        assertTrue(current.cold());
        assertNotEquals(stale.sourceRevision(), current.sourceRevision());
        assertConditionalStaleMarkerOnlyMutatesTheExactCapturedView();
    }

    @Test
    void preparedCandidateMustRecheckSourceAtFinalCommit() {
        CatalogSnapshotStore store = new CatalogSnapshotStore(
                "prepared-stale-source");
        CatalogBuildView captured = store.capture("tenant-a");

        try (CatalogSnapshotStore.CandidateScope scope =
                     store.openCandidate(captured)) {
            scope.candidate().discoverQueryModels(
                    Set.of("PreparedQueryModel"));
            scope.prepareCommit();
            store.advanceSourceRevision("tenant-a");

            StaleCatalogBuildException failure = assertThrows(
                    StaleCatalogBuildException.class, scope::commit);
            assertEquals(
                    StaleCatalogBuildException.Reason.SOURCE_REVISION_CHANGED,
                    failure.reason());
        }

        assertTrue(store.current("tenant-a").isEmpty());
    }

    @Test
    void preparedCandidateMustRecheckBaseAtFinalCommit() throws Exception {
        CatalogSnapshotStore store = new CatalogSnapshotStore(
                "prepared-stale-base");
        CatalogBuildView captured = store.capture("tenant-a");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (CatalogSnapshotStore.CandidateScope stale =
                     store.openCandidate(captured)) {
            stale.candidate().discoverQueryModels(
                    Set.of("PreparedQueryModel"));
            stale.prepareCommit();

            CatalogSnapshot winner = executor.submit(() -> {
                try (CatalogSnapshotStore.CandidateScope scope =
                             store.openCandidate(captured)) {
                    scope.candidate().discoverQueryModels(
                            Set.of("WinnerQueryModel"));
                    return scope.commit();
                }
            }).get(5, TimeUnit.SECONDS);

            StaleCatalogBuildException failure = assertThrows(
                    StaleCatalogBuildException.class, stale::commit);
            assertEquals(
                    StaleCatalogBuildException.Reason.BASE_CATALOG_CHANGED,
                    failure.reason());
            assertSame(winner, store.current("tenant-a").orElseThrow());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void clearMustPreventANullBaseCandidateFromPassingAnAbaCheck() {
        CatalogSnapshotStore store = new CatalogSnapshotStore("clear-aba");
        CatalogBuildView oldColdView = store.capture("tenant-a");
        try (CatalogSnapshotStore.CandidateScope scope =
                     store.openCandidate("tenant-a")) {
            scope.candidate().discoverQueryModels(Set.of("PublishedQueryModel"));
            scope.commit();
        }
        store.clearNamespace("tenant-a");

        try (CatalogSnapshotStore.CandidateScope stale =
                     store.openCandidate(oldColdView)) {
            stale.candidate().discoverQueryModels(Set.of("LateQueryModel"));
            StaleCatalogBuildException failure = assertThrows(
                    StaleCatalogBuildException.class,
                    stale::commit);
            assertEquals(StaleCatalogBuildException.Reason.SOURCE_REVISION_CHANGED,
                    failure.reason());
        }
        assertFalse(store.current("tenant-a").isPresent());
    }

    @Test
    void ownerScopeCloseMustSealAbandonedFailedAndStaleCandidates() {
        CatalogSnapshotStore store = new CatalogSnapshotStore("seal-on-close");

        CatalogCandidate abandoned;
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("tenant-a")) {
            abandoned = scope.candidate();
        }
        assertSealed(abandoned, "AbandonedQueryModel");

        CatalogCandidate failed;
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("tenant-a")) {
            failed = scope.candidate();
            failed.fail("controlled failure");
            assertThrows(IllegalStateException.class, scope::commit);
        }
        assertSealed(failed, "FailedQueryModel");

        CatalogBuildView staleView = store.capture("tenant-a");
        store.advanceSourceRevision("tenant-a");
        CatalogCandidate stale;
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(staleView)) {
            stale = scope.candidate();
            stale.discoverQueryModels(Set.of("StaleQueryModel"));
            assertThrows(StaleCatalogBuildException.class, scope::commit);
        }
        assertSealed(stale, "LateStaleQueryModel");
    }

    private CommitAttempt detachedCommitAttempt(
            CatalogSnapshotStore store,
            CatalogBuildView captured,
            String modelName,
            CountDownLatch candidatesReady,
            CountDownLatch releasePublication
    ) throws InterruptedException {
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(captured)) {
            scope.candidate().discoverQueryModels(Set.of(modelName));
            candidatesReady.countDown();
            if (!releasePublication.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting to release detached publication");
            }
            try {
                scope.commit();
                return new CommitAttempt(true, null);
            } catch (StaleCatalogBuildException stale) {
                return new CommitAttempt(false, stale.reason());
            }
        }
    }

    private record CommitAttempt(
            boolean published,
            StaleCatalogBuildException.Reason staleReason
    ) {
    }

    private void assertSealed(CatalogCandidate candidate, String lateModelName) {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> candidate.discoverQueryModels(Set.of(lateModelName)));
        assertEquals("catalog candidate is sealed after publication", failure.getMessage());
    }

    private CatalogSnapshot seed(
            CatalogSnapshotStore store,
            String namespace,
            String modelName
    ) {
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(namespace)) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(Set.of(modelName));
            candidate.putQueryModel(
                    modelName,
                    queryModel(modelName, candidate.aliasFor(modelName)),
                    provenance(candidate, modelName, ModelProvenance.ModelKind.QUERY));
            return scope.commit();
        }
    }

    private Map<String, String> aliases(CatalogSnapshotStore store, List<String> arrivalOrder) {
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("")) {
            CatalogCandidate candidate = scope.candidate();
            for (String name : arrivalOrder) {
                candidate.discoverQueryModels(Set.of(name));
            }
            CatalogSnapshot snapshot = scope.commit();
            return snapshot.canonicalToAlias();
        }
    }

    private Map<String, String> syntheticAliases(
            CatalogSnapshotStore store,
            List<String> arrivalOrder
    ) {
        String sourceName = "FactSalesNestedDimQueryModel";
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("")) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(Set.of(sourceName));
            candidate.putQueryModel(sourceName,
                    queryModel(sourceName, candidate.aliasFor(sourceName)),
                    provenance(candidate, sourceName, ModelProvenance.ModelKind.QUERY));
            scope.commit();
        }
        for (String syntheticName : arrivalOrder) {
            try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate("")) {
                CatalogCandidate candidate = scope.candidate();
                candidate.discoverQueryModels(Set.of(syntheticName));
                candidate.putSyntheticQueryModel(
                        syntheticName,
                        queryModel(syntheticName, candidate.aliasFor(syntheticName)),
                        new ModelProvenance(
                                syntheticName,
                                ModelProvenance.ModelKind.SYNTHETIC_QUERY,
                                candidate.sourceRevision(),
                                Set.of(CatalogModelKey.query(sourceName)),
                                Map.of(),
                                false,
                                List.of()));
                scope.commit();
            }
        }
        CatalogSnapshot snapshot = store.current("").orElseThrow();
        Map<String, String> aliases = new java.util.TreeMap<>();
        arrivalOrder.forEach(name -> aliases.put(name, snapshot.canonicalToAlias().get(name)));
        return aliases;
    }

    private QueryModel queryModel(String name, String alias) {
        QueryModel model = mock(QueryModel.class);
        when(model.getName()).thenReturn(name);
        when(model.getShortAlias()).thenReturn(alias);
        return model;
    }

    private ModelProvenance provenance(
            CatalogCandidate candidate,
            String name,
            ModelProvenance.ModelKind kind
    ) {
        return new ModelProvenance(name, kind, candidate.sourceRevision(),
                Set.of(), Map.of(), false, List.of());
    }
}
