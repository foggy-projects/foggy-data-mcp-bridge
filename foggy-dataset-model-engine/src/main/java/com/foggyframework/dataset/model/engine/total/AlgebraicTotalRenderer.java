package com.foggyframework.dataset.model.engine.total;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.engine.query.SimpleSqlJdbcQueryVisitor;
import com.foggyframework.dataset.model.engine.stage.QueryStageType;
import com.foggyframework.dataset.model.engine.stage.result.ResultStagePlan;
import com.foggyframework.dataset.model.engine.stage.result.ResultStagePreparation;
import com.foggyframework.dataset.model.engine.stage.result.ResultStageRenderer;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Renders grouped totalData from mergeable aggregate states. */
public final class AlgebraicTotalRenderer {
    private final AggregateStateColumnFactory stateColumnFactory;

    public AlgebraicTotalRenderer(AggregateStateColumnFactory stateColumnFactory) {
        this.stateColumnFactory = stateColumnFactory;
    }

    public BoundSqlExpression render(SystemBundlesContext systemBundlesContext,
                                     JdbcQueryModel jdbcQueryModel,
                                     DbQueryRequestDef queryRequest,
                                     JdbcQuery jdbcQuery,
                                     DomainTransport domainTransport,
                                     TotalDataAggregatePlan plan,
                                     ResultStagePreparation preparation,
                                     ResultStageRenderer resultStageRenderer) {
        FDialect dialect = jdbcQueryModel != null
                ? jdbcQueryModel.getDialect() : FDialect.MYSQL_DIALECT;
        List<DbColumn> originalColumns = new ArrayList<>(jdbcQuery.getSelect().getColumns());
        List<DbColumn> baseColumns;
        List<Object> stateExpressionParams;
        if (preparation != null) {
            baseColumns = new ArrayList<>(preparation.sourceColumns(ResultStagePlan.Mode.TOTAL));
            stateExpressionParams = new ArrayList<>(
                    preparation.expressionValues(ResultStagePlan.Mode.TOTAL));
        } else {
            baseColumns = new ArrayList<>(originalColumns);
            stateExpressionParams = new ArrayList<>();
            for (TotalDataAggregatePlan.AggregateStateSpec state : plan.getStates()) {
                stateColumnFactory.append(baseColumns, state, stateExpressionParams);
            }
        }

        List<DbColumn> savedColumns = jdbcQuery.getSelect().getColumns();
        JdbcQuery.JdbcOrder savedOrder = jdbcQuery.getOrder();
        String baseSql;
        List<Object> baseVisitorParams;
        try {
            jdbcQuery.getSelect().setColumns(baseColumns);
            jdbcQuery.setOrder(null);
            SimpleSqlJdbcQueryVisitor visitor = new SimpleSqlJdbcQueryVisitor(
                    systemBundlesContext.getApplicationContext(), jdbcQueryModel, queryRequest);
            jdbcQuery.accept(visitor);
            baseSql = visitor.getSqlWithoutOrder();
            baseVisitorParams = new ArrayList<>(visitor.getValues());
        } finally {
            jdbcQuery.getSelect().setColumns(savedColumns);
            jdbcQuery.setOrder(savedOrder);
        }

        List<Object> baseParams = new ArrayList<>(stateExpressionParams);
        baseParams.addAll(baseVisitorParams);
        if (preparation != null) {
            return renderSharedResultStageTotal(
                    plan, originalColumns, baseSql, baseParams, domainTransport,
                    dialect, preparation, resultStageRenderer);
        }
        return renderSingleStageAlgebraicTotal(
                plan, baseSql, baseParams, domainTransport, dialect);
    }

    private BoundSqlExpression renderSharedResultStageTotal(
            TotalDataAggregatePlan plan,
            List<DbColumn> originalColumns,
            String baseSql,
            List<Object> baseParams,
            DomainTransport domainTransport,
            FDialect dialect,
            ResultStagePreparation preparation,
            ResultStageRenderer resultStageRenderer) {
        ResultStagePlan.Graph graph = preparation.graph();
        String boundThroughStageId = lastBaseStageId(graph);
        String totalSourceAlias = lastResultStageAlias(graph);
        ResultStagePlan.Executable executable = preparation.bind(
                ResultStagePlan.Mode.TOTAL,
                domainTransport == null ? List.of() : domainTransport.structuredCtes(),
                boundThroughStageId,
                new BoundSqlExpression(baseSql, baseParams),
                buildTotalFinalProjection(
                        plan, originalColumns, totalSourceAlias, graph, dialect));
        ResultStagePlan.RenderResult rendered = resultStageRenderer.render(executable, dialect);
        return new BoundSqlExpression(rendered.assembledSql(), rendered.assembledValues());
    }

    private List<ResultStagePlan.FinalProjection> buildTotalFinalProjection(
            TotalDataAggregatePlan plan,
            List<DbColumn> originalColumns,
            String totalSourceAlias,
            ResultStagePlan.Graph graph,
            FDialect dialect) {
        Map<String, DbColumn> originalByAlias = new LinkedHashMap<>();
        for (DbColumn column : originalColumns) {
            originalByAlias.put(column.getAlias(), column);
        }
        Set<String> resultStageAliases = graph.stages().stream()
                .flatMap(stage -> stage.computedColumns().stream())
                .map(ResultStagePlan.Column::alias)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ResultStagePlan.FinalProjection> projections = new ArrayList<>();
        for (String alias : plan.getPublicAliases()) {
            String expression = plan.renderPublicExpression(alias, dialect, totalSourceAlias);
            DbColumn original = originalByAlias.get(alias);
            projections.add(new ResultStagePlan.FinalProjection(
                    alias,
                    resultStageAliases.contains(alias)
                            ? ResultStagePlan.ColumnRole.RESULT_STAGE_ONLY
                            : ResultStagePlan.ColumnRole.PUBLIC_RESULT,
                    original == null ? DbColumnType.UNKNOWN : original.getType(),
                    BoundSqlExpression.of(StringUtils.isEmpty(expression) ? "NULL" : expression)));
        }
        projections.add(new ResultStagePlan.FinalProjection(
                "total",
                ResultStagePlan.ColumnRole.PUBLIC_RESULT,
                DbColumnType.INTEGER,
                BoundSqlExpression.of("COUNT(*)")));
        return projections;
    }

    private BoundSqlExpression renderSingleStageAlgebraicTotal(
            TotalDataAggregatePlan plan,
            String baseSql,
            List<Object> baseParams,
            DomainTransport domainTransport,
            FDialect dialect) {
        List<Object> params = new ArrayList<>();
        String ctePrefix = "";
        if (domainTransport != null && domainTransport.hasCte()) {
            params.addAll(domainTransport.cteParams());
            ctePrefix = "WITH " + String.join(",\n", domainTransport.cteFragments())
                    + ",\n__foggy_total_base AS (\n" + baseSql + "\n)\n";
            baseSql = "SELECT * FROM __foggy_total_base";
        }
        params.addAll(baseParams);

        String totalAlias = "tx";
        List<String> totalProjections = new ArrayList<>();
        for (String alias : plan.getPublicAliases()) {
            String expression = plan.renderPublicExpression(alias, dialect, totalAlias);
            totalProjections.add((StringUtils.isEmpty(expression) ? "NULL" : expression)
                    + " AS " + dialect.quoteIdentifier(alias));
        }
        totalProjections.add("COUNT(*) AS " + dialect.quoteIdentifier("total"));
        String sql = ctePrefix + "SELECT " + String.join(",\n       ", totalProjections)
                + "\nFROM (\n" + baseSql + "\n) " + totalAlias;
        return new BoundSqlExpression(sql, params);
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

    private String lastResultStageAlias(ResultStagePlan.Graph graph) {
        String alias = null;
        for (ResultStagePlan.Stage stage : graph.stages()) {
            if (stage.type() == QueryStageType.WINDOW_RESULT_STAGE
                    || stage.type() == QueryStageType.POST_AGGREGATE_STAGE) {
                alias = stage.renderAlias();
            }
        }
        if (alias == null) {
            throw new IllegalArgumentException("Shared TOTAL graph has no result stage");
        }
        return alias;
    }

    /** Immutable transport data required when totalData owns the outer CTE assembly. */
    public record DomainTransport(List<ResultStagePlan.StructuredCte> structuredCtes,
                                  List<String> cteFragments,
                                  List<Object> cteParams) {
        public DomainTransport {
            structuredCtes = structuredCtes == null ? List.of() : List.copyOf(structuredCtes);
            cteFragments = cteFragments == null ? List.of() : List.copyOf(cteFragments);
            cteParams = cteParams == null ? List.of() : List.copyOf(cteParams);
        }

        public boolean hasCte() {
            return !cteFragments.isEmpty();
        }
    }
}
