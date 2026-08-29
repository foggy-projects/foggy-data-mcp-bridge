package com.foggyframework.dataset.model.engine.stage.result;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.engine.stage.QueryStagePlan;
import com.foggyframework.dataset.model.engine.stage.QueryStageType;
import com.foggyframework.dataset.model.spi.DbColumnType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("方案 B：共享 ResultStageRenderer SQL/参数契约")
class ResultStageRendererTest {

    private final ResultStageRenderer renderer = new ResultStageRenderer();

    @Test
    @DisplayName("window MAIN：base/window/final 使用一个 CTE composer，order 只出现在 sql")
    void rendersWindowMainWithSharedCteComposer() {
        ResultStagePlan.Graph graph = ResultStagePlan.Graph.create(
                ResultStagePlanContractTest.ctePlan(List.of()),
                ResultStagePlanContractTest.windowSpecs());
        ResultStagePlan.Executable executable = ResultStagePlan.Executable.bind(
                graph,
                ResultStagePlan.Mode.MAIN,
                ResultStagePlanContractTest.root(),
                ResultStagePlanContractTest.publicProjection());

        ResultStagePlan.RenderResult rendered = renderer.render(executable, FDialect.SQLITE_DIALECT);

        assertEquals(List.of("stage1", "__POST_RESULT_STAGE__"),
                rendered.cteStages().stream().map(SqlGenerationResult.CteStage::alias).toList());
        assertTrue(rendered.outerSql().contains("FROM __POST_RESULT_STAGE__"), rendered.outerSql());
        assertTrue(rendered.outerSql().contains("WHERE \"rankNo\" <= ?"), rendered.outerSql());
        assertTrue(rendered.outerSql().contains("ORDER BY \"openingYear\" DESC"), rendered.outerSql());
        assertFalse(rendered.outerSqlWithoutOrder().contains("ORDER BY"), rendered.outerSqlWithoutOrder());
        assertEquals(List.of(5), rendered.outerValues());
        assertEquals(List.of(2020, 5), rendered.assembledValues());
        assertEquals(rendered.assembledSql(), asSqlGenerationResult(rendered).getAssembledSql());
        assertEquals(rendered.assembledValues(), asSqlGenerationResult(rendered).getAssembledParams());
    }

    @Test
    @DisplayName("TOTAL：复用相同 graph/filter，忽略 final order，并保留 result-only NULL key")
    void totalUsesSameGraphWithoutOrderAndKeepsNullKey() {
        ResultStagePlan.Graph graph = ResultStagePlan.Graph.create(
                ResultStagePlanContractTest.ctePlan(List.of()),
                ResultStagePlanContractTest.windowSpecs());
        ResultStagePlan.Executable total = ResultStagePlan.Executable.bind(
                graph,
                ResultStagePlan.Mode.TOTAL,
                ResultStagePlanContractTest.root(),
                ResultStagePlanContractTest.totalProjection());

        ResultStagePlan.RenderResult rendered = renderer.render(total, FDialect.SQLITE_DIALECT);

        assertFalse(rendered.outerSql().contains("ORDER BY"), rendered.outerSql());
        assertTrue(rendered.outerSql().contains("NULL AS \"rankNo\""), rendered.outerSql());
        assertTrue(rendered.outerSql().contains("SUM(\"waybillCount\") AS \"waybillCount\""),
                rendered.outerSql());
        assertTrue(rendered.outerSql().contains("WHERE \"rankNo\" <= ?"), rendered.outerSql());
        assertEquals(List.of(2020, 5), rendered.assembledValues());
    }

    @Test
    @DisplayName("CTE 参数严格按 sibling CTE 后 outer SELECT 的词法顺序")
    void cteParameterOrderFollowsSerializedSql() {
        ResultStagePlan.Graph graph = postAggregateGraph("cte");
        ResultStagePlan.RootSql root = new ResultStagePlan.RootSql(
                List.of(), "agg", new BoundSqlExpression("SELECT ? AS \"amount\"", List.of(10)), List.of());
        List<ResultStagePlan.FinalProjection> finals = List.of(new ResultStagePlan.FinalProjection(
                "adjusted",
                ResultStagePlan.ColumnRole.PUBLIC_RESULT,
                DbColumnType.NUMBER,
                new BoundSqlExpression("(? + \"adjusted\")", List.of(3))));

        ResultStagePlan.RenderResult rendered = renderer.render(
                ResultStagePlan.Executable.bind(graph, ResultStagePlan.Mode.MAIN, root, finals),
                FDialect.SQLITE_DIALECT);

        assertEquals(List.of(10, 2, 3, 4), rendered.assembledValues());
        assertEquals(List.of(3, 4), rendered.outerValues());
        assertTrue(rendered.assembledSql().indexOf("amount * ?")
                < rendered.assembledSql().indexOf("? + \"adjusted\""));
    }

    @Test
    @DisplayName("derived 参数按 outer projection → FROM 子树 → WHERE 的词法顺序")
    void derivedParameterOrderFollowsSerializedSql() {
        ResultStagePlan.Graph graph = postAggregateGraph("derived");
        ResultStagePlan.RootSql root = new ResultStagePlan.RootSql(
                List.of(), "agg", new BoundSqlExpression("SELECT ? AS \"amount\"", List.of(10)), List.of());
        List<ResultStagePlan.FinalProjection> finals = List.of(new ResultStagePlan.FinalProjection(
                "adjusted",
                ResultStagePlan.ColumnRole.PUBLIC_RESULT,
                DbColumnType.NUMBER,
                new BoundSqlExpression("(? + \"adjusted\")", List.of(3))));

        ResultStagePlan.RenderResult rendered = renderer.render(
                ResultStagePlan.Executable.bind(graph, ResultStagePlan.Mode.MAIN, root, finals),
                FDialect.MYSQL_DIALECT);

        assertTrue(rendered.cteStages().isEmpty());
        assertEquals(rendered.outerSql(), rendered.assembledSql());
        assertEquals(List.of(3, 2, 10, 4), rendered.outerValues());
        assertEquals(rendered.outerValues(), rendered.assembledValues());
    }

    @Test
    @DisplayName("derived + prerequisite domain CTE 保持 fail-closed")
    void derivedWithPrerequisiteCteFailsClosed() {
        ResultStagePlan.Graph graph = postAggregateGraph("derived");
        ResultStagePlan.StructuredCte domain = new ResultStagePlan.StructuredCte(
                "domain_year",
                List.of("year"),
                new BoundSqlExpression("VALUES (?)", List.of(2026)));
        ResultStagePlan.RootSql root = new ResultStagePlan.RootSql(
                List.of(domain), "agg", BoundSqlExpression.of("SELECT 1 AS \"amount\""), List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> renderer.render(
                        ResultStagePlan.Executable.bind(
                                graph, ResultStagePlan.Mode.MAIN, root,
                                List.of(new ResultStagePlan.FinalProjection(
                                        "amount", ResultStagePlan.ColumnRole.PUBLIC_RESULT,
                                        DbColumnType.NUMBER, BoundSqlExpression.of("\"amount\"")))),
                        FDialect.MYSQL_DIALECT));

        assertTrue(ex.getMessage().contains("DERIVED_STAGE_CTE_TRANSPORT_UNSUPPORTED"));
    }

    @Test
    @DisplayName("任何 expression/filter 的占位符不完整都必须 fail-closed")
    void incompleteStageBindingsFailClosed() {
        ResultStagePlan.Graph graph = postAggregateGraph("cte");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ResultStagePlan.FinalProjection(
                        "adjusted",
                        ResultStagePlan.ColumnRole.PUBLIC_RESULT,
                        DbColumnType.NUMBER,
                        new BoundSqlExpression("? + \"adjusted\"", List.of())));
        assertTrue(ex.getMessage().contains("placeholder"));
    }

    private static ResultStagePlan.Graph postAggregateGraph(String strategy) {
        QueryStagePlan diagnostics = new QueryStagePlan(
                true,
                "sqlite",
                strategy,
                "final",
                "final-stage-count",
                List.of(
                        diagnostic("row", QueryStageType.ROW_STAGE, "stage0"),
                        diagnostic("agg", QueryStageType.AGGREGATE_STAGE, "stage1"),
                        diagnostic("post_agg", QueryStageType.POST_AGGREGATE_STAGE, "stage2"),
                        diagnostic("final", QueryStageType.FINAL_STAGE, "final")
                ),
                List.of(),
                List.of());
        ResultStagePlan.Column adjusted = new ResultStagePlan.Column(
                "adjusted",
                ResultStagePlan.ColumnRole.RESULT_STAGE_ONLY,
                "post_agg",
                "final",
                DbColumnType.NUMBER,
                "amount",
                new BoundSqlExpression("stage1.\"amount\" * ?", List.of(2)));
        return ResultStagePlan.Graph.create(diagnostics, List.of(
                ResultStagePlan.Stage.metadata("row", QueryStageType.ROW_STAGE, "stage0"),
                ResultStagePlan.Stage.metadata("agg", QueryStageType.AGGREGATE_STAGE, "stage1"),
                new ResultStagePlan.Stage(
                        "post_agg",
                        QueryStageType.POST_AGGREGATE_STAGE,
                        "post_stage",
                        List.of(adjusted),
                        List.of(new BoundSqlExpression("\"adjusted\" > ?", List.of(4))),
                        List.of()),
                ResultStagePlan.Stage.metadata("final", QueryStageType.FINAL_STAGE, "final")
        ));
    }

    private static QueryStagePlan.Stage diagnostic(String id, QueryStageType type, String alias) {
        return new QueryStagePlan.Stage(
                id, type, alias, List.of(), List.of(), List.of(), List.of(), true, 0);
    }

    private static SqlGenerationResult asSqlGenerationResult(ResultStagePlan.RenderResult result) {
        return new SqlGenerationResult(result.outerSql(), result.outerValues(), null, result.cteStages());
    }
}
