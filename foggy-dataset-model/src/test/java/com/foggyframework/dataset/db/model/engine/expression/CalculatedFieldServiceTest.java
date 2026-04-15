package com.foggyframework.dataset.db.model.engine.expression;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CalculatedFieldService.extractColumnReferences(String) 单元测试
 * <p>
 * 验证从表达式字符串中提取列引用的正确性。
 * 解析器使用 FSScript (compileEl) 产生 SQL 表达式 AST 节点。
 * </p>
 *
 * @since 8.2.0
 */
@DisplayName("CalculatedFieldService 列引用提取测试")
class CalculatedFieldServiceTest {

    @Test
    @DisplayName("简单字段 — 返回单个列引用")
    void extractRefs_simpleField() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("amount");
        assertEquals(Set.of("amount"), refs);
    }

    @Test
    @DisplayName("二元表达式 — 提取左右两侧列引用")
    void extractRefs_binaryExpr() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("a + b");
        assertEquals(Set.of("a", "b"), refs);
    }

    @Test
    @DisplayName("函数表达式 — 提取函数参数中的列引用")
    void extractRefs_functionExpr() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("ROUND(amount * rate, 2)");
        assertEquals(Set.of("amount", "rate"), refs);
    }

    @Test
    @DisplayName("聚合表达式 — 提取聚合函数内部的列引用")
    void extractRefs_aggregateExpr() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("SUM(a + b)");
        assertEquals(Set.of("a", "b"), refs);
    }

    @Test
    @DisplayName("嵌套函数 — 提取内层函数参数的列引用")
    void extractRefs_nestedFunction() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("YEAR(orderDate)");
        assertEquals(Set.of("orderDate"), refs);
    }

    @Test
    @DisplayName("纯字面量 — 返回空集合")
    void extractRefs_literal() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("100");
        assertTrue(refs.isEmpty(), "纯字面量表达式不应包含列引用");
    }

    @Test
    @DisplayName("null 或空字符串 — 返回空集合")
    void extractRefs_nullOrEmpty() {
        Set<String> refsNull = CalculatedFieldService.extractColumnReferences(null);
        assertTrue(refsNull.isEmpty(), "null 表达式应返回空集合");

        Set<String> refsEmpty = CalculatedFieldService.extractColumnReferences("");
        assertTrue(refsEmpty.isEmpty(), "空字符串表达式应返回空集合");
    }

    @Test
    @DisplayName("多列引用 — 提取所有不同的列")
    void extractRefs_multipleRefs() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("a * b + c - d");
        assertEquals(Set.of("a", "b", "c", "d"), refs);
    }

    @Test
    @DisplayName("复杂嵌套函数 — 提取所有层级的列引用")
    void extractRefs_complexNested() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("ROUND(amount * rate, 2) + YEAR(orderDate)");
        assertTrue(refs.contains("amount"));
        assertTrue(refs.contains("rate"));
        assertTrue(refs.contains("orderDate"));
        assertEquals(3, refs.size());
    }

    @Test
    @DisplayName("表达式仅含字面量和函数（无列引用）— 返回空集合")
    void extractRefs_onlyLiteralsAndFunctions() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("1 + 2 * 3");
        assertTrue(refs.isEmpty(), "纯算术字面量表达式不应包含列引用");
    }

    @Test
    @DisplayName("重复列引用 — 去重")
    void extractRefs_duplicateColumns() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("a + a + a * b");
        assertEquals(Set.of("a", "b"), refs);
    }

    @Test
    @DisplayName("一元运算符 — 提取操作数列引用")
    void extractRefs_unaryOperator() {
        Set<String> refs = CalculatedFieldService.extractColumnReferences("-amount");
        assertEquals(Set.of("amount"), refs);
    }
}
