package com.foggyframework.dataset.model.def;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validate before bean conversion can silently coerce non-string annotations. */
public final class TimeMetadataDefinitionValidator {
    private TimeMetadataDefinitionValidator() {}

    public static void validate(Object exported) {
        if (exported instanceof Map<?, ?> model) {
            validate(model, String.valueOf(model.get("name")),
                    Collections.newSetFromMap(new IdentityHashMap<>()));
        }
    }

    private static void validate(Map<?, ?> node, String path, Set<Object> visited) {
        if (!visited.add(node)) return;
        for (String key : List.of("timeRole", "recommendedUse")) {
            Object value = node.get(key);
            if (value != null && !(value instanceof String)) {
                throw new IllegalArgumentException("INVALID_TIME_METADATA: " + path + "." + key
                        + " must be a string or null; use a semantic role label for timeRole"
                        + " and explanatory text for recommendedUse (received "
                        + value.getClass().getSimpleName() + ")");
            }
        }
        for (String key : List.of("properties", "dimensions")) {
            if (node.get(key) instanceof List<?> children) {
                for (int i = 0; i < children.size(); i++) {
                    if (children.get(i) instanceof Map<?, ?> child) {
                        validate(child, path + "." + key + "[" + i + "](" + child.get("name") + ")", visited);
                    }
                }
            }
        }
    }
}
