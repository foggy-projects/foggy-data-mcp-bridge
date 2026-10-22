package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.engine.compose.plan.expr.*;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

/** Governed source -> grain aggregation -> window -> display range. No hidden dependency is public. */
public final class AccumulatingPlanBuilder {
    private static final String KEY = "__tw_bucket_key";
    private static final String START = "__tw_bucket_start";
    private static final String RESET = "__tw_reset_key";
    private static final String LOW = "__tw_window_low";
    private AccumulatingPlanBuilder() {}
    public record Result(QueryPlan plan, Set<String> outputs, List<String> warnings) {}

    public static Result build(BaseModelPlan input, TimeWindowDef tw, TimeAxisResolver.Axis axis,
                               Set<String> measures, Clock clock) {
        return build(input, tw, axis, measures, clock, false);
    }

    public static Result build(BaseModelPlan input, TimeWindowDef tw, TimeAxisResolver.Axis axis,
                               Set<String> measures, Clock clock, boolean numericRange) {
        if (axis == null) return legacyObserved(input, tw, measures);
        List<String> business = input.groupBy().stream().filter(f -> !axis.contains(f)).toList();
        List<String> metrics = tw.targetMetrics() == null || tw.targetMetrics().isEmpty()
                ? input.columns().stream().filter(String.class::isInstance).map(String.class::cast)
                    .filter(measures::contains).distinct().toList() : tw.targetMetrics();
        List<Object> columns = new ArrayList<>();
        List<Object> publicColumns = new ArrayList<>();
        Set<String> outputs = new LinkedHashSet<>();
        List<String> groups = new ArrayList<>(input.groupBy());
        List<CalculatedFieldDef> fields = new ArrayList<>(input.calculatedFields());
        for (CalculatedFieldDef field : fields) reserved(field.getName());
        for (Object col : input.columns()) {
            if (!(col instanceof String text)) throw invalid("COLUMN_UNSUPPORTED", "expected model field");
            String[] parts = text.split("(?i)\\s+AS\\s+");
            String name = parts[0].trim(), alias = parts[parts.length - 1].trim();
            reserved(name); reserved(alias);
            // Preserve a daily surrogate key's public identity; typed dates are normalized to the grain.
            boolean timeProjection = name.equals(axis.dateField()) || (name.equals(axis.requestedField())
                    && (!"day".equals(tw.grain()) || axis.dateField().equals(axis.requestedField())));
            if (timeProjection) {
                groups.remove(name);
                publicColumns.add(new ProjectedColumn(new ColumnExpr(START), alias, null));
            } else if (axis.dimension() != null && axis.contains(name)
                    && Set.of("year", "month", "quarter", "day").contains(name.substring(axis.dimension().length() + 1))) {
                String property = name.substring(axis.dimension().length() + 1);
                String internal = "__tw_time_" + property;
                if (!columns.contains(internal)) {
                    columns.add(internal);
                    fields.add(field(internal, property + "(" + axis.dateField() + ")"));
                }
                if (groups.remove(name)) groups.add(internal);
                publicColumns.add(new ProjectedColumn(new ColumnExpr(internal), alias, null));
            } else {
                columns.add(text);
                publicColumns.add(alias);
            }
            outputs.add(alias);
        }
        // Reject finer time groups instead of silently splitting a requested monthly/quarterly bucket.
        Set<String> allowed = switch (tw.grain()) {
            case "month" -> Set.of("year", "month");
            case "quarter" -> Set.of("year", "quarter");
            case "week" -> Set.of();
            default -> Set.of("year", "month", "day");
        };
        for (String group : input.groupBy()) {
            if (!axis.contains(group) || group.equals(axis.requestedField()) || group.equals(axis.dateField())) continue;
            String property = axis.dimension() == null ? group : group.substring(axis.dimension().length() + 1);
            if (!allowed.contains(property)) throw invalid("GRAIN_INCOMPATIBLE", "time group " + group + " splits " + tw.grain());
        }
        if (axis.dimension() != null && !input.groupBy().contains(axis.dimension() + "$year")
                && (input.groupBy().contains(axis.dimension() + "$month") || input.groupBy().contains(axis.dimension() + "$quarter"))
                && !"day".equals(tw.grain())) throw invalid("PERIOD_IDENTITY_INCOMPLETE", "include year with month/quarter");
        // A week crossing Jan 1 cannot have one natural-year reset identity.
        if (tw.isCumulative() && "week".equals(tw.grain()))
            throw invalid("GRAIN_INCOMPATIBLE", "weekly YTD crosses natural-year boundaries; use day/month/quarter");
        for (String group : business) if (!columns.contains(group)) columns.add(group);
        for (String internal : List.of(KEY, START)) { columns.add(internal); groups.add(internal); }
        fields.add(field(KEY, "period_key(" + axis.dateField() + ", '" + tw.grain() + "', '" + tw.comparison() + "')"));
        fields.add(field(START, "period_start(" + axis.dateField() + ", '" + tw.grain() + "')"));
        if (tw.isCumulative()) {
            columns.add(RESET); groups.add(RESET);
            fields.add(field(RESET, "period_key(" + axis.dateField() + ", '"
                    + ("ytd".equals(tw.comparison()) ? "year" : "month") + "', '" + tw.comparison() + "')"));
        }
        List<Object> sourceSlice = new ArrayList<>(input.slice());
        List<Object> displaySlice = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (tw.isRolling() && tw.rollingMode() == null)
            warnings.add("TIMEWINDOW_OBSERVED_DEFAULT: rolling uses N observed buckets; set rollingMode=calendar for N civil days or observed to retain bucket semantics");
        if (!tw.value().isEmpty()) {
            LocalDate today = LocalDate.now(clock);
            LocalDate start = ComparativePeriodResolver.date(tw.value().get(0), today);
            LocalDate end = ComparativePeriodResolver.date(tw.value().get(1), today);
            if ("[]".equals(tw.range())) end = end.plusDays(1);
            if (end.isBefore(start)) throw invalid("RANGE_INVALID", "end precedes start");
            if (!ComparativePeriodResolver.floor(start, tw.grain()).equals(start)
                    || !ComparativePeriodResolver.floor(end, tw.grain()).equals(end))
                throw invalid("PARTIAL_PERIOD_UNSUPPORTED", "value must align to complete " + tw.grain() + " buckets");
            displaySlice.add(Map.of("field", KEY, "op", ">=", "value", periodNumber(start, tw.grain())));
            displaySlice.add(Map.of("field", KEY, "op", "<", "value", periodNumber(end, tw.grain())));
            LocalDate history = tw.isCumulative() ? ("ytd".equals(tw.comparison()) ? start.withDayOfYear(1) : start.withDayOfMonth(1))
                    : tw.isCalendarRolling() ? start.minusDays(tw.rollingWindowSize() - 1L) : null;
            if (history != null) sourceSlice.add(range(axis.dateField(), history, end));
            // Observed buckets may be arbitrarily far apart. Never approximate N buckets with N days.
            else sourceSlice.add(Map.of("field", axis.dateField(), "op", "<", "value", java.sql.Date.valueOf(end)));
            if (!input.slice().isEmpty()) warnings.add("TIMEWINDOW_HISTORY_SOURCE_FILTERED: explicit slice remains a hard constraint on window history");
        }
        BaseModelPlan base = BaseModelPlan.builder().model(input.model()).columns(columns).groupBy(groups)
                .slice(sourceSlice).having(input.having()).calculatedFields(fields).distinct(input.distinct()).build();
        QueryPlan window;
        List<Object> windowColumns = columns.stream().map(c -> c instanceof String s ? alias(s) : c).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        String agg = tw.rollingAggregator() == null ? "SUM" : tw.rollingAggregator().toUpperCase(Locale.ROOT);
        if (tw.isCalendarRolling() && numericRange) {
            WindowFrame frame = new WindowFrame(WindowFrame.Unit.RANGE,
                    new WindowFrame.Bound.Preceding(tw.rollingWindowSize() - 1), new WindowFrame.Bound.CurrentRow());
            for (String metric : metrics) {
                var value = new WindowColumn(agg, new PlanColumnRef(base, metric), List.of(), new OverClause(business, List.of(KEY), frame));
                // Range joins never match a NULL date. Preserve their NULL/COUNT=0 contract;
                // native RANGE would otherwise aggregate the NULL peer group.
                var result = new CaseWhenExpr(List.of(new CaseWhenExpr.WhenThen(
                        new BinaryExpr(new ColumnExpr(KEY), "=", new ColumnExpr(KEY)), value)),
                        new LiteralExpr("COUNT".equals(agg) ? 0 : null));
                windowColumns.add(new ProjectedColumn(result, metric + "__" + tw.comparison(), null));
            }
            window = DerivedQueryPlan.builder().source(base).columns(windowColumns).build();
        } else if (tw.isCalendarRolling()) {
            // A bounded self join works on SQL Server too (which disallows numeric RANGE offsets).
            List<Object> leftCols = new ArrayList<>(windowColumns);
            leftCols.add(new ProjectedColumn(new BinaryExpr(new ColumnExpr(KEY), "-", new LiteralExpr(tw.rollingWindowSize() - 1)), LOW, null));
            QueryPlan left = DerivedQueryPlan.builder().source(base).columns(leftCols).build();
            List<Object> rightCols = new ArrayList<>();
            rightCols.add(KEY + " AS __tw_history_key");
            for (int i = 0; i < business.size(); i++) rightCols.add(business.get(i) + " AS __tw_history_dim_" + i);
            for (int i = 0; i < metrics.size(); i++) rightCols.add(metrics.get(i) + " AS __tw_history_metric_" + i);
            QueryPlan right = DerivedQueryPlan.builder().source(base).columns(rightCols).build();
            List<JoinOn> on = new ArrayList<>(List.of(JoinOn.of(KEY, ">=", "__tw_history_key"), JoinOn.of(LOW, "<=", "__tw_history_key")));
            for (int i = 0; i < business.size(); i++) on.add(JoinOn.builder().left(business.get(i)).right("__tw_history_dim_" + i).op("=").nullSafe(true).build());
            QueryPlan joined = JoinPlan.builder().left(left).right(right).type(JoinType.LEFT).on(on).build();
            List<String> groupKeys = windowColumns.stream().map(String.class::cast).toList();
            for (int i = 0; i < metrics.size(); i++) windowColumns.add(new AggregateColumn(new PlanColumnRef(joined, "__tw_history_metric_" + i), agg).as(metrics.get(i) + "__" + tw.comparison()));
            window = DerivedQueryPlan.builder().source(joined).columns(windowColumns).groupBy(groupKeys).build();
        } else {
            List<String> partition = new ArrayList<>(business);
            if (tw.isCumulative()) partition.add(RESET);
            WindowFrame frame = tw.isCumulative() ? WindowFrame.cumulativeRows() : WindowFrame.rollingRows(tw.rollingWindowSize());
            for (String metric : metrics) windowColumns.add(new ProjectedColumn(new WindowColumn(agg,
                    new PlanColumnRef(base, metric), List.of(), new OverClause(partition, List.of(KEY), frame)), metric + "__" + tw.comparison(), null));
            window = DerivedQueryPlan.builder().source(base).columns(windowColumns).build();
        }
        for (String metric : metrics) { String name = metric + "__" + tw.comparison(); publicColumns.add(name); outputs.add(name); }
        return new Result(DerivedQueryPlan.builder().source(window).columns(publicColumns).slice(displaySlice).build(),
                Collections.unmodifiableSet(outputs), List.copyOf(warnings));
    }

    private static Map<String,Object> range(String field, LocalDate start, LocalDate end) {
        return Map.of("field", field, "op", "[)", "value", List.of(java.sql.Date.valueOf(start), java.sql.Date.valueOf(end)));
    }
    private static Result legacyObserved(BaseModelPlan input, TimeWindowDef tw, Set<String> measures) {
        if (!tw.isRolling() || tw.isCalendarRolling() || !tw.value().isEmpty())
            throw invalid("FIELD_NOT_TIME", "calendar arithmetic requires a typed date axis");
        var expansion = TimeWindowExpander.expandRolling(tw, input, input.groupBy(), measures);
        List<Object> columns = new ArrayList<>(input.columns()); columns.addAll(expansion.additionalColumns());
        Set<String> outputs = new LinkedHashSet<>();
        input.columns().forEach(c -> outputs.add(alias((String)c)));
        expansion.additionalColumns().forEach(c -> outputs.add(c.alias()));
        return new Result(DerivedQueryPlan.builder().source(input).columns(columns).build(), outputs,
                tw.rollingMode() == null ? List.of("TIMEWINDOW_OBSERVED_DEFAULT: rolling uses N observed buckets; calendar mode requires a typed date axis") : List.of());
    }
    private static long periodNumber(LocalDate date, String grain) {
        return switch (grain) {
            case "month" -> date.getYear() * 12L + date.getMonthValue() - 1;
            case "quarter" -> date.getYear() * 4L + (date.getMonthValue() - 1) / 3;
            case "year" -> date.getYear();
            default -> date.toEpochDay();
        };
    }
    private static CalculatedFieldDef field(String name, String expression) {
        CalculatedFieldDef f = new CalculatedFieldDef(); f.setName(name); f.setExpression(expression);
        f.setOrigin(CalculatedFieldDef.Origin.TIME_WINDOW_INTERNAL); return f;
    }
    private static String alias(String field) { String[] parts = field.split("(?i)\\s+AS\\s+"); return parts[parts.length - 1].trim(); }
    private static void reserved(String field) { if (field != null && field.startsWith("__tw_")) throw invalid("RESERVED_ALIAS", field); }
    private static IllegalArgumentException invalid(String code, String detail) {
        return new com.foggyframework.dataset.model.engine.expression.QueryCalculationException("TIMEWINDOW_" + code, null,
                code.contains("PERIOD") || code.contains("RANGE") ? "timeWindow.value" : "timeWindow.grain", detail,
                "Keep the requested grain; use complete bucket boundaries and an authorized typed date axis.", Map.of());
    }
}
