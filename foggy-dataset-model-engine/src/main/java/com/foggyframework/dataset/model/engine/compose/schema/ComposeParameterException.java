package com.foggyframework.dataset.model.engine.compose.schema;

import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Input contract failure, retaining IllegalArgumentException compatibility for Java callers. */
public final class ComposeParameterException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final String parameter;
    private final Map<String, Object> details;

    private ComposeParameterException(String code, String path, String parameter,
                                      String message, String action, Map<String, Object> extra) {
        super(code + ": " + path + " " + message);
        this.code = code;
        this.parameter = parameter;
        Map<String, Object> values = new LinkedHashMap<>(extra);
        values.put("path", path);
        values.put("parameter", parameter);
        values.put("suggestedNextAction", action);
        values.put("safeToAutoRepair", false);
        this.details = Map.copyOf(values);
    }

    public String code() { return code; }
    public String parameter() { return parameter; }
    public String phase() { return ComposeSchemaErrorCodes.PHASE_PLAN_BUILD; }
    public Map<String, Object> details() { return details; }

    public static ComposeParameterException invalidType(String code, String path,
                                                       String parameter, String expected, Object value) {
        String actual = typeName(value);
        return new ComposeParameterException(code, path, parameter,
                "requires " + expected + "; got " + actual,
                "Provide " + expected + " at " + path + ".",
                Map.of("expectedType", expected, "actualType", actual));
    }

    public static ComposeParameterException conflictingSources() {
        return new ComposeParameterException("COMPOSE_SOURCE_CONFLICT", "dsl.model", "model",
                "and dsl.source cannot both select an input plan",
                "Use model with a QM name or QueryPlan; remove the other source parameter.", Map.of());
    }

    public static ComposeParameterException unsupportedDerived(String path, String parameter) {
        String action = "calculatedFields".equals(parameter)
                ? "Project supported expressions in columns with AS aliases; add another .query(...) stage for filtering the new aliases."
                : "Filter aggregate results in another .query({slice: [...]}) stage.";
        return new ComposeParameterException("DERIVED_PARAMETER_UNSUPPORTED", path, parameter,
                "is not supported for derived plans; only null or an empty array is accepted",
                action, Map.of("supportedAlternative", "calculatedFields".equals(parameter)
                        ? "columns expression AS alias, followed by .query({slice: [...]}) if needed"
                        : ".query({slice: [...]})"));
    }

    private static String typeName(Object value) {
        if (value == null) return "null";
        if (value instanceof QueryPlan) return "QueryPlan";
        if (value instanceof String) return "string";
        if (value instanceof Map<?, ?>) return "object";
        if (value instanceof List<?> || value.getClass().isArray()) return "array";
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";
        return "unsupported value";
    }
}
