package com.foggyframework.dataset.model.engine.compose.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M4 · extract_column_alias edge-case coverage.
 *
 * <p>Mirrors Python {@code tests/compose/schema/test_alias_extraction.py}.</p>
 */
@DisplayName("M4 AliasExtractor")
class AliasExtractorTest {

    @Nested
    @DisplayName("裸列名 Bare column names")
    class BareColumnNames {

        @Test
        @DisplayName("plain identifier")
        void plainIdentifier() {
            ColumnAliasParts parts = AliasExtractor.extract("orderId");
            assertEquals("orderId", parts.expression());
            assertEquals("orderId", parts.outputName());
            assertFalse(parts.hasAlias());
        }

        @Test
        @DisplayName("dimension path `customer$id` 是合法 bare output")
        void dimensionPath() {
            ColumnAliasParts parts = AliasExtractor.extract("customer$id");
            assertEquals("customer$id", parts.outputName());
            assertFalse(parts.hasAlias());
        }

        @Test
        @DisplayName("首尾空白被 strip")
        void whitespaceIsStripped() {
            ColumnAliasParts parts = AliasExtractor.extract("   orderId   ");
            assertEquals("orderId", parts.expression());
            assertEquals("orderId", parts.outputName());
        }
    }

    @Nested
    @DisplayName("函数表达式")
    class FunctionExpressions {

        @Test
        @DisplayName("无 alias 的聚合函数 output 保留整表达式")
        void aggregateWithoutAlias() {
            ColumnAliasParts parts = AliasExtractor.extract("SUM(amount)");
            assertEquals("SUM(amount)", parts.expression());
            assertEquals("SUM(amount)", parts.outputName());
            assertFalse(parts.hasAlias());
        }

        @Test
        @DisplayName("嵌套 IIF 表达式整体当 output")
        void nestedIifExpression() {
            String spec = "SUM(IIF(isOverdue==1, residualAmount, 0))";
            ColumnAliasParts parts = AliasExtractor.extract(spec);
            assertEquals(spec, parts.outputName());
            assertFalse(parts.hasAlias());
        }
    }

    @Nested
    @DisplayName("alias 提取")
    class AliasExtraction {

        @Test
        @DisplayName("大写 AS")
        void uppercaseAs() {
            ColumnAliasParts parts = AliasExtractor.extract("orderId AS oid");
            assertEquals("orderId", parts.expression());
            assertEquals("oid", parts.outputName());
            assertTrue(parts.hasAlias());
        }

        @Test
        @DisplayName("小写 as")
        void lowercaseAs() {
            ColumnAliasParts parts = AliasExtractor.extract("orderId as oid");
            assertEquals("oid", parts.outputName());
        }

        @Test
        @DisplayName("混合大小写 As")
        void mixedCaseAs() {
            ColumnAliasParts parts = AliasExtractor.extract("orderId As oid");
            assertEquals("oid", parts.outputName());
        }

        @Test
        @DisplayName("聚合函数 + alias")
        void aggregateWithAlias() {
            ColumnAliasParts parts = AliasExtractor.extract("SUM(amount) AS totalAmount");
            assertEquals("SUM(amount)", parts.expression());
            assertEquals("totalAmount", parts.outputName());
        }

        @Test
        @DisplayName("深层嵌套 + alias")
        void deeplyNestedWithAlias() {
            String spec = "SUM(IIF(isOverdue == 1, residualAmount, 0)) AS customerOverdue";
            ColumnAliasParts parts = AliasExtractor.extract(spec);
            assertEquals("SUM(IIF(isOverdue == 1, residualAmount, 0))", parts.expression());
            assertEquals("customerOverdue", parts.outputName());
        }

        @Test
        @DisplayName("AS 两侧多余空白被吸收")
        void extraWhitespaceAroundAs() {
            ColumnAliasParts parts = AliasExtractor.extract("   orderId   AS   oid   ");
            assertEquals("orderId", parts.expression());
            assertEquals("oid", parts.outputName());
        }

        @Test
        @DisplayName("维度路径 + alias")
        void dimensionPathAlias() {
            ColumnAliasParts parts = AliasExtractor.extract("customer$id AS customerId");
            assertEquals("customer$id", parts.expression());
            assertEquals("customerId", parts.outputName());
        }
    }

    @Nested
    @DisplayName("AS 在标识符内 / 字符串字面量内不触发分割")
    class AsInsideIdentifierNotMatched {

        @Test
        @DisplayName("标识符内 `SUM(ASSETS)` — AS 无两侧空白，不分割")
        void asSubstringInIdentifier() {
            ColumnAliasParts parts = AliasExtractor.extract("SUM(ASSETS)");
            assertEquals("SUM(ASSETS)", parts.outputName());
            assertFalse(parts.hasAlias());
        }

        @Test
        @DisplayName("字符串字面量内的 AS 被 pattern 匹配但 alias 校验失败，回退为整字符串 expression")
        void asInsideStringLiteralDoesNotSplit() {
            // Whitespace-anchored AS does match inside literal, but the
            // trailing slot `bar'` is not a legal identifier → falls back.
            ColumnAliasParts parts = AliasExtractor.extract("'foo AS bar'");
            assertFalse(parts.hasAlias());
            assertEquals("'foo AS bar'", parts.outputName());
        }
    }

    @Nested
    @DisplayName("恶意 / 异常输入")
    class MalformedInputs {

        @Test
        @DisplayName("null 被拒绝")
        void nullRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> AliasExtractor.extract(null));
        }

        @Test
        @DisplayName("空字符串被拒绝")
        void emptyStringRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> AliasExtractor.extract(""));
        }

        @Test
        @DisplayName("纯空白字符串被拒绝")
        void whitespaceOnlyRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> AliasExtractor.extract("   "));
        }

        @Test
        @DisplayName("`' AS alias'` 被整体当作 expression （安全回退）")
        void leadingAsTreatedAsExpression() {
            // After strip: "AS alias"; \s+AS\s+ pattern needs leading
            // whitespace so no match. Whole string becomes expression.
            ColumnAliasParts parts = AliasExtractor.extract(" AS alias");
            assertFalse(parts.hasAlias());
            assertEquals("AS alias", parts.expression());
        }

        @Test
        @DisplayName("alias 以数字开头（非法 ident）回退为整字符串 expression")
        void aliasWithBadIdentifierFallsBack() {
            ColumnAliasParts parts = AliasExtractor.extract("x AS 1foo");
            assertFalse(parts.hasAlias());
            assertEquals("x AS 1foo", parts.outputName());
        }
    }

    @Nested
    @DisplayName("值对象契约")
    class ValueSemantics {

        @Test
        @DisplayName("ColumnAliasParts 值相等 + hash 一致")
        void columnAliasPartsEquality() {
            ColumnAliasParts a = new ColumnAliasParts("x", "x", false);
            ColumnAliasParts b = new ColumnAliasParts("x", "x", false);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }
}
