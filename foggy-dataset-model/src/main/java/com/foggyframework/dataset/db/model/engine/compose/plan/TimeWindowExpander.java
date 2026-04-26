package com.foggyframework.dataset.db.model.engine.compose.plan;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.engine.compose.plan.expr.*;
import com.foggyframework.dataset.db.model.spi.DbMeasure;

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

        List<String> metrics = resolveMetrics(tw, measureFields);
        String aggFn = tw.rollingAggregator() != null ? tw.rollingAggregator().toUpperCase() : "SUM";

        List<ProjectedColumn> additionalCols = new ArrayList<>();
        for (String metric : metrics) {
            WindowColumn wc = new WindowColumn(
                    aggFn,
                    new PlanColumnRef(plan, metric),
                    List.of(),
                    new OverClause(groupByFields, List.of(tw.field()), frame)
            );

            additionalCols.add(new ProjectedColumn(wc, metric + "__" + tw.comparison(), null));
        }

        return new ExpansionResult(
                additionalCols,
                tw.field(),
                groupByFields,
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

        List<String> windowPartition = new ArrayList<>(groupByFields);
        if ("ytd".equalsIgnoreCase(tw.comparison())) {
            // YTD resets every year, so partition by the year of the time field
            windowPartition.add("YEAR(" + tw.field() + ")");
        } else if ("mtd".equalsIgnoreCase(tw.comparison())) {
            // MTD resets every month, so partition by the year/month of the time field
            windowPartition.add("YEAR(" + tw.field() + ")");
            windowPartition.add("MONTH(" + tw.field() + ")");
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
                groupByFields,
                frame,
                desc);
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

        int offset;
        RelativeDateParser.OffsetUnit unit;
        String shiftField;
        String grainKeyField;

        String baseField = tw.field();
        if (baseField.endsWith("$id")) {
            baseField = baseField.substring(0, baseField.length() - 3);
        }

        if ("yoy".equalsIgnoreCase(tw.comparison())) {
            offset = -1;
            unit = RelativeDateParser.OffsetUnit.YEAR;
            shiftField = baseField + "$year";
            if ("month".equalsIgnoreCase(tw.grain())) {
                grainKeyField = baseField + "$month";
            } else if ("quarter".equalsIgnoreCase(tw.grain())) {
                grainKeyField = baseField + "$quarter";
            } else {
                grainKeyField = baseField + "$dayOfYear";
            }
        } else if ("mom".equalsIgnoreCase(tw.comparison())) {
            offset = -1;
            unit = RelativeDateParser.OffsetUnit.MONTH;
            shiftField = baseField + "$month";
            grainKeyField = baseField + "$id";
        } else if ("wow".equalsIgnoreCase(tw.comparison())) {
            offset = -1;
            unit = RelativeDateParser.OffsetUnit.WEEK;
            shiftField = baseField + "$week";
            grainKeyField = baseField + "$dayOfWeek";
        } else {
            throw new IllegalArgumentException("Unsupported comparative mode: " + tw.comparison());
        }

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

    public static QueryPlan buildComparativePlan(
            BaseModelPlan basePlan,
            ComparativeExpansionResult compResult,
            TimeWindowDef tw) {
        // Deduplicate: exclude dims that overlap with shiftField or grainKeyField
        // (e.g. groupBy may contain salesDate$year which is also the shiftField)
        List<String> dims = new ArrayList<>();
        for (String d : compResult.dimensionFields()) {
            if (!d.equals(compResult.shiftField()) && !d.equals(compResult.grainKeyField())) {
                dims.add(d);
            }
        }

        // 1. Base Derived Plan (Current Period)
        List<String> baseCols = new ArrayList<>();
        // Include all dimension fields for join
        for (String dim : dims) {
            baseCols.add(dim);
        }
        baseCols.add(compResult.shiftField());
        baseCols.add(compResult.grainKeyField());
        for (ComparativeColumn c : compResult.projectedColumns()) {
            baseCols.add(c.currentAlias() + " AS " + c.currentAlias());
        }
        
        DerivedQueryPlan baseDerived = DerivedQueryPlan.builder()
                .source(basePlan)
                .columns(baseCols)
                .build();
                
        // 2. Prior Derived Plan (Prior Period)
        List<String> priorCols = new ArrayList<>();
        // Include dimension fields for join (prior side)
        for (String dim : dims) {
            priorCols.add(dim);
        }
        priorCols.add(compResult.grainKeyField());
        for (ComparativeColumn c : compResult.projectedColumns()) {
            priorCols.add(c.currentAlias() + " AS " + c.priorAlias());
        }
        
        DerivedQueryPlan priorDerived = DerivedQueryPlan.builder()
                .source(basePlan)
                .columns(priorCols)
                .build();

        // 3. Join Plan — ON must cover ALL dimension fields + grain key
        List<JoinOn> joinConditions = new ArrayList<>();
        for (String dim : dims) {
            joinConditions.add(JoinOn.builder()
                    .left(dim).op("=").right(dim)
                    .build());
        }
        joinConditions.add(JoinOn.builder()
                .left(compResult.grainKeyField())
                .op("=")
                .right(compResult.grainKeyField())
                .build());

        JoinPlan joinPlan = JoinPlan.builder()
                .left(baseDerived)
                .right(priorDerived)
                .type(JoinType.LEFT)
                .on(joinConditions)
                .build();
                
        // 4. Outer Projection (Calculates Diff and Ratio)
        List<Object> finalCols = new ArrayList<>();
        for (String dim : dims) {
            finalCols.add(dim);
        }
        finalCols.add(compResult.shiftField());
        finalCols.add(compResult.grainKeyField());
        for (ComparativeColumn c : compResult.projectedColumns()) {
            finalCols.add(c.currentAlias());
            finalCols.add(c.priorAlias());
            
            ColumnExpr currExpr = new ColumnExpr(c.currentAlias());
            ColumnExpr priorExpr = new ColumnExpr(c.priorAlias());
            
            // Diff: current - prior
            BinaryExpr diffExpr = new BinaryExpr(currExpr, "-", priorExpr);
            finalCols.add(new ProjectedColumn(diffExpr, c.diffAlias(), null));
            
            // Ratio: CASE WHEN prior IS NULL OR prior = 0 THEN NULL ELSE (current - prior) / prior END
            BinaryExpr priorIsNull = new BinaryExpr(priorExpr, "IS", new LiteralExpr(null));
            BinaryExpr priorIsZero = new BinaryExpr(priorExpr, "=", new LiteralExpr(0));
            BinaryExpr isNullOrZero = new BinaryExpr(priorIsNull, "OR", priorIsZero);
            
            BinaryExpr ratioMath = new BinaryExpr(new BinaryExpr(currExpr, "-", priorExpr), "/", priorExpr);
            
            CaseWhenExpr ratioExpr = new CaseWhenExpr(
                    List.of(new CaseWhenExpr.WhenThen(isNullOrZero, new LiteralExpr(null))),
                    ratioMath
            );
            finalCols.add(new ProjectedColumn(ratioExpr, c.ratioAlias(), null));
        }

        return DerivedQueryPlan.builder()
                .source(joinPlan)
                .columns(finalCols)
                .build();
    }
}
