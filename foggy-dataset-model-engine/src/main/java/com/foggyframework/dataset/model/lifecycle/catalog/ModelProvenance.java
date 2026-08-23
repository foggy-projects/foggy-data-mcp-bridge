package com.foggyframework.dataset.model.lifecycle.catalog;

import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.fsscript.parser.spi.FsscriptSourceContentRevision;

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
        List<String> diagnostics,
        ModelSource source
) {
    public ModelProvenance(
            String canonicalName,
            ModelKind kind,
            SourceRevision sourceRevision,
            Set<CatalogModelKey> modelDependencies,
            Map<String, DatasourceBindingIdentity> datasourceBindings,
            boolean bindingIdentityComplete,
            List<String> diagnostics
    ) {
        this(canonicalName, kind, sourceRevision, modelDependencies,
                datasourceBindings, bindingIdentityComplete, diagnostics, null);
    }

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

    /** Bundle/resource ownership evidence for diagnostics and governance. */
    public record ModelSource(
            String bundleName,
            String namespace,
            String resourceIdentity,
            String sourceClosureRevision
    ) {
        public ModelSource(
                String bundleName,
                String namespace,
                String resourceIdentity) {
            this(bundleName, namespace, resourceIdentity, null);
        }

        public ModelSource {
            if (bundleName == null || bundleName.isBlank()) {
                throw new IllegalArgumentException("bundleName must not be blank");
            }
            bundleName = bundleName.trim();
            namespace = namespace == null || namespace.isBlank()
                    ? ""
                    : namespace.trim();
            if (resourceIdentity == null || resourceIdentity.isBlank()) {
                throw new IllegalArgumentException(
                        "resourceIdentity must not be blank");
            }
            resourceIdentity = resourceIdentity.trim();
            if (sourceClosureRevision != null
                    && !FsscriptSourceContentRevision.isCanonical(
                    sourceClosureRevision)) {
                throw new IllegalArgumentException(
                        "sourceClosureRevision must be a canonical SHA-256 revision");
            }
        }
    }
}
