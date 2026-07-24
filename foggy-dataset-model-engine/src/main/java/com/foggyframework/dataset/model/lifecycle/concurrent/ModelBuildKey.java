package com.foggyframework.dataset.model.lifecycle.concurrent;

import com.foggyframework.dataset.model.lifecycle.catalog.CatalogModelKey;
import com.foggyframework.dataset.model.lifecycle.catalog.ModelProvenance;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Complete identity of one lazy model-build attempt.
 *
 * <p>A shareable key contains only lifecycle identities that can be compared safely. When a
 * datasource dependency cannot provide a stable binding identity, callers must create an
 * {@linkplain #isolatedUntracked isolated} key. The single-flight coordinator deliberately
 * bypasses its shared map for such a key; the private nonce is an additional guard against an
 * untracked attempt accidentally being treated as reusable identity elsewhere.</p>
 */
public final class ModelBuildKey {

    private final CatalogModelKey modelKey;
    private final String namespace;
    private final CatalogGeneration baseCatalogGeneration;
    private final SourceRevision sourceRevision;
    private final List<DatasourceBindingIdentity> datasourceBindings;
    private final boolean bindingIdentityComplete;
    private final UUID untrackedIsolationId;

    private ModelBuildKey(
            CatalogModelKey modelKey,
            String namespace,
            CatalogGeneration baseCatalogGeneration,
            SourceRevision sourceRevision,
            Collection<DatasourceBindingIdentity> datasourceBindings,
            boolean bindingIdentityComplete
    ) {
        this.modelKey = Objects.requireNonNull(modelKey, "modelKey");
        this.namespace = CatalogIdentity.canonicalNamespace(namespace);
        this.baseCatalogGeneration = baseCatalogGeneration;
        this.sourceRevision = Objects.requireNonNull(sourceRevision, "sourceRevision");
        this.datasourceBindings = canonicalBindings(datasourceBindings);
        this.bindingIdentityComplete = bindingIdentityComplete;
        this.untrackedIsolationId = bindingIdentityComplete ? null : UUID.randomUUID();
    }

    public static ModelBuildKey tracked(
            CatalogModelKey modelKey,
            String namespace,
            CatalogGeneration baseCatalogGeneration,
            SourceRevision sourceRevision,
            Collection<DatasourceBindingIdentity> datasourceBindings
    ) {
        return new ModelBuildKey(modelKey, namespace, baseCatalogGeneration, sourceRevision,
                datasourceBindings, true);
    }

    public static ModelBuildKey tracked(
            ModelProvenance.ModelKind kind,
            String namespace,
            String canonicalModelName,
            CatalogGeneration baseCatalogGeneration,
            SourceRevision sourceRevision,
            Collection<DatasourceBindingIdentity> datasourceBindings
    ) {
        return tracked(new CatalogModelKey(kind, canonicalModelName), namespace,
                baseCatalogGeneration, sourceRevision, datasourceBindings);
    }

    public static ModelBuildKey isolatedUntracked(
            CatalogModelKey modelKey,
            String namespace,
            CatalogGeneration baseCatalogGeneration,
            SourceRevision sourceRevision,
            Collection<DatasourceBindingIdentity> knownDatasourceBindings
    ) {
        return new ModelBuildKey(modelKey, namespace, baseCatalogGeneration, sourceRevision,
                knownDatasourceBindings, false);
    }

    /** Selects fail-closed isolation when dependency binding identity is incomplete. */
    public static ModelBuildKey of(
            CatalogModelKey modelKey,
            String namespace,
            CatalogGeneration baseCatalogGeneration,
            SourceRevision sourceRevision,
            Collection<DatasourceBindingIdentity> knownDatasourceBindings,
            boolean bindingIdentityComplete
    ) {
        return bindingIdentityComplete
                ? tracked(modelKey, namespace, baseCatalogGeneration, sourceRevision,
                        knownDatasourceBindings)
                : isolatedUntracked(modelKey, namespace, baseCatalogGeneration, sourceRevision,
                        knownDatasourceBindings);
    }

    public CatalogModelKey modelKey() {
        return modelKey;
    }

    public ModelProvenance.ModelKind kind() {
        return modelKey.kind();
    }

    public String canonicalModelName() {
        return modelKey.canonicalName();
    }

    public String namespace() {
        return namespace;
    }

    /** Empty means a cold catalog capture; reading this value never allocates a generation. */
    public Optional<CatalogGeneration> baseCatalogGeneration() {
        return Optional.ofNullable(baseCatalogGeneration);
    }

    public SourceRevision sourceRevision() {
        return sourceRevision;
    }

    public List<DatasourceBindingIdentity> datasourceBindings() {
        return datasourceBindings;
    }

    public boolean bindingIdentityComplete() {
        return bindingIdentityComplete;
    }

    public boolean isShareable() {
        return bindingIdentityComplete;
    }

    boolean sameLogicalModel(ModelBuildKey other) {
        return other != null
                && modelKey.equals(other.modelKey)
                && namespace.equals(other.namespace);
    }

    String diagnosticLabel() {
        String scopedName = namespace.isEmpty()
                ? canonicalModelName()
                : namespace + ":" + canonicalModelName();
        return kind() + " " + scopedName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModelBuildKey that)) {
            return false;
        }
        return bindingIdentityComplete == that.bindingIdentityComplete
                && modelKey.equals(that.modelKey)
                && namespace.equals(that.namespace)
                && Objects.equals(baseCatalogGeneration, that.baseCatalogGeneration)
                && sourceRevision.equals(that.sourceRevision)
                && datasourceBindings.equals(that.datasourceBindings)
                && Objects.equals(untrackedIsolationId, that.untrackedIsolationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelKey, namespace, baseCatalogGeneration, sourceRevision,
                datasourceBindings, bindingIdentityComplete, untrackedIsolationId);
    }

    @Override
    public String toString() {
        return "ModelBuildKey[" + diagnosticLabel()
                + ", baseGeneration="
                + (baseCatalogGeneration == null ? "<cold>" : baseCatalogGeneration.value())
                + ", sourceRevision=" + sourceRevision.value()
                + ", bindingCount=" + datasourceBindings.size()
                + ", shareable=" + bindingIdentityComplete + "]";
    }

    private static List<DatasourceBindingIdentity> canonicalBindings(
            Collection<DatasourceBindingIdentity> datasourceBindings
    ) {
        Objects.requireNonNull(datasourceBindings, "datasourceBindings");
        Map<String, DatasourceBindingIdentity> byBindingKey = new HashMap<>();
        for (DatasourceBindingIdentity identity : datasourceBindings) {
            Objects.requireNonNull(identity, "datasource binding identity");
            DatasourceBindingIdentity previous = byBindingKey.putIfAbsent(
                    identity.bindingKey(), identity);
            if (previous != null && !previous.equals(identity)) {
                throw new IllegalArgumentException(
                        "conflicting datasource binding identities for key "
                                + identity.bindingKey());
            }
        }
        ArrayList<DatasourceBindingIdentity> sorted = new ArrayList<>(byBindingKey.values());
        sorted.sort(DatasourceBindingIdentity::compareTo);
        return List.copyOf(sorted);
    }
}
