package com.foggyframework.dataset.model.engine.total;

import com.foggyframework.dataset.model.engine.expression.BoundSqlExpression;
import com.foggyframework.dataset.model.engine.expression.TotalExpressionNode;
import com.foggyframework.dataset.model.spi.DbColumnType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("totalData 代数聚合计划安全边界")
class TotalDataAggregatePlanTest {

    @Test
    @DisplayName("AVG DISTINCT 没有集合状态时必须 fail closed")
    void distinctAverageShouldBeRefused() {
        TotalDataAggregatePlan plan = planFor(
                BoundSqlExpression.of("DISTINCT t.unit_price"));

        assertEquals(TotalDataAggregatePlan.LoweringStatus.REFUSED, plan.getStatus());
    }

    @Test
    @DisplayName("聚合源包含未绑定 JDBC 占位符时必须 fail closed")
    void unboundAggregateArgumentShouldBeRefused() {
        TotalDataAggregatePlan plan = planFor(
                BoundSqlExpression.of("CASE WHEN t.status = ? THEN t.unit_price END"));

        assertEquals(TotalDataAggregatePlan.LoweringStatus.REFUSED, plan.getStatus());
    }

    @Test
    @DisplayName("聚合源参数完整时必须保留独立参数所有权")
    void boundAggregateArgumentShouldPreserveValues() {
        BoundSqlExpression source = new BoundSqlExpression(
                "CASE WHEN t.status = ? THEN t.unit_price END", List.of("PAID"));

        TotalDataAggregatePlan plan = planFor(source);

        assertEquals(TotalDataAggregatePlan.LoweringStatus.LOWERED, plan.getStatus());
        assertEquals(List.of("PAID"), plan.getStates().get(0).source().values());
    }

    @Test
    @DisplayName("SQL 字面量中的问号不能被误判为 JDBC 占位符")
    void quotedQuestionMarkShouldNotBeTreatedAsPlaceholder() {
        TotalDataAggregatePlan plan = planFor(
                BoundSqlExpression.of("CASE WHEN t.code = '?' THEN t.unit_price END"));

        assertEquals(TotalDataAggregatePlan.LoweringStatus.LOWERED, plan.getStatus());
    }

    private TotalDataAggregatePlan planFor(BoundSqlExpression source) {
        TotalExpressionNode expression = TotalExpressionNode.aggregate("AVG", source);
        TotalDataAggregatePlan.Builder builder = new TotalDataAggregatePlan.Builder();
        builder.addPublicExpression("averagePrice", expression);
        builder.bindLeaves("averagePrice", expression, DbColumnType.NUMBER);
        return builder.build();
    }
}
