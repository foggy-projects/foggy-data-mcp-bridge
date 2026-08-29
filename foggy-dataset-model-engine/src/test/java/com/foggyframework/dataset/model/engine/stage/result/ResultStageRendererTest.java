package com.foggyframework.dataset.model.engine.stage.result;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.engine.stage.QueryStagePlan;
import com.foggyframework.dataset.model.engine.stage.QueryStageType;
import com.foggyframework.dataset.model.spi.DbColumnType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

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
        String windowStage = rendered.cteStages().get(1).sql();
        assertFalse(windowStage.contains("stage1.*"),
                "renderer 必须消费 root columns，不能继续透传 sourceAlias.*: " + windowStage);
        assertTrue(windowStage.contains("stage1.\"openingYear\""), windowStage);
        assertTrue(windowStage.contains("stage1.\"waybillCount\""), windowStage);
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
    @DisplayName("无 filter window 的 MAIN collapse 仅是物理优化，TOTAL 仍执行同一 graph expression")
    void unfilteredMainWindowCollapseKeepsLogicalParityWithTotal() {
        List<ResultStagePlan.Stage> stages =
                new java.util.ArrayList<>(ResultStagePlanContractTest.windowSpecs());
        ResultStagePlan.Stage window = stages.get(2);
        stages.set(2, new ResultStagePlan.Stage(
                window.stageId(),
                window.type(),
                window.renderAlias(),
                window.computedColumns(),
                List.of(),
                window.orders()));
        ResultStagePlan.Graph graph = ResultStagePlan.Graph.create(
                ResultStagePlanContractTest.ctePlan(List.of()), stages);

        ResultStagePlan.RenderResult main = renderer.render(
                ResultStagePlan.Executable.bind(
                        graph,
                        ResultStagePlan.Mode.MAIN,
                        ResultStagePlanContractTest.root(),
                        ResultStagePlanContractTest.publicProjection()),
                FDialect.SQLITE_DIALECT);
        ResultStagePlan.RenderResult total = renderer.render(
                ResultStagePlan.Executable.bind(
                        graph,
                        ResultStagePlan.Mode.TOTAL,
                        ResultStagePlanContractTest.root(),
                        ResultStagePlanContractTest.totalProjection()),
                FDialect.SQLITE_DIALECT);

        assertEquals(List.of("stage1"),
                main.cteStages().stream().map(SqlGenerationResult.CteStage::alias).toList());
        assertEquals(List.of("stage1", "__POST_RESULT_STAGE__"),
                total.cteStages().stream().map(SqlGenerationResult.CteStage::alias).toList());
        String expression = window.computedColumns().get(0).expression().sql();
        assertTrue(main.outerSql().contains(expression), main.outerSql());
        assertTrue(total.cteStages().get(1).sql().contains(expression),
                total.cteStages().get(1).sql());
        assertTrue(main.outerValues().isEmpty());
        assertTrue(total.outerValues().isEmpty());
    }

    @Test
    @DisplayName("CTE 参数严格按 sibling CTE 后 outer SELECT 的词法顺序")
    void cteParameterOrderFollowsSerializedSql() {
        ResultStagePlan.Graph graph = postAggregateGraph("cte");
        ResultStagePlan.RootSql root = new ResultStagePlan.RootSql(
                List.of(), "agg", new BoundSqlExpression("SELECT ? AS \"amount\"", List.of(10)),
                amountRootColumns());
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
                List.of(), "agg", new BoundSqlExpression("SELECT ? AS \"amount\"", List.of(10)),
                amountRootColumns());
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
                List.of(domain), "agg", BoundSqlExpression.of("SELECT 1 AS \"amount\""),
                amountRootColumns());

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("cteDialectCases")
    @DisplayName("domain + AVG state + base/result/final/filter 参数按四方言 assembled SQL 词法顺序")
    void combinedCteTopologyKeepsSiblingAndParameterOrder(
            String name,
            FDialect dialect,
            String domainBody) {
        ResultStagePlan.Graph graph = postAggregateGraph("cte");
        ResultStagePlan.StructuredCte domain = new ResultStagePlan.StructuredCte(
                "domain_year",
                List.of("openingYear"),
                new BoundSqlExpression(domainBody, List.of(2026)));
        List<ResultStagePlan.Column> rootColumns = List.of(
                rootColumn("amount", ResultStagePlan.ColumnRole.PUBLIC_RESULT),
                rootColumn("__foggy_avg_sum_0",
                        ResultStagePlan.ColumnRole.INTERNAL_AGGREGATE_STATE),
                rootColumn("__foggy_avg_count_0",
                        ResultStagePlan.ColumnRole.INTERNAL_AGGREGATE_STATE));
        ResultStagePlan.RootSql root = new ResultStagePlan.RootSql(
                List.of(domain),
                "agg",
                new BoundSqlExpression(
                        "SELECT ? AS \"amount\", ? AS \"__foggy_avg_sum_0\", "
                                + "? AS \"__foggy_avg_count_0\" WHERE ? = ?",
                        List.of(10, 11, 11, "base", "base")),
                rootColumns);
        List<ResultStagePlan.FinalProjection> finals = List.of(
                new ResultStagePlan.FinalProjection(
                        "adjusted",
                        ResultStagePlan.ColumnRole.RESULT_STAGE_ONLY,
                        DbColumnType.NUMBER,
                        new BoundSqlExpression("(? + \"adjusted\")", List.of(3))),
                new ResultStagePlan.FinalProjection(
                        "averageAmount",
                        ResultStagePlan.ColumnRole.PUBLIC_RESULT,
                        DbColumnType.NUMBER,
                        BoundSqlExpression.of(
                                "SUM(\"__foggy_avg_sum_0\") / "
                                        + "NULLIF(SUM(\"__foggy_avg_count_0\"), 0)")));

        ResultStagePlan.RenderResult rendered = renderer.render(
                ResultStagePlan.Executable.bind(
                        graph, ResultStagePlan.Mode.TOTAL, root, finals),
                dialect);

        assertEquals(List.of("domain_year", "stage1", "post_stage"),
                rendered.cteStages().stream().map(SqlGenerationResult.CteStage::alias).toList());
        assertEquals(List.of(2026, 10, 11, 11, "base", "base", 2, 3, 4),
                rendered.assembledValues(), name);
        assertFalse(rendered.assembledSql().contains("stage1.*"), rendered.assembledSql());
        String upper = rendered.assembledSql().toUpperCase();
        assertEquals(upper.indexOf("WITH "), upper.lastIndexOf("WITH "),
                "只能有一个根 WITH，禁止 nested WITH: " + rendered.assembledSql());
        assertEquals(rendered.assembledSql(), asSqlGenerationResult(rendered).getAssembledSql());
        assertEquals(rendered.assembledValues(), asSqlGenerationResult(rendered).getAssembledParams());
    }

    private static Stream<Arguments> cteDialectCases() {
        return Stream.of(
                Arguments.of("postgres", FDialect.POSTGRES_DIALECT, "VALUES (?)"),
                Arguments.of("sqlite", FDialect.SQLITE_DIALECT, "VALUES (?)"),
                Arguments.of("mysql8", FDialect.MYSQL8_DIALECT, "VALUES ROW(?)"),
                Arguments.of("sqlserver", FDialect.SQLSERVER_DIALECT,
                        "SELECT CAST(? AS INT) AS [openingYear]")
        );
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

    private static List<ResultStagePlan.Column> amountRootColumns() {
        return List.of(rootColumn("amount", ResultStagePlan.ColumnRole.PUBLIC_RESULT));
    }

    private static ResultStagePlan.Column rootColumn(
            String alias,
            ResultStagePlan.ColumnRole role) {
        return new ResultStagePlan.Column(
                alias,
                role,
                "agg",
                "final",
                DbColumnType.NUMBER,
                alias,
                BoundSqlExpression.of("\"" + alias + "\""));
    }

    private static SqlGenerationResult asSqlGenerationResult(ResultStagePlan.RenderResult result) {
        return new SqlGenerationResult(result.outerSql(), result.outerValues(), null, result.cteStages());
    }
}
