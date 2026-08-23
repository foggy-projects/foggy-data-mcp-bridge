package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;
import com.foggyframework.analytics.definition.api.AnalyticsDashboardDefinition;
import com.foggyframework.analytics.definition.api.AnalyticsDashboardWidget;
import com.foggyframework.analytics.definition.api.AnalyticsQueryRef;
import com.foggyframework.analytics.definition.api.AnalyticsQuerySpec;
import com.foggyframework.analytics.definition.api.AnalyticsReportDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Parses a validated bundle snapshot into a typed, cross-reference-safe index. */
public final class AnalyticsBundleIndexer {

    private final AnalyticsDefinitionJsonCodec definitionCodec;
    private final AnalyticsArtifactPathPolicy pathPolicy;

    public AnalyticsBundleIndexer() {
        this(new AnalyticsDefinitionJsonCodec(), new AnalyticsArtifactPathPolicy());
    }

    AnalyticsBundleIndexer(
            AnalyticsDefinitionJsonCodec definitionCodec,
            AnalyticsArtifactPathPolicy pathPolicy) {
        this.definitionCodec = Objects.requireNonNull(definitionCodec, "definitionCodec");
        this.pathPolicy = Objects.requireNonNull(pathPolicy, "pathPolicy");
    }

    public AnalyticsBundleIndex index(AnalyticsDefinitionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        ParsedDefinitions parsed = parse(
                snapshot.resolvedBundle().manifest(),
                snapshot.definitionPaths(),
                snapshot::readDefinition);
        return new AnalyticsBundleIndex(
                snapshot.resolvedBundle(),
                parsed.queries(),
                parsed.reports(),
                parsed.dashboards());
    }

    void validate(AnalyticsBundleManifest manifest, Map<String, byte[]> artifacts) {
        Objects.requireNonNull(artifacts, "artifacts");
        parse(
                manifest,
                artifacts.keySet(),
                path -> Objects.requireNonNull(
                        artifacts.get(path),
                        "artifact content").clone());
    }

    void validateArtifact(String relativePath, byte[] content) {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(content, "content");
        if (relativePath.endsWith(".query.json")) {
            definitionCodec.readQuery(content);
        } else if (relativePath.endsWith(".report.json")) {
            definitionCodec.readReport(content);
        } else if (relativePath.endsWith(".dashboard.json")) {
            definitionCodec.readDashboard(content);
        }
    }

    private ParsedDefinitions parse(
            AnalyticsBundleManifest manifest,
            Set<String> artifactPaths,
            Function<String, byte[]> artifactReader) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(artifactPaths, "artifactPaths");
        Objects.requireNonNull(artifactReader, "artifactReader");
        Map<AnalyticsQueryRef, AnalyticsQuerySpec> queries = new LinkedHashMap<>();
        Map<AnalyticsArtifactRef, AnalyticsReportDefinition> reports = new LinkedHashMap<>();
        Map<AnalyticsArtifactRef, AnalyticsDashboardDefinition> dashboards = new LinkedHashMap<>();

        for (String relativePath : artifactPaths.stream().sorted().toList()) {
            if (!pathPolicy.isDefinitionArtifact(relativePath)) {
                throw invalid("Unsupported Analytics definition file: " + relativePath);
            }
            byte[] content = artifactReader.apply(relativePath);
            if (relativePath.endsWith(".query.json")) {
                AnalyticsQuerySpec query = definitionCodec.readQuery(content);
                rejectDuplicate("queryRef", queries.put(query.queryRef(), query), query.queryRef());
            } else if (relativePath.endsWith(".report.json")) {
                AnalyticsReportDefinition report = definitionCodec.readReport(content);
                rejectDuplicate(
                        "report artifactRef",
                        reports.put(report.artifactRef(), report),
                        report.artifactRef());
            } else if (relativePath.endsWith(".dashboard.json")) {
                AnalyticsDashboardDefinition dashboard = definitionCodec.readDashboard(content);
                rejectDuplicate(
                        "dashboard artifactRef",
                        dashboards.put(dashboard.artifactRef(), dashboard),
                        dashboard.artifactRef());
            }
        }

        validateReferences(manifest, queries, reports, dashboards);
        return new ParsedDefinitions(queries, reports, dashboards);
    }

    private static void validateReferences(
            AnalyticsBundleManifest manifest,
            Map<AnalyticsQueryRef, AnalyticsQuerySpec> queries,
            Map<AnalyticsArtifactRef, AnalyticsReportDefinition> reports,
            Map<AnalyticsArtifactRef, AnalyticsDashboardDefinition> dashboards) {
        for (AnalyticsQuerySpec query : queries.values()) {
            if (!query.namespaceRef().equals(manifest.namespaceRef())) {
                throw invalid("Query namespaceRef must match the bundle manifest: "
                        + query.queryRef().value());
            }
            boolean dependencyExists = manifest.modelDependencies().stream()
                    .filter(dependency -> "qm".equals(dependency.modelKind()))
                    .anyMatch(dependency -> dependency.namespace().equals(query.namespaceRef())
                            && dependency.modelName().equals(query.modelName()));
            if (!dependencyExists) {
                throw invalid("Query has no pinned QM model dependency: "
                        + query.queryRef().value());
            }
        }
        for (AnalyticsReportDefinition report : reports.values()) {
            if (!queries.containsKey(report.queryRef())) {
                throw invalid("Report references a missing queryRef: "
                        + report.queryRef().value());
            }
        }
        for (AnalyticsDashboardDefinition dashboard : dashboards.values()) {
            for (AnalyticsDashboardWidget widget : dashboard.widgets()) {
                if (widget.reportRef() != null && !reports.containsKey(widget.reportRef())) {
                    throw invalid("Dashboard widget references a missing reportRef: "
                            + widget.reportRef().value());
                }
                if (widget.queryRef() != null && !queries.containsKey(widget.queryRef())) {
                    throw invalid("Dashboard widget references a missing queryRef: "
                            + widget.queryRef().value());
                }
            }
        }
    }

    private static void rejectDuplicate(String field, Object previous, Object identity) {
        if (previous != null) {
            throw invalid("Duplicate " + field + ": " + identity);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private record ParsedDefinitions(
            Map<AnalyticsQueryRef, AnalyticsQuerySpec> queries,
            Map<AnalyticsArtifactRef, AnalyticsReportDefinition> reports,
            Map<AnalyticsArtifactRef, AnalyticsDashboardDefinition> dashboards) {
    }
}
