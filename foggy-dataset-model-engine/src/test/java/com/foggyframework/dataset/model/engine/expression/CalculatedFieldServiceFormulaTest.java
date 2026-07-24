package com.foggyframework.dataset.model.engine.expression;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 计算字段服务 —— 公式字段引用提取测试
 *
 * <p>验证 columnGroups.formula 中的日期/时间函数不会被 AST 列引用提取器误判为普通字段。
 * 这是 Phase 3 bridge cleanup 后引擎原生支持 predefined formula 的关键保证。</p>
 *
 * @since 1.4 B1a — Python parity: formula reference extraction
 */
@DisplayName("CalculatedFieldService 公式字段引用提取测试")
class CalculatedFieldServiceFormulaTest {

    @Test
    @DisplayName("now() 不应被识别为字段引用")
    void nowIsNotAColumnReference() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("dateMaturity < now()");
        assertTrue(refs.contains("dateMaturity"), "dateMaturity should be a column reference");
        assertFalse(refs.contains("now"), "now should NOT be a column reference (it's a function)");
    }

    @Test
    @DisplayName("复杂 AR 公式 — now() 与字段正确分离")
    void arFormulaExtractsFieldsNotFunctions() {
        String arOverdueFormula =
                "IIF(move$moveType == 'out_invoice' "
                + "&& move$state == 'posted' "
                + "&& move$paymentState in ('not_paid', 'partial', 'in_payment') "
                + "&& dateMaturity < now(), "
                + "amountResidual, 0)";

        Set<String> refs = CalculatedFieldService.extractColumnReferences(arOverdueFormula);

        // Fields must be present
        assertTrue(refs.contains("dateMaturity"), "dateMaturity should be extracted");
        assertTrue(refs.contains("amountResidual"), "amountResidual should be extracted");
        assertTrue(refs.contains("move$moveType"), "move$moveType should be extracted");
        assertTrue(refs.contains("move$state"), "move$state should be extracted");
        assertTrue(refs.contains("move$paymentState"), "move$paymentState should be extracted");

        // Functions must NOT be treated as fields
        assertFalse(refs.contains("now"), "now() is a function, not a field");
        assertFalse(refs.contains("IIF"), "IIF is a function, not a field");
    }

    @Test
    @DisplayName("简单表达式的字段提取")
    void simpleExpressionFieldExtraction() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("amountTotal / orderCount");
        assertTrue(refs.contains("amountTotal"));
        assertTrue(refs.contains("orderCount"));
        assertEquals(2, refs.size());
    }

    @Test
    @DisplayName("resolveBaseColumnReferences — 传递展开嵌套计算字段")
    void resolveBaseRefsExpandsNestedCalcFields() {
        Map<String, String> calcFieldMap = Map.of(
                "netAmount", "debit - credit",
                "ratio", "netAmount / amountTotal"
        );

        Set<String> baseRefs = CalculatedFieldService.resolveBaseColumnReferences("ratio", calcFieldMap);

        // ratio → netAmount, amountTotal
        // netAmount → debit, credit
        // Final base refs: debit, credit, amountTotal
        assertTrue(baseRefs.contains("debit"), "debit should be a base ref via netAmount");
        assertTrue(baseRefs.contains("credit"), "credit should be a base ref via netAmount");
        assertTrue(baseRefs.contains("amountTotal"), "amountTotal should be a base ref");
        assertFalse(baseRefs.contains("netAmount"), "netAmount is a calc field, not a base ref");
        assertFalse(baseRefs.contains("ratio"), "ratio is a calc field, not a base ref");
    }

    @Test
    @DisplayName("空表达式返回空集合")
    void emptyExpressionReturnsEmptySet() {
        assertTrue(CalculatedFieldService.extractColumnReferences("").isEmpty());
        assertTrue(CalculatedFieldService.extractColumnReferences(null).isEmpty());
    }

    @Test
    @DisplayName("emptyDefault 包裹已有聚合公式为 COALESCE")
    void emptyDefaultWrapsExistingAggregateFormula() {
        CalculatedFieldDef def = new CalculatedFieldDef();
        def.setName("qualifiedAmount");
        def.setExpression("sum(amountTotal)");
        def.setEmptyDefault(0);

        SqlFragment sumFragment = SqlFragment.function(
                "SUM",
                List.of(SqlFragment.ofLiteral("t.amount_total"))
        );

        SqlFragment wrapped = CalculatedFieldService.applyEmptyDefault(sumFragment, def);

        assertEquals("COALESCE(SUM(t.amount_total), 0)", wrapped.getSql());
        assertTrue(wrapped.isHasAggregate());
        assertEquals("SUM", wrapped.getAggregationType());
    }

    @Test
    @DisplayName("emptyDefault + agg 推断应包裹聚合结果而不是聚合输入")
    void emptyDefaultWithInferredAggWrapsAggregateResult() {
        CalculatedFieldDef def = new CalculatedFieldDef();
        def.setName("qualifiedAmount");
        def.setExpression("amountTotal");
        def.setAgg("SUM");
        def.setEmptyDefault(0);

        SqlFragment amountFragment = SqlFragment.ofLiteral("t.amount_total");

        SqlFragment wrapped = CalculatedFieldService.applyEmptyDefault(amountFragment, def);

        assertEquals("COALESCE(SUM(t.amount_total), 0)", wrapped.getSql());
        assertTrue(wrapped.isHasAggregate());
        assertEquals("SUM", wrapped.getAggregationType());
    }
}
