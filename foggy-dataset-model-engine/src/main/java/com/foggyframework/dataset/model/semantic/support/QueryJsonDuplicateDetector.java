package com.foggyframework.dataset.model.semantic.support;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Detects repeated JSON object keys before Jackson collapses them into a tree or map. */
public final class QueryJsonDuplicateDetector {

    private final ObjectMapper objectMapper;

    public QueryJsonDuplicateDetector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<DuplicateQueryProperty> detect(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        LinkedHashMap<String, DuplicateAccumulator> duplicates = new LinkedHashMap<>();
        try (JsonParser parser = objectMapper.getFactory().createParser(json)) {
            JsonToken first = parser.nextToken();
            if (first != null) {
                inspectValue(parser, first, "$", duplicates);
            }
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("Query JSON must contain exactly one root value.");
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid query JSON: " + failure.getMessage(), failure);
        }
        return duplicates.values().stream()
                .map(value -> new DuplicateQueryProperty(
                        value.path, value.property, value.occurrences))
                .toList();
    }

    private static void inspectValue(
            JsonParser parser,
            JsonToken token,
            String path,
            Map<String, DuplicateAccumulator> duplicates
    ) throws IOException {
        if (token == JsonToken.START_OBJECT) {
            inspectObject(parser, path, duplicates);
        } else if (token == JsonToken.START_ARRAY) {
            inspectArray(parser, path, duplicates);
        }
    }

    private static void inspectObject(
            JsonParser parser,
            String path,
            Map<String, DuplicateAccumulator> duplicates
    ) throws IOException {
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
            if (token != JsonToken.FIELD_NAME) {
                throw new IllegalArgumentException("Invalid JSON object at " + path + ".");
            }
            String property = parser.currentName();
            int count = occurrences.merge(property, 1, Integer::sum);
            String propertyPath = path + "." + safeProperty(property);
            if (count > 1) {
                DuplicateAccumulator duplicate = duplicates.computeIfAbsent(
                        propertyPath,
                        ignored -> new DuplicateAccumulator(propertyPath, property, count));
                duplicate.occurrences = count;
            }
            JsonToken valueToken = parser.nextToken();
            if (valueToken == null) {
                throw new IllegalArgumentException("Missing JSON value at " + propertyPath + ".");
            }
            inspectValue(parser, valueToken, propertyPath, duplicates);
        }
    }

    private static void inspectArray(
            JsonParser parser,
            String path,
            Map<String, DuplicateAccumulator> duplicates
    ) throws IOException {
        int index = 0;
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == null) {
                throw new IllegalArgumentException("Unterminated JSON array at " + path + ".");
            }
            inspectValue(parser, token, path + "[" + index + "]", duplicates);
            index++;
        }
    }

    private static String safeProperty(String property) {
        String safe = property == null ? "null" : property.replaceAll("[\\p{Cntrl}]", "?");
        return safe.length() <= 128 ? safe : safe.substring(0, 128);
    }

    private static final class DuplicateAccumulator {
        private final String path;
        private final String property;
        private int occurrences;

        private DuplicateAccumulator(String path, String property, int occurrences) {
            this.path = path;
            this.property = property;
            this.occurrences = occurrences;
        }
    }
}
