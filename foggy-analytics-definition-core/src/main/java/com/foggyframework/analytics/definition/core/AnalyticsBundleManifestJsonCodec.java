package com.foggyframework.analytics.definition.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.definition.api.AnalyticsSchemaVersion;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reads the flat v1 manifest shape and creates its revision-stable projection. */
public final class AnalyticsBundleManifestJsonCodec {

    public static final String SUPPORTED_SCHEMA_VERSION = AnalyticsSchemaVersion.V1.value();

    private static final Set<String> ROOT_FIELDS = Set.of(
            "kind",
            "schemaVersion",
            "bundleRef",
            "bundleRevision",
            "namespaceRef",
            "modelDependencies",
            "generatedAt",
            "signature");
    private static final Set<String> DEPENDENCY_FIELDS = Set.of(
            "namespace",
            "modelKind",
            "modelName",
            "sourceBundleRef",
            "sourceBundleRevision",
            "catalogIdentity");

    private final ObjectMapper objectMapper;

    public AnalyticsBundleManifestJsonCodec() {
        this(new ObjectMapper());
    }

    public AnalyticsBundleManifestJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public AnalyticsBundleManifest read(byte[] json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonNode root = objectMapper.readTree(json);
            requireObject("manifest", root);
            rejectUnknownFields("manifest", root, ROOT_FIELDS);
            List<AnalyticsModelDependency> dependencies = new ArrayList<>();
            JsonNode dependencyNodes = required(root, "modelDependencies");
            if (!dependencyNodes.isArray()) {
                throw invalid("modelDependencies must be an array");
            }
            for (JsonNode dependency : dependencyNodes) {
                requireObject("modelDependency", dependency);
                rejectUnknownFields("modelDependency", dependency, DEPENDENCY_FIELDS);
                dependencies.add(new AnalyticsModelDependency(
                        new AnalyticsNamespaceRef(text(dependency, "namespace")),
                        text(dependency, "modelKind"),
                        text(dependency, "modelName"),
                        new AnalyticsBundleRef(text(dependency, "sourceBundleRef")),
                        new AnalyticsBundleRevision(text(dependency, "sourceBundleRevision")),
                        text(dependency, "catalogIdentity")));
            }
            AnalyticsSchemaVersion schemaVersion = new AnalyticsSchemaVersion(
                    text(root, "schemaVersion"));
            if (!SUPPORTED_SCHEMA_VERSION.equals(schemaVersion.value())) {
                throw invalid("Unsupported Analytics manifest schemaVersion: "
                        + schemaVersion.value());
            }
            return new AnalyticsBundleManifest(
                    text(root, "kind"),
                    schemaVersion,
                    new AnalyticsBundleRef(text(root, "bundleRef")),
                    new AnalyticsBundleRevision(text(root, "bundleRevision")),
                    new AnalyticsNamespaceRef(text(root, "namespaceRef")),
                    dependencies);
        } catch (IOException failure) {
            throw invalid("manifest.json is not valid JSON", failure);
        }
    }

    public byte[] stableRevisionProjection(AnalyticsBundleManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                write(output, manifest.kind());
                write(output, manifest.schemaVersion().value());
                write(output, manifest.bundleRef().value());
                write(output, manifest.namespaceRef().value());
                List<AnalyticsModelDependency> dependencies = manifest.modelDependencies().stream()
                        .sorted(Comparator
                                .comparing((AnalyticsModelDependency dependency) ->
                                        dependency.namespace().value())
                                .thenComparing(AnalyticsModelDependency::modelKind)
                                .thenComparing(AnalyticsModelDependency::modelName)
                                .thenComparing(dependency -> dependency.sourceBundleRef().value())
                                .thenComparing(dependency ->
                                        dependency.sourceBundleRevision().value())
                                .thenComparing(AnalyticsModelDependency::catalogIdentity))
                        .toList();
                output.writeInt(dependencies.size());
                for (AnalyticsModelDependency dependency : dependencies) {
                    write(output, dependency.namespace().value());
                    write(output, dependency.modelKind());
                    write(output, dependency.modelName());
                    write(output, dependency.sourceBundleRef().value());
                    write(output, dependency.sourceBundleRevision().value());
                    write(output, dependency.catalogIdentity());
                }
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("In-memory manifest projection failed", impossible);
        }
    }

    /** Rewrites only the derived revision while preserving optional transport metadata. */
    public byte[] withBundleRevision(byte[] json, AnalyticsBundleRevision revision) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(revision, "revision");
        read(json);
        try {
            JsonNode root = objectMapper.readTree(json);
            ((ObjectNode) root).put("bundleRevision", revision.value());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        } catch (IOException failure) {
            throw invalid("manifest.json could not be rewritten", failure);
        }
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw invalid(field + " is required");
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = required(node, field);
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

    private static void rejectUnknownFields(String field, JsonNode node, Set<String> allowed) {
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

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }
}
