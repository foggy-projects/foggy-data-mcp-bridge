package com.foggyframework.runtime.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.semantic.support.DuplicateQueryProperty;
import com.foggyframework.dataset.model.semantic.support.QueryInputValidationException;
import com.foggyframework.dataset.model.semantic.support.QueryJsonDuplicateDetector;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Raw JSON support retained at Runtime ingress so duplicate keys are not lost during binding. */
final class RuntimeQueryJsonSupport {

    private RuntimeQueryJsonSupport() {
    }

    static ParsedJson parse(ObjectMapper objectMapper, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return new ParsedJson(null, List.of());
        }
        List<DuplicateQueryProperty> duplicates =
                new QueryJsonDuplicateDetector(objectMapper).detect(rawJson);
        try {
            return new ParsedJson(objectMapper.readTree(rawJson), duplicates);
        } catch (Exception failure) {
            throw new IllegalArgumentException("Invalid query JSON: " + failure.getMessage(), failure);
        }
    }

    static List<DuplicateQueryProperty> queryDuplicates(
            JsonNode body,
            List<DuplicateQueryProperty> duplicates
    ) {
        if (duplicates == null || duplicates.isEmpty()) {
            return List.of();
        }
        String root = body != null && body.hasNonNull("payload")
                ? "payload"
                : body != null && body.hasNonNull("request") ? "request" : null;
        if (root == null) {
            return List.copyOf(duplicates);
        }
        List<DuplicateQueryProperty> scoped = rebase(duplicates, root);
        addExactPath(duplicates, scoped, "$." + root);
        addExactPath(duplicates, scoped, "$.namespace");
        return List.copyOf(scoped);
    }

    static List<DuplicateQueryProperty> nestedDuplicates(
            List<DuplicateQueryProperty> duplicates,
            String root
    ) {
        if (duplicates == null || duplicates.isEmpty()) {
            return List.of();
        }
        List<DuplicateQueryProperty> scoped = rebase(duplicates, root);
        addExactPath(duplicates, scoped, "$." + root);
        return List.copyOf(scoped);
    }

    static RuntimeDiagnostics validationDiagnostics(QueryInputValidationException failure) {
        return new RuntimeDiagnostics(
                null,
                null,
                List.of(),
                Map.of("violations", failure.getViolations()));
    }

    private static List<DuplicateQueryProperty> rebase(
            List<DuplicateQueryProperty> duplicates,
            String root
    ) {
        String prefix = "$." + root;
        List<DuplicateQueryProperty> scoped = new ArrayList<>();
        for (DuplicateQueryProperty duplicate : duplicates) {
            if (duplicate.path().startsWith(prefix + ".")
                    || duplicate.path().startsWith(prefix + "[")) {
                scoped.add(new DuplicateQueryProperty(
                        "$" + duplicate.path().substring(prefix.length()),
                        duplicate.property(),
                        duplicate.occurrences()));
            }
        }
        return scoped;
    }

    private static void addExactPath(
            List<DuplicateQueryProperty> source,
            List<DuplicateQueryProperty> target,
            String path
    ) {
        source.stream()
                .filter(duplicate -> path.equals(duplicate.path()))
                .forEach(target::add);
    }

    record ParsedJson(JsonNode body, List<DuplicateQueryProperty> duplicates) {
    }
}
