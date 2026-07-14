package com.foggyframework.dataset.db.model.lifecycle.concurrent;

import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.SourceRevision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelBuildKeyTest {

    @Test
    void trackedKeyMustCanonicalizeSortDeduplicateAndDefensivelyCopy() {
        DatasourceBindingIdentity bindingB = binding("binding-b", "backend-b", "generation-2");
        DatasourceBindingIdentity bindingA = binding("binding-a", "backend-a", "generation-1");
        ArrayList<DatasourceBindingIdentity> mutable = new ArrayList<>(
                List.of(bindingB, bindingA, bindingA));

        ModelBuildKey key = ModelBuildKey.tracked(
                CatalogModelKey.table("  FactOrderModel  "),
                "  tenant-a  ",
                null,
                new SourceRevision("source-1"),
                mutable);
        mutable.clear();

        assertEquals("tenant-a", key.namespace());
        assertEquals("FactOrderModel", key.canonicalModelName());
        assertTrue(key.baseCatalogGeneration().isEmpty());
        assertEquals(List.of(bindingA, bindingB), key.datasourceBindings());
        assertThrows(UnsupportedOperationException.class,
                () -> key.datasourceBindings().add(binding("binding-c", "backend-c", "generation-3")));
        assertTrue(key.bindingIdentityComplete());
        assertTrue(key.isShareable());

        ModelBuildKey same = ModelBuildKey.tracked(
                CatalogModelKey.table("FactOrderModel"),
                "tenant-a",
                null,
                new SourceRevision("source-1"),
                List.of(bindingA, bindingB));
        assertEquals(key, same);
        assertEquals(key.hashCode(), same.hashCode());
    }

    @Test
    void conflictingGenerationsForOneBindingKeyMustFailClosed() {
        DatasourceBindingIdentity first = binding("orders", "backend-a", "generation-1");
        DatasourceBindingIdentity changed = binding("orders", "backend-a", "generation-2");

        assertThrows(IllegalArgumentException.class, () -> ModelBuildKey.tracked(
                CatalogModelKey.table("FactOrderModel"),
                "tenant-a",
                new CatalogGeneration("catalog-1"),
                new SourceRevision("source-1"),
                List.of(first, changed)));
    }

    @Test
    void everyLifecycleComponentMustParticipateInTrackedEquality() {
        DatasourceBindingIdentity binding = binding("orders", "backend-a", "generation-1");
        ModelBuildKey baseline = tracked("tenant-a", "FactOrderModel", "catalog-1",
                "source-1", binding);

        assertNotEquals(baseline, ModelBuildKey.tracked(
                CatalogModelKey.query("FactOrderModel"), "tenant-a",
                new CatalogGeneration("catalog-1"), new SourceRevision("source-1"), List.of(binding)));
        assertNotEquals(baseline, tracked("tenant-b", "FactOrderModel", "catalog-1",
                "source-1", binding));
        assertNotEquals(baseline, tracked("tenant-a", "FactPaymentModel", "catalog-1",
                "source-1", binding));
        assertNotEquals(baseline, tracked("tenant-a", "FactOrderModel", "catalog-2",
                "source-1", binding));
        assertNotEquals(baseline, tracked("tenant-a", "FactOrderModel", "catalog-1",
                "source-2", binding));
        assertNotEquals(baseline, tracked("tenant-a", "FactOrderModel", "catalog-1",
                "source-1", binding("orders", "backend-a", "generation-2")));
    }

    @Test
    void incompleteBindingIdentityMustProduceNonReusableIsolationKeys() {
        ModelBuildKey first = ModelBuildKey.of(
                CatalogModelKey.table("FactOrderModel"),
                "tenant-a",
                new CatalogGeneration("catalog-1"),
                new SourceRevision("source-1"),
                List.of(),
                false);
        ModelBuildKey second = ModelBuildKey.isolatedUntracked(
                CatalogModelKey.table("FactOrderModel"),
                "tenant-a",
                new CatalogGeneration("catalog-1"),
                new SourceRevision("source-1"),
                List.of());

        assertFalse(first.bindingIdentityComplete());
        assertFalse(first.isShareable());
        assertNotEquals(first, second);
        assertTrue(first.sameLogicalModel(second));
    }

    private ModelBuildKey tracked(
            String namespace,
            String model,
            String catalogGeneration,
            String sourceRevision,
            DatasourceBindingIdentity binding
    ) {
        return ModelBuildKey.tracked(
                CatalogModelKey.table(model),
                namespace,
                new CatalogGeneration(catalogGeneration),
                new SourceRevision(sourceRevision),
                List.of(binding));
    }

    private DatasourceBindingIdentity binding(String key, String backend, String generation) {
        return new DatasourceBindingIdentity(
                key, backend, new DatasourceBindingGeneration(generation));
    }
}
