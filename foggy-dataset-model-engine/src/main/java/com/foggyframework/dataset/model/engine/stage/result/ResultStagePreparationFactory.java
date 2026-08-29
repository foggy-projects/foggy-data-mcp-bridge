package com.foggyframework.dataset.model.engine.stage.result;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.PostAggregateCalculationDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.engine.stage.QueryStagePlan;
import com.foggyframework.dataset.model.engine.stage.QueryStageType;
import com.foggyframework.dataset.model.engine.total.AggregateStateColumnFactory;
import com.foggyframework.dataset.model.engine.total.TotalDataAggregatePlan;
import com.foggyframework.dataset.model.impl.query.DbQueryOrderColumnImpl;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.dataset.model.spi.DbQueryColumn;
import com.foggyframework.dataset.model.spi.support.AggregationDbColumn;
import com.foggyframework.dataset.model.spi.support.CalculatedDbColumn;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prepares the immutable graph and source projections shared by MAIN and TOTAL modes.
 */
public final class ResultStagePreparationFactory {
    private final AggregateStateColumnFactory stateColumnFactory;

    public ResultStagePreparationFactory(AggregateStateColumnFactory stateColumnFactory) {
        this.stateColumnFactory = stateColumnFactory;
    }

    public ResultStagePreparation build(
            DbQueryRequestDef queryRequest,
            JdbcQuery jdbcQuery,
            QueryStagePlan stagePlan,
            TotalDataAggregatePlan totalPlan,
            List<SliceRequestDef> postAggregateSlice,
            IdentityHashMap<DbColumn, DbColumn> aggregateSourceColumns,
            FDialect dialect,
            WindowDependencyResolver windowDependencyResolver) {
        if (!stagePlan.requiresPostAggregateRenderer()
                && !stagePlan.requiresWindowResultRenderer()) {
            return null;
        }
        List<DbColumn> originalColumns =
                new ArrayList<>(jdbcQuery.getSelect().getColumns());
        List<DbColumn> mainBaseColumns;
        ResultStagePlan.Graph graph;
        String hiddenLastConsumerStageId = null;

        if (stagePlan.requiresPostAggregateRenderer()) {
            mainBaseColumns = new ArrayList<>(originalColumns);
            graph = preparePostAggregateResultStageGraph(
                    stagePlan, queryRequest, originalColumns,
                    postAggregateSlice, dialect);
        } else {
            List<WindowColumnInfo> windowColumns = identifyWindowColumns(originalColumns);
            if (windowColumns.isEmpty()) {
                throw new IllegalArgumentException(
                        "Window result-stage plan has no window output column");
            }
            mainBaseColumns = new ArrayList<>();
            for (DbColumn column : originalColumns) {
                if (!isWindowResultColumn(column, windowColumns)) {
                    mainBaseColumns.add(column);
                }
            }
            for (WindowColumnInfo window : windowColumns) {
                CalculatedDbColumn calculated = sourceCalculatedColumn(
                        window.column, aggregateSourceColumns);
                if (calculated == null || calculated.getReferencedColumns() == null) {
                    continue;
                }
                for (DbQueryColumn reference : calculated.getReferencedColumns()) {
                    if (!containsProjectionAlias(mainBaseColumns, reference)) {
                        mainBaseColumns.add(windowDependencyResolver.resolve(reference));
                    }
                }
            }
            graph = prepareWindowResultStageGraph(
                    stagePlan, queryRequest, originalColumns,
                    windowColumns, jdbcQuery.getOrder(), dialect);
            hiddenLastConsumerStageId = stageId(
                    stagePlan, QueryStageType.WINDOW_RESULT_STAGE);
        }

        Set<String> publicBaseAliases = originalColumns.stream()
                .filter(column -> graph.stages().stream()
                        .flatMap(stage -> stage.computedColumns().stream())
                        .noneMatch(computed -> computed.alias().equals(column.getAlias())))
                .map(ResultStagePreparationFactory::projectionAlias)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<DbColumn> totalBaseColumns = new ArrayList<>(mainBaseColumns);
        List<Object> stateExpressionValues = new ArrayList<>();
        if (totalPlan.getStatus() == TotalDataAggregatePlan.LoweringStatus.LOWERED) {
            for (TotalDataAggregatePlan.AggregateStateSpec state : totalPlan.getStates()) {
                stateColumnFactory.append(totalBaseColumns, state, stateExpressionValues);
            }
        }

        ResultStagePreparation.BaseProjection mainProjection = buildPreparedBaseProjection(
                graph, mainBaseColumns, publicBaseAliases,
                hiddenLastConsumerStageId, List.of(), dialect);
        ResultStagePreparation.BaseProjection totalProjection = buildPreparedBaseProjection(
                graph, totalBaseColumns, publicBaseAliases,
                hiddenLastConsumerStageId, stateExpressionValues, dialect);
        return new ResultStagePreparation(
                graph,
                new ResultStagePreparation.BaseProjectionPlan(
                        mainProjection, totalProjection));
    }

    private ResultStagePreparation.BaseProjection buildPreparedBaseProjection(
            ResultStagePlan.Graph graph,
            List<DbColumn> sourceColumns,
            Set<String> publicAliases,
            String hiddenLastConsumerStageId,
            List<Object> expressionValues,
            FDialect dialect) {
        String producerStageId = lastBaseStageId(graph);
        String finalStageId = stageId(graph.diagnostics(), QueryStageType.FINAL_STAGE);
        List<ResultStagePreparation.Projection> projections = new ArrayList<>();
        for (DbColumn source : sourceColumns) {
            String alias = projectionAlias(source);
            boolean aggregateState = alias.toLowerCase(Locale.ROOT).startsWith("__foggy_");
            boolean publicResult = publicAliases.contains(alias);
            ResultStagePlan.ColumnRole role = aggregateState
                    ? ResultStagePlan.ColumnRole.INTERNAL_AGGREGATE_STATE
                    : publicResult
                    ? ResultStagePlan.ColumnRole.PUBLIC_RESULT
                    : ResultStagePlan.ColumnRole.HIDDEN_DEPENDENCY;
            String lastConsumerStageId =
                    role == ResultStagePlan.ColumnRole.HIDDEN_DEPENDENCY
                            && hiddenLastConsumerStageId != null
                            ? hiddenLastConsumerStageId : finalStageId;
            String lineage = StringUtils.isNotEmpty(source.getAlias())
                    ? source.getAlias() : alias;
            ResultStagePlan.Column column = new ResultStagePlan.Column(
                    alias, role, producerStageId, lastConsumerStageId,
                    source.getType(), lineage,
                    BoundSqlExpression.of(dialect.quoteIdentifier(alias)));
            projections.add(new ResultStagePreparation.Projection(source, column));
        }
        return new ResultStagePreparation.BaseProjection(projections, expressionValues);
    }

    private ResultStagePlan.Graph prepareWindowResultStageGraph(
            QueryStagePlan stagePlan,
            DbQueryRequestDef queryRequest,
            List<DbColumn> originalSelectCols,
            List<WindowColumnInfo> windowColumns,
            JdbcQuery.JdbcOrder savedOrder,
            FDialect dialect) {
        Set<String> availableAliases = new LinkedHashSet<>();
        for (DbColumn column : originalSelectCols) {
            availableAliases.add(resultOutputAlias(column, windowColumns));
        }

        List<ResultStagePlan.Column> computedWindowColumns = new ArrayList<>();
        for (WindowColumnInfo window : windowColumns) {
            String alias = window.column.getAlias();
            computedWindowColumns.add(new ResultStagePlan.Column(
                    alias,
                    ResultStagePlan.ColumnRole.RESULT_STAGE_ONLY,
                    "window_result",
                    "final",
                    window.column.getType(),
                    alias,
                    BoundSqlExpression.of(window.column.getDeclare())));
        }

        List<BoundSqlExpression> resultFilters = new ArrayList<>();
        if (queryRequest.getPostSlice() != null) {
            for (SliceRequestDef slice : queryRequest.getPostSlice()) {
                List<Object> params = new ArrayList<>();
                String filterSql = buildPostAggregateFilterSql(
                        slice, dialect, availableAliases, params);
                resultFilters.add(new BoundSqlExpression(filterSql, params));
            }
        }

        List<BoundSqlExpression> finalOrders = buildWindowResultOrderExprs(
                savedOrder, windowColumns, dialect).stream()
                .map(BoundSqlExpression::of)
                .collect(Collectors.toList());

        List<ResultStagePlan.Stage> stages = new ArrayList<>();
        for (QueryStagePlan.Stage diagnostic : stagePlan.getStages()) {
            QueryStageType type = diagnostic.getType();
            if (type == QueryStageType.WINDOW_RESULT_STAGE) {
                stages.add(new ResultStagePlan.Stage(
                        diagnostic.getId(), type, "__POST_RESULT_STAGE__",
                        computedWindowColumns, resultFilters, List.of()));
            } else if (type == QueryStageType.FINAL_STAGE) {
                stages.add(new ResultStagePlan.Stage(
                        diagnostic.getId(), type, "final",
                        List.of(), List.of(), finalOrders));
            } else {
                stages.add(ResultStagePlan.Stage.metadata(
                        diagnostic.getId(), type, "stage1"));
            }
        }
        return ResultStagePlan.Graph.create(stagePlan, stages);
    }

    private ResultStagePlan.Graph preparePostAggregateResultStageGraph(
            QueryStagePlan stagePlan,
            DbQueryRequestDef queryRequest,
            List<DbColumn> originalSelectCols,
            List<SliceRequestDef> postAggregateSlice,
            FDialect dialect) {
        String postAggregateStageId = stageId(
                stagePlan, QueryStageType.POST_AGGREGATE_STAGE);
        String finalStageId = stageId(stagePlan, QueryStageType.FINAL_STAGE);
        Set<String> finalAliases = new LinkedHashSet<>();
        for (DbColumn column : originalSelectCols) {
            finalAliases.add(column.getAlias());
        }

        List<ResultStagePlan.Column> postAggregateColumns = new ArrayList<>();
        for (PostAggregateCalculationDef calc : queryRequest.getPostAggregateCalculations()) {
            String measureRef = "stage1." + dialect.quoteIdentifier(calc.getMeasure());
            postAggregateColumns.add(new ResultStagePlan.Column(
                    calc.getName(),
                    ResultStagePlan.ColumnRole.RESULT_STAGE_ONLY,
                    postAggregateStageId,
                    finalStageId,
                    DbColumnType.NUMBER,
                    calc.getMeasure(),
                    BoundSqlExpression.of(buildPostAggregateCalculationSql(calc, measureRef))));
            finalAliases.add(calc.getName());
        }

        List<BoundSqlExpression> resultFilters = new ArrayList<>();
        List<SliceRequestDef> resultStageSlice = new ArrayList<>();
        if (postAggregateSlice != null) {
            resultStageSlice.addAll(postAggregateSlice);
        }
        if (queryRequest.getPostSlice() != null) {
            resultStageSlice.addAll(queryRequest.getPostSlice());
        }
        for (SliceRequestDef slice : resultStageSlice) {
            List<Object> params = new ArrayList<>();
            resultFilters.add(new BoundSqlExpression(
                    buildPostAggregateFilterSql(slice, dialect, finalAliases, params),
                    params));
        }

        List<BoundSqlExpression> finalOrders = new ArrayList<>();
        if (queryRequest.getOrderBy() != null) {
            for (OrderRequestDef order : queryRequest.getOrderBy()) {
                if (!finalAliases.contains(order.getField())) {
                    continue;
                }
                String orderSql = dialect.quoteIdentifier(order.getField());
                if (StringUtils.isNotEmpty(order.getDir())) {
                    orderSql += " " + order.getDir().toUpperCase();
                }
                finalOrders.add(BoundSqlExpression.of(orderSql));
            }
        }

        boolean hasWindowResultStage = stagePlan.hasStage(QueryStageType.WINDOW_RESULT_STAGE);
        List<ResultStagePlan.Stage> stages = new ArrayList<>();
        for (QueryStagePlan.Stage diagnostic : stagePlan.getStages()) {
            QueryStageType type = diagnostic.getType();
            if (type == QueryStageType.POST_AGGREGATE_STAGE) {
                stages.add(new ResultStagePlan.Stage(
                        diagnostic.getId(), type, "post_stage",
                        postAggregateColumns,
                        hasWindowResultStage ? List.of() : resultFilters,
                        List.of()));
            } else if (type == QueryStageType.WINDOW_RESULT_STAGE) {
                stages.add(new ResultStagePlan.Stage(
                        diagnostic.getId(), type, "__POST_RESULT_STAGE__",
                        List.of(), resultFilters, List.of()));
            } else if (type == QueryStageType.FINAL_STAGE) {
                stages.add(new ResultStagePlan.Stage(
                        diagnostic.getId(), type, "final",
                        List.of(), List.of(), finalOrders));
            } else {
                stages.add(ResultStagePlan.Stage.metadata(
                        diagnostic.getId(), type, "stage1"));
            }
        }
        return ResultStagePlan.Graph.create(stagePlan, stages);
    }

    private List<WindowColumnInfo> identifyWindowColumns(List<DbColumn> columns) {
        List<WindowColumnInfo> result = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            DbColumn column = columns.get(i);
            if (column instanceof AggregationDbColumn aggregation
                    && aggregation.getAggregation() == DbAggregation.WINDOW) {
                result.add(new WindowColumnInfo(i, column));
            } else if (column instanceof CalculatedDbColumn calculated
                    && calculated.hasWindow()) {
                result.add(new WindowColumnInfo(i, column));
            }
        }
        return result;
    }

    private List<String> buildWindowResultOrderExprs(
            JdbcQuery.JdbcOrder savedOrder,
            List<WindowColumnInfo> windowColumns,
            FDialect dialect) {
        if (savedOrder == null || savedOrder.getOrders().isEmpty()) {
            return List.of();
        }
        List<String> orderExprs = new ArrayList<>();
        for (DbQueryOrderColumnImpl order : savedOrder.getOrders()) {
            DbColumn orderCol = order.getSelectColumn();
            String orderRef = dialect.quoteIdentifier(projectionAlias(orderCol));
            boolean isWindowCol = windowColumns.stream()
                    .anyMatch(window -> window.column.getAlias().equals(orderCol.getAlias()));
            if (isWindowCol) {
                orderRef = dialect.quoteIdentifier(orderCol.getAlias());
            }
            if (order.isNullLast() || order.isNullFirst()) {
                orderRef = dialect.buildNullOrderClause(orderRef, order.isNullFirst());
            }
            if (StringUtils.isNotEmpty(order.getOrder())) {
                orderRef += " " + order.getOrder();
            }
            orderExprs.add(orderRef);
        }
        return orderExprs;
    }

    private String buildPostAggregateCalculationSql(
            PostAggregateCalculationDef calc,
            String measureRef) {
        String kind = StringUtils.isEmpty(calc.getKind()) ? "" : calc.getKind();
        return switch (kind) {
            case "ratioToTotal" -> formatRatioPostAggregate(
                    calc, measureRef + " / NULLIF(SUM(" + measureRef + ") OVER (), 0)");
            case "cumulativeSum" -> "SUM(" + measureRef + ") OVER (ORDER BY " + measureRef
                    + " DESC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)";
            case "cumulativeRatioToTotal" -> formatRatioPostAggregate(
                    calc,
                    "SUM(" + measureRef + ") OVER (ORDER BY " + measureRef
                            + " DESC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)"
                            + " / NULLIF(SUM(" + measureRef + ") OVER (), 0)");
            case "rankByMeasure" -> "RANK() OVER (ORDER BY " + measureRef + " DESC)";
            default -> throw RX.throwAUserTip(
                    "POST_AGGREGATE_CALCULATION_UNSUPPORTED: unsupported kind '"
                            + kind + "' for '" + calc.getName() + "'.");
        };
    }

    private String formatRatioPostAggregate(PostAggregateCalculationDef calc, String expr) {
        return "percent".equals(calc.getFormat()) ? "(" + expr + ") * 100" : expr;
    }

    private String buildPostAggregateFilterSql(
            CondRequestDef slice,
            FDialect dialect,
            Set<String> availableAliases,
            List<Object> params) {
        if (slice._isLogicalGroup()) {
            List<String> parts = new ArrayList<>();
            for (CondRequestDef child : slice._getGroupChildren()) {
                parts.add(buildPostAggregateFilterSql(
                        child, dialect, availableAliases, params));
            }
            String link = " " + slice._getGroupLink() + " ";
            return "(" + String.join(link, parts) + ")";
        }
        String field = slice.getField();
        if (!availableAliases.contains(field)) {
            throw RX.throwAUserTip(
                    "POST_AGGREGATE_SLICE_FIELD_NOT_SELECTED: slice field '" + field
                            + "' is not available in the post-aggregate stage.");
        }
        params.add(slice.getValue());
        return dialect.quoteIdentifier(field) + " " + normalizeOperator(slice.getOp()) + " ?";
    }

    private String normalizeOperator(String op) {
        if (op == null) {
            return "=";
        }
        return switch (op.toLowerCase()) {
            case "eq" -> "=";
            case "ne", "<>" -> "!=";
            case "gt" -> ">";
            case "gte" -> ">=";
            case "lt" -> "<";
            case "lte" -> "<=";
            default -> op;
        };
    }

    private CalculatedDbColumn sourceCalculatedColumn(
            DbColumn column,
            IdentityHashMap<DbColumn, DbColumn> aggregateSourceColumns) {
        if (column instanceof CalculatedDbColumn calculated) {
            return calculated;
        }
        DbColumn source = aggregateSourceColumns.get(column);
        return source instanceof CalculatedDbColumn calculated ? calculated : null;
    }

    private static boolean containsProjectionAlias(
            List<DbColumn> columns,
            DbColumn candidate) {
        String candidateAlias = projectionAlias(candidate);
        return columns.stream()
                .anyMatch(column -> candidateAlias.equals(projectionAlias(column)));
    }

    private static String projectionAlias(DbColumn column) {
        if (column instanceof DbQueryColumn && StringUtils.isNotEmpty(column.getName())) {
            return column.getName();
        }
        return StringUtils.isNotEmpty(column.getAlias())
                ? column.getAlias() : column.getName();
    }

    private String resultOutputAlias(
            DbColumn column,
            List<WindowColumnInfo> windowColumns) {
        return isWindowResultColumn(column, windowColumns)
                ? column.getAlias() : projectionAlias(column);
    }

    private boolean isWindowResultColumn(
            DbColumn column,
            List<WindowColumnInfo> windowColumns) {
        return windowColumns.stream().anyMatch(window -> window.column == column);
    }

    private String lastBaseStageId(ResultStagePlan.Graph graph) {
        String stageId = null;
        for (ResultStagePlan.Stage stage : graph.stages()) {
            if (stage.type() == QueryStageType.WINDOW_RESULT_STAGE
                    || stage.type() == QueryStageType.POST_AGGREGATE_STAGE
                    || stage.type() == QueryStageType.FINAL_STAGE) {
                break;
            }
            stageId = stage.stageId();
        }
        if (stageId == null) {
            throw new IllegalArgumentException("Result-stage graph has no base stage");
        }
        return stageId;
    }

    private String stageId(QueryStagePlan stagePlan, QueryStageType type) {
        return stagePlan.getStages().stream()
                .filter(stage -> stage.getType() == type)
                .map(QueryStagePlan.Stage::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Result-stage diagnostics missing " + type));
    }

    private record WindowColumnInfo(int originalIndex, DbColumn column) {
    }

    @FunctionalInterface
    public interface WindowDependencyResolver {
        DbColumn resolve(DbQueryColumn reference);
    }
}
