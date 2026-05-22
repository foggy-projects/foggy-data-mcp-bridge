package com.foggyframework.dataset.db.model.memorygrid.bridge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plans the minimum executable Memory Grid subset.
 */
public final class MemoryGridExecutablePlanner {

    public static final String STATUS_READY = "BRIDGE_READY";
    public static final String STATUS_DEFERRED = "BRIDGE_DEFERRED";

    private static final Pattern BINARY_EXPR = Pattern.compile(
            "^\\s*([A-Za-z_][\\w.$]*)\\s*([+\\-*/])\\s*([A-Za-z_][\\w.$]*)\\s*$");

    private MemoryGridExecutablePlanner() {
    }

    public static BridgePlan plan(Map<String, Object> plan) {
        List<String> unsupported = new ArrayList<>();
        if (plan == null || plan.isEmpty()) {
            unsupported.add("memory_grid_plan must be provided");
            return BridgePlan.deferred(unsupported);
        }
        if (plan.containsKey("rows")) {
            unsupported.add("memory grid execution does not accept request-provided rows");
        }

        List<Map<String, Object>> inputs = mapList(plan.get("inputs"), "inputs", unsupported);
        if (inputs.size() != 2) {
            unsupported.add("Memory Grid bridge v1 requires exactly two inputs");
        }
        for (Map<String, Object> input : inputs) {
            if (input.containsKey("rows")) {
                unsupported.add("memory grid input rows must come from result resolver: " + inputName(input));
            }
        }

        Map<String, Object> join = mapValue(plan.get("join"));
        String joinType = normalize(join == null ? null : join.get("type"));
        if (!"inner".equals(joinType)) {
            unsupported.add("Memory Grid bridge v1 supports inner join only");
        }
        List<String> joinKeys = join == null ? List.of() : stringList(join.get("keys"));
        if (joinKeys.size() != 1) {
            unsupported.add("Memory Grid bridge v1 requires a single join key");
        }

        List<DerivedFormula> derived = derivedFormulas(plan.get("derived"), unsupported);
        if (derived.isEmpty()) {
            unsupported.add("Memory Grid bridge v1 requires at least one binary numeric derived formula");
        }

        Integer outputLimit = intValue(plan.get("output_limit"));
        if (outputLimit == null || outputLimit <= 0) {
            unsupported.add("Memory Grid bridge v1 requires positive output_limit");
        }

        List<String> outputColumns = outputColumns(plan.get("output"), inputs, joinKeys, derived);
        if (outputColumns.isEmpty()) {
            unsupported.add("Memory Grid bridge v1 requires output columns");
        }

        if (!unsupported.isEmpty()) {
            return BridgePlan.deferred(unsupported);
        }
        return new BridgePlan(STATUS_READY, List.of(), joinKeys, derived, outputLimit, outputColumns);
    }

    private static List<DerivedFormula> derivedFormulas(Object raw, List<String> unsupported) {
        List<Map<String, Object>> maps = mapList(raw, "derived", unsupported);
        List<DerivedFormula> result = new ArrayList<>();
        for (Map<String, Object> derived : maps) {
            String name = stringValue(derived.get("name"));
            String expr = stringValue(derived.get("expr"));
            Matcher matcher = expr == null ? null : BINARY_EXPR.matcher(expr);
            if (name == null || name.isBlank() || matcher == null || !matcher.matches()) {
                unsupported.add("derived formula is not executable through Memory Grid bridge v1: " + derived);
                continue;
            }
            result.add(new DerivedFormula(name, matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        return result;
    }

    private static List<String> outputColumns(Object rawOutput, List<Map<String, Object>> inputs,
                                              List<String> joinKeys, List<DerivedFormula> derived) {
        List<String> declared = stringList(rawOutput);
        if (!declared.isEmpty()) {
            return declared;
        }
        Set<String> columns = new LinkedHashSet<>(joinKeys);
        for (Map<String, Object> input : inputs) {
            for (Map<String, Object> metric : mapList(input.get("metrics"), "metrics", new ArrayList<>())) {
                String name = stringValue(metric.get("name"));
                if (name != null && !name.isBlank()) {
                    columns.add(name);
                }
            }
        }
        for (DerivedFormula formula : derived) {
            columns.add(formula.name());
        }
        return new ArrayList<>(columns);
    }

    private static String inputName(Map<String, Object> input) {
        String name = stringValue(input.get("name"));
        return name == null || name.isBlank() ? "input" : name;
    }

    private static List<Map<String, Object>> mapList(Object value, String name, List<String> unsupported) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = mapValue(item);
            if (map == null) {
                unsupported.add(name + " entries must be objects");
            } else {
                result.add(map);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = stringValue(item);
            if (text != null && !text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private static String normalize(Object value) {
        String text = stringValue(value);
        return text == null ? null : text.trim().toLowerCase(Locale.ROOT);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public record DerivedFormula(String name, String left, String operator, String right) {
    }

    public record BridgePlan(String status,
                             List<String> unsupported,
                             List<String> joinKeys,
                             List<DerivedFormula> derived,
                             int outputLimit,
                             List<String> outputColumns) {
        static BridgePlan deferred(List<String> unsupported) {
            return new BridgePlan(STATUS_DEFERRED, List.copyOf(unsupported),
                    List.of(), List.of(), 0, List.of());
        }

        public boolean ready() {
            return STATUS_READY.equals(status);
        }
    }
}
