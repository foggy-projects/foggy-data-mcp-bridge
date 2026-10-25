package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.dataset.model.engine.compose.schema.ComposeParameterException;
import com.foggyframework.dataset.model.engine.compose.ComposeOrderByNormalizer;

import java.util.ArrayList;
import java.util.List;

/** Shared derived-option boundary for script, legacy source and typed Java entry points. */
public final class ComposeOptionValidator {
    private ComposeOptionValidator() { }

    public static void validateDerived(Object calculatedFields, Object having, String path) {
        validateUnsupported(calculatedFields, "calculatedFields", path);
        validateUnsupported(having, "having", path);
    }

    public static List<String> orderBy(List<?> entries, String path) {
        List<String> result = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            try {
                var order = ComposeOrderByNormalizer.parse(entries.get(i));
                if (order.field() == null || order.field().isBlank()) throw new IllegalArgumentException();
                result.add("desc".equalsIgnoreCase(order.dir()) ? "-" + order.field() : order.field());
            } catch (IllegalArgumentException invalid) {
                throw ComposeParameterException.invalidType("COMPOSE_PARAMETER_TYPE_INVALID",
                        path + "[" + i + "]", "orderBy", "a field name or order object with a string field", entries.get(i));
            }
        }
        return result;
    }

    private static void validateUnsupported(Object value, String parameter, String path) {
        if (value == null) return;
        if (!(value instanceof List<?> entries)) {
            throw ComposeParameterException.invalidType("COMPOSE_PARAMETER_TYPE_INVALID",
                    path + "." + parameter, parameter, "an array or null", value);
        }
        if (!entries.isEmpty()) {
            throw ComposeParameterException.unsupportedDerived(path + "." + parameter, parameter);
        }
    }
}
