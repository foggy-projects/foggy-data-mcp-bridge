package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.dataset.model.engine.compose.plan.expr.*;
import com.foggyframework.dataset.model.engine.compose.relation.ReferencePolicy;
import com.foggyframework.dataset.model.engine.compose.relation.SemanticKind;
import com.foggyframework.dataset.model.engine.compose.schema.ColumnSpec;
import com.foggyframework.dataset.model.engine.compose.schema.OutputSchema;

import java.util.*;

/**
 * Expands a {@link TimeWindowDef} into concrete QueryPlan AST nodes.
 * <p>
 * This is the core "DSL -> AST" transformer. For each comparison mode,
 * it generates the appropriate WindowFrame and aggregate projections.
 */
public class TimeWindowExpander {

    public record ExpansionResult(
            List<ProjectedColumn> additionalColumns,
            String orderByField,
            List<String> partitionByFields,
            WindowFrame frame,
            String description
    ) {}

    public record ComparativeColumn(String currentAlias, String priorAlias, String diffAlias, String ratioAlias) {}

    public record ComparativeExpansionResult(
            int periodOffset,
            RelativeDateParser.OffsetUnit offsetUnit,
            String grainKeyField,
            String shiftField,
            List<String> dimensionFields,
            List<String> metrics,
            List<ComparativeColumn> projectedColumns
    ) {}

    private static List<String> resolveMetrics(TimeWindowDef tw, Set<String> availableMeasures) {
        if (tw.targetMetrics() == null || tw.targetMetrics().isEmpty()) {
            return new ArrayList<>(availableMeasures);
        }
        return tw.targetMetrics();
    }

    // ---- Rolling mode expansion ----

    public static ExpansionResult expandRolling(
            TimeWindowDef tw,
            BaseModelPlan plan,
            List<String> groupByFields,
            Set<String> measureFields) {

        if (!tw.isRolling()) {
            throw new IllegalArgumentException("Not a rolling time window: " + tw.comparison());
        }

        int nRows = tw.rollingWindowSize();

        WindowFrame frame = WindowFrame.rollingRows(nRows);
        String desc = tw.comparison() + " window logic via OVER(ROWS BETWEEN " + (nRows - 1) + " PRECEDING AND CURRENT ROW)";
        List<String> partitionByFields = nonTimeGroupByFields(tw, groupByFields);

        List<String> metrics = resolveMetrics(tw, measureFields);
        String aggFn = tw.rollingAggregator() != null ? tw.rollingAggregator().toUpperCase() : "SUM";

        List<ProjectedColumn> additionalCols = new ArrayList<>();
        for (String metric : metrics) {
            WindowColumn wc = new WindowColumn(
                    aggFn,
                    new PlanColumnRef(plan, metric),
                    List.of(),
                    new OverClause(partitionByFields, List.of(tw.field()), frame)
            );

            additionalCols.add(new ProjectedColumn(wc, metric + "__" + tw.comparison(), null));
        }

        return new ExpansionResult(
                additionalCols,
                tw.field(),
                partitionByFields,
                frame,
                desc);
    }

    // ---- Cumulative mode expansion ----

    public static ExpansionResult expandCumulative(
            TimeWindowDef tw,
            BaseModelPlan plan,
            List<String> groupByFields,
            Set<String> measureFields) {

        if (!tw.isCumulative()) {
            throw new IllegalArgumentException("Not a cumulative time window: " + tw.comparison());
        }

        WindowFrame frame = WindowFrame.cumulativeRows();
        String desc = tw.comparison() + " window logic via OVER(ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)";

        List<String> metrics = resolveMetrics(tw, measureFields);
        String aggFn = tw.rollingAggregator() != null ? tw.rollingAggregator().toUpperCase() : "SUM";

        List<String> windowPartition = nonTimeGroupByFields(tw, groupByFields);
        String baseField = baseTimeField(tw.field());
        if ("ytd".equalsIgnoreCase(tw.comparison())) {
            // YTD resets every year, so partition by the year of the time field
            windowPartition.add(baseField + "$year");
        } else if ("mtd".equalsIgnoreCase(tw.comparison())) {
            // MTD resets every month, so partition by the year/month of the time field
            windowPartition.add(baseField + "$year");
            windowPartition.add(baseField + "$month");
        }

        List<ProjectedColumn> additionalCols = new ArrayList<>();
        for (String metric : metrics) {
            WindowColumn wc = new WindowColumn(
                    aggFn,
                    new PlanColumnRef(plan, metric),
                    List.of(),
                    new OverClause(windowPartition, List.of(tw.field()), frame)
            );

            additionalCols.add(new ProjectedColumn(wc, metric + "__" + tw.comparison(), null));
        }

        return new ExpansionResult(
                additionalCols,
                tw.field(),
                windowPartition,
                frame,
                desc);
    }

    private static List<String> nonTimeGroupByFields(TimeWindowDef tw, List<String> groupByFields) {
        if (groupByFields == null || groupByFields.isEmpty()) {
            return List.of();
        }
        String baseField = baseTimeField(tw.field());
        List<String> result = new ArrayList<>();
        for (String field : groupByFields) {
            if (!field.equals(baseField) && !field.startsWith(baseField + "$")) {
                result.add(field);
            }
        }
        return result;
    }

    private static String baseTimeField(String field) {
        if (field == null) {
            return "";
        }
        int idx = field.lastIndexOf('$');
        return idx >= 0 ? field.substring(0, idx) : field;
    }

    // ---- Comparative mode expansion ----

    /**
     * Expand a comparative time window (yoy/mom/wow) into an intermediate result.
     *
     * @param tw             the parsed TimeWindowDef
     * @param measureFields  available measure field names
     * @param dimensionFields non-time, non-measure dimension fields from the query
     *                       (e.g. product$category, customer$city). These are
     *                       included in the JOIN ON to prevent cross-dimension inflation.
     */
    public static ComparativeExpansionResult expandComparative(
            TimeWindowDef tw,
            Set<String> measureFields,
            List<String> dimensionFields) {

        if (!tw.isComparative()) {
            throw new IllegalArgumentException("Not a comparative time window: " + tw.comparison());
        }

        // Compatibility description only. Runtime periods and ranges have one implementation in ComparativePeriodResolver.
        int offset = -1;
        RelativeDateParser.OffsetUnit unit = switch (tw.comparison()) {
            case "yoy" -> RelativeDateParser.OffsetUnit.YEAR;
            case "mom" -> RelativeDateParser.OffsetUnit.MONTH;
            case "wow" -> RelativeDateParser.OffsetUnit.WEEK;
            default -> throw new IllegalArgumentException("Unsupported comparative mode: " + tw.comparison());
        };
        String shiftField = "__tw_period_key";
        String grainKeyField = "__tw_period_key";

        List<String> metrics = resolveMetrics(tw, measureFields);
        List<ComparativeColumn> projectedCols = new ArrayList<>();
        for (String metric : metrics) {
            projectedCols.add(new ComparativeColumn(
                    metric,
                    metric + "__prior",
                    metric + "__diff",
                    metric + "__ratio"
            ));
        }

        return new ComparativeExpansionResult(
                offset,
                unit,
                grainKeyField,
                shiftField,
                dimensionFields != null ? dimensionFields : List.of(),
                metrics,
                projectedCols
        );
    }

    /** Compatibility adapter; runtime callers resolve the typed model axis before building. */
    @Deprecated
    public static QueryPlan buildComparativePlan(BaseModelPlan basePlan,
            ComparativeExpansionResult result, TimeWindowDef window) {
        String dimension = window.field().contains("$")
                ? window.field().substring(0, window.field().lastIndexOf('$')) : null;
        return ComparativePlanBuilder.build(basePlan, window,
                new TimeAxisResolver.Axis(window.field(), window.field(), dimension),
                new LinkedHashSet<>(result.metrics()), java.time.Clock.systemUTC()).plan();
    }

    /**
     * Compute the full set of column names available in the timeWindow output.
     * <p>
     * This includes original dimension columns, original metrics, and all derived
     * columns generated by the timeWindow expansion (e.g. __prior, __diff, __ratio,
     * __rolling_*, __ytd, __mtd).
     * <p>
     * Used by {@link TimeWindowValidator#validateCalculatedFieldInteraction} to
     * check that post calculatedFields only reference valid columns.
     *
     * @param tw              the time window definition
     * @param dimensionFields non-time dimension fields from the query
     * @param measureFields   available measure field names
     * @return set of all output column names
     * @since 8.5.0.beta
     */
    public static Set<String> getOutputColumns(
            TimeWindowDef tw,
            List<String> dimensionFields,
            Set<String> measureFields) {

        Set<String> output = new LinkedHashSet<>();

        // Add all dimension fields
        if (dimensionFields != null) {
            output.addAll(dimensionFields);
        }

        // Resolve targeted metrics
        List<String> metrics;
        if (tw.targetMetrics() == null || tw.targetMetrics().isEmpty()) {
            metrics = new ArrayList<>(measureFields);
        } else {
            metrics = tw.targetMetrics();
        }

        // Add original metrics
        output.addAll(metrics);

        // Add derived columns based on comparison mode
        if (tw.isComparative()) {
            for (String metric : metrics) {
                output.add(metric + "__prior");
                output.add(metric + "__diff");
                output.add(metric + "__ratio");
            }
        } else if (tw.isRolling()) {
            for (String metric : metrics) {
                output.add(metric + "__" + tw.comparison());
            }
        } else if (tw.isCumulative()) {
            for (String metric : metrics) {
                output.add(metric + "__" + tw.comparison());
            }
        }

        return output;
    }

    /**
     * Produce an {@link OutputSchema} with full S7a semantic metadata for
     * timeWindow output columns.
     *
     * <p>This parallels {@link #getOutputColumns(TimeWindowDef, List, Set)}
     * but returns {@link ColumnSpec} objects with {@code semanticKind},
     * {@code referencePolicy}, {@code valueMeaning}, and {@code lineage}
     * populated per the S7a contract.</p>
     *
     * @since 8.5.0.beta (S7a)
     */
    public static OutputSchema getOutputSchema(
            TimeWindowDef tw,
            List<String> dimensionFields,
            Set<String> measureFields) {

        List<ColumnSpec> specs = new ArrayList<>();

        // 1. Dimension fields
        if (dimensionFields != null) {
            for (String dim : dimensionFields) {
                specs.add(ColumnSpec.builder()
                        .name(dim).expression(dim)
                        .semanticKind(SemanticKind.BASE_FIELD)
                        .referencePolicy(ReferencePolicy.DIMENSION_DEFAULT)
                        .build());
            }
        }

        // Resolve targeted metrics
        List<String> metrics;
        if (tw.targetMetrics() == null || tw.targetMetrics().isEmpty()) {
            metrics = new ArrayList<>(measureFields);
        } else {
            metrics = tw.targetMetrics();
        }

        // 2. Original metrics
        for (String metric : metrics) {
            specs.add(ColumnSpec.builder()
                    .name(metric).expression(metric)
                    .semanticKind(SemanticKind.AGGREGATE_MEASURE)
                    .referencePolicy(ReferencePolicy.MEASURE_DEFAULT)
                    .build());
        }

        // 3. Derived columns based on comparison mode
        if (tw.isComparative()) {
            for (String metric : metrics) {
                // __prior
                specs.add(ColumnSpec.builder()
                        .name(metric + "__prior").expression(metric + "__prior")
                        .semanticKind(SemanticKind.TIME_WINDOW_DERIVED)
                        .referencePolicy(ReferencePolicy.TIME_WINDOW_DERIVED_DEFAULT)
                        .valueMeaning("prior period " + metric)
                        .lineage(Set.of(metric))
                        .build());
                // __diff
                specs.add(ColumnSpec.builder()
                        .name(metric + "__diff").expression(metric + "__diff")
                        .semanticKind(SemanticKind.TIME_WINDOW_DERIVED)
                        .referencePolicy(ReferencePolicy.TIME_WINDOW_DERIVED_DEFAULT)
                        .valueMeaning("current minus prior " + metric)
                        .lineage(Set.of(metric))
                        .build());
                // __ratio — NOT aggregatable
                specs.add(ColumnSpec.builder()
                        .name(metric + "__ratio").expression(metric + "__ratio")
                        .semanticKind(SemanticKind.TIME_WINDOW_DERIVED)
                        .referencePolicy(ReferencePolicy.TIME_WINDOW_DERIVED_DEFAULT)
                        .valueMeaning("current relative to prior " + metric)
                        .lineage(Set.of(metric))
                        .build());
            }
        } else if (tw.isRolling()) {
            for (String metric : metrics) {
                String derivedName = metric + "__" + tw.comparison();
                specs.add(ColumnSpec.builder()
                        .name(derivedName).expression(derivedName)
                        .semanticKind(SemanticKind.TIME_WINDOW_DERIVED)
                        .referencePolicy(ReferencePolicy.TIME_WINDOW_DERIVED_DEFAULT)
                        .valueMeaning("rolling " + tw.comparison() + " of " + metric)
                        .lineage(Set.of(metric))
                        .build());
            }
        } else if (tw.isCumulative()) {
            for (String metric : metrics) {
                String derivedName = metric + "__" + tw.comparison();
                specs.add(ColumnSpec.builder()
                        .name(derivedName).expression(derivedName)
                        .semanticKind(SemanticKind.TIME_WINDOW_DERIVED)
                        .referencePolicy(ReferencePolicy.TIME_WINDOW_DERIVED_DEFAULT)
                        .valueMeaning("cumulative " + tw.comparison() + " of " + metric)
                        .lineage(Set.of(metric))
                        .build());
            }
        }

        return OutputSchema.of(specs);
    }
}
