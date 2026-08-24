package com.foggyframework.analytics.function.fap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;

/** Mirrors the canonical JSON digest algorithm frozen by FAP service-provider v1alpha1. */
final class FapCanonicalDigests {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    private FapCanonicalDigests() {
    }

    static String json(Object value) {
        try {
            JsonNode tree = canonicalize(JSON.valueToTree(value));
            byte[] bytes = JSON.writeValueAsBytes(tree);
            return digest(bytes);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "FAP canonical JSON could not be serialized", exception);
        }
    }

    private static JsonNode canonicalize(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            ArrayList<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            names.forEach(name -> result.set(name, canonicalize(value.get(name))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = JSON.createArrayNode();
            value.forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        return value.deepCopy();
    }

    private static String digest(byte[] bytes) {
        try {
            return "sha256:" + java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
