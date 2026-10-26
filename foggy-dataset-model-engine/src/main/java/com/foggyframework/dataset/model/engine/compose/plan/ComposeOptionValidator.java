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

    /** Strict known options, without rejecting unrelated extension keys. Null means omitted. */
    public static void validateOptions(java.util.Map<String, Object> options, String path, boolean derived) {
        com.foggyframework.dataset.model.engine.compose.sandbox.SecurityParamGuard.validate(options, "script-eval");
        Object timeWindow = options.get("timeWindow");
        if (timeWindow != null) {
            if (!(timeWindow instanceof java.util.Map<?, ?> window)) throw ComposeParameterException.invalidType(
                    "COMPOSE_PARAMETER_TYPE_INVALID", path + ".timeWindow", "timeWindow", "an object or null", timeWindow);
            if (!window.isEmpty()) throw ComposeParameterException.unsupportedTimeWindow(path + ".timeWindow");
        }
        if ("query".equals(path)) {
            for (String selector : List.of("model", "source")) {
                if (options.get(selector) != null) throw ComposeParameterException.invalidType(
                        "COMPOSE_PARAMETER_TYPE_INVALID", path + "." + selector, selector,
                        "omission (query uses its receiver as source)", options.get(selector));
            }
        }
        if (derived) validateDerived(options.get("calculatedFields"), options.get("having"), path);
        for (String key : List.of("columns", "slice", "groupBy", "orderBy", "having", "calculatedFields")) {
            array(options.get(key), path + "." + key, key);
        }
        strings(options.get("groupBy"), path + ".groupBy", "groupBy");
        for (String key : List.of("limit", "start", "offset")) integer(options.get(key), path + "." + key, key);
        bool(options.get("distinct"), path + ".distinct", "distinct");
    }

    /** Script call signatures are checked before dispatch; typed Java overloads remain unchanged. */
    public static Object[] arguments(Object[] args, int minimum, int maximum, String method) {
        int count = args == null ? 0 : args.length;
        if (count < minimum || count > maximum) throw ComposeParameterException.argumentCount(method, minimum, maximum, count);
        return args == null ? new Object[0] : args;
    }

    public static String requiredString(Object value, String path, String parameter) {
        if (value instanceof String text && !text.isBlank()) return text;
        throw ComposeParameterException.invalidType("COMPOSE_PARAMETER_TYPE_INVALID", path, parameter, "a non-blank string", value);
    }

    public static Object[] aliasArguments(Object[] args) {
        Object[] checked = arguments(args, 1, 2, "as");
        requiredString(checked[0], "as.alias", "alias");
        if (checked.length == 2) string(checked[1], "as.caption", "caption");
        return checked;
    }

    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> windowArguments(Object[] args) {
        Object[] checked = arguments(args, 0, 1, "over");
        if (checked.length == 0 || checked[0] == null) return null;
        if (checked[0] instanceof java.util.Map<?, ?> map) return (java.util.Map<String, Object>) map;
        throw ComposeParameterException.invalidType("COMPOSE_PARAMETER_TYPE_INVALID", "over", "options", "an object or null", checked[0]);
    }

    public static List<?> array(Object value, String path, String parameter) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) return list;
        throw ComposeParameterException.invalidType("COMPOSE_PARAMETER_TYPE_INVALID", path, parameter, "an array or null", value);
    }

    public static List<String> strings(Object value, String path, String parameter) {
        List<?> raw = array(value, path, parameter);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            if (!(raw.get(i) instanceof String text) || text.isBlank()) throw ComposeParameterException.invalidType(
                    "COMPOSE_PARAMETER_TYPE_INVALID", path + "[" + i + "]", parameter, "a non-blank string", raw.get(i));
            result.add(text);
        }
        return result;
    }

    public static String string(Object value, String path, String parameter) {
        if (value == null || value instanceof String) return (String) value;
        throw ComposeParameterException.invalidType("COMPOSE_PARAMETER_TYPE_INVALID", path, parameter, "a string or null", value);
    }

    public static Integer integer(Object value, String path, String parameter) {
        if (value == null) return null;
        if (value instanceof Number number) {
            try {
                int exact = new java.math.BigDecimal(number.toString()).intValueExact();
                if (exact >= 0) return exact;
            } catch (NumberFormatException | ArithmeticException invalid) { /* fail below */ }
        }
        throw ComposeParameterException.invalidType("COMPOSE_PARAMETER_TYPE_INVALID", path, parameter,
                "an integer from 0 to 2147483647 or null", value);
    }

    public static boolean bool(Object value, String path, String parameter) {
        if (value == null) return false;
        if (value instanceof Boolean flag) return flag;
        throw ComposeParameterException.invalidType("COMPOSE_PARAMETER_TYPE_INVALID", path, parameter, "a boolean or null", value);
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
