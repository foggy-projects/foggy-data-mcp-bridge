package com.foggyframework.dataset.db.model.lifecycle.catalog;

import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogSnapshotTest {

    @Test
    void mustDefensivelyCopyEveryPublishedContainerAndResolveAliases() {
        QueryModel queryModel = mock(QueryModel.class);
        TableModel tableModel = mock(TableModel.class);
        when(queryModel.getName()).thenReturn("FactOrderQueryModel");
        when(queryModel.getShortAlias()).thenReturn("FO");
        when(tableModel.getName()).thenReturn("FactOrderTableModel");
        Map<String, QueryModel> queryModels = new LinkedHashMap<>();
        queryModels.put("FactOrderQueryModel", queryModel);
        Map<String, TableModel> tableModels = new LinkedHashMap<>();
        tableModels.put("FactOrderTableModel", tableModel);
        Set<String> discovered = new LinkedHashSet<>(Set.of("FactOrderQueryModel"));
        Map<String, String> canonicalToAlias = new LinkedHashMap<>(
                Map.of("FactOrderQueryModel", "FO"));
        Map<String, String> aliasToCanonical = new LinkedHashMap<>(
                Map.of("FO", "FactOrderQueryModel"));
        SourceRevision sourceRevision = new SourceRevision("source-a");
        Map<CatalogModelKey, ModelProvenance> provenance = new LinkedHashMap<>();
        provenance.put(CatalogModelKey.table("FactOrderTableModel"), new ModelProvenance(
                "FactOrderTableModel", ModelProvenance.ModelKind.TABLE, sourceRevision,
                Set.of(), Map.of(), false, java.util.List.of()));
        provenance.put(CatalogModelKey.query("FactOrderQueryModel"), new ModelProvenance(
                "FactOrderQueryModel", ModelProvenance.ModelKind.QUERY, sourceRevision,
                Set.of(CatalogModelKey.table("FactOrderTableModel")), Map.of(), false,
                java.util.List.of()));

        CatalogSnapshot snapshot = new CatalogSnapshot(
                new CatalogIdentity("tenant-a", new CatalogGeneration("catalog-a"),
                        sourceRevision),
                tableModels,
                queryModels,
                Map.of(),
                discovered,
                canonicalToAlias,
                aliasToCanonical,
                provenance
        );

        queryModels.clear();
        tableModels.clear();
        discovered.clear();
        canonicalToAlias.clear();
        aliasToCanonical.clear();

        assertEquals(Set.of("FactOrderQueryModel"), snapshot.discoveredQueryModelNames());
        assertSame(queryModel, snapshot.resolveQueryModel("FO").orElseThrow());
        assertSame(tableModel, snapshot.tableModels().get("FactOrderTableModel"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.queryModels().put("other", queryModel));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.discoveredQueryModelNames().add("other"));
    }

    @Test
    void mustRejectNonBijectiveAliasIndexes() {
        assertThrows(IllegalArgumentException.class, () -> new CatalogSnapshot(
                new CatalogIdentity("", new CatalogGeneration("catalog-b"),
                        new SourceRevision("source-b")),
                Map.of(), Map.of(), Map.of(), Set.of("AQueryModel"),
                Map.of("AQueryModel", "A"), Map.of("A", "DifferentQueryModel"), Map.of()
        ));
    }

    @Test
    void mustRejectHybridModelAndProvenanceViews() {
        QueryModel queryModel = mock(QueryModel.class);
        SourceRevision revision = new SourceRevision("source-c");
        CatalogIdentity identity = new CatalogIdentity(
                "", new CatalogGeneration("catalog-c"), revision);

        assertThrows(IllegalArgumentException.class, () -> new CatalogSnapshot(
                identity,
                Map.of(),
                Map.of("OrderQueryModel", queryModel),
                Map.of(),
                Set.of("OrderQueryModel"),
                Map.of("OrderQueryModel", "O"),
                Map.of("O", "OrderQueryModel"),
                Map.of()
        ));
    }

    @Test
    void resolutionMustRejectMismatchedDependencyBindingMapKey() {
        QueryModel queryModel = mock(QueryModel.class);
        when(queryModel.getName()).thenReturn("OrderQueryModel");
        DatasourceBindingIdentity bindingIdentity = new DatasourceBindingIdentity(
                "runtime:namespace-default:tenant-a",
                "runtime-registry:sales",
                new DatasourceBindingGeneration("binding-a")
        );

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new CatalogResolution<>(
                        "OrderQueryModel",
                        queryModel,
                        new CatalogIdentity(
                                "tenant-a",
                                new CatalogGeneration("catalog-a"),
                                new SourceRevision("source-a")
                        ),
                        Map.of("wrong-binding-key", bindingIdentity),
                        true
                )
        );

        assertEquals("dependency binding map key must equal identity.bindingKey",
                failure.getMessage());
    }

    @Test
    void mustRejectCyclicProvenanceEvenWhenAllDependencySlotsExist() {
        SourceRevision revision = new SourceRevision("source-cycle");
        TableModel first = mock(TableModel.class);
        TableModel second = mock(TableModel.class);
        when(first.getName()).thenReturn("FirstModel");
        when(second.getName()).thenReturn("SecondModel");
        CatalogModelKey firstKey = CatalogModelKey.table("FirstModel");
        CatalogModelKey secondKey = CatalogModelKey.table("SecondModel");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new CatalogSnapshot(
                        new CatalogIdentity("", new CatalogGeneration("catalog-cycle"), revision),
                        Map.of("FirstModel", first, "SecondModel", second),
                        Map.of(),
                        Map.of(),
                        Set.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(
                                firstKey, new ModelProvenance(
                                        "FirstModel", ModelProvenance.ModelKind.TABLE, revision,
                                        Set.of(secondKey), Map.of(), false, java.util.List.of()),
                                secondKey, new ModelProvenance(
                                        "SecondModel", ModelProvenance.ModelKind.TABLE, revision,
                                        Set.of(firstKey), Map.of(), false, java.util.List.of())
                        )
                )
        );

        assertTrue(failure.getMessage().startsWith("MODEL_BUILD_DEPENDENCY_CYCLE:"));
    }
}
