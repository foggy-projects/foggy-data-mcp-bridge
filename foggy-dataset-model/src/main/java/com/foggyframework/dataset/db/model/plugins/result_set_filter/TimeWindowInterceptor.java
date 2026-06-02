package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.engine.compose.plan.*;
import com.foggyframework.dataset.db.model.engine.compose.plan.expr.*;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.DbMeasure;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Order(-22)
public class TimeWindowInterceptor implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        if (ctx.getQueryType() != ModelResultContext.QueryType.SEMANTIC) {
            return 0;
        }

        Map<String, Object> extData = ctx.getExtData();
        if (extData == null || !extData.containsKey("timeWindow")) {
            return 0;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> twMap = (Map<String, Object>) extData.get("timeWindow");
        if (twMap == null || twMap.isEmpty()) {
            return 0;
        }

        TimeWindowDef twDef = TimeWindowDef.fromMap(twMap);
        QueryModel queryModel = ctx.getQueryModel();

        // Collect available fields for validation
        Set<String> availableFields = new HashSet<>();
        Set<String> timeFields = new HashSet<>();
        Set<String> measureFields = new HashSet<>();

        if (queryModel != null && queryModel.getJdbcModel() != null) {
            if (queryModel.getJdbcModel().getDimensions() != null) {
                for (DbDimension dim : queryModel.getJdbcModel().getDimensions()) {
                    String effName = dim.getEffectiveName();
                    availableFields.add(effName + "$id");
                    availableFields.add(effName + "$caption");
                    // Add dimension properties (e.g. salesDate$year, salesDate$month)
                    // so grain field strict validation can check for them.
                    // Note: getVisibleSelectColumns() returns names already in
                    // "dimName$propName" format, so we add them directly.
                    if (dim.getVisibleSelectColumns() != null) {
                        for (DbColumn col : dim.getVisibleSelectColumns()) {
                            if (col.getName() != null) {
                                availableFields.add(col.getName());
                            }
                        }
                    }
                    if ("business_date".equals(dim.getTimeRole())) {
                        timeFields.add(effName + "$id");
                        timeFields.add(effName + "$caption");
                        // Also add raw dimension name as time field since some requests might use it
                        timeFields.add(effName);
                    }
                }
            }
            if (queryModel.getJdbcModel().getMeasures() != null) {
                for (DbMeasure m : queryModel.getJdbcModel().getMeasures()) {
                    availableFields.add(m.getName());
                    measureFields.add(m.getName());
                }
            }
        }
        // Always add the field itself to availableFields as fallback
        availableFields.add(twDef.field());
        timeFields.add(twDef.field());

        // Validate
        String errorCode = TimeWindowValidator.validate(twDef, availableFields, timeFields, measureFields);
        if (errorCode != null) {
            throw new IllegalArgumentException("Invalid time window: " + errorCode);
        }

        // Get group by fields from DbQueryRequestDef
        List<String> groupByFields = new ArrayList<>();
        if (ctx.getRequest() != null && ctx.getRequest().getParam() != null && ctx.getRequest().getParam().getGroupBy() != null) {
            groupByFields = ctx.getRequest().getParam().getGroupBy().stream()
                    .map(g -> g.getField())
                    .collect(Collectors.toList());
        }

        // Determine original query columns excluding dynamically generated ones
        // and request-level calculatedField output names (those are projected in
        // the outer DerivedQueryPlan wrapper, not the inner BaseModelPlan).
        Set<String> calcFieldOutputNames = new HashSet<>();
        if (ctx.getRequest() != null && ctx.getRequest().getParam() != null
                && ctx.getRequest().getParam().getCalculatedFields() != null) {
            for (var cf : ctx.getRequest().getParam().getCalculatedFields()) {
                if (cf != null && cf.getName() != null) {
                    calcFieldOutputNames.add(cf.getName());
                }
            }
        }

        List<String> originalColumns = new ArrayList<>();
        if (ctx.getRequest() != null && ctx.getRequest().getParam() != null && ctx.getRequest().getParam().getColumns() != null) {
            for (String col : ctx.getRequest().getParam().getColumns()) {
                if (!isGeneratedTimeWindowColumn(col) && !calcFieldOutputNames.contains(col)) {
                    originalColumns.add(col);
                }
            }
        }
        
        for (String metric : targetMetrics(twDef, measureFields)) {
            if (!originalColumns.contains(metric)) {
                originalColumns.add(metric);
            }
        }

        BaseModelPlan basePlan = BaseModelPlan.builder()
                .model(ctx.getRequest() != null && ctx.getRequest().getParam() != null ? ctx.getRequest().getParam().getQueryModel() : "unknown")
                .columns(originalColumns)
                .groupBy(groupByFields)
                .slice(ctx.getRequest() != null && ctx.getRequest().getParam() != null && ctx.getRequest().getParam().getSlice() != null ? new ArrayList<>(ctx.getRequest().getParam().getSlice()) : List.of())
                .build();

        if (twDef.isRolling() || twDef.isCumulative()) {
            TimeWindowExpander.ExpansionResult expResult;
            if (twDef.isRolling()) {
                expResult = TimeWindowExpander.expandRolling(twDef, basePlan, groupByFields, measureFields);
            } else {
                expResult = TimeWindowExpander.expandCumulative(twDef, basePlan, groupByFields, measureFields);
            }

            List<Object> finalColumns = new ArrayList<>(originalColumns);
            for (ProjectedColumn pc : expResult.additionalColumns()) {
                finalColumns.add(pc);
            }
            QueryPlan timeWindowPlan = DerivedQueryPlan.builder()
                    .source(basePlan)
                    .columns(finalColumns)
                    .build();

            extData.put("timeWindowPlan", timeWindowPlan);
            extData.put("timeWindowDesc", expResult.description());
            ctx.setSkipQuery(true);

        } else if (twDef.isComparative()) {
            TimeWindowExpander.ComparativeExpansionResult compResult = TimeWindowExpander.expandComparative(twDef, measureFields, groupByFields);
            QueryPlan compPlan = TimeWindowExpander.buildComparativePlan(basePlan, compResult, twDef);
            
            extData.put("comparativePlan", compPlan);
            extData.put("timeWindowPlan", compPlan);
            ctx.setSkipQuery(true);
        }

        // Metadata markers for downstream components
        extData.put("derivedFromTimeWindow", true);
        extData.put("timeWindowMode", twDef.comparison());

        // ---- calculatedFields interaction validation (8.5.0 contract) ----
        List<CalculatedFieldDef> calcFields = null;
        Set<String> calcFieldNames = Set.of();
        if (ctx.getRequest() != null && ctx.getRequest().getParam() != null) {
            // SemanticQueryRequest carries calculatedFields in extData
            Object calcFieldsObj = extData.get("calculatedFields");
            if (calcFieldsObj instanceof List<?> cfList && !cfList.isEmpty()) {
                calcFields = new ArrayList<>();
                Set<String> names = new LinkedHashSet<>();
                for (Object cf : cfList) {
                    if (cf instanceof CalculatedFieldDef cfd) {
                        calcFields.add(cfd);
                        if (cfd.getName() != null) names.add(cfd.getName());
                    } else if (cf instanceof Map<?, ?> cfMap) {
                        // JSON-deserialized as Map, convert
                        CalculatedFieldDef cfd = new CalculatedFieldDef();
                        cfd.setName((String) cfMap.get("name"));
                        cfd.setExpression((String) cfMap.get("expression"));
                        cfd.setAgg((String) cfMap.get("agg"));
                        Object pb = cfMap.get("partitionBy");
                        if (pb instanceof List<?> pbList) {
                            cfd.setPartitionBy(pbList.stream().map(String::valueOf).collect(Collectors.toList()));
                        }
                        Object wob = cfMap.get("windowOrderBy");
                        if (wob instanceof List<?>) {
                            // Simplified: if windowOrderBy exists at all, flag it
                            cfd.setWindowOrderBy(List.of(new com.foggyframework.dataset.db.model.def.query.request.WindowOrderDef()));
                        }
                        cfd.setWindowFrame((String) cfMap.get("windowFrame"));
                        calcFields.add(cfd);
                        if (cfd.getName() != null) names.add(cfd.getName());
                    }
                }
                calcFieldNames = names;
            }
        }

        if (!calcFieldNames.isEmpty()) {
            // Compute timeWindow output columns for validation
            Set<String> twOutputCols = TimeWindowExpander.getOutputColumns(
                    twDef, groupByFields, measureFields);
            // Also add any original columns from the request
            twOutputCols.addAll(originalColumns);

            String calcError = TimeWindowValidator.validateCalculatedFieldInteraction(
                    twDef, calcFieldNames, calcFields, twOutputCols);
            if (calcError != null) {
                throw new IllegalArgumentException(
                        "Invalid timeWindow + calculatedFields interaction: " + calcError);
            }

            // Wrap post calc fields as outer DerivedQueryPlan projection
            QueryPlan timeWindowPlan = (QueryPlan) extData.get("timeWindowPlan");
            if (timeWindowPlan != null && calcFields != null && !calcFields.isEmpty()) {
                List<Object> outerCols = new ArrayList<>();
                // Pass through all timeWindow output columns
                for (String col : twOutputCols) {
                    outerCols.add(col);
                }
                // Add post calc field projections
                for (CalculatedFieldDef cf : calcFields) {
                    // Parse expression and create a ProjectedColumn with raw expression
                    outerCols.add(new ProjectedColumn(
                            new RawExpr(cf.getExpression()),
                            cf.getName(),
                            cf.getCaption()));
                }
                QueryPlan outerPlan = DerivedQueryPlan.builder()
                        .source(timeWindowPlan)
                        .columns(outerCols)
                        .build();
                extData.put("timeWindowPlan", outerPlan);
                if (extData.containsKey("comparativePlan")) {
                    extData.put("comparativePlan", outerPlan);
                }
                // Mark that post calc fields have been handled by timeWindow pipeline
                extData.put("timeWindowPostCalcFieldsHandled", true);
            }
        }

        applyFinalControls(ctx, extData);

        // Resolve dialect for downstream plan execution.
        // ctx.getQueryModel() is JdbcQueryModelImpl at runtime, which implements JdbcQueryModel.
        String resolvedDialect = "mysql";
        if (queryModel instanceof com.foggyframework.dataset.db.model.spi.JdbcQueryModel) {
            com.foggyframework.dataset.db.dialect.FDialect fd =
                    ((com.foggyframework.dataset.db.model.spi.JdbcQueryModel) queryModel).getDialect();
            if (fd != null && fd.getProductName() != null) {
                String pn = fd.getProductName().toLowerCase(java.util.Locale.ROOT);
                if (pn.contains("postgres")) resolvedDialect = "postgres";
                else if (pn.contains("sqlite")) resolvedDialect = "sqlite";
                else if (pn.contains("sqlserver") || pn.contains("microsoft")) resolvedDialect = "mssql";
            }
        }
        extData.put("timeWindowDialect", resolvedDialect);
        
        return 0;
    }

    private static void applyFinalControls(ModelResultContext ctx, Map<String, Object> extData) {
        QueryPlan timeWindowPlan = (QueryPlan) extData.get("timeWindowPlan");
        if (timeWindowPlan == null || ctx.getRequest() == null || ctx.getRequest().getParam() == null) {
            return;
        }

        DbQueryRequestDef request = ctx.getRequest().getParam();
        List<String> orderBy = toPlanOrderBy(request.getOrderBy());
        Integer limit = extData.get("timeWindowLimit") instanceof Number n ? n.intValue() : null;
        Integer start = extData.get("timeWindowStart") instanceof Number n ? n.intValue() : null;
        if (orderBy.isEmpty() && limit == null && start == null) {
            return;
        }

        QueryPlan finalPlan = DerivedQueryPlan.builder()
                .source(timeWindowPlan)
                .columns(List.of())
                .orderBy(orderBy)
                .limit(limit)
                .start(start)
                .build();
        extData.put("timeWindowPlan", finalPlan);
        if (extData.containsKey("comparativePlan")) {
            extData.put("comparativePlan", finalPlan);
        }
    }

    private static List<String> toPlanOrderBy(List<OrderRequestDef> requestOrderBy) {
        if (requestOrderBy == null || requestOrderBy.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (OrderRequestDef item : requestOrderBy) {
            if (item == null || item.getField() == null || item.getField().isBlank()) {
                continue;
            }
            String dir = item.getDir() == null ? "asc" : item.getDir().trim().toLowerCase(Locale.ROOT);
            result.add("desc".equals(dir) ? "-" + item.getField() : item.getField());
        }
        return result;
    }

    private static boolean isGeneratedTimeWindowColumn(String col) {
        return col.endsWith("__prior")
                || col.endsWith("__diff")
                || col.endsWith("__ratio")
                || col.endsWith("__growth")
                || col.endsWith("__ytd")
                || col.endsWith("__mtd")
                || col.contains("__rolling_");
    }

    private static Collection<String> targetMetrics(TimeWindowDef twDef, Set<String> measureFields) {
        if (twDef.targetMetrics() == null || twDef.targetMetrics().isEmpty()) {
            return measureFields;
        }
        return twDef.targetMetrics();
    }
}
