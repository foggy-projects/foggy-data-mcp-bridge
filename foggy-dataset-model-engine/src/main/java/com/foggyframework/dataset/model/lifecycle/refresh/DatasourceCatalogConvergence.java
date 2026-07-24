package com.foggyframework.dataset.model.lifecycle.refresh;

import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshot;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Bridges a committed datasource mutation to catalog admission and refresh.
 *
 * <p>The caller must invoke {@link #blockAffectedNamespaces(Collection, Collection)} while it
 * still owns the datasource mutation admission lock, then invoke {@link #refresh(Collection)}
 * after releasing that lock. This ordering prevents an old catalog from admitting work after
 * its binding generation has been revoked without holding the datasource lock during model
 * construction.</p>
 */
public final class DatasourceCatalogConvergence {

    private static final String BLOCKED_DIAGNOSTIC =
            "DATASOURCE_BINDING_NOT_CURRENT: CHANGED";

    private final CatalogSnapshotStore snapshotStore;
    private final CatalogRefreshCoordinator refreshCoordinator;

    public DatasourceCatalogConvergence(
            CatalogSnapshotStore snapshotStore,
            CatalogRefreshCoordinator refreshCoordinator
    ) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.refreshCoordinator = Objects.requireNonNull(
                refreshCoordinator, "refreshCoordinator");
    }

    /**
     * Computes and blocks every directly named namespace or snapshot whose provenance consumes
     * one of the changed logical binding keys. The retained snapshot is intentionally read through
     * {@code current}, not {@code readCurrent}, because an already blocked catalog still needs to
     * participate in a later convergence attempt.
     */
    public Set<String> blockAffectedNamespaces(
            Collection<String> directNamespaces,
            Collection<String> changedBindingKeys
    ) {
        TreeSet<String> affected = new TreeSet<>();
        if (directNamespaces != null) {
            directNamespaces.stream()
                    .filter(Objects::nonNull)
                    .map(CatalogIdentity::canonicalNamespace)
                    .forEach(affected::add);
        }

        TreeSet<String> bindingKeys = canonicalBindingKeys(changedBindingKeys);
        if (!bindingKeys.isEmpty()) {
            for (String namespace : snapshotStore.knownNamespaces()) {
                CatalogSnapshot snapshot = snapshotStore.current(namespace).orElse(null);
                if (snapshot != null && consumesAny(snapshot, bindingKeys)) {
                    affected.add(namespace);
                }
            }
        }

        affected.forEach(namespace -> snapshotStore.markStaleAdmissionBlocked(
                namespace, BLOCKED_DIAGNOSTIC));
        return immutableSorted(affected);
    }

    /**
     * Runs synchronous namespace refreshes outside the datasource mutation lock. All affected
     * namespaces are attempted even if an earlier namespace fails; failed namespaces remain
     * admission-blocked by the coordinator contract.
     */
    public void refresh(Collection<String> namespaces) {
        RuntimeException firstFailure = null;
        for (String namespace : immutableSorted(namespaces)) {
            try {
                refreshCoordinator.refresh(CatalogRefreshRequest.namespace(
                        namespace, CatalogRefreshTrigger.DATASOURCE));
            } catch (RuntimeException failure) {
                if (firstFailure == null) {
                    firstFailure = failure;
                } else {
                    firstFailure.addSuppressed(failure);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private boolean consumesAny(
            CatalogSnapshot snapshot,
            Set<String> changedBindingKeys
    ) {
        for (ModelProvenance provenance : snapshot.provenance().values()) {
            if (provenance.datasourceBindings().keySet().stream()
                    .anyMatch(changedBindingKeys::contains)) {
                return true;
            }
        }
        return false;
    }

    private static TreeSet<String> canonicalBindingKeys(Collection<String> source) {
        TreeSet<String> keys = new TreeSet<>();
        if (source != null) {
            source.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(key -> !key.isEmpty())
                    .forEach(keys::add);
        }
        return keys;
    }

    private static Set<String> immutableSorted(Collection<String> source) {
        TreeSet<String> sorted = new TreeSet<>();
        if (source != null) {
            source.stream()
                    .filter(Objects::nonNull)
                    .map(CatalogIdentity::canonicalNamespace)
                    .forEach(sorted::add);
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }
}
