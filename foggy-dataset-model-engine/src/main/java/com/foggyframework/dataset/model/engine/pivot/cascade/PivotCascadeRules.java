package com.foggyframework.dataset.model.engine.pivot.cascade;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.engine.pivot.rollup.MetricAdditivityAnalyzer;
import com.foggyframework.dataset.model.engine.pivot.rollup.RollupMetricPlan;
import com.foggyframework.dataset.model.engine.pivot.rollup.RollupStrategy;
import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.model.semantic.domain.pivot.MetricFilter;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotOptions;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PIVOT-91-C2 cascade Generate classifier and fail-closed validator.
 */
public final class PivotCascadeRules {

    private PivotCascadeRules() {
    }

    public static boolean isCascadeRequest(PivotRequest pivot) {
        if (pivot == null) return false;
        return analyzeAxis(pivot.getRows()).cascade || analyzeAxis(pivot.getColumns()).cascade;
    }

    public static void validateRequestShape(PivotRequest pivot) {
        if (pivot == null || !isCascadeRequest(pivot)) {
            return;
        }

        AxisAnalysis rows = analyzeAxis(pivot.getRows());
        AxisAnalysis columns = analyzeAxis(pivot.getColumns());

        if (hasTreeMode(pivot.getRows()) || hasTreeMode(pivot.getColumns())) {
            throw PivotCascadeException.treeRejected();
        }

        if (columns.cascade) {
            throw PivotCascadeException.crossAxisRejected("Column-axis cascade is not in the C2 v1 whitelist.");
        }
        if (rows.cascade && columns.hasDomainOperation) {
            throw PivotCascadeException.crossAxisRejected("Rows cascade plus column-axis TopN/having is not in the C2 v1 whitelist.");
        }
        if (!rows.cascade) {
            throw PivotCascadeException.scopeUnsupported("Only rows-axis cascade is allowed in C2 v1.");
        }
        if (rows.levelCount != 2) {
            throw PivotCascadeException.scopeUnsupported("Rows cascade must have exactly two levels in C2 v1.");
        }
        if (rows.havingOnlyCascade) {
            throw PivotCascadeException.scopeUnsupported("Multi-level having-only cascade has no accepted oracle in C2 v1.");
        }
        if (!pivot.getParentShareMetrics().isEmpty() || !pivot.getBaselineRatioMetrics().isEmpty()) {
            throw PivotCascadeException.scopeUnsupported("parentShare and baselineRatio cannot be combined with cascade TopN in C2 v1.");
        }

        requireOrderByForLimitedCascadeLevels(pivot.getRows());
    }

    public static void validateAdditivity(PivotRequest pivot,
                                          QueryModel queryModel,
                                          List<CalculatedFieldDef> calculatedFields) {
        if (pivot == null || !isCascadeRequest(pivot) || queryModel == null) {
            return;
        }

        Set<String> metrics = new LinkedHashSet<>(pivot.getSqlMetricNames());
        metrics.addAll(collectAxisMetricRefs(pivot.getRows()));
        metrics.addAll(collectAxisMetricRefs(pivot.getColumns()));

        PivotOptions options = pivot.getOptions();
        boolean totalsEnabled = options != null &&
                (options.isRowSubtotals() || options.isColumnSubtotals() || options.isGrandTotal());
        if (totalsEnabled) {
            metrics.addAll(pivot.getAllOutputMetricNames());
        }

        List<RollupMetricPlan> plans = MetricAdditivityAnalyzer.analyze(
                new ArrayList<>(metrics), queryModel, calculatedFields);
        for (RollupMetricPlan plan : plans) {
            if (plan.getStrategy() == RollupStrategy.AUX_REQUERY ||
                    plan.getStrategy() == RollupStrategy.RECOMPUTE_FROM_BASE ||
                    plan.getStrategy() == RollupStrategy.UNSUPPORTED) {
                throw PivotCascadeException.nonAdditiveRejected(plan.getMetricName());
            }
        }
    }

    private static void requireOrderByForLimitedCascadeLevels(List<AxisField> fields) {
        if (fields == null) return;
        for (AxisField field : fields) {
            if (field.getLimit() != null && field.getLimit() > 0 &&
                    (field.getOrderBy() == null || field.getOrderBy().isEmpty())) {
                throw PivotCascadeException.orderByRequired(field.getField(), field.getLimit());
            }
        }
    }

    private static boolean hasTreeMode(List<AxisField> fields) {
        if (fields == null) return false;
        for (AxisField field : fields) {
            if (field.isTreeMode()) return true;
        }
        return false;
    }

    private static Set<String> collectAxisMetricRefs(List<AxisField> fields) {
        if (fields == null) return Collections.emptySet();
        Set<String> metrics = new LinkedHashSet<>();
        for (AxisField field : fields) {
            if (field.getOrderBy() != null) {
                for (String orderBy : field.getOrderBy()) {
                    if (orderBy == null || orderBy.isBlank()) continue;
                    metrics.add(orderBy.charAt(0) == '-' ? orderBy.substring(1) : orderBy);
                }
            }
            if (field.getHaving() != null) {
                for (MetricFilter filter : field.getHaving()) {
                    if (filter.getMetric() != null && !filter.getMetric().isBlank()) {
                        metrics.add(filter.getMetric());
                    }
                }
            }
        }
        return metrics;
    }

    private static AxisAnalysis analyzeAxis(List<AxisField> fields) {
        AxisAnalysis result = new AxisAnalysis();
        result.levelCount = fields == null ? 0 : fields.size();
        if (fields == null || fields.isEmpty()) {
            return result;
        }

        int operationCount = 0;
        boolean nonLeafOperation = false;
        for (int i = 0; i < fields.size(); i++) {
            AxisField field = fields.get(i);
            boolean hasLimit = field.getLimit() != null && field.getLimit() > 0;
            boolean hasHaving = field.getHaving() != null && !field.getHaving().isEmpty();
            boolean hasOperation = hasLimit || hasHaving;
            if (!hasOperation) continue;

            operationCount++;
            result.hasDomainOperation = true;
            result.hasLimit = result.hasLimit || hasLimit;
            result.hasHaving = result.hasHaving || hasHaving;
            if (i < fields.size() - 1) {
                nonLeafOperation = true;
            }
        }

        result.cascade = fields.size() > 1 && (operationCount >= 2 || nonLeafOperation);
        result.havingOnlyCascade = result.cascade && result.hasHaving && !result.hasLimit;
        return result;
    }

    private static class AxisAnalysis {
        private int levelCount;
        private boolean hasDomainOperation;
        private boolean hasLimit;
        private boolean hasHaving;
        private boolean cascade;
        private boolean havingOnlyCascade;
    }
}
