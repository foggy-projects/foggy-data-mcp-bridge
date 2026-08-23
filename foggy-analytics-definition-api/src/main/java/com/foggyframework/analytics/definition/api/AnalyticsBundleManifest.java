package com.foggyframework.analytics.definition.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stable technical manifest for an Analytics Bundle.
 *
 * <p>Owner, tenant, ACL and product publication metadata intentionally do not
 * belong to this contract.</p>
 */
public record AnalyticsBundleManifest(
        String kind,
        AnalyticsSchemaVersion schemaVersion,
        AnalyticsBundleRef bundleRef,
        AnalyticsBundleRevision bundleRevision,
        AnalyticsNamespaceRef namespaceRef,
        List<AnalyticsModelDependency> modelDependencies) {

    public static final String ANALYTICS_KIND = "analytics";

    public AnalyticsBundleManifest {
        if (!ANALYTICS_KIND.equals(kind)) {
            throw new IllegalArgumentException("kind must be 'analytics'");
        }
        schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        bundleRef = Objects.requireNonNull(bundleRef, "bundleRef");
        bundleRevision = Objects.requireNonNull(bundleRevision, "bundleRevision");
        namespaceRef = Objects.requireNonNull(namespaceRef, "namespaceRef");
        modelDependencies = List.copyOf(
                Objects.requireNonNull(modelDependencies, "modelDependencies"));
        Set<String> dependencyIdentities = new HashSet<>();
        for (AnalyticsModelDependency dependency : modelDependencies) {
            String identity = dependency.namespace().value()
                    + '\0' + dependency.modelKind()
                    + '\0' + dependency.modelName();
            if (!dependencyIdentities.add(identity)) {
                throw new IllegalArgumentException(
                        "modelDependencies must not pin the same logical model twice");
            }
        }
    }
}
