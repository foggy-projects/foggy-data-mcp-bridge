package com.foggyframework.analytics.definition.core;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.definition.api.AnalyticsArtifactKind;
import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;
import com.foggyframework.analytics.definition.api.AnalyticsDashboardDefinition;
import com.foggyframework.analytics.definition.api.AnalyticsDashboardWidget;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.definition.api.AnalyticsQueryRef;
import com.foggyframework.analytics.definition.api.AnalyticsQuerySpec;
import com.foggyframework.analytics.definition.api.AnalyticsReportDefinition;
import com.foggyframework.analytics.definition.api.AnalyticsVisualIntent;
import com.foggyframework.analytics.definition.api.AnalyticsVisualKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict v1 bundle shape and cross-reference validation. */
public final class AnalyticsBundleStructureValidator {

    private static final Set<String> QUERY_FIELDS = Set.of(
            "queryRef", "namespaceRef", "modelName", "columns", "groupBy");
    private static final Set<String> REPORT_FIELDS = Set.of(
            "artifactRef", "queryRef", "visualIntent");
    private static final Set<String> DASHBOARD_FIELDS = Set.of(
            "artifactRef", "widgets");
    private static final Set<String> WIDGET_FIELDS = Set.of(
            "widgetRef", "reportRef", "queryRef", "visualIntent");
    private static final Set<String> VISUAL_FIELDS = Set.of("kind", "hints");

    private final ObjectMapper objectMapper;
    private final AnalyticsArtifactPathPolicy pathPolicy;

    public AnalyticsBundleStructureValidator() {
        this(strictObjectMapper(), new AnalyticsArtifactPathPolicy());
    }

    AnalyticsBundleStructureValidator(
            ObjectMapper objectMapper,
            AnalyticsArtifactPathPolicy pathPolicy) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.pathPolicy = Objects.requireNonNull(pathPolicy, "pathPolicy");
    }

    public void validate(Path bundleRoot, AnalyticsBundleManifest manifest) throws IOException {
        Objects.requireNonNull(bundleRoot, "bundleRoot");
        Objects.requireNonNull(manifest, "manifest");
        Map<AnalyticsQueryRef, AnalyticsQuerySpec> queries = new LinkedHashMap<>();
        Map<AnalyticsArtifactRef, AnalyticsReportDefinition> reports = new LinkedHashMap<>();
        List<AnalyticsDashboardDefinition> dashboards = new ArrayList<>();

        try (var paths = Files.walk(bundleRoot)) {
            for (Path path : paths.filter(candidate -> !candidate.equals(bundleRoot)).toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw invalid("Analytics Bundle must not contain symbolic links: " + path);
                }
                String relative = portable(bundleRoot, path);
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (!pathPolicy.isSupportedDirectory(relative)) {
                        throw invalid("Unsupported Analytics Bundle directory: " + relative);
                    }
                    continue;
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw invalid("Unsupported Analytics Bundle entry: " + relative);
                }
                if (AnalyticsBundleRevisionCalculator.MANIFEST_FILE.equals(relative)) {
                    continue;
                }
                if (!pathPolicy.isSupportedArtifact(relative)) {
                    throw invalid("Unsupported Analytics Bundle file: " + relative);
                }
                byte[] content = Files.readAllBytes(path);
                if (relative.endsWith(".query.json")) {
                    AnalyticsQuerySpec query = readQuery(content);
                    rejectDuplicate("queryRef", queries.put(query.queryRef(), query), query.queryRef());
                } else if (relative.endsWith(".report.json")) {
                    AnalyticsReportDefinition report = readReport(content);
                    rejectDuplicate(
                            "report artifactRef",
                            reports.put(report.artifactRef(), report),
                            report.artifactRef());
                } else if (relative.endsWith(".dashboard.json")) {
                    dashboards.add(readDashboard(content));
                }
            }
        }

        validateReferences(manifest, queries, reports, dashboards);
    }

    /** Performs local syntax/shape validation before a write transaction starts. */
    void validateArtifact(String relativePath, byte[] content) {
        Objects.requireNonNull(content, "content");
        if (relativePath.endsWith(".query.json")) {
            readQuery(content);
        } else if (relativePath.endsWith(".report.json")) {
            readReport(content);
        } else if (relativePath.endsWith(".dashboard.json")) {
            readDashboard(content);
        }
    }

    private AnalyticsQuerySpec readQuery(byte[] content) {
        JsonNode root = readObject("QuerySpec", content);
        rejectUnknown("QuerySpec", root, QUERY_FIELDS);
        return new AnalyticsQuerySpec(
                new AnalyticsQueryRef(text(root, "queryRef")),
                new AnalyticsNamespaceRef(text(root, "namespaceRef")),
                text(root, "modelName"),
                stringList(root, "columns", false),
                stringList(root, "groupBy", true));
    }

    private AnalyticsReportDefinition readReport(byte[] content) {
        JsonNode root = readObject("ReportDefinition", content);
        rejectUnknown("ReportDefinition", root, REPORT_FIELDS);
        return new AnalyticsReportDefinition(
                new AnalyticsArtifactRef(
                        AnalyticsArtifactKind.REPORT,
                        text(root, "artifactRef")),
                new AnalyticsQueryRef(text(root, "queryRef")),
                visual(required(root, "visualIntent")));
    }

    private AnalyticsDashboardDefinition readDashboard(byte[] content) {
        JsonNode root = readObject("DashboardDefinition", content);
        rejectUnknown("DashboardDefinition", root, DASHBOARD_FIELDS);
        JsonNode widgetNodes = required(root, "widgets");
        if (!widgetNodes.isArray()) {
            throw invalid("widgets must be an array");
        }
        List<AnalyticsDashboardWidget> widgets = new ArrayList<>();
        for (JsonNode widget : widgetNodes) {
            requireObject("DashboardWidget", widget);
            rejectUnknown("DashboardWidget", widget, WIDGET_FIELDS);
            JsonNode reportRef = widget.get("reportRef");
            JsonNode queryRef = widget.get("queryRef");
            widgets.add(new AnalyticsDashboardWidget(
                    text(widget, "widgetRef"),
                    reportRef == null || reportRef.isNull()
                            ? null
                            : new AnalyticsArtifactRef(
                                    AnalyticsArtifactKind.REPORT,
                                    textual("reportRef", reportRef)),
                    queryRef == null || queryRef.isNull()
                            ? null
                            : new AnalyticsQueryRef(textual("queryRef", queryRef)),
                    visual(required(widget, "visualIntent"))));
        }
        return new AnalyticsDashboardDefinition(
                new AnalyticsArtifactRef(
                        AnalyticsArtifactKind.DASHBOARD,
                        text(root, "artifactRef")),
                widgets);
    }

    private AnalyticsVisualIntent visual(JsonNode node) {
        requireObject("visualIntent", node);
        rejectUnknown("visualIntent", node, VISUAL_FIELDS);
        AnalyticsVisualKind kind;
        try {
            kind = AnalyticsVisualKind.valueOf(text(node, "kind"));
        } catch (IllegalArgumentException invalidKind) {
            throw invalid("Unsupported visualIntent.kind", invalidKind);
        }
        JsonNode hintsNode = node.get("hints");
        Map<String, String> hints = new HashMap<>();
        if (hintsNode != null && !hintsNode.isNull()) {
            requireObject("visualIntent.hints", hintsNode);
            hintsNode.fields().forEachRemaining(entry ->
                    hints.put(entry.getKey(), textual("visualIntent.hints", entry.getValue())));
        }
        return new AnalyticsVisualIntent(kind, hints);
    }

    private void validateReferences(
            AnalyticsBundleManifest manifest,
            Map<AnalyticsQueryRef, AnalyticsQuerySpec> queries,
            Map<AnalyticsArtifactRef, AnalyticsReportDefinition> reports,
            List<AnalyticsDashboardDefinition> dashboards) {
        for (AnalyticsQuerySpec query : queries.values()) {
            if (!query.namespaceRef().equals(manifest.namespaceRef())) {
                throw invalid("Query namespaceRef must match the bundle manifest: "
                        + query.queryRef().value());
            }
            boolean dependencyExists = manifest.modelDependencies().stream().anyMatch(dependency ->
                    dependency.namespace().equals(query.namespaceRef())
                            && dependency.modelName().equals(query.modelName()));
            if (!dependencyExists) {
                throw invalid("Query has no pinned model dependency: "
                        + query.queryRef().value());
            }
        }
        for (AnalyticsReportDefinition report : reports.values()) {
            if (!queries.containsKey(report.queryRef())) {
                throw invalid("Report references a missing queryRef: "
                        + report.queryRef().value());
            }
        }
        Set<AnalyticsArtifactRef> dashboardRefs = new HashSet<>();
        for (AnalyticsDashboardDefinition dashboard : dashboards) {
            if (!dashboardRefs.add(dashboard.artifactRef())) {
                throw invalid("Duplicate dashboard artifactRef: "
                        + dashboard.artifactRef().value());
            }
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

    private JsonNode readObject(String kind, byte[] content) {
        if (content.length >= 3
                && (content[0] & 0xff) == 0xef
                && (content[1] & 0xff) == 0xbb
                && (content[2] & 0xff) == 0xbf) {
            throw invalid(kind + " must not contain a UTF-8 BOM");
        }
        try {
            JsonNode root = objectMapper.reader().readTree(content);
            requireObject(kind, root);
            return root;
        } catch (IOException failure) {
            throw invalid(kind + " is not valid JSON", failure);
        }
    }

    private static List<String> stringList(JsonNode node, String field, boolean defaultEmpty) {
        JsonNode values = node.get(field);
        if ((values == null || values.isNull()) && defaultEmpty) {
            return List.of();
        }
        if (values == null || !values.isArray()) {
            throw invalid(field + " must be an array");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            result.add(textual(field, value));
        }
        return result;
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw invalid(field + " is required");
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        return textual(field, required(node, field));
    }

    private static String textual(String field, JsonNode value) {
        if (!value.isTextual()) {
            throw invalid(field + " must be a string");
        }
        return value.textValue();
    }

    private static void requireObject(String field, JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalid(field + " must be an object");
        }
    }

    private static void rejectUnknown(String field, JsonNode node, Set<String> allowed) {
        Set<String> unknown = new HashSet<>();
        node.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                unknown.add(name);
            }
        });
        if (!unknown.isEmpty()) {
            throw invalid(field + " contains unsupported fields: " + unknown);
        }
    }

    private static void rejectDuplicate(String field, Object previous, Object identity) {
        if (previous != null) {
            throw invalid("Duplicate " + field + ": " + identity);
        }
    }

    private static String portable(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static ObjectMapper strictObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        return mapper;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }
}
