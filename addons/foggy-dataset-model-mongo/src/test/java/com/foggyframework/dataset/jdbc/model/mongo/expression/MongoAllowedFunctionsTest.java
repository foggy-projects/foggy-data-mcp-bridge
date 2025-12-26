package com.foggyframework.dataset.jdbc.model.mongo.expression;

import com.foggyframework.dataset.jdbc.model.engine.expression.MongoAllowedFunctions;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MongoAllowedFunctions 单元测试
 *
 * <p>测试 MongoDB 计算字段允许的函数白名单</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@DisplayName("MongoAllowedFunctions 单元测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MongoAllowedFunctionsTest {

    // ==========================================
    // 算术运算符测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("算术运算符 - 基本运算")
    void testArithmeticOperators_Basic() {
        assertTrue(MongoAllowedFunctions.isArithmeticOperator("+"));
        assertTrue(MongoAllowedFunctions.isArithmeticOperator("-"));
        assertTrue(MongoAllowedFunctions.isArithmeticOperator("*"));
        assertTrue(MongoAllowedFunctions.isArithmeticOperator("/"));
        assertTrue(MongoAllowedFunctions.isArithmeticOperator("%"));

        assertEquals("$add", MongoAllowedFunctions.getArithmeticOperator("+"));
        assertEquals("$subtract", MongoAllowedFunctions.getArithmeticOperator("-"));
        assertEquals("$multiply", MongoAllowedFunctions.getArithmeticOperator("*"));
        assertEquals("$divide", MongoAllowedFunctions.getArithmeticOperator("/"));
        assertEquals("$mod", MongoAllowedFunctions.getArithmeticOperator("%"));

        log.info("算术运算符测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("算术运算符 - 非算术运算符")
    void testArithmeticOperators_NotArithmetic() {
        assertFalse(MongoAllowedFunctions.isArithmeticOperator("=="));
        assertFalse(MongoAllowedFunctions.isArithmeticOperator(">"));
        assertFalse(MongoAllowedFunctions.isArithmeticOperator("&&"));

        log.info("非算术运算符识别测试通过");
    }

    // ==========================================
    // 比较运算符测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("比较运算符 - 等于/不等于")
    void testComparisonOperators_Equality() {
        assertTrue(MongoAllowedFunctions.isComparisonOperator("=="));
        assertTrue(MongoAllowedFunctions.isComparisonOperator("!="));

        assertEquals("$eq", MongoAllowedFunctions.getComparisonOperator("=="));
        assertEquals("$ne", MongoAllowedFunctions.getComparisonOperator("!="));

        log.info("等于/不等于运算符测试通过");
    }

    @Test
    @Order(11)
    @DisplayName("比较运算符 - 大小比较")
    void testComparisonOperators_Ordering() {
        assertTrue(MongoAllowedFunctions.isComparisonOperator(">"));
        assertTrue(MongoAllowedFunctions.isComparisonOperator(">="));
        assertTrue(MongoAllowedFunctions.isComparisonOperator("<"));
        assertTrue(MongoAllowedFunctions.isComparisonOperator("<="));

        assertEquals("$gt", MongoAllowedFunctions.getComparisonOperator(">"));
        assertEquals("$gte", MongoAllowedFunctions.getComparisonOperator(">="));
        assertEquals("$lt", MongoAllowedFunctions.getComparisonOperator("<"));
        assertEquals("$lte", MongoAllowedFunctions.getComparisonOperator("<="));

        log.info("大小比较运算符测试通过");
    }

    // ==========================================
    // 逻辑运算符测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("逻辑运算符 - AND/OR/NOT")
    void testLogicalOperators() {
        // 符号形式
        assertTrue(MongoAllowedFunctions.isLogicalOperator("&&"));
        assertTrue(MongoAllowedFunctions.isLogicalOperator("||"));
        assertTrue(MongoAllowedFunctions.isLogicalOperator("!"));

        // 关键字形式
        assertTrue(MongoAllowedFunctions.isLogicalOperator("AND"));
        assertTrue(MongoAllowedFunctions.isLogicalOperator("OR"));
        assertTrue(MongoAllowedFunctions.isLogicalOperator("NOT"));

        assertEquals("$and", MongoAllowedFunctions.getLogicalOperator("&&"));
        assertEquals("$or", MongoAllowedFunctions.getLogicalOperator("||"));
        assertEquals("$not", MongoAllowedFunctions.getLogicalOperator("!"));

        log.info("逻辑运算符测试通过");
    }

    // ==========================================
    // 数学函数测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("允许的函数 - 数学函数")
    void testAllowedFunctions_Math() {
        assertTrue(MongoAllowedFunctions.isAllowed("ABS"));
        assertTrue(MongoAllowedFunctions.isAllowed("ROUND"));
        assertTrue(MongoAllowedFunctions.isAllowed("FLOOR"));
        assertTrue(MongoAllowedFunctions.isAllowed("CEIL"));
        assertTrue(MongoAllowedFunctions.isAllowed("SQRT"));
        assertTrue(MongoAllowedFunctions.isAllowed("POW"));
        assertTrue(MongoAllowedFunctions.isAllowed("TRUNC"));

        assertEquals("$abs", MongoAllowedFunctions.getMongoOperator("ABS"));
        assertEquals("$round", MongoAllowedFunctions.getMongoOperator("ROUND"));
        assertEquals("$floor", MongoAllowedFunctions.getMongoOperator("FLOOR"));
        assertEquals("$ceil", MongoAllowedFunctions.getMongoOperator("CEIL"));

        log.info("数学函数测试通过");
    }

    // ==========================================
    // 日期函数测试
    // ==========================================

    @Test
    @Order(31)
    @DisplayName("允许的函数 - 日期函数")
    void testAllowedFunctions_Date() {
        assertTrue(MongoAllowedFunctions.isAllowed("YEAR"));
        assertTrue(MongoAllowedFunctions.isAllowed("MONTH"));
        assertTrue(MongoAllowedFunctions.isAllowed("DAY"));
        assertTrue(MongoAllowedFunctions.isAllowed("HOUR"));
        assertTrue(MongoAllowedFunctions.isAllowed("MINUTE"));
        assertTrue(MongoAllowedFunctions.isAllowed("SECOND"));

        assertEquals("$year", MongoAllowedFunctions.getMongoOperator("YEAR"));
        assertEquals("$month", MongoAllowedFunctions.getMongoOperator("MONTH"));
        assertEquals("$dayOfMonth", MongoAllowedFunctions.getMongoOperator("DAY"));

        log.info("日期函数测试通过");
    }

    // ==========================================
    // 字符串函数测试
    // ==========================================

    @Test
    @Order(32)
    @DisplayName("允许的函数 - 字符串函数")
    void testAllowedFunctions_String() {
        assertTrue(MongoAllowedFunctions.isAllowed("CONCAT"));
        assertTrue(MongoAllowedFunctions.isAllowed("UPPER"));
        assertTrue(MongoAllowedFunctions.isAllowed("LOWER"));
        assertTrue(MongoAllowedFunctions.isAllowed("SUBSTR"));
        assertTrue(MongoAllowedFunctions.isAllowed("LENGTH"));
        assertTrue(MongoAllowedFunctions.isAllowed("TRIM"));

        assertEquals("$concat", MongoAllowedFunctions.getMongoOperator("CONCAT"));
        assertEquals("$toUpper", MongoAllowedFunctions.getMongoOperator("UPPER"));
        assertEquals("$toLower", MongoAllowedFunctions.getMongoOperator("LOWER"));
        assertEquals("$substrCP", MongoAllowedFunctions.getMongoOperator("SUBSTR"));

        log.info("字符串函数测试通过");
    }

    // ==========================================
    // 条件函数测试
    // ==========================================

    @Test
    @Order(33)
    @DisplayName("允许的函数 - 条件函数")
    void testAllowedFunctions_Conditional() {
        assertTrue(MongoAllowedFunctions.isAllowed("IF"));
        assertTrue(MongoAllowedFunctions.isAllowed("COALESCE"));
        assertTrue(MongoAllowedFunctions.isAllowed("IFNULL"));
        assertTrue(MongoAllowedFunctions.isAllowed("NVL"));

        assertEquals("$cond", MongoAllowedFunctions.getMongoOperator("IF"));
        assertEquals("$ifNull", MongoAllowedFunctions.getMongoOperator("COALESCE"));
        assertEquals("$ifNull", MongoAllowedFunctions.getMongoOperator("IFNULL"));

        log.info("条件函数测试通过");
    }

    // ==========================================
    // 聚合函数测试
    // ==========================================

    @Test
    @Order(34)
    @DisplayName("允许的函数 - 聚合函数")
    void testAllowedFunctions_Aggregate() {
        assertTrue(MongoAllowedFunctions.isAllowed("SUM"));
        assertTrue(MongoAllowedFunctions.isAllowed("AVG"));
        assertTrue(MongoAllowedFunctions.isAllowed("COUNT"));
        assertTrue(MongoAllowedFunctions.isAllowed("MIN"));
        assertTrue(MongoAllowedFunctions.isAllowed("MAX"));

        assertTrue(MongoAllowedFunctions.isAggregateFunction("SUM"));
        assertTrue(MongoAllowedFunctions.isAggregateFunction("AVG"));
        assertTrue(MongoAllowedFunctions.isAggregateFunction("COUNT"));
        assertTrue(MongoAllowedFunctions.isAggregateFunction("MIN"));
        assertTrue(MongoAllowedFunctions.isAggregateFunction("MAX"));

        assertEquals("$sum", MongoAllowedFunctions.getMongoOperator("SUM"));
        assertEquals("$avg", MongoAllowedFunctions.getMongoOperator("AVG"));
        assertEquals("$sum", MongoAllowedFunctions.getMongoOperator("COUNT")); // COUNT uses $sum:1

        log.info("聚合函数测试通过");
    }

    // ==========================================
    // 不允许的函数测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("不允许的函数")
    void testDisallowedFunctions() {
        assertFalse(MongoAllowedFunctions.isAllowed("EVAL"));
        assertFalse(MongoAllowedFunctions.isAllowed("EXEC"));
        assertFalse(MongoAllowedFunctions.isAllowed("SYSTEM"));
        assertFalse(MongoAllowedFunctions.isAllowed("UNKNOWN_FUNC"));

        assertNull(MongoAllowedFunctions.getMongoOperator("UNKNOWN"));

        log.info("不允许的函数识别测试通过");
    }

    // ==========================================
    // 大小写不敏感测试
    // ==========================================

    @Test
    @Order(50)
    @DisplayName("大小写不敏感")
    void testCaseInsensitive() {
        assertTrue(MongoAllowedFunctions.isAllowed("SUM"));
        assertTrue(MongoAllowedFunctions.isAllowed("sum"));
        assertTrue(MongoAllowedFunctions.isAllowed("Sum"));

        assertEquals("$sum", MongoAllowedFunctions.getMongoOperator("SUM"));
        assertEquals("$sum", MongoAllowedFunctions.getMongoOperator("sum"));

        log.info("大小写不敏感测试通过");
    }

    // ==========================================
    // 非聚合函数判断
    // ==========================================

    @Test
    @Order(60)
    @DisplayName("非聚合函数判断")
    void testNonAggregateFunctions() {
        assertFalse(MongoAllowedFunctions.isAggregateFunction("ABS"));
        assertFalse(MongoAllowedFunctions.isAggregateFunction("ROUND"));
        assertFalse(MongoAllowedFunctions.isAggregateFunction("CONCAT"));
        assertFalse(MongoAllowedFunctions.isAggregateFunction("YEAR"));
        assertFalse(MongoAllowedFunctions.isAggregateFunction("IF"));

        log.info("非聚合函数判断测试通过");
    }
}
