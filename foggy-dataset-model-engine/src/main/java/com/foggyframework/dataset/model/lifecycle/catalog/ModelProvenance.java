package com.foggyframework.dataset.model.lifecycle.catalog;

import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable source and datasource dependency evidence for one canonical model. */
public record ModelProvenance(
        String canonicalName,
        ModelKind kind,
        SourceRevision sourceRevision,
        Set<CatalogModelKey> modelDependencies,
        Map<String, DatasourceBindingIdentity> datasourceBindings,
        boolean bindingIdentityComplete,
        List<String> diagnostics
) {
    public ModelProvenance {
        if (canonicalName == null || canonicalName.isBlank()) {
            throw new IllegalArgumentException("canonicalName must not be blank");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        modelDependencies = modelDependencies == null ? Set.of() : Set.copyOf(modelDependencies);
        datasourceBindings = datasourceBindings == null ? Map.of() : Map.copyOf(datasourceBindings);
        datasourceBindings.forEach((bindingKey, identity) -> {
            if (identity == null || !bindingKey.equals(identity.bindingKey())) {
                throw new IllegalArgumentException(
                        "datasource binding map key must equal identity.bindingKey");
            }
        });
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public CatalogModelKey key() {
        return new CatalogModelKey(kind, canonicalName);
    }

    public enum ModelKind {
        TABLE,
        QUERY,
        SYNTHETIC_QUERY
    }
}
