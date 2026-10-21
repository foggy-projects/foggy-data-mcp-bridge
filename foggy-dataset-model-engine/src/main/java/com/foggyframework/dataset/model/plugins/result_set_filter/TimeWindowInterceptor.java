package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.engine.compose.plan.*;
import com.foggyframework.dataset.model.engine.compose.plan.expr.*;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.DbDimension;
import com.foggyframework.dataset.model.spi.DbMeasure;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Order(-22)
public class TimeWindowInterceptor implements DataSetResultStep {
    private java.time.Clock clock;
    @org.springframework.beans.factory.annotation.Value("${foggy.dataset.query.time-zone:UTC}")
    private String timeZone = "UTC";

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setClock(java.time.Clock clock) { this.clock = clock; }

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
        TimeAxisResolver.Axis axis = null;
        if (queryModel != null && queryModel.getJdbcQueryColumns() != null) {
            for (var column : queryModel.getJdbcQueryColumns()) {
                availableFields.add(column.getName());
                if (column.getType() == com.foggyframework.dataset.model.spi.DbColumnType.DAY
                        || column.getType() == com.foggyframework.dataset.model.spi.DbColumnType.DATETIME) {
                    timeFields.add(column.getName());
                }
            }
        }
        if (twDef.isComparative() || twDef.isCumulative() || twDef.isRolling()) {
            try {
                axis = TimeAxisResolver.resolve(queryModel, twDef.field());
            } catch (IllegalArgumentException failure) {
                // Existing observed windows can sort declared textual bucket keys without a
                // calendar. Preserve that contract only when no date arithmetic is requested.
                if (!(twDef.isRolling() && !twDef.isCalendarRolling() && twDef.value().isEmpty()
                        && failure.getMessage().startsWith("TIMEWINDOW_FIELD_NOT_TIME"))) throw failure;
            }
            // The requested surrogate is accepted only after resolving a typed date mapping.
            if (axis != null) timeFields.add(axis.requestedField());
        }

        // Validate
        String errorCode = TimeWindowValidator.validate(twDef, availableFields, timeFields, measureFields);
        if (errorCode != null) {
            throw new com.foggyframework.dataset.model.engine.expression.QueryCalculationException(errorCode, twDef.field(),
                    errorCode.equals("TIMEWINDOW_ROLLING_MODE_INVALID") ? "timeWindow.rollingMode" : "timeWindow",
                    "Invalid time window", "Check the timeWindow field, grain and rollingMode contract.", Map.of());
        }
        if ((twDef.isCumulative() || (twDef.isRolling() && !twDef.isCalendarRolling()))
                && queryModel instanceof com.foggyframework.dataset.model.spi.JdbcQueryModel jdbc
                && !jdbc.getDialect().supportsWindowFunctions()) {
            throw new com.foggyframework.dataset.model.engine.expression.QueryCalculationException(
                    "TIMEWINDOW_WINDOW_UNSUPPORTED", twDef.field(), "timeWindow.comparison",
                    "This database does not support SQL window functions required by cumulative/observed windows.",
                    "Use a database with window support; calendar rolling is available separately and has different semantics.", Map.of());
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
                if (cf != null && cf.getName() != null && cf.getOrigin() == CalculatedFieldDef.Origin.USER_DECLARED) {
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
        
        if (twDef.isComparative() && (twDef.targetMetrics() == null || twDef.targetMetrics().isEmpty())) {
            Set<String> requested = new LinkedHashSet<>(ctx.getRequest().getParam().getColumns());
            if (ctx.getRequest().getParam().getCalculatedFields() != null) {
                for (CalculatedFieldDef cf : ctx.getRequest().getParam().getCalculatedFields())
                    requested.addAll(com.foggyframework.dataset.model.engine.expression.CalculatedFieldService.extractColumnReferences(cf.getExpression()));
            }
            measureFields.removeIf(metric -> requested.stream().noneMatch(name -> name.equals(metric) || name.startsWith(metric + "__")));
            if (measureFields.isEmpty()) throw new IllegalArgumentException("TIMEWINDOW_TARGET_REQUIRED: select an aggregate measure or provide targetMetrics");
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
                .calculatedFields(ctx.getRequest().getParam().getCalculatedFields() == null ? List.of() :
                        ctx.getRequest().getParam().getCalculatedFields().stream()
                                .filter(cf -> cf.getOrigin() != CalculatedFieldDef.Origin.USER_DECLARED).toList())
                .slice(planConditions(ctx.getRequest().getParam().getSlice()))
                .having(planConditions(ctx.getRequest().getParam().getHaving()))
                .build();

        Set<String> actualOutputs = null;
        if (twDef.isRolling() || twDef.isCumulative()) {
            AccumulatingPlanBuilder.Result built = AccumulatingPlanBuilder.build(basePlan, twDef, axis, measureFields,
                    clock != null ? clock : java.time.Clock.system(java.time.ZoneId.of(timeZone)));
            actualOutputs = built.outputs();
            if (!built.warnings().isEmpty()) {
                List<String> warnings = new ArrayList<>();
                if (extData.get("engineWarnings") instanceof List<?> existing) existing.forEach(w -> warnings.add(String.valueOf(w)));
                warnings.addAll(built.warnings());
                extData.put("engineWarnings", warnings);
            }
            extData.put("timeWindowPlan", built.plan());
            extData.put("timeWindowDesc", twDef.comparison() + " " + (twDef.isCalendarRolling() ? "calendar" : "observed"));
            ctx.setSkipQuery(true);

        } else if (twDef.isComparative()) {
            ComparativePlanBuilder.Result built = ComparativePlanBuilder.build(basePlan, twDef, axis, measureFields, clock != null ? clock : java.time.Clock.system(java.time.ZoneId.of(timeZone)));
            QueryPlan compPlan = built.plan();
            actualOutputs = built.outputs();
            if (!built.warnings().isEmpty()) {
                List<String> warnings = new ArrayList<>();
                if (extData.get("engineWarnings") instanceof List<?> existing) existing.forEach(w -> warnings.add(String.valueOf(w)));
                warnings.addAll(built.warnings());
                extData.put("engineWarnings", warnings);
            }
            
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
                            cfd.setWindowOrderBy(List.of(new com.foggyframework.dataset.model.def.query.request.WindowOrderDef()));
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
            Set<String> twOutputCols = actualOutputs != null ? new LinkedHashSet<>(actualOutputs)
                    : TimeWindowExpander.getOutputColumns(twDef, groupByFields, measureFields);

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
        ctx.setExecutionDisposition(ModelResultContext.ExecutionDisposition.DEFERRED);

        // Resolve dialect for downstream plan execution.
        // ctx.getQueryModel() is JdbcQueryModelImpl at runtime, which implements JdbcQueryModel.
        String resolvedDialect = "mysql";
        if (queryModel instanceof com.foggyframework.dataset.model.spi.JdbcQueryModel) {
            com.foggyframework.dataset.db.dialect.FDialect fd =
                    ((com.foggyframework.dataset.model.spi.JdbcQueryModel) queryModel).getDialect();
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
        QueryPlan finalPlan = DerivedQueryPlan.builder()
                .source(timeWindowPlan)
                .columns(request.getColumns() == null ? List.of() : request.getColumns().stream().map(column -> {
                    String[] parts = column.split("(?i)\\s+AS\\s+");
                    return (Object) parts[parts.length - 1].trim();
                }).toList())
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

    private static List<Object> planConditions(List<? extends com.foggyframework.dataset.model.def.query.request.CondRequestDef> conditions) {
        if (conditions == null) return List.of();
        List<Object> result = new ArrayList<>();
        for (var condition : conditions) {
            Map<String, Object> item = new LinkedHashMap<>();
            if (condition._isLogicalGroup()) item.put(condition._isOrGroup() ? "$or" : "$and", planConditions(condition._getGroupChildren()));
            else if (condition.getExpr() != null) item.put("$expr", condition.getExpr());
            else {
                item.put("field", condition.getField());
                item.put("op", condition.getOp());
                item.put("value", condition.getValue());
                if (condition.getMaxDepth() != null) item.put("maxDepth", condition.getMaxDepth());
            }
            result.add(item);
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
