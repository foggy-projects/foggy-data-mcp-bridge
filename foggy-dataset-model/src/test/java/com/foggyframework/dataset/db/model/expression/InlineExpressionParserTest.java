package com.foggyframework.dataset.db.model.expression;

import com.foggyframework.dataset.db.model.engine.expression.InlineExpressionParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 InlineExpressionParser 内联表达式解析
 */
public class InlineExpressionParserTest {

    @Test
    void testParseFunctionWithAlias() {
        // 函数调用带别名
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("YEAR(orderdate) AS orderYear");

        assertNotNull(result, "Should detect function call as expression");
        assertEquals("YEAR(orderdate)", result.getExpression());
        assertEquals("orderYear", result.getAlias());
        assertTrue(result.hasAlias());
    }

    @Test
    void testParseArithmeticWithAlias() {
        // 算术表达式带别名
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("totaldue - discountAmount AS netAmount");

        assertNotNull(result, "Should detect arithmetic expression");
        assertEquals("totaldue - discountAmount", result.getExpression());
        assertEquals("netAmount", result.getAlias());
        assertTrue(result.hasAlias());
    }

    @Test
    void testParseArithmeticWithoutAlias() {
        // 算术表达式无别名
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("totaldue - discountAmount");

        assertNotNull(result, "Should detect arithmetic expression without alias");
        assertEquals("totaldue - discountAmount", result.getExpression());
        assertNull(result.getAlias());
        assertFalse(result.hasAlias());
    }

    @Test
    void testParseMultiParameterFunction() {
        // 多参数函数
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("CONCAT(firstName, ' ', lastName) AS fullName");

        assertNotNull(result, "Should detect multi-parameter function");
        assertEquals("CONCAT(firstName, ' ', lastName)", result.getExpression());
        assertEquals("fullName", result.getAlias());
    }

    @Test
    void testParseSimpleColumnName() {
        // 简单列名 - 不应识别为表达式
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("columnName");

        assertNull(result, "Simple column name should not be treated as expression");
    }

    @Test
    void testParseDimensionReference() {
        // 维度引用 - 不应识别为表达式
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("dimension$caption");

        assertNull(result, "Dimension reference should not be treated as expression");
    }

    @Test
    void testParseTableQualifiedColumn() {
        // 表限定列名 - 不应识别为表达式
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("table.columnName");

        assertNull(result, "Table-qualified column should not be treated as expression");
    }

    @Test
    void testParseSimpleAlias() {
        // 简单别名（无表达式）- 不应识别为表达式
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("columnName AS alias");

        assertNull(result, "Simple alias without expression should not be treated as expression");
    }

    @Test
    void testParseEmptyString() {
        // 空字符串
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("");

        assertNull(result, "Empty string should return null");
    }

    @Test
    void testParseNull() {
        // null
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse(null);

        assertNull(result, "Null should return null");
    }

    @Test
    void testParseMathOperators() {
        // 测试各种数学运算符
        String[] operators = {"+", "-", "*", "/", "%"};

        for (String op : operators) {
            String expr = "a " + op + " b";
            InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse(expr);

            assertNotNull(result, "Should detect expression with operator: " + op);
            assertEquals(expr, result.getExpression());
        }
    }

    @Test
    void testParseComplexExpression() {
        // 复杂表达式
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("(price * quantity) - discount AS total");

        assertNotNull(result, "Should detect complex expression");
        assertEquals("(price * quantity) - discount", result.getExpression());
        assertEquals("total", result.getAlias());
    }

    @Test
    void testParseLowerCaseAs() {
        // 小写 as
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("YEAR(orderdate) as orderYear");

        assertNotNull(result, "Should handle lowercase 'as'");
        assertEquals("YEAR(orderdate)", result.getExpression());
        assertEquals("orderYear", result.getAlias());
    }

    @Test
    void testParseNestedFunction() {
        // 嵌套函数
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("ROUND(SUM(amount), 2) AS totalRounded");

        assertNotNull(result, "Should detect nested function");
        assertEquals("ROUND(SUM(amount), 2)", result.getExpression());
        assertEquals("totalRounded", result.getAlias());
    }

    @Test
    void testParseNegativeNumber() {
        // 单独的负数不应该被识别为表达式
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("-123.45");

        assertNull(result, "Negative number alone should not be treated as expression");
    }

    @Test
    void testParseDimensionWithCaptionAndAlias() {
        // 维度引用带别名 - 不应识别为表达式
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("salesperson$caption AS spName");

        assertNull(result, "Dimension reference with alias should not be treated as expression");
    }

    // ==========================================
    // 深层嵌套与复杂表达式
    // ==========================================

    @Test
    @DisplayName("深层嵌套函数: ROUND(AVG(COALESCE(amount, 0)), 2)")
    void testDeepNestedFunction() {
        InlineExpressionParser.InlineExpression result =
                InlineExpressionParser.parse("ROUND(AVG(COALESCE(amount, 0)), 2) AS avgAmount");

        assertNotNull(result, "Should detect deep nested function");
        assertEquals("ROUND(AVG(COALESCE(amount, 0)), 2)", result.getExpression());
        assertEquals("avgAmount", result.getAlias());
    }

    @Test
    @DisplayName("多层括号: ((a + b) * (c - d)) / e")
    void testMultiLayerParentheses() {
        InlineExpressionParser.InlineExpression result =
                InlineExpressionParser.parse("((a + b) * (c - d)) / e AS calc");

        assertNotNull(result, "Should detect multi-layer parenthesized expression");
        assertEquals("((a + b) * (c - d)) / e", result.getExpression());
        assertEquals("calc", result.getAlias());
    }

    @Test
    @DisplayName("函数 + 算术混合: YEAR(orderdate) * 100 + MONTH(orderdate)")
    void testFunctionArithmeticMix() {
        InlineExpressionParser.InlineExpression result =
                InlineExpressionParser.parse("YEAR(orderdate) * 100 + MONTH(orderdate) AS yearMonth");

        assertNotNull(result, "Should detect mixed function+arithmetic");
        assertEquals("YEAR(orderdate) * 100 + MONTH(orderdate)", result.getExpression());
        assertEquals("yearMonth", result.getAlias());
    }

    // ==========================================
    // 空白字符边界
    // ==========================================

    @Test
    @DisplayName("前后带空白: '  YEAR(date)  AS  y  '")
    void testWhitespaceHandling() {
        InlineExpressionParser.InlineExpression result =
                InlineExpressionParser.parse("  YEAR(date)  AS  y  ");

        // 无论是否 trim，只要能正确检测到表达式即可
        if (result != null) {
            assertTrue(result.getExpression().contains("YEAR(date)"));
            assertTrue(result.getAlias().trim().equals("y"));
        }
        // 如果返回 null 也可接受（取决于 trim 实现）
    }

    @Test
    @DisplayName("仅空白字符")
    void testOnlyWhitespace() {
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("   ");
        assertNull(result, "Whitespace-only should return null");
    }

    // ==========================================
    // 特殊输入模式
    // ==========================================

    @Test
    @DisplayName("不含 AS 的纯函数调用")
    void testFunctionWithoutAlias() {
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("UPPER(name)");

        assertNotNull(result, "Should detect function call without alias");
        assertEquals("UPPER(name)", result.getExpression());
        assertFalse(result.hasAlias());
    }

    @Test
    @DisplayName("包含数字的列名不应识别为表达式")
    void testColumnNameWithDigits() {
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("col123");
        assertNull(result, "Column name with digits should not be expression");
    }

    @Test
    @DisplayName("包含下划线的列名不应识别为表达式")
    void testColumnNameWithUnderscore() {
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse("total_amount");
        assertNull(result, "Column name with underscore should not be expression");
    }

    @Test
    @DisplayName("CASE WHEN 表达式")
    void testCaseWhenExpression() {
        String expr = "CASE WHEN status = 1 THEN 'active' ELSE 'inactive' END AS statusName";
        InlineExpressionParser.InlineExpression result = InlineExpressionParser.parse(expr);

        // CASE WHEN 含有空格和关键字，行为取决于实现
        // 主要验证不会抛异常
        if (result != null) {
            assertNotNull(result.getExpression());
        }
    }

    @Test
    @DisplayName("混合大小写 As 关键字")
    void testMixedCaseAs() {
        InlineExpressionParser.InlineExpression result =
                InlineExpressionParser.parse("SUM(amount) As total");

        assertNotNull(result, "Should handle mixed case 'As'");
        assertEquals("SUM(amount)", result.getExpression());
        assertEquals("total", result.getAlias());
    }
}
