package com.foggyframework.analytics.function.fap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One Analytics operation plus its FAP publication projection.
 *
 * <p>Side-effect and confirmation are local adapter policy because the current
 * FAP BusinessFunctionProjection wire schema has no matching fields. Only
 * {@link Projection#publicationValue()} is sent to FAP.</p>
 */
public record FapAnalyticsFunctionDescriptor(
        String operation,
        SideEffect sideEffect,
        Confirmation confirmation,
        Projection projection) {

    public FapAnalyticsFunctionDescriptor {
        operation = FapAnalyticsValues.text("operation", operation, 160);
        sideEffect = Objects.requireNonNull(sideEffect, "sideEffect");
        confirmation = Objects.requireNonNull(confirmation, "confirmation");
        projection = Objects.requireNonNull(projection, "projection");
    }

    public enum SideEffect {
        READ_ONLY
    }

    public enum Confirmation {
        NOT_REQUIRED
    }

    /** Exact JSON fields accepted by FAP BusinessFunctionProjection v1alpha1. */
    public record Projection(
            String functionRef,
            String name,
            String displayName,
            String description,
            String searchText,
            List<String> tags,
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema,
            List<Object> examples,
            String schemaDigest,
            String projectionDigest) {

        public Projection {
            functionRef = FapAnalyticsValues.functionRef(functionRef);
            name = FapAnalyticsValues.stableName(name);
            displayName = FapAnalyticsValues.text(
                    "displayName", displayName, 256);
            description = FapAnalyticsValues.text(
                    "description", description, 4_000);
            searchText = searchText == null
                    ? ""
                    : FapAnalyticsValues.text("searchText", searchText, 4_096);
            tags = FapAnalyticsValues.tags(tags);
            inputSchema = FapAnalyticsValues.object(
                    "inputSchema", Objects.requireNonNull(inputSchema, "inputSchema"));
            outputSchema = FapAnalyticsValues.object(
                    "outputSchema", Objects.requireNonNull(outputSchema, "outputSchema"));
            examples = normalizeExamples(examples);
            schemaDigest = FapAnalyticsValues.digest("schemaDigest", schemaDigest);
            projectionDigest = FapAnalyticsValues.digest(
                    "projectionDigest", projectionDigest);

            String expectedSchemaDigest = schemaDigest(
                    functionRef, name, inputSchema, outputSchema);
            if (!schemaDigest.equals(expectedSchemaDigest)) {
                throw new IllegalArgumentException(
                        "schemaDigest does not match the FAP projection contract");
            }
            String expectedProjectionDigest = projectionDigest(
                    functionRef,
                    name,
                    displayName,
                    description,
                    searchText,
                    tags,
                    inputSchema,
                    outputSchema,
                    examples,
                    schemaDigest);
            if (!projectionDigest.equals(expectedProjectionDigest)) {
                throw new IllegalArgumentException(
                        "projectionDigest does not match the FAP projection content");
            }
        }

        public static Projection create(
                String functionRef,
                String name,
                String displayName,
                String description,
                String searchText,
                List<String> tags,
                Map<String, Object> inputSchema,
                Map<String, Object> outputSchema,
                List<?> examples) {
            String normalizedFunctionRef = FapAnalyticsValues.functionRef(functionRef);
            String normalizedName = FapAnalyticsValues.stableName(name);
            String normalizedDisplayName = FapAnalyticsValues.text(
                    "displayName", displayName, 256);
            String normalizedDescription = FapAnalyticsValues.text(
                    "description", description, 4_000);
            String normalizedSearchText = searchText == null
                    ? ""
                    : FapAnalyticsValues.text("searchText", searchText, 4_096);
            List<String> normalizedTags = FapAnalyticsValues.tags(tags);
            Map<String, Object> normalizedInput = FapAnalyticsValues.object(
                    "inputSchema", inputSchema);
            Map<String, Object> normalizedOutput = FapAnalyticsValues.object(
                    "outputSchema", outputSchema);
            List<Object> normalizedExamples = normalizeExamples(examples);
            String schemaDigest = schemaDigest(
                    normalizedFunctionRef,
                    normalizedName,
                    normalizedInput,
                    normalizedOutput);
            String projectionDigest = projectionDigest(
                    normalizedFunctionRef,
                    normalizedName,
                    normalizedDisplayName,
                    normalizedDescription,
                    normalizedSearchText,
                    normalizedTags,
                    normalizedInput,
                    normalizedOutput,
                    normalizedExamples,
                    schemaDigest);
            return new Projection(
                    normalizedFunctionRef,
                    normalizedName,
                    normalizedDisplayName,
                    normalizedDescription,
                    normalizedSearchText,
                    normalizedTags,
                    normalizedInput,
                    normalizedOutput,
                    normalizedExamples,
                    schemaDigest,
                    projectionDigest);
        }

        /** Wire-ready value with no adapter-only policy fields. */
        public Map<String, Object> publicationValue() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("functionRef", functionRef);
            value.put("name", name);
            value.put("displayName", displayName);
            value.put("description", description);
            value.put("searchText", searchText);
            value.put("tags", tags);
            value.put("inputSchema", inputSchema);
            value.put("outputSchema", outputSchema);
            value.put("examples", examples);
            value.put("schemaDigest", schemaDigest);
            value.put("projectionDigest", projectionDigest);
            return FapAnalyticsValues.object("publication", value);
        }

        private static List<Object> normalizeExamples(List<?> source) {
            if (source == null) {
                return List.of();
            }
            if (source.size() > 32) {
                throw new IllegalArgumentException("examples exceed the FAP limit");
            }
            List<Object> values = new ArrayList<>(source.size());
            for (int index = 0; index < source.size(); index++) {
                values.add(FapAnalyticsValues.jsonValue(
                        "examples[" + index + ']',
                        source.get(index)));
            }
            return Collections.unmodifiableList(values);
        }

        private static String schemaDigest(
                String functionRef,
                String name,
                Map<String, Object> inputSchema,
                Map<String, Object> outputSchema) {
            return FapCanonicalDigests.json(Map.of(
                    "functionRef", functionRef,
                    "name", name,
                    "inputSchema", inputSchema,
                    "outputSchema", outputSchema));
        }

        private static String projectionDigest(
                String functionRef,
                String name,
                String displayName,
                String description,
                String searchText,
                List<String> tags,
                Map<String, Object> inputSchema,
                Map<String, Object> outputSchema,
                List<Object> examples,
                String schemaDigest) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("functionRef", functionRef);
            value.put("name", name);
            value.put("displayName", displayName);
            value.put("description", description);
            value.put("searchText", searchText);
            value.put("tags", tags);
            value.put("inputSchema", inputSchema);
            value.put("outputSchema", outputSchema);
            value.put("examples", examples);
            value.put("schemaDigest", schemaDigest);
            return FapCanonicalDigests.json(value);
        }
    }
}
