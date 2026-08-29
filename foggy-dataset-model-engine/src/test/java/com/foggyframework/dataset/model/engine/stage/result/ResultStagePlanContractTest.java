package com.foggyframework.dataset.model.engine.stage.result;

import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.engine.stage.QueryStagePlan;
import com.foggyframework.dataset.model.engine.stage.QueryStageType;
import com.foggyframework.dataset.model.spi.DbColumnType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("方案 A：规范化结果阶段图契约")
class ResultStagePlanContractTest {

    @Test
    @DisplayName("executable plan 必须引用请求级同一个 graph 实例")
    void executablePlansShareTheSameRequestGraph() {
        ResultStagePlan.Graph graph = graph(ctePlan(List.of()), windowSpecs());

        ResultStagePlan.RootSql root = root();
        ResultStagePlan.Executable main = ResultStagePlan.Executable.bind(
                graph, ResultStagePlan.Mode.MAIN, root, publicProjection());
        ResultStagePlan.Executable total = ResultStagePlan.Executable.bind(
                graph, ResultStagePlan.Mode.TOTAL, root, totalProjection());

        assertSame(graph, main.graph());
        assertSame(graph, total.graph());
        assertSame(main.graph(), total.graph());
    }

    @Test
    @DisplayName("spec 与 QueryStagePlan 必须 stageId/type 一一对应且不可重排")
    void graphRejectsMissingAdditionalOrReorderedStages() {
        QueryStagePlan diagnostics = ctePlan(List.of());
        List<ResultStagePlan.Stage> exact = windowSpecs();

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> graph(diagnostics, exact.subList(0, exact.size() - 1)));
        assertTrue(missing.getMessage().contains("one-to-one"));

        List<ResultStagePlan.Stage> reordered = List.of(
                exact.get(0), exact.get(2), exact.get(1), exact.get(3));
        IllegalArgumentException order = assertThrows(IllegalArgumentException.class,
                () -> graph(diagnostics, reordered));
        assertTrue(order.getMessage().contains("stage mismatch"));
    }

    @Test
    @DisplayName("planner unsupported 是 graph 创建硬门禁")
    void graphRejectsUnsupportedDiagnostics() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> graph(ctePlan(List.of("post-aggregate-window-mix-unsupported")), windowSpecs()));

        assertTrue(ex.getMessage().contains("post-aggregate-window-mix-unsupported"));
    }

    @Test
    @DisplayName("列 producer/last-consumer 必须属于 graph 且生命周期单调")
    void graphValidatesColumnLifecycle() {
        List<ResultStagePlan.Stage> stages = windowSpecs();
        ResultStagePlan.Column invalid = new ResultStagePlan.Column(
                "hiddenAmount",
                ResultStagePlan.ColumnRole.HIDDEN_DEPENDENCY,
                "window_result",
                "missing_stage",
                DbColumnType.NUMBER,
                "Waybill.receivableTransportAmount",
                BoundSqlExpression.of("stage1.\"hiddenAmount\""));
        ResultStagePlan.Stage brokenWindow = stages.get(2).withComputedColumns(List.of(invalid));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> graph(ctePlan(List.of()), List.of(
                        stages.get(0), stages.get(1), brokenWindow, stages.get(3))));

        assertTrue(ex.getMessage().contains("last consumer"));
    }

    @Test
    @DisplayName("root 必须绑定到 graph 中存在的 base stage 且 SQL 参数完整")
    void executableValidatesRootStageAndBindings() {
        ResultStagePlan.Graph graph = graph(ctePlan(List.of()), windowSpecs());

        ResultStagePlan.RootSql unknownStage = new ResultStagePlan.RootSql(
                List.of(), "missing", new BoundSqlExpression("SELECT ?", List.of(1)), List.of());
        assertThrows(IllegalArgumentException.class, () -> ResultStagePlan.Executable.bind(
                graph, ResultStagePlan.Mode.MAIN, unknownStage, publicProjection()));

        IllegalArgumentException bindings = assertThrows(IllegalArgumentException.class,
                () -> new ResultStagePlan.RootSql(
                        List.of(), "agg", new BoundSqlExpression("SELECT ?", List.of()), List.of()));
        assertTrue(bindings.getMessage().contains("placeholder"));
    }

    @Test
    @DisplayName("QueryStagePlan parameterCount 只保留诊断值，不参与执行参数收集")
    void diagnosticParameterCountIsNotExecutionSource() {
        ResultStagePlan.Graph graph = graph(ctePlan(List.of()), windowSpecs());
        ResultStagePlan.Executable executable = ResultStagePlan.Executable.bind(
                graph, ResultStagePlan.Mode.MAIN, root(), publicProjection());

        assertEquals(99, graph.diagnostics().getStages().get(2).getParameterCount());
        assertEquals(List.of(2020), executable.root().body().values());
    }

    private static ResultStagePlan.Graph graph(QueryStagePlan plan, List<ResultStagePlan.Stage> stages) {
        return ResultStagePlan.Graph.create(plan, stages);
    }

    static QueryStagePlan ctePlan(List<String> unsupported) {
        return new QueryStagePlan(
                true,
                "sqlite",
                "cte",
                "final",
                "final-stage-count",
                List.of(
                        diagnostic("row", QueryStageType.ROW_STAGE, "stage0", 0),
                        diagnostic("agg", QueryStageType.AGGREGATE_STAGE, "stage1", 0),
                        diagnostic("window_result", QueryStageType.WINDOW_RESULT_STAGE, "stage2", 99),
                        diagnostic("final", QueryStageType.FINAL_STAGE, "final", 0)
                ),
                List.of(),
                unsupported
        );
    }

    static List<ResultStagePlan.Stage> windowSpecs() {
        ResultStagePlan.Column rank = new ResultStagePlan.Column(
                "rankNo",
                ResultStagePlan.ColumnRole.RESULT_STAGE_ONLY,
                "window_result",
                "final",
                DbColumnType.INTEGER,
                "waybillCount",
                BoundSqlExpression.of("RANK() OVER (ORDER BY stage1.\"waybillCount\" DESC)"));
        return List.of(
                ResultStagePlan.Stage.metadata("row", QueryStageType.ROW_STAGE, "stage0"),
                ResultStagePlan.Stage.metadata("agg", QueryStageType.AGGREGATE_STAGE, "stage1"),
                new ResultStagePlan.Stage(
                        "window_result",
                        QueryStageType.WINDOW_RESULT_STAGE,
                        "__POST_RESULT_STAGE__",
                        List.of(rank),
                        List.of(new BoundSqlExpression("\"rankNo\" <= ?", List.of(5))),
                        List.of()),
                new ResultStagePlan.Stage(
                        "final",
                        QueryStageType.FINAL_STAGE,
                        "final",
                        List.of(),
                        List.of(),
                        List.of(BoundSqlExpression.of("\"openingYear\" DESC")))
        );
    }

    static ResultStagePlan.RootSql root() {
        return new ResultStagePlan.RootSql(
                List.of(),
                "agg",
                new BoundSqlExpression(
                        "SELECT opening_year AS \"openingYear\", COUNT(*) AS \"waybillCount\" "
                                + "FROM waybill WHERE opening_year >= ? GROUP BY opening_year",
                        List.of(2020)),
                rootColumns());
    }

    static List<ResultStagePlan.Column> rootColumns() {
        return List.of(
                new ResultStagePlan.Column(
                        "openingYear",
                        ResultStagePlan.ColumnRole.PUBLIC_RESULT,
                        "agg",
                        "final",
                        DbColumnType.INTEGER,
                        "openingYear",
                        BoundSqlExpression.of("\"openingYear\"")),
                new ResultStagePlan.Column(
                        "waybillCount",
                        ResultStagePlan.ColumnRole.PUBLIC_RESULT,
                        "agg",
                        "final",
                        DbColumnType.BIGINT,
                        "waybillCount",
                        BoundSqlExpression.of("\"waybillCount\""))
        );
    }

    static List<ResultStagePlan.FinalProjection> publicProjection() {
        return List.of(
                projection("openingYear", ResultStagePlan.ColumnRole.PUBLIC_RESULT,
                        BoundSqlExpression.of("\"openingYear\""), DbColumnType.INTEGER),
                projection("waybillCount", ResultStagePlan.ColumnRole.PUBLIC_RESULT,
                        BoundSqlExpression.of("\"waybillCount\""), DbColumnType.BIGINT),
                projection("rankNo", ResultStagePlan.ColumnRole.RESULT_STAGE_ONLY,
                        BoundSqlExpression.of("\"rankNo\""), DbColumnType.INTEGER)
        );
    }

    static List<ResultStagePlan.FinalProjection> totalProjection() {
        return List.of(
                projection("openingYear", ResultStagePlan.ColumnRole.PUBLIC_RESULT,
                        BoundSqlExpression.of("NULL"), DbColumnType.INTEGER),
                projection("waybillCount", ResultStagePlan.ColumnRole.PUBLIC_RESULT,
                        BoundSqlExpression.of("SUM(\"waybillCount\")"), DbColumnType.BIGINT),
                projection("rankNo", ResultStagePlan.ColumnRole.RESULT_STAGE_ONLY,
                        BoundSqlExpression.of("NULL"), DbColumnType.INTEGER)
        );
    }

    private static ResultStagePlan.FinalProjection projection(
            String alias,
            ResultStagePlan.ColumnRole role,
            BoundSqlExpression expression,
            DbColumnType type) {
        return new ResultStagePlan.FinalProjection(alias, role, type, expression);
    }

    private static QueryStagePlan.Stage diagnostic(
            String id, QueryStageType type, String sqlAlias, int parameterCount) {
        return new QueryStagePlan.Stage(
                id, type, sqlAlias, List.of(), List.of(), List.of(), List.of(), true, parameterCount);
    }
}
