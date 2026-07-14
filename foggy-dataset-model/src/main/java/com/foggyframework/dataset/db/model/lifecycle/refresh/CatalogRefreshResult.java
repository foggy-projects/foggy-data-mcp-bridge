package com.foggyframework.dataset.db.model.lifecycle.refresh;

import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogAdmissionState;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.SourceRevision;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable successful outcome of one atomic catalog refresh. */
public record CatalogRefreshResult(
        String namespace,
        CatalogRefreshScope scope,
        CatalogIdentity beforeIdentity,
        CatalogIdentity afterIdentity,
        SourceRevision sourceRevision,
        Set<CatalogModelKey> refreshedModels,
        Set<CatalogModelKey> preservedModels,
        List<DatasourceBindingIdentity> affectedBindings,
        long durationMs,
        CatalogAdmissionState catalogState,
        List<CatalogRefreshDiagnostic> diagnostics
) {

    public CatalogRefreshResult {
        namespace = CatalogIdentity.canonicalNamespace(namespace);
        Objects.requireNonNull(scope, "scope");
        if (beforeIdentity != null
                && !namespace.equals(beforeIdentity.namespace())) {
            throw new IllegalArgumentException(
                    "before catalog identity namespace mismatch");
        }
        Objects.requireNonNull(afterIdentity, "afterIdentity");
        if (!namespace.equals(afterIdentity.namespace())) {
            throw new IllegalArgumentException(
                    "after catalog identity namespace mismatch");
        }
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        if (!sourceRevision.equals(afterIdentity.sourceRevision())) {
            throw new IllegalArgumentException(
                    "result source revision does not match after identity");
        }
        refreshedModels = immutableModelKeys(refreshedModels);
        preservedModels = immutableModelKeys(preservedModels);
        TreeSet<DatasourceBindingIdentity> bindings = new TreeSet<>(
                affectedBindings == null ? List.of() : affectedBindings);
        affectedBindings = List.copyOf(bindings);
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        Objects.requireNonNull(catalogState, "catalogState");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public int refreshedCount() {
        return refreshedModels.size();
    }

    public int preservedCount() {
        return preservedModels.size();
    }

    private static Set<CatalogModelKey> immutableModelKeys(
            Set<CatalogModelKey> source
    ) {
        TreeSet<CatalogModelKey> sorted = new TreeSet<>(
                source == null ? Set.of() : source);
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }
}
