package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Executes the narrow Memory Grid bridge-ready subset.
 */
public final class MemoryGridExecutor {

    public static final String RESULT_HANDLE_NOT_FOUND = "MEMORY_GRID_RESULT_HANDLE_NOT_FOUND";
    public static final String SCHEMA_MISMATCH = "MEMORY_GRID_RESULT_SCHEMA_MISMATCH";
    public static final String GOVERNANCE_MISMATCH = "MEMORY_GRID_RESULT_GOVERNANCE_MISMATCH";

    private MemoryGridExecutor() {
    }

    public static ExecutionResult execute(Map<String, Object> plan,
                                          MemoryGridExecutablePlanner.BridgePlan bridgePlan,
                                          MemoryGridResultResolver resolver,
                                          SemanticRequestContext context) {
        if (resolver == null) {
            throw RX.throwB(RESULT_HANDLE_NOT_FOUND + ": no MemoryGridResultResolver is configured.");
        }
        List<Map<String, Object>> inputs = mapList(plan.get("inputs"));
        Map<String, Object> leftInput = inputs.get(0);
        Map<String, Object> rightInput = inputs.get(1);
        MemoryGridResultResolver.ResolvedResult left = resolve(leftInput, resolver, context);
        MemoryGridResultResolver.ResolvedResult right = resolve(rightInput, resolver, context);

        String joinKey = bridgePlan.joinKeys().get(0);
        validateResolvedInput(leftInput, left, joinKey);
        validateResolvedInput(rightInput, right, joinKey);
        validateGlobalColumns(left, right, bridgePlan);

        Map<Object, List<Map<String, Object>>> rightRowsByKey = rowsByKey(right.rows(), joinKey);
        List<Map<String, Object>> output = new ArrayList<>();
        for (Map<String, Object> leftRow : left.rows()) {
            Object key = leftRow.get(joinKey);
            List<Map<String, Object>> matched = rightRowsByKey.get(key);
            if (matched == null) {
                continue;
            }
            for (Map<String, Object> rightRow : matched) {
                Map<String, Object> combined = combine(leftRow, rightRow);
                for (MemoryGridExecutablePlanner.DerivedFormula formula : bridgePlan.derived()) {
                    combined.put(formula.name(), evaluate(formula, combined));
                }
                output.add(project(combined, bridgePlan.outputColumns()));
                if (output.size() >= bridgePlan.outputLimit()) {
                    return new ExecutionResult(output, summary(plan, bridgePlan, left, right, output.size(), true));
                }
            }
        }
        return new ExecutionResult(output, summary(plan, bridgePlan, left, right, output.size(), false));
    }

    private static MemoryGridResultResolver.ResolvedResult resolve(Map<String, Object> input,
                                                                   MemoryGridResultResolver resolver,
                                                                   SemanticRequestContext context) {
        String handle = stringValue(input.get("result_handle"));
        if (handle == null || handle.isBlank()) {
            throw RX.throwB(RESULT_HANDLE_NOT_FOUND + ": memory grid input result_handle is missing.");
        }
        MemoryGridResultResolver.ResolvedResult result = resolver.resolve(handle, context);
        if (result == null) {
            throw RX.throwB(RESULT_HANDLE_NOT_FOUND + ": " + handle);
        }
        if (result.schema() == null || result.schema().isEmpty()) {
            throw RX.throwB(SCHEMA_MISMATCH + ": resolver schema is missing for " + handle + ".");
        }
        if (result.rows() == null) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": resolver rows are missing for " + handle + ".");
        }
        return result;
    }

    private static void validateResolvedInput(Map<String, Object> input,
                                              MemoryGridResultResolver.ResolvedResult result,
                                              String joinKey) {
        String handle = stringValue(input.get("result_handle"));
        if (!handle.equals(result.resultHandle())) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": resolver returned mismatched result_handle.");
        }
        String expectedRoute = normalize(input.get("source_route"));
        String actualRoute = normalize(result.sourceRoute());
        if (expectedRoute == null || expectedRoute.isBlank()) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": input source_route is missing for " + handle + ".");
        }
        if (actualRoute != null && !expectedRoute.equals(actualRoute)) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": resolver returned mismatched source_route for " + handle + ".");
        }
        Integer rowLimit = intValue(input.get("row_limit"));
        if (rowLimit != null && result.rows().size() > rowLimit) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": resolver row count exceeds declared row_limit for " + handle + ".");
        }
        requireColumn(result, joinKey, true, false, true);
        for (String metric : metricNames(input)) {
            requireColumn(result, metric, false, true, true);
        }
    }

    private static void validateGlobalColumns(MemoryGridResultResolver.ResolvedResult left,
                                              MemoryGridResultResolver.ResolvedResult right,
                                              MemoryGridExecutablePlanner.BridgePlan bridgePlan) {
        for (MemoryGridExecutablePlanner.DerivedFormula formula : bridgePlan.derived()) {
            requireAvailableForDerived(left, right, formula.left());
            requireAvailableForDerived(left, right, formula.right());
        }
        for (String output : bridgePlan.outputColumns()) {
            if (!containsOutput(left, output) && !containsOutput(right, output)
                    && bridgePlan.derived().stream().noneMatch(d -> d.name().equals(output))) {
                throw RX.throwB(SCHEMA_MISMATCH + ": output column is not available: " + output);
            }
        }
    }

    private static void requireColumn(MemoryGridResultResolver.ResolvedResult result, String name,
                                      boolean join, boolean derived, boolean output) {
        MemoryGridResultResolver.Column column = result.schema().get(name);
        if (column == null) {
            throw RX.throwB(SCHEMA_MISMATCH + ": column is not available: " + name);
        }
        if (join && !column.joinAllowed()) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": column is not join-allowed: " + name);
        }
        if (derived && !column.derivedAllowed()) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": column is not derived-allowed: " + name);
        }
        if (output && !column.outputAllowed()) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": column is not output-allowed: " + name);
        }
    }

    private static void requireAvailableForDerived(MemoryGridResultResolver.ResolvedResult left,
                                                   MemoryGridResultResolver.ResolvedResult right,
                                                   String name) {
        if (containsDerived(left, name) || containsDerived(right, name)) {
            return;
        }
        throw RX.throwB(SCHEMA_MISMATCH + ": derived operand is not available: " + name);
    }

    private static boolean containsDerived(MemoryGridResultResolver.ResolvedResult result, String name) {
        MemoryGridResultResolver.Column column = result.schema().get(name);
        return column != null && column.derivedAllowed();
    }

    private static boolean containsOutput(MemoryGridResultResolver.ResolvedResult result, String name) {
        MemoryGridResultResolver.Column column = result.schema().get(name);
        return column != null && column.outputAllowed();
    }

    private static Map<Object, List<Map<String, Object>>> rowsByKey(List<Map<String, Object>> rows, String joinKey) {
        Map<Object, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.computeIfAbsent(row.get(joinKey), ignored -> new ArrayList<>()).add(row);
        }
        return result;
    }

    private static Map<String, Object> combine(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> combined = new LinkedHashMap<>(left);
        for (Map.Entry<String, Object> entry : right.entrySet()) {
            combined.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return combined;
    }

    private static Map<String, Object> project(Map<String, Object> row, List<String> columns) {
        Map<String, Object> projected = new LinkedHashMap<>();
        for (String column : columns) {
            projected.put(column, row.get(column));
        }
        return projected;
    }

    private static Object evaluate(MemoryGridExecutablePlanner.DerivedFormula formula,
                                   Map<String, Object> row) {
        BigDecimal left = decimalValue(row.get(formula.left()));
        BigDecimal right = decimalValue(row.get(formula.right()));
        if (left == null || right == null) {
            return null;
        }
        return switch (formula.operator()) {
            case "+" -> left.add(right).doubleValue();
            case "-" -> left.subtract(right).doubleValue();
            case "*" -> left.multiply(right).doubleValue();
            case "/" -> BigDecimal.ZERO.compareTo(right) == 0 ? null
                    : left.divide(right, MathContext.DECIMAL64).doubleValue();
            default -> null;
        };
    }

    private static Map<String, Object> summary(Map<String, Object> plan,
                                               MemoryGridExecutablePlanner.BridgePlan bridgePlan,
                                               MemoryGridResultResolver.ResolvedResult left,
                                               MemoryGridResultResolver.ResolvedResult right,
                                               int outputRows,
                                               boolean outputLimited) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("memory_grid_bridge_status", bridgePlan.status());
        summary.put("result_handles", List.of(left.resultHandle(), right.resultHandle()));
        summary.put("input_row_counts", List.of(left.rows().size(), right.rows().size()));
        summary.put("join_type", mapValue(plan.get("join")).get("type"));
        summary.put("join_keys", bridgePlan.joinKeys());
        summary.put("derived", bridgePlan.derived().stream().map(MemoryGridExecutablePlanner.DerivedFormula::name).toList());
        summary.put("output_rows", outputRows);
        summary.put("output_limited", outputLimited);
        return summary;
    }

    private static List<String> metricNames(Map<String, Object> input) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> metric : mapList(input.get("metrics"))) {
            String name = stringValue(metric.get("name"));
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String normalize(Object value) {
        String text = stringValue(value);
        return text == null ? null : text.trim().toUpperCase(Locale.ROOT);
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

    private static BigDecimal decimalValue(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public record ExecutionResult(List<Map<String, Object>> rows, Map<String, Object> summary) {
    }
}
