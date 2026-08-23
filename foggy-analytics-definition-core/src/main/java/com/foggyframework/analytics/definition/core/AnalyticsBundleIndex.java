package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.api.AnalyticsDashboardDefinition;
import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsQueryRef;
import com.foggyframework.analytics.definition.api.AnalyticsQuerySpec;
import com.foggyframework.analytics.definition.api.AnalyticsReportDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable typed lookup index for one exact Analytics Bundle revision. */
public final class AnalyticsBundleIndex {

    private final ResolvedAnalyticsBundle resolvedBundle;
    private final Map<AnalyticsQueryRef, AnalyticsQuerySpec> queries;
    private final Map<AnalyticsArtifactRef, AnalyticsReportDefinition> reports;
    private final Map<AnalyticsArtifactRef, AnalyticsDashboardDefinition> dashboards;

    public AnalyticsBundleIndex(
            ResolvedAnalyticsBundle resolvedBundle,
            Map<AnalyticsQueryRef, AnalyticsQuerySpec> queries,
            Map<AnalyticsArtifactRef, AnalyticsReportDefinition> reports,
            Map<AnalyticsArtifactRef, AnalyticsDashboardDefinition> dashboards) {
        this.resolvedBundle = Objects.requireNonNull(resolvedBundle, "resolvedBundle");
        this.queries = immutableDefinitions(
                "query",
                queries,
                AnalyticsQuerySpec::queryRef);
        this.reports = immutableDefinitions(
                "report",
                reports,
                AnalyticsReportDefinition::artifactRef);
        this.dashboards = immutableDefinitions(
                "dashboard",
                dashboards,
                AnalyticsDashboardDefinition::artifactRef);
    }

    public ResolvedAnalyticsBundle resolvedBundle() {
        return resolvedBundle;
    }

    public AnalyticsBundleManifest manifest() {
        return resolvedBundle.manifest();
    }

    public AnalyticsBundleRevision bundleRevision() {
        return resolvedBundle.bundleRevision();
    }

    public Map<AnalyticsQueryRef, AnalyticsQuerySpec> queries() {
        return queries;
    }

    public Map<AnalyticsArtifactRef, AnalyticsReportDefinition> reports() {
        return reports;
    }

    public Map<AnalyticsArtifactRef, AnalyticsDashboardDefinition> dashboards() {
        return dashboards;
    }

    public Optional<AnalyticsQuerySpec> query(AnalyticsQueryRef queryRef) {
        return Optional.ofNullable(queries.get(Objects.requireNonNull(queryRef, "queryRef")));
    }

    public Optional<AnalyticsReportDefinition> report(AnalyticsArtifactRef reportRef) {
        return Optional.ofNullable(reports.get(Objects.requireNonNull(reportRef, "reportRef")));
    }

    public Optional<AnalyticsDashboardDefinition> dashboard(AnalyticsArtifactRef dashboardRef) {
        return Optional.ofNullable(
                dashboards.get(Objects.requireNonNull(dashboardRef, "dashboardRef")));
    }

    public Optional<AnalyticsModelDependency> modelDependency(AnalyticsQuerySpec querySpec) {
        Objects.requireNonNull(querySpec, "querySpec");
        return manifest().modelDependencies().stream()
                .filter(dependency -> "qm".equals(dependency.modelKind()))
                .filter(dependency -> dependency.namespace().equals(querySpec.namespaceRef()))
                .filter(dependency -> dependency.modelName().equals(querySpec.modelName()))
                .findFirst();
    }

    private static <K, V> Map<K, V> immutableDefinitions(
            String kind,
            Map<K, V> definitions,
            java.util.function.Function<V, K> identity) {
        Objects.requireNonNull(definitions, kind + " definitions");
        Map<K, V> copy = new LinkedHashMap<>();
        definitions.forEach((key, value) -> {
            Objects.requireNonNull(key, kind + " key");
            Objects.requireNonNull(value, kind + " definition");
            if (!key.equals(identity.apply(value))) {
                throw new IllegalArgumentException(kind + " map key must match its definition");
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}
