package com.foggyframework.dataset.db.model.memorygrid.bridge;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridResultResolver;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
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
    public static final String RESULT_HANDLE_EXPIRED = "MEMORY_GRID_RESULT_HANDLE_EXPIRED";
    public static final String NAMESPACE_MISMATCH = "MEMORY_GRID_RESULT_NAMESPACE_MISMATCH";
    public static final String SOURCE_ROUTE_MISMATCH = "MEMORY_GRID_RESULT_SOURCE_ROUTE_MISMATCH";
    public static final String SCHEMA_MISMATCH = "MEMORY_GRID_RESULT_SCHEMA_MISMATCH";
    public static final String SCHEMA_DRIFT = "MEMORY_GRID_RESULT_SCHEMA_DRIFT";
    public static final String AUTH_REPLAY_MISMATCH = "MEMORY_GRID_RESULT_AUTH_REPLAY_MISMATCH";
    public static final String GOVERNANCE_MISMATCH = "MEMORY_GRID_RESULT_GOVERNANCE_MISMATCH";
    public static final String STORAGE_UNAVAILABLE = "MEMORY_GRID_RESULT_STORAGE_UNAVAILABLE";

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
        validateResolvedInput(leftInput, left, joinKey, context);
        validateResolvedInput(rightInput, right, joinKey, context);
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
                                              String joinKey,
                                              SemanticRequestContext context) {
        String handle = stringValue(input.get("result_handle"));
        if (!handle.equals(result.resultHandle())) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": resolver returned mismatched result_handle.");
        }
        validateMetadata(input, result, context);
        String expectedRoute = normalize(input.get("source_route"));
        String actualRoute = normalize(result.sourceRoute());
        if (expectedRoute == null || expectedRoute.isBlank()) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": input source_route is missing for " + handle + ".");
        }
        if (actualRoute != null && !expectedRoute.equals(actualRoute)) {
            throw RX.throwB(SOURCE_ROUTE_MISMATCH + ": resolver returned mismatched source_route for " + handle + ".");
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

    private static void validateMetadata(Map<String, Object> input,
                                         MemoryGridResultResolver.ResolvedResult result,
                                         SemanticRequestContext context) {
        MemoryGridResultResolver.ResultHandleMetadata metadata = result.metadata();
        if (metadata == null) {
            return;
        }
        String handle = stringValue(input.get("result_handle"));
        if (metadata.handleId() == null || !metadata.handleId().equals(result.resultHandle())) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": resolver metadata handle_id mismatch for " + handle + ".");
        }
        if (metadata.expiresAt() != null && metadata.expiresAt().isBefore(Instant.now())) {
            throw RX.throwB(RESULT_HANDLE_EXPIRED + ": " + handle);
        }
        if (metadata.invalidatedAt() != null) {
            throw RX.throwB(RESULT_HANDLE_EXPIRED + ": " + handle + " is invalidated.");
        }
        String requestNamespace = normalizeNamespace(context == null ? null : context.getNamespace());
        String resultNamespace = normalizeNamespace(firstNonBlank(metadata.namespace(), result.namespace()));
        if (!namespacesMatch(resultNamespace, requestNamespace)) {
            throw RX.throwB(NAMESPACE_MISMATCH + ": resolver namespace does not match request namespace for " + handle + ".");
        }
        String expectedRoute = normalize(input.get("source_route"));
        String metadataRoute = normalize(firstNonBlank(metadata.sourceRoute(), result.sourceRoute()));
        if (expectedRoute != null && metadataRoute != null && !expectedRoute.equals(metadataRoute)) {
            throw RX.throwB(SOURCE_ROUTE_MISMATCH + ": resolver metadata source_route mismatch for " + handle + ".");
        }
        if (metadata.rowCount() >= 0 && metadata.rowCount() != result.rows().size()) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": resolver metadata row_count mismatch for " + handle + ".");
        }
        if (metadata.rowLimit() >= 0 && result.rows().size() > metadata.rowLimit()) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": resolver rows exceed metadata row_limit for " + handle + ".");
        }
        if (metadata.cellCount() >= 0 && metadata.cellCount() != cellCount(result.rows())) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": resolver metadata cell_count mismatch for " + handle + ".");
        }
        if (metadata.maxReadCount() >= 0 && metadata.readCount() > metadata.maxReadCount()) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": resolver read_count exceeds max_read_count for " + handle + ".");
        }
        if (metadata.storageRef() == null || metadata.storageRef().isBlank()) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": resolver metadata storage_ref is missing for " + handle + ".");
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
        if (column.sensitive() && (derived || output)) {
            throw RX.throwB(GOVERNANCE_MISMATCH + ": sensitive column cannot be used in Memory Grid output or derived expression: " + name);
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
        return column != null && column.derivedAllowed() && !column.sensitive();
    }

    private static boolean containsOutput(MemoryGridResultResolver.ResolvedResult result, String name) {
        MemoryGridResultResolver.Column column = result.schema().get(name);
        return column != null && column.outputAllowed() && !column.sensitive();
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
        summary.put("resolver_audit", List.of(audit(left), audit(right)));
        return summary;
    }

    private static Map<String, Object> audit(MemoryGridResultResolver.ResolvedResult result) {
        return MemoryGridAuditExposurePolicy.externalSafe().expose(result.metadata(), result);
    }

    private static int cellCount(List<Map<String, Object>> rows) {
        int count = 0;
        for (Map<String, Object> row : rows) {
            count += row == null ? 0 : row.size();
        }
        return count;
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

    private static String normalizeNamespace(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean namespacesMatch(String resultNamespace, String requestNamespace) {
        return resultNamespace == null ? requestNamespace == null : resultNamespace.equals(requestNamespace);
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
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
