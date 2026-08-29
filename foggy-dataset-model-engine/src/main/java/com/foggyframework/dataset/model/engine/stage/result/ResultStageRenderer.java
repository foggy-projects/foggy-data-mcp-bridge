package com.foggyframework.dataset.model.engine.stage.result;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.engine.stage.QueryStageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared SQL renderer for MAIN and TOTAL result stages. */
public final class ResultStageRenderer {

    private final RootCteLowerer rootCteLowerer;

    public ResultStageRenderer() {
        this(new RootCteLowerer());
    }

    ResultStageRenderer(RootCteLowerer rootCteLowerer) {
        this.rootCteLowerer = Objects.requireNonNull(rootCteLowerer, "rootCteLowerer");
    }

    public ResultStagePlan.RenderResult render(
            ResultStagePlan.Executable executable,
            FDialect dialect) {
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(dialect, "dialect");
        String strategy = executable.graph().diagnostics().getRenderStrategy();
        if ("derived".equals(strategy)) {
            return renderDerived(executable, dialect);
        }
        if ("cte".equals(strategy)) {
            return renderCte(executable, dialect);
        }
        if ("single".equals(strategy)) {
            return renderDerived(executable, dialect);
        }
        throw new IllegalArgumentException("Unsupported result-stage render strategy: " + strategy);
    }

    private ResultStagePlan.RenderResult renderCte(
            ResultStagePlan.Executable executable,
            FDialect dialect) {
        ResultStagePlan.Graph graph = executable.graph();
        ResultStagePlan.RootSql root = executable.root();
        int rootIndex = graph.indexOf(root.boundThroughStageId());

        List<SqlGenerationResult.CteStage> ctes = new ArrayList<>();
        for (ResultStagePlan.StructuredCte prerequisite : root.prerequisiteCtes()) {
            ctes.add(rootCteLowerer.lower(prerequisite, dialect));
        }

        ResultStagePlan.Stage rootStage = graph.stages().get(rootIndex);
        ctes.add(new SqlGenerationResult.CteStage(
                rootStage.renderAlias(), root.body().sql(), root.body().values()));
        ResultStagePlan.Stage collapsibleWindow = collapsibleMainWindow(executable, rootIndex);
        if (collapsibleWindow != null) {
            FinalSql collapsed = renderCollapsedMainWindow(
                    executable, rootStage.renderAlias(), collapsibleWindow, dialect);
            return new ResultStagePlan.RenderResult(
                    collapsed.sql(), collapsed.sqlWithoutOrder(), collapsed.values(), ctes);
        }
        String currentAlias = rootStage.renderAlias();
        List<ResultStagePlan.Column> currentColumns = new ArrayList<>(root.columns());
        List<BoundSqlExpression> finalFilters = new ArrayList<>();
        ResultStagePlan.Stage finalStage = null;

        for (int i = rootIndex + 1; i < graph.stages().size(); i++) {
            ResultStagePlan.Stage stage = graph.stages().get(i);
            if (stage.type() == QueryStageType.FINAL_STAGE) {
                finalStage = stage;
                continue;
            }
            if (!isResultStage(stage.type())) {
                continue;
            }
            BoundSqlExpression stageBody = renderCteStage(
                    graph, stage, currentAlias, currentColumns, dialect);
            ctes.add(new SqlGenerationResult.CteStage(
                    stage.renderAlias(), stageBody.sql(), stageBody.values()));
            currentColumns = columnsAfterStage(graph, stage, currentColumns);
            currentAlias = stage.renderAlias();
            finalFilters.addAll(stage.filters());
        }

        FinalSql finalSql = renderFinalFromAlias(
                executable, currentAlias, finalFilters,
                finalStage == null ? List.of() : finalStage.orders(), dialect);
        return new ResultStagePlan.RenderResult(
                finalSql.sql(), finalSql.sqlWithoutOrder(), finalSql.values(), ctes);
    }

    private BoundSqlExpression renderCteStage(
            ResultStagePlan.Graph graph,
            ResultStagePlan.Stage stage,
            String sourceAlias,
            List<ResultStagePlan.Column> sourceColumns,
            FDialect dialect) {
        List<String> projections = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        appendSurvivingSourceColumns(
                projections, graph, stage, sourceAlias, sourceColumns, dialect);
        for (ResultStagePlan.Column column : stage.computedColumns()) {
            projections.add(column.expression().sql() + " AS " + dialect.quoteIdentifier(column.alias()));
            values.addAll(column.expression().values());
        }
        if (projections.isEmpty()) {
            throw new IllegalArgumentException(
                    "Result stage '" + stage.stageId() + "' has no surviving projection");
        }
        String sql = "SELECT " + String.join(",\n       ", projections)
                + "\nFROM " + sourceAlias;
        return new BoundSqlExpression(sql, values);
    }

    private ResultStagePlan.Stage collapsibleMainWindow(
            ResultStagePlan.Executable executable,
            int rootIndex) {
        // Compatibility-only physical lowering: the logical graph is unchanged
        // and TOTAL still renders the same window expression as a separate stage.
        if (executable.mode() != ResultStagePlan.Mode.MAIN) {
            return null;
        }
        ResultStagePlan.Stage candidate = null;
        for (int i = rootIndex + 1; i < executable.graph().stages().size(); i++) {
            ResultStagePlan.Stage stage = executable.graph().stages().get(i);
            if (stage.type() == QueryStageType.FINAL_STAGE) {
                continue;
            }
            if (!isResultStage(stage.type())) {
                continue;
            }
            if (candidate != null
                    || stage.type() != QueryStageType.WINDOW_RESULT_STAGE
                    || !stage.filters().isEmpty()) {
                return null;
            }
            candidate = stage;
        }
        return candidate;
    }

    private FinalSql renderCollapsedMainWindow(
            ResultStagePlan.Executable executable,
            String sourceAlias,
            ResultStagePlan.Stage window,
            FDialect dialect) {
        List<Object> values = new ArrayList<>();
        List<String> projections = new ArrayList<>();
        for (ResultStagePlan.FinalProjection projection : executable.finalProjection()) {
            ResultStagePlan.Column computed = window.computedColumns().stream()
                    .filter(column -> column.alias().equals(projection.alias()))
                    .findFirst()
                    .orElse(null);
            BoundSqlExpression expression = computed == null
                    ? projection.expression() : computed.expression();
            projections.add(expression.sql() + " AS " + dialect.quoteIdentifier(projection.alias()));
            values.addAll(expression.values());
        }
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(String.join(",\n       ", projections))
                .append("\nFROM ").append(sourceAlias);
        String withoutOrder = sql.toString();
        ResultStagePlan.Stage finalStage = executable.graph().stages().stream()
                .filter(stage -> stage.type() == QueryStageType.FINAL_STAGE)
                .findFirst()
                .orElse(null);
        appendOrder(sql, executable.mode(), finalStage == null ? List.of() : finalStage.orders());
        return new FinalSql(sql.toString(), withoutOrder, values);
    }

    private ResultStagePlan.RenderResult renderDerived(
            ResultStagePlan.Executable executable,
            FDialect dialect) {
        if (!executable.root().prerequisiteCtes().isEmpty()) {
            throw new IllegalArgumentException(
                    "DERIVED_STAGE_CTE_TRANSPORT_UNSUPPORTED: derived result stage cannot consume prerequisite CTEs");
        }
        ResultStagePlan.Graph graph = executable.graph();
        int rootIndex = graph.indexOf(executable.root().boundThroughStageId());
        ResultStagePlan.Stage rootStage = graph.stages().get(rootIndex);
        BoundSqlExpression current = executable.root().body();
        String currentAlias = rootStage.renderAlias();
        List<ResultStagePlan.Column> currentColumns =
                new ArrayList<>(executable.root().columns());
        List<BoundSqlExpression> finalFilters = new ArrayList<>();
        ResultStagePlan.Stage finalStage = null;

        for (int i = rootIndex + 1; i < graph.stages().size(); i++) {
            ResultStagePlan.Stage stage = graph.stages().get(i);
            if (stage.type() == QueryStageType.FINAL_STAGE) {
                finalStage = stage;
                continue;
            }
            if (!isResultStage(stage.type())) {
                continue;
            }
            List<String> projections = new ArrayList<>();
            List<Object> values = new ArrayList<>();
            appendSurvivingSourceColumns(
                    projections, graph, stage, currentAlias, currentColumns, dialect);
            for (ResultStagePlan.Column column : stage.computedColumns()) {
                projections.add(column.expression().sql() + " AS "
                        + dialect.quoteIdentifier(column.alias()));
                values.addAll(column.expression().values());
            }
            if (projections.isEmpty()) {
                throw new IllegalArgumentException(
                        "Result stage '" + stage.stageId() + "' has no surviving projection");
            }
            String sql = "SELECT " + String.join(",\n       ", projections)
                    + "\nFROM (\n" + current.sql() + "\n) " + currentAlias;
            values.addAll(current.values());
            current = new BoundSqlExpression(sql, values);
            currentColumns = columnsAfterStage(graph, stage, currentColumns);
            currentAlias = stage.renderAlias();
            finalFilters.addAll(stage.filters());
        }

        FinalSql finalSql = renderFinalFromDerived(
                executable, currentAlias, current, finalFilters,
                finalStage == null ? List.of() : finalStage.orders(), dialect);
        return new ResultStagePlan.RenderResult(
                finalSql.sql(), finalSql.sqlWithoutOrder(), finalSql.values(), List.of());
    }

    private FinalSql renderFinalFromAlias(
            ResultStagePlan.Executable executable,
            String sourceAlias,
            List<BoundSqlExpression> filters,
            List<BoundSqlExpression> orders,
            FDialect dialect) {
        List<Object> values = new ArrayList<>();
        String select = finalProjectionSql(executable.finalProjection(), dialect, values);
        StringBuilder sql = new StringBuilder(select).append("\nFROM ").append(sourceAlias);
        appendFilters(sql, filters, values);
        String withoutOrder = sql.toString();
        appendOrder(sql, executable.mode(), orders);
        return new FinalSql(sql.toString(), withoutOrder, values);
    }

    private FinalSql renderFinalFromDerived(
            ResultStagePlan.Executable executable,
            String sourceAlias,
            BoundSqlExpression source,
            List<BoundSqlExpression> filters,
            List<BoundSqlExpression> orders,
            FDialect dialect) {
        List<Object> values = new ArrayList<>();
        String select = finalProjectionSql(executable.finalProjection(), dialect, values);
        StringBuilder sql = new StringBuilder(select)
                .append("\nFROM (\n").append(source.sql()).append("\n) ").append(sourceAlias);
        values.addAll(source.values());
        appendFilters(sql, filters, values);
        String withoutOrder = sql.toString();
        appendOrder(sql, executable.mode(), orders);
        return new FinalSql(sql.toString(), withoutOrder, values);
    }

    private String finalProjectionSql(
            List<ResultStagePlan.FinalProjection> projections,
            FDialect dialect,
            List<Object> values) {
        if (projections.isEmpty()) {
            return "SELECT *";
        }
        List<String> sql = new ArrayList<>();
        for (ResultStagePlan.FinalProjection projection : projections) {
            sql.add(projection.expression().sql() + " AS "
                    + dialect.quoteIdentifier(projection.alias()));
            values.addAll(projection.expression().values());
        }
        return "SELECT " + String.join(",\n       ", sql);
    }

    private void appendFilters(
            StringBuilder sql,
            List<BoundSqlExpression> filters,
            List<Object> values) {
        if (filters.isEmpty()) {
            return;
        }
        List<String> predicates = new ArrayList<>();
        for (BoundSqlExpression filter : filters) {
            predicates.add(filter.sql());
            values.addAll(filter.values());
        }
        sql.append("\nWHERE ").append(String.join(" AND ", predicates));
    }

    private void appendOrder(
            StringBuilder sql,
            ResultStagePlan.Mode mode,
            List<BoundSqlExpression> orders) {
        if (mode != ResultStagePlan.Mode.MAIN || orders.isEmpty()) {
            return;
        }
        sql.append("\nORDER BY ")
                .append(String.join(", ", orders.stream().map(BoundSqlExpression::sql).toList()));
    }

    private boolean isResultStage(QueryStageType type) {
        return type == QueryStageType.WINDOW_RESULT_STAGE
                || type == QueryStageType.POST_AGGREGATE_STAGE;
    }

    private void appendSurvivingSourceColumns(
            List<String> projections,
            ResultStagePlan.Graph graph,
            ResultStagePlan.Stage stage,
            String sourceAlias,
            List<ResultStagePlan.Column> sourceColumns,
            FDialect dialect) {
        int currentIndex = graph.indexOf(stage.stageId());
        for (ResultStagePlan.Column column : sourceColumns) {
            if (graph.indexOf(column.lastConsumerStageId()) > currentIndex) {
                projections.add(sourceAlias + "." + dialect.quoteIdentifier(column.alias()));
            }
        }
    }

    private List<ResultStagePlan.Column> columnsAfterStage(
            ResultStagePlan.Graph graph,
            ResultStagePlan.Stage stage,
            List<ResultStagePlan.Column> sourceColumns) {
        int currentIndex = graph.indexOf(stage.stageId());
        List<ResultStagePlan.Column> result = new ArrayList<>();
        for (ResultStagePlan.Column column : sourceColumns) {
            if (graph.indexOf(column.lastConsumerStageId()) > currentIndex) {
                result.add(column);
            }
        }
        for (ResultStagePlan.Column column : stage.computedColumns()) {
            if (graph.indexOf(column.lastConsumerStageId()) > currentIndex) {
                result.add(column);
            }
        }
        return result;
    }

    private record FinalSql(String sql, String sqlWithoutOrder, List<Object> values) {
        private FinalSql {
            values = List.copyOf(values);
        }
    }
}
