package com.foggyframework.dataset.model.lifecycle.concurrent;

import com.foggyframework.dataset.model.lifecycle.catalog.CatalogBuildView;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.catalog.StaleCatalogBuildException;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.model.lifecycle.port.DatasourceBindingResolver;
import com.foggyframework.dataset.model.lifecycle.port.StaleDatasourceBindingException;

import java.util.Collection;
import java.util.List;

/** Safe, credential-free diagnostics for one rejected lazy build attempt. */
public final class ModelBuildStaleDiagnostics {

    private ModelBuildStaleDiagnostics() {
    }

    public static String describe(
            String kind,
            String namespace,
            String model,
            int attempt,
            int maxAttempts,
            CatalogBuildView buildView,
            CatalogSnapshot observedBase,
            SourceRevision observedSource,
            Collection<DatasourceBindingIdentity> bindings,
            DatasourceBindingResolver bindingResolver,
            CatalogSnapshotStore snapshotStore,
            CatalogSnapshotStore.RefreshObservation refreshBefore,
            RuntimeException stale
    ) {
        CatalogSnapshot after = currentSnapshot(snapshotStore, namespace);
        SourceRevision afterSource = currentSourceRevision(snapshotStore, namespace);
        CatalogSnapshotStore.RefreshObservation refreshAfter =
                refreshObservation(snapshotStore, namespace, refreshBefore);
        String beforeGeneration = buildView == null
                ? generation(observedBase)
                : buildView.catalogGeneration()
                .map(value -> value.value()).orElse("<cold>");
        String beforeSource = buildView == null
                ? value(observedSource)
                : buildView.sourceRevision().value();
        List<String> bindingState = bindings == null
                ? List.of()
                : bindings.stream().sorted().map(identity -> binding(identity)
                        + ":currentness=" + currentness(bindingResolver, identity))
                .toList();
        boolean concurrentRefresh = refreshBefore != null && (refreshBefore.inProgress()
                || refreshAfter.inProgress()
                || refreshBefore.sequence() != refreshAfter.sequence());
        return "kind=" + kind
                + ", namespace='" + namespace + "'"
                + ", model=" + model
                + ", attempt=" + attempt + "/" + maxAttempts
                + ", staleReason=" + reason(stale)
                + ", beforeCatalogGeneration=" + beforeGeneration
                + ", afterCatalogGeneration=" + generation(after)
                + ", beforeSourceRevision=" + beforeSource
                + ", afterSourceRevision=" + value(afterSource)
                + ", bindings=" + bindingState
                + ", concurrentRefresh=" + concurrentRefresh;
    }

    private static String reason(RuntimeException stale) {
        if (stale instanceof StaleCatalogBuildException catalog) {
            return catalog.reason().name();
        }
        if (stale instanceof StaleDatasourceBindingException) {
            return "DATASOURCE_BINDING_CHANGED";
        }
        return stale.getClass().getSimpleName();
    }

    private static String generation(CatalogSnapshot snapshot) {
        return snapshot == null
                ? "<cold>"
                : snapshot.identity().generation().value();
    }

    private static String value(SourceRevision revision) {
        return revision == null ? "<unknown>" : revision.value();
    }

    private static CatalogSnapshot currentSnapshot(
            CatalogSnapshotStore store,
            String namespace
    ) {
        try {
            return store.current(namespace).orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static SourceRevision currentSourceRevision(
            CatalogSnapshotStore store,
            String namespace
    ) {
        try {
            return store.currentSourceRevision(namespace);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static CatalogSnapshotStore.RefreshObservation refreshObservation(
            CatalogSnapshotStore store,
            String namespace,
            CatalogSnapshotStore.RefreshObservation fallback
    ) {
        try {
            return store.refreshObservation(namespace);
        } catch (RuntimeException ignored) {
            return fallback == null
                    ? new CatalogSnapshotStore.RefreshObservation(0L, false)
                    : fallback;
        }
    }

    private static String binding(DatasourceBindingIdentity identity) {
        return identity.bindingKey() + "/" + identity.backendId()
                + "/" + identity.generation().value();
    }

    private static BindingCurrentness currentness(
            DatasourceBindingResolver resolver,
            DatasourceBindingIdentity identity
    ) {
        if (resolver == null) {
            return BindingCurrentness.UNKNOWN;
        }
        try {
            return resolver.currentness(identity);
        } catch (RuntimeException ignored) {
            return BindingCurrentness.UNKNOWN;
        }
    }
}
