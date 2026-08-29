package com.foggyframework.dataset.model.engine.stage.result;

import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.DbColumnType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@DisplayName("方案 A：请求级 Graph + BaseProjectionPlan prepare 契约")
class ResultStagePreparationTest {

    @Test
    @DisplayName("MAIN/TOTAL 必须从同一 preparation 绑定 graph、root columns 与物理投影")
    void mainAndTotalBindFromOnePreparedProjectionSource() {
        ResultStagePlan.Graph graph = ResultStagePlan.Graph.create(
                ResultStagePlanContractTest.ctePlan(List.of()),
                ResultStagePlanContractTest.windowSpecs());
        DbColumn openingYear = mock(DbColumn.class);
        DbColumn hiddenAmount = mock(DbColumn.class);
        DbColumn averageSum = mock(DbColumn.class);
        DbColumn averageCount = mock(DbColumn.class);

        ResultStagePreparation.BaseProjection main = new ResultStagePreparation.BaseProjection(
                List.of(
                        projection(openingYear, column(
                                "openingYear", ResultStagePlan.ColumnRole.PUBLIC_RESULT, "final")),
                        projection(hiddenAmount, column(
                                "receivableAmount", ResultStagePlan.ColumnRole.HIDDEN_DEPENDENCY,
                                "window_result"))),
                List.of());
        ResultStagePreparation.BaseProjection total = new ResultStagePreparation.BaseProjection(
                List.of(
                        projection(openingYear, column(
                                "openingYear", ResultStagePlan.ColumnRole.PUBLIC_RESULT, "final")),
                        projection(hiddenAmount, column(
                                "receivableAmount", ResultStagePlan.ColumnRole.HIDDEN_DEPENDENCY,
                                "window_result")),
                        projection(averageSum, column(
                                "__foggy_avg_sum_0",
                                ResultStagePlan.ColumnRole.INTERNAL_AGGREGATE_STATE, "final")),
                        projection(averageCount, column(
                                "__foggy_avg_count_0",
                                ResultStagePlan.ColumnRole.INTERNAL_AGGREGATE_STATE, "final"))),
                List.of("sum-arg", "count-arg"));
        ResultStagePreparation prepared = new ResultStagePreparation(
                graph, new ResultStagePreparation.BaseProjectionPlan(main, total));

        ResultStagePlan.Executable mainExecutable = prepared.bind(
                ResultStagePlan.Mode.MAIN,
                List.of(),
                "agg",
                BoundSqlExpression.of("SELECT 1 AS \"openingYear\", 10 AS \"receivableAmount\""),
                ResultStagePlanContractTest.publicProjection());
        ResultStagePlan.Executable totalExecutable = prepared.bind(
                ResultStagePlan.Mode.TOTAL,
                List.of(),
                "agg",
                new BoundSqlExpression(
                        "SELECT ? AS \"__foggy_avg_sum_0\", ? AS \"__foggy_avg_count_0\"",
                        List.of("sum-arg", "count-arg")),
                ResultStagePlanContractTest.totalProjection());

        assertSame(graph, prepared.graph());
        assertSame(graph, mainExecutable.graph());
        assertSame(graph, totalExecutable.graph());
        assertEquals(main.columns(), mainExecutable.root().columns());
        assertEquals(total.columns(), totalExecutable.root().columns());
        assertEquals(List.of(openingYear, hiddenAmount), prepared.sourceColumns(ResultStagePlan.Mode.MAIN));
        assertEquals(List.of(openingYear, hiddenAmount, averageSum, averageCount),
                prepared.sourceColumns(ResultStagePlan.Mode.TOTAL));
        assertEquals(List.of("sum-arg", "count-arg"),
                prepared.expressionValues(ResultStagePlan.Mode.TOTAL));
    }

    @Test
    @DisplayName("projection producer 和 lifecycle 不一致必须在 prepare 阶段 fail-closed")
    void invalidBaseProjectionLifecycleFailsClosedBeforeVisitor() {
        ResultStagePlan.Graph graph = ResultStagePlan.Graph.create(
                ResultStagePlanContractTest.ctePlan(List.of()),
                ResultStagePlanContractTest.windowSpecs());
        ResultStagePlan.Column invalid = new ResultStagePlan.Column(
                "amount",
                ResultStagePlan.ColumnRole.HIDDEN_DEPENDENCY,
                "missing",
                "window_result",
                DbColumnType.NUMBER,
                "amount",
                BoundSqlExpression.of("\"amount\""));
        ResultStagePreparation.BaseProjection projection = new ResultStagePreparation.BaseProjection(
                List.of(projection(mock(DbColumn.class), invalid)), List.of());

        assertThrows(IllegalArgumentException.class,
                () -> new ResultStagePreparation(
                        graph,
                        new ResultStagePreparation.BaseProjectionPlan(projection, projection)));
    }

    private static ResultStagePreparation.Projection projection(
            DbColumn source,
            ResultStagePlan.Column column) {
        return new ResultStagePreparation.Projection(source, column);
    }

    private static ResultStagePlan.Column column(
            String alias,
            ResultStagePlan.ColumnRole role,
            String lastConsumer) {
        return new ResultStagePlan.Column(
                alias,
                role,
                "agg",
                lastConsumer,
                DbColumnType.NUMBER,
                alias,
                BoundSqlExpression.of("\"" + alias + "\""));
    }
}
