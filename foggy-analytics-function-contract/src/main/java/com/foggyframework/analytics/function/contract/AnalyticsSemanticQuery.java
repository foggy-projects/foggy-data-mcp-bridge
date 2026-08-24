package com.foggyframework.analytics.function.contract;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deliberately narrow semantic query subset for direct questions.
 *
 * <p>Raw SQL, Compose, scripts, calculated fields, hints and extension data are absent by
 * construction. More advanced signed query contracts can be added without widening v1.</p>
 */
public record AnalyticsSemanticQuery(
        List<String> columns,
        List<Filter> filters,
        List<Group> groupBy,
        List<Order> orderBy,
        int start,
        int limit,
        boolean returnTotal,
        boolean distinct) {

    private static final int MAX_COLUMNS = 64;
    private static final int MAX_FILTERS = 32;
    private static final int MAX_GROUPS = 16;
    private static final int MAX_ORDERS = 16;

    public AnalyticsSemanticQuery {
        columns = textList(columns, "columns", true, MAX_COLUMNS);
        filters = immutable(filters, "filters", MAX_FILTERS);
        groupBy = immutable(groupBy, "groupBy", MAX_GROUPS);
        orderBy = immutable(orderBy, "orderBy", MAX_ORDERS);
        if (start < 0) {
            throw new IllegalArgumentException("start must not be negative");
        }
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
    }

    public record Filter(String field, String operator, Object value) {

        private static final Set<String> OPERATORS = Set.of(
                "=", "!=", ">", ">=", "<", "<=",
                "like", "left_like", "right_like", "in", "not in",
                "[]", "[)", "(]", "()", "is null", "is not null");

        public Filter {
            field = AnalyticsFunctionValues.requireText("filter.field", field);
            requireFieldLength(field);
            operator = AnalyticsFunctionValues.requireText("filter.operator", operator);
            if (!OPERATORS.contains(operator)) {
                throw new IllegalArgumentException("filter.operator is unsupported");
            }
            if (("is null".equals(operator) || "is not null".equals(operator))) {
                value = null;
            } else {
                value = governedValue(
                        operator,
                        AnalyticsFunctionJsonValues.normalizeValue("filter.value", value));
            }
        }

        private static Object governedValue(String operator, Object value) {
            if (Set.of("in", "not in").contains(operator)) {
                if (!(value instanceof List<?> values)
                        || values.isEmpty()
                        || values.size() > 256) {
                    throw new IllegalArgumentException(
                            "filter.value must contain 1 to 256 scalar values");
                }
                values.forEach(Filter::requireScalar);
                return value;
            }
            if (Set.of("[]", "[)", "(]", "()").contains(operator)) {
                if (!(value instanceof List<?> values) || values.size() != 2) {
                    throw new IllegalArgumentException(
                            "filter.value must contain exactly two scalar bounds");
                }
                values.forEach(Filter::requireScalar);
                return value;
            }
            requireScalar(value);
            return value;
        }

        private static void requireScalar(Object value) {
            if (!(value instanceof String
                    || value instanceof Boolean
                    || value instanceof BigInteger
                    || value instanceof BigDecimal)) {
                throw new IllegalArgumentException(
                        "filter.value must use a non-null JSON scalar");
            }
            if (value instanceof String text && text.length() > 4_096) {
                throw new IllegalArgumentException("filter.value string is too long");
            }
        }
    }

    public record Group(String field, String aggregation) {

        private static final Set<String> AGGREGATIONS = Set.of(
                "MAX", "MIN", "SUM", "AVG", "COUNT", "COUNT_DISTINCT",
                "STDDEV_POP", "STDDEV_SAMP", "VAR_POP", "VAR_SAMP", "PK");

        public Group {
            field = AnalyticsFunctionValues.requireText("group.field", field);
            requireFieldLength(field);
            if (aggregation != null) {
                aggregation = AnalyticsFunctionValues.requireText(
                        "group.aggregation", aggregation).toUpperCase();
                if (!AGGREGATIONS.contains(aggregation)) {
                    throw new IllegalArgumentException("group.aggregation is unsupported");
                }
            }
        }
    }

    public record Order(String field, String direction) {

        public Order {
            field = AnalyticsFunctionValues.requireText("order.field", field);
            requireFieldLength(field);
            direction = AnalyticsFunctionValues.requireText(
                    "order.direction", direction).toLowerCase();
            if (!Set.of("asc", "desc").contains(direction)) {
                throw new IllegalArgumentException("order.direction must be asc or desc");
            }
        }
    }

    private static List<String> textList(
            List<String> source,
            String field,
            boolean required,
            int maxSize) {
        if (source == null || source.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException(field + " must not be empty");
            }
            return List.of();
        }
        if (source.size() > maxSize) {
            throw new IllegalArgumentException(field + " contains too many items");
        }
        return source.stream()
                .map(value -> {
                    String text = AnalyticsFunctionValues.requireText(field, value);
                    requireFieldLength(text);
                    return text;
                })
                .toList();
    }

    private static <T> List<T> immutable(List<T> source, String field, int maxSize) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        if (source.size() > maxSize) {
            throw new IllegalArgumentException(field + " contains too many items");
        }
        source.forEach(value -> Objects.requireNonNull(value, field + " item"));
        return List.copyOf(source);
    }

    private static void requireFieldLength(String value) {
        if (value.length() > 256) {
            throw new IllegalArgumentException("semantic query field is too long");
        }
    }
}
