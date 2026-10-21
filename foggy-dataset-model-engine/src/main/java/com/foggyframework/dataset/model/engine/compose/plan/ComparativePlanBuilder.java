package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.engine.compose.plan.expr.*;
import java.time.Clock;
import java.util.*;

/** Two separately governed aggregate branches joined by a complete calendar identity. */
public final class ComparativePlanBuilder {
    private static final String KEY = "__tw_period_key";
    private static final String START = "__tw_period_start";
    private ComparativePlanBuilder() {}
    public record Result(QueryPlan plan, Set<String> outputs, List<String> warnings) {}

    public static Result build(BaseModelPlan input, TimeWindowDef tw, TimeAxisResolver.Axis axis,
                               Set<String> measures, Clock clock) {
        List<String> metrics = tw.targetMetrics() == null || tw.targetMetrics().isEmpty()
                ? input.columns().stream().filter(String.class::isInstance).map(String.class::cast).filter(measures::contains).distinct().toList() : tw.targetMetrics();
        List<String> business = input.groupBy().stream().filter(f -> !axis.contains(f)).toList();
        List<String> timeGroups = input.groupBy().stream().filter(axis::contains).toList();
        // The internal calendar key must not silently split an explicitly ambiguous public grain.
        if (axis.dimension() != null && (timeGroups.contains(axis.dimension() + "$month")
                || timeGroups.contains(axis.dimension() + "$quarter"))
                && !timeGroups.contains(axis.dimension() + "$year")) {
            throw new IllegalArgumentException("TIMEWINDOW_PERIOD_IDENTITY_INCOMPLETE: include year with month/quarter");
        }
        if ("week".equals(tw.grain()) && timeGroups.stream().anyMatch(f -> f.endsWith("$year")
                || f.endsWith("$month") || f.endsWith("$quarter"))) {
            throw new IllegalArgumentException("TIMEWINDOW_GRAIN_INCOMPATIBLE: natural year/month groups split ISO weeks; use the date axis");
        }
        if (axis.dimension() != null) {
            Set<String> allowed = switch (tw.grain()) {
                case "month" -> Set.of("year", "month");
                case "quarter" -> Set.of("year", "quarter");
                case "year" -> Set.of("year");
                case "week" -> Set.of();
                default -> Set.of("year", "month", "day");
            };
            for (String group : timeGroups) {
                if (group.equals(axis.requestedField()) || group.equals(axis.dateField())) continue;
                String property = group.substring(axis.dimension().length() + 1);
                if (!allowed.contains(property)) throw new IllegalArgumentException(
                        "TIMEWINDOW_GRAIN_INCOMPATIBLE: time group '" + group + "' is not a supported " + tw.grain() + " period identity; use the typed date axis");
            }
        }
        List<Object> columns = new ArrayList<>();
        List<String> publicNames = new ArrayList<>();
        boolean showStart = timeGroups.isEmpty();
        for (Object col : input.columns()) {
            if (!(col instanceof String name)) throw new IllegalArgumentException("TIMEWINDOW_COLUMN_UNSUPPORTED");
            if (name.equals(axis.requestedField()) || name.equals(axis.dateField())) {
                showStart = true;
                publicNames.add(name);
            } else {
                columns.add(name);
                publicNames.add(alias(name));
            }
        }
        if (showStart && publicNames.stream().noneMatch(n -> n.equals(axis.requestedField()) || n.equals(axis.dateField()))) {
            publicNames.add(axis.requestedField());
        }
        for (String dimension : business) if (!columns.contains(dimension)) columns.add(dimension);
        if (publicNames.contains(KEY) || publicNames.contains(START)
                || fieldsUseReservedNames(input.calculatedFields())) throw new IllegalArgumentException("TIMEWINDOW_RESERVED_ALIAS: internal period aliases are reserved");
        columns.add(KEY);
        if (showStart) columns.add(START);
        List<String> groups = new ArrayList<>(input.groupBy());
        groups.remove(axis.requestedField());
        groups.remove(axis.dateField());
        groups.add(KEY);
        if (showStart) groups.add(START);
        List<CalculatedFieldDef> fields = new ArrayList<>(input.calculatedFields());
        fields.add(field(KEY, "period_key(" + axis.dateField() + ", '" + tw.grain() + "', '" + tw.comparison() + "')"));
        if (showStart) fields.add(field(START, "period_start(" + axis.dateField() + ", '" + tw.grain() + "')"));
        var ranges = ComparativePeriodResolver.resolve(tw, clock);
        BaseModelPlan current = base(input, columns, groups, fields, ranges.current(), axis.dateField());
        BaseModelPlan prior = base(input, columns, groups, fields, ranges.prior(), axis.dateField());
        List<Object> priorColumns = new ArrayList<>(business);
        priorColumns.add(new ProjectedColumn(new BinaryExpr(new ColumnExpr(KEY), "+", new LiteralExpr(offset(tw))), KEY, null));
        for (String metric : metrics) priorColumns.add(metric + " AS " + metric + "__prior");
        QueryPlan shifted = DerivedQueryPlan.builder().source(prior).columns(priorColumns).build();
        List<JoinOn> conditions = new ArrayList<>();
        conditions.add(JoinOn.of(KEY, "=", KEY));
        for (String dimension : business) conditions.add(JoinOn.builder().left(dimension).right(dimension).op("=").nullSafe(true).build());
        QueryPlan joined = JoinPlan.builder().left(current).right(shifted).type(JoinType.LEFT).on(conditions).build();
        List<Object> result = new ArrayList<>();
        Set<String> outputs = new LinkedHashSet<>();
        for (String name : publicNames) {
            if (!outputs.add(name)) continue;
            result.add(name.equals(axis.requestedField()) || name.equals(axis.dateField())
                    ? new ProjectedColumn(new ColumnExpr(START), name, null) : name);
        }
        for (String metric : metrics) {
            if (outputs.add(metric)) result.add(metric);
            String previous = metric + "__prior";
            result.add(previous);
            BinaryExpr diff = new BinaryExpr(new ColumnExpr(metric), "-", new ColumnExpr(previous));
            result.add(new ProjectedColumn(diff, metric + "__diff", null));
            // Force decimal division on SQLite and integer-valued measures too.
            result.add(new ProjectedColumn(new BinaryExpr(new BinaryExpr(LiteralExpr.approximate(1.0), "*", diff),
                    "/", new ColumnExpr(previous)), metric + "__ratio", null));
            outputs.addAll(List.of(previous, metric + "__diff", metric + "__ratio"));
        }
        return new Result(DerivedQueryPlan.builder().source(joined).columns(result).build(), Collections.unmodifiableSet(outputs),
                clippingWarnings(input.slice(), ranges.prior(), axis.dateField()));
    }

    private static boolean fieldsUseReservedNames(List<CalculatedFieldDef> fields) {
        return fields.stream().anyMatch(f -> KEY.equals(f.getName()) || START.equals(f.getName()));
    }

    private static int offset(TimeWindowDef tw) {
        if ("mom".equals(tw.comparison())) return 1;
        if ("wow".equals(tw.comparison())) return 7;
        return switch (tw.grain()) { case "year" -> 1; case "quarter" -> 4; case "month" -> 12; case "week" -> 100;
            default -> throw new IllegalArgumentException("TIMEWINDOW_GRAIN_INCOMPATIBLE"); };
    }

    private static CalculatedFieldDef field(String name, String expression) {
        CalculatedFieldDef field = new CalculatedFieldDef();
        field.setName(name);
        field.setExpression(expression);
        field.setOrigin(CalculatedFieldDef.Origin.TIME_WINDOW_INTERNAL);
        return field;
    }

    private static BaseModelPlan base(BaseModelPlan input, List<Object> columns, List<String> groups,
                                      List<CalculatedFieldDef> fields, List<ComparativePeriodResolver.Range> ranges, String date) {
        List<Object> slice = new ArrayList<>(input.slice());
        List<Object> predicates = new ArrayList<>();
        for (var range : ranges) predicates.add(Map.of("field", date, "op", "[)",
                // Calendar bounds are JDBC DATE values, not instants parsed in the JVM zone.
                // A DATETIME comparison promotes them to midnight in the source calendar.
                "value", List.of(java.sql.Date.valueOf(range.start()), java.sql.Date.valueOf(range.endExclusive()))));
        if (predicates.size() == 1) slice.add(predicates.get(0));
        else if (!predicates.isEmpty()) slice.add(Map.of("$or", predicates));
        List<CalculatedFieldDef> copiedFields = fields.stream().map(original -> {
            CalculatedFieldDef copy = new CalculatedFieldDef();
            org.springframework.beans.BeanUtils.copyProperties(original, copy, "compiledExp");
            return copy;
        }).toList();
        return BaseModelPlan.builder().model(input.model()).columns(columns).groupBy(groups)
                .slice(slice).having(input.having()).calculatedFields(copiedFields).distinct(input.distinct()).build();
    }

    private static List<String> clippingWarnings(List<Object> slices, List<ComparativePeriodResolver.Range> prior, String date) {
        for (Object entry : slices) {
            if (!(entry instanceof Map<?, ?> map) || !date.equals(map.get("field"))
                    || !(map.get("value") instanceof List<?> bounds) || bounds.size() != 2
                    || !("[)".equals(map.get("op")) || "[]".equals(map.get("op")))) continue;
            try {
                var low = java.time.LocalDate.parse(String.valueOf(bounds.get(0)));
                var high = java.time.LocalDate.parse(String.valueOf(bounds.get(1)));
                if ("[]".equals(map.get("op"))) high = high.plusDays(1);
                for (var range : prior) {
                    if (range.start().isBefore(low) || range.endExclusive().isAfter(high)) {
                        return List.of("TIMEWINDOW_PRIOR_CLIPPED_BY_SLICE: source slice restricts the comparison range; source filters are preserved");
                    }
                }
            } catch (java.time.format.DateTimeParseException ignored) {
                // Only diagnose a provable literal date range. No data/permission probing.
            }
        }
        return List.of();
    }

    private static String alias(String column) {
        String[] parts = column.split("(?i)\\s+AS\\s+");
        return parts[parts.length - 1].trim();
    }
}
