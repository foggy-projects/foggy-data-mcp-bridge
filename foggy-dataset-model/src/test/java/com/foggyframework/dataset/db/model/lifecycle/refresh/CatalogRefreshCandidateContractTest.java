package com.foggyframework.dataset.db.model.lifecycle.refresh;

import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogCandidate;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.db.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Deterministic candidate semantics required by namespace/model refresh. */
class CatalogRefreshCandidateContractTest {

    private static final String NAMESPACE = "tenant-refresh";
    private static final String QUERY_X = "RefreshXQueryModel";
    private static final String QUERY_Y = "RefreshYQueryModel";
    private static final String TABLE_X = "RefreshXTableModel";
    private static final String TABLE_Y = "RefreshYTableModel";

    @Test
    void namespaceRefreshMustReplaceTheExactDiscoveryAndDropRemovedSlots() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        seedQueryOnlyCatalog(store, Set.of(QUERY_X, QUERY_Y));
        store.advanceSourceRevision(NAMESPACE);

        CatalogSnapshot published;
        try (CatalogSnapshotStore.CandidateScope scope =
                     store.openCandidate(store.capture(NAMESPACE))) {
            CatalogCandidate candidate = scope.candidate();
            invokeRequired(
                    candidate,
                    "resetForNamespaceRefresh",
                    new Class<?>[]{Collection.class},
                    List.of(QUERY_X)
            );

            assertNull(candidate.findQueryModel(QUERY_Y),
                    "a namespace rebuild must not retain a model removed from committed source");
            assertNull(candidate.modelProvenance(CatalogModelKey.query(QUERY_Y)),
                    "removed model provenance must not survive a namespace rebuild");

            QueryModel replacement = queryModel(QUERY_X, candidate.aliasFor(QUERY_X));
            candidate.putQueryModel(
                    QUERY_X,
                    replacement,
                    provenance(candidate, CatalogModelKey.query(QUERY_X), Set.of(), Map.of(), true)
            );
            published = scope.commit();
        }

        assertEquals(Set.of(QUERY_X), published.discoveredQueryModelNames());
        assertEquals(Set.of(QUERY_X), published.queryModels().keySet());
        assertEquals(Set.of(QUERY_X), published.canonicalToAlias().keySet());
    }

    @Test
    void modelRefreshMustInvalidateTargetAndReverseDependentsButPreserveSibling() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        CatalogSnapshot before = seedDependentCatalog(store, true);

        try (CatalogSnapshotStore.CandidateScope scope =
                     store.openCandidate(store.capture(NAMESPACE))) {
            CatalogCandidate candidate = scope.candidate();
            @SuppressWarnings("unchecked")
            Set<CatalogModelKey> closure = (Set<CatalogModelKey>) invokeRequired(
                    candidate,
                    "invalidateForModelRefresh",
                    new Class<?>[]{Collection.class, Collection.class},
                    Set.of(CatalogModelKey.table(TABLE_X)),
                    Set.of(QUERY_X, QUERY_Y)
            );

            assertEquals(
                    Set.of(CatalogModelKey.table(TABLE_X), CatalogModelKey.query(QUERY_X)),
                    closure,
                    "refresh closure must include reverse QM dependents"
            );
            assertNull(candidate.findTableModel(TABLE_X));
            assertNull(candidate.findQueryModel(QUERY_X));
            assertNull(candidate.modelProvenance(CatalogModelKey.table(TABLE_X)));
            assertNull(candidate.modelProvenance(CatalogModelKey.query(QUERY_X)));
            assertSame(before.tableModels().get(TABLE_Y), candidate.findTableModel(TABLE_Y));
            assertSame(before.queryModels().get(QUERY_Y), candidate.findQueryModel(QUERY_Y));
            assertEquals(before.provenance().get(CatalogModelKey.query(QUERY_Y)),
                    candidate.modelProvenance(CatalogModelKey.query(QUERY_Y)));
        }
    }

    @Test
    void effectiveBindingsMustBeDeduplicatedAndReturnedInCanonicalKeyOrder() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        seedDependentCatalog(store, true);

        try (CatalogSnapshotStore.CandidateScope scope =
                     store.openCandidate(store.capture(NAMESPACE))) {
            CatalogCandidate candidate = scope.candidate();
            @SuppressWarnings("unchecked")
            Map<String, DatasourceBindingIdentity> bindings =
                    (Map<String, DatasourceBindingIdentity>) invokeRequired(
                            candidate,
                            "effectiveDatasourceBindings",
                            new Class<?>[0]
                    );

            assertEquals(List.of("binding-a", "binding-z"),
                    List.copyOf(bindings.keySet()));
            assertEquals(2, bindings.size(),
                    "the same binding inherited by a TM and its QM must be deduplicated");
            assertTrue((Boolean) invokeRequired(
                    candidate,
                    "bindingIdentityComplete",
                    new Class<?>[0]
            ));
        }
    }

    @Test
    void anyUntrackedProvenanceMustMakeTheCandidateBindingSetIncomplete() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        seedDependentCatalog(store, false);

        try (CatalogSnapshotStore.CandidateScope scope =
                     store.openCandidate(store.capture(NAMESPACE))) {
            assertFalse((Boolean) invokeRequired(
                    scope.candidate(),
                    "bindingIdentityComplete",
                    new Class<?>[0]
            ));
        }
    }

    private static CatalogSnapshot seedQueryOnlyCatalog(
            CatalogSnapshotStore store,
            Set<String> names
    ) {
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(NAMESPACE)) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(names);
            for (String name : names) {
                candidate.putQueryModel(
                        name,
                        queryModel(name, candidate.aliasFor(name)),
                        provenance(candidate, CatalogModelKey.query(name), Set.of(), Map.of(), true)
                );
            }
            return scope.commit();
        }
    }

    private static CatalogSnapshot seedDependentCatalog(
            CatalogSnapshotStore store,
            boolean complete
    ) {
        DatasourceBindingIdentity bindingA = binding("binding-a", "generation-a");
        DatasourceBindingIdentity bindingZ = binding("binding-z", "generation-z");
        try (CatalogSnapshotStore.CandidateScope scope = store.openCandidate(NAMESPACE)) {
            CatalogCandidate candidate = scope.candidate();
            candidate.discoverQueryModels(Set.of(QUERY_X, QUERY_Y));

            TableModel tableX = tableModel(TABLE_X);
            TableModel tableY = tableModel(TABLE_Y);
            candidate.putTableModel(
                    TABLE_X,
                    tableX,
                    provenance(candidate, CatalogModelKey.table(TABLE_X), Set.of(),
                            Map.of(bindingZ.bindingKey(), bindingZ), complete)
            );
            candidate.putTableModel(
                    TABLE_Y,
                    tableY,
                    provenance(candidate, CatalogModelKey.table(TABLE_Y), Set.of(),
                            Map.of(bindingA.bindingKey(), bindingA), true)
            );
            candidate.putQueryModel(
                    QUERY_X,
                    queryModel(QUERY_X, candidate.aliasFor(QUERY_X)),
                    provenance(candidate, CatalogModelKey.query(QUERY_X),
                            Set.of(CatalogModelKey.table(TABLE_X)),
                            Map.of(bindingZ.bindingKey(), bindingZ), complete)
            );
            candidate.putQueryModel(
                    QUERY_Y,
                    queryModel(QUERY_Y, candidate.aliasFor(QUERY_Y)),
                    provenance(candidate, CatalogModelKey.query(QUERY_Y),
                            Set.of(CatalogModelKey.table(TABLE_Y)),
                            Map.of(bindingA.bindingKey(), bindingA), true)
            );
            return scope.commit();
        }
    }

    private static ModelProvenance provenance(
            CatalogCandidate candidate,
            CatalogModelKey key,
            Set<CatalogModelKey> dependencies,
            Map<String, DatasourceBindingIdentity> bindings,
            boolean complete
    ) {
        return new ModelProvenance(
                key.canonicalName(),
                key.kind(),
                candidate.sourceRevision(),
                dependencies,
                bindings,
                complete,
                List.of()
        );
    }

    private static DatasourceBindingIdentity binding(String key, String generation) {
        return new DatasourceBindingIdentity(
                key,
                "jdbc",
                new DatasourceBindingGeneration(generation)
        );
    }

    private static QueryModel queryModel(String name, String alias) {
        QueryModel model = mock(QueryModel.class);
        when(model.getName()).thenReturn(name);
        when(model.getShortAlias()).thenReturn(alias);
        return model;
    }

    private static TableModel tableModel(String name) {
        TableModel model = mock(TableModel.class);
        when(model.getName()).thenReturn(name);
        return model;
    }

    private static Object invokeRequired(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments
    ) {
        Method method;
        try {
            method = target.getClass().getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(
                    "Missing Batch 5 candidate method: " + methodName,
                    e
            );
        }
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Batch 5 candidate method is not public: " + methodName, e);
        } catch (InvocationTargetException e) {
            throw new AssertionError(
                    "Batch 5 candidate method failed: " + methodName,
                    e.getCause()
            );
        }
    }
}
