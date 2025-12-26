package com.foggyframework.dataset.jdbc.model.mongo.expression;

import com.foggyframework.dataset.jdbc.model.engine.expression.MongoFragment;
import com.foggyframework.dataset.jdbc.model.spi.DbColumnType;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MongoFragment 单元测试
 *
 * <p>测试 MongoFragment 类的基本功能，包括：
 * <ul>
 *   <li>字面量表达式创建</li>
 *   <li>二元运算表达式</li>
 *   <li>函数调用表达式</li>
 *   <li>类型推断</li>
 * </ul>
 * </p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@DisplayName("MongoFragment 单元测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MongoFragmentTest {

    // ==========================================
    // 字面量表达式测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("字面量 - 整数类型")
    void testLiteral_Integer() {
        MongoFragment fragment = MongoFragment.ofLiteral(42);
        assertEquals(42, fragment.getExpression());
        assertEquals(DbColumnType.INTEGER, fragment.getInferredType());
        assertFalse(fragment.isHasAggregate());

        log.info("整数字面量: {}", fragment);
    }

    @Test
    @Order(2)
    @DisplayName("字面量 - 浮点数类型")
    void testLiteral_Double() {
        MongoFragment fragment = MongoFragment.ofLiteral(3.14);
        assertEquals(3.14, fragment.getExpression());
        assertEquals(DbColumnType.NUMBER, fragment.getInferredType());

        log.info("浮点数字面量: {}", fragment);
    }

    @Test
    @Order(3)
    @DisplayName("字面量 - 字符串类型")
    void testLiteral_String() {
        MongoFragment fragment = MongoFragment.ofLiteral("hello world");
        assertEquals("hello world", fragment.getExpression());
        assertEquals(DbColumnType.TEXT, fragment.getInferredType());
        assertFalse(fragment.isHasAggregate());

        log.info("字符串字面量: {}", fragment);
    }

    @Test
    @Order(4)
    @DisplayName("字面量 - 布尔类型")
    void testLiteral_Boolean() {
        MongoFragment trueFragment = MongoFragment.ofLiteral(true);
        assertEquals(true, trueFragment.getExpression());
        assertEquals(DbColumnType.BOOL, trueFragment.getInferredType());

        MongoFragment falseFragment = MongoFragment.ofLiteral(false);
        assertEquals(false, falseFragment.getExpression());
        assertEquals(DbColumnType.BOOL, falseFragment.getInferredType());

        log.info("布尔字面量: true={}, false={}", trueFragment, falseFragment);
    }

    @Test
    @Order(5)
    @DisplayName("字面量 - null 值")
    void testLiteral_Null() {
        MongoFragment fragment = MongoFragment.ofLiteral(null);
        assertNull(fragment.getExpression());
        assertEquals(DbColumnType.UNKNOWN, fragment.getInferredType());

        log.info("null字面量: {}", fragment);
    }

    @Test
    @Order(6)
    @DisplayName("字面量 - 带类型指定")
    void testLiteral_WithType() {
        MongoFragment fragment = MongoFragment.ofLiteral(100, DbColumnType.MONEY);
        assertEquals(100, fragment.getExpression());
        assertEquals(DbColumnType.MONEY, fragment.getInferredType());

        log.info("带类型字面量: {}", fragment);
    }

    // ==========================================
    // 二元运算表达式测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("二元运算 - 加法")
    void testBinary_Add() {
        MongoFragment left = MongoFragment.ofLiteral(10);
        MongoFragment right = MongoFragment.ofLiteral(20);
        MongoFragment result = MongoFragment.binary(left, "$add", right);

        Object expr = result.getExpression();
        assertTrue(expr instanceof Document);
        Document doc = (Document) expr;
        assertTrue(doc.containsKey("$add"));

        List<?> args = (List<?>) doc.get("$add");
        assertEquals(2, args.size());
        assertEquals(10, args.get(0));
        assertEquals(20, args.get(1));

        log.info("加法运算: 10 + 20 -> {}", result);
    }

    @Test
    @Order(11)
    @DisplayName("二元运算 - 减法")
    void testBinary_Subtract() {
        MongoFragment left = MongoFragment.ofLiteral(100);
        MongoFragment right = MongoFragment.ofLiteral(30);
        MongoFragment result = MongoFragment.binary(left, "$subtract", right);

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$subtract"));

        log.info("减法运算: 100 - 30 -> {}", result);
    }

    @Test
    @Order(12)
    @DisplayName("二元运算 - 乘法")
    void testBinary_Multiply() {
        MongoFragment left = MongoFragment.ofLiteral(5);
        MongoFragment right = MongoFragment.ofLiteral(8);
        MongoFragment result = MongoFragment.binary(left, "$multiply", right);

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$multiply"));

        log.info("乘法运算: 5 * 8 -> {}", result);
    }

    @Test
    @Order(13)
    @DisplayName("二元运算 - 除法")
    void testBinary_Divide() {
        MongoFragment left = MongoFragment.ofLiteral(100);
        MongoFragment right = MongoFragment.ofLiteral(4);
        MongoFragment result = MongoFragment.binary(left, "$divide", right);

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$divide"));
        assertEquals(DbColumnType.NUMBER, result.getInferredType());

        log.info("除法运算: 100 / 4 -> {}", result);
    }

    @Test
    @Order(14)
    @DisplayName("二元运算 - 取模")
    void testBinary_Mod() {
        MongoFragment left = MongoFragment.ofLiteral(17);
        MongoFragment right = MongoFragment.ofLiteral(5);
        MongoFragment result = MongoFragment.binary(left, "$mod", right);

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$mod"));

        log.info("取模运算: 17 % 5 -> {}", result);
    }

    // ==========================================
    // 比较运算表达式测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("比较运算 - 大于")
    void testComparison_GreaterThan() {
        MongoFragment left = MongoFragment.ofLiteral(100);
        MongoFragment right = MongoFragment.ofLiteral(50);
        MongoFragment result = MongoFragment.comparison(left, "$gt", right);

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$gt"));
        assertEquals(DbColumnType.BOOL, result.getInferredType());

        log.info("大于比较: 100 > 50 -> {}", result);
    }

    @Test
    @Order(21)
    @DisplayName("比较运算 - 等于")
    void testComparison_Equal() {
        MongoFragment left = MongoFragment.ofLiteral(42);
        MongoFragment right = MongoFragment.ofLiteral(42);
        MongoFragment result = MongoFragment.comparison(left, "$eq", right);

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$eq"));
        assertEquals(DbColumnType.BOOL, result.getInferredType());

        log.info("等于比较: 42 == 42 -> {}", result);
    }

    // ==========================================
    // 逻辑运算表达式测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("逻辑运算 - AND")
    void testLogical_And() {
        MongoFragment left = MongoFragment.ofLiteral(true);
        MongoFragment right = MongoFragment.ofLiteral(false);
        MongoFragment result = MongoFragment.logical("$and", Arrays.asList(left, right));

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$and"));
        assertEquals(DbColumnType.BOOL, result.getInferredType());

        log.info("AND运算: true && false -> {}", result);
    }

    @Test
    @Order(31)
    @DisplayName("逻辑运算 - OR")
    void testLogical_Or() {
        MongoFragment left = MongoFragment.ofLiteral(true);
        MongoFragment right = MongoFragment.ofLiteral(false);
        MongoFragment result = MongoFragment.logical("$or", Arrays.asList(left, right));

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$or"));
        assertEquals(DbColumnType.BOOL, result.getInferredType());

        log.info("OR运算: true || false -> {}", result);
    }

    // ==========================================
    // 一元运算表达式测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("一元运算 - NOT")
    void testUnary_Not() {
        MongoFragment operand = MongoFragment.ofLiteral(true);
        MongoFragment result = MongoFragment.unary("$not", operand);

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$not"));
        assertEquals(DbColumnType.BOOL, result.getInferredType());

        log.info("NOT运算: !true -> {}", result);
    }

    @Test
    @Order(41)
    @DisplayName("一元运算 - ABS")
    void testUnary_Abs() {
        MongoFragment operand = MongoFragment.ofLiteral(-42);
        MongoFragment result = MongoFragment.unary("$abs", operand);

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$abs"));

        log.info("ABS运算: abs(-42) -> {}", result);
    }

    // ==========================================
    // 函数调用表达式测试
    // ==========================================

    @Test
    @Order(50)
    @DisplayName("函数调用 - ROUND (单参数)")
    void testFunction_RoundSingleArg() {
        MongoFragment value = MongoFragment.ofLiteral(3.14159);
        MongoFragment result = MongoFragment.function("$round", Arrays.asList(value));

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$round"));
        assertEquals(DbColumnType.NUMBER, result.getInferredType());

        log.info("ROUND函数(单参数): round(3.14159) -> {}", result);
    }

    @Test
    @Order(51)
    @DisplayName("函数调用 - ROUND (双参数)")
    void testFunction_RoundTwoArgs() {
        MongoFragment value = MongoFragment.ofLiteral(3.14159);
        MongoFragment decimals = MongoFragment.ofLiteral(2);
        MongoFragment result = MongoFragment.function("$round", Arrays.asList(value, decimals));

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$round"));

        List<?> args = (List<?>) doc.get("$round");
        assertEquals(2, args.size());

        log.info("ROUND函数(双参数): round(3.14159, 2) -> {}", result);
    }

    @Test
    @Order(52)
    @DisplayName("函数调用 - CONCAT")
    void testFunction_Concat() {
        MongoFragment str1 = MongoFragment.ofLiteral("Hello");
        MongoFragment str2 = MongoFragment.ofLiteral(" ");
        MongoFragment str3 = MongoFragment.ofLiteral("World");
        MongoFragment result = MongoFragment.function("$concat", Arrays.asList(str1, str2, str3));

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$concat"));
        assertEquals(DbColumnType.TEXT, result.getInferredType());

        List<?> args = (List<?>) doc.get("$concat");
        assertEquals(3, args.size());

        log.info("CONCAT函数: concat('Hello', ' ', 'World') -> {}", result);
    }

    @Test
    @Order(53)
    @DisplayName("函数调用 - 聚合函数 SUM")
    void testFunction_Sum() {
        MongoFragment value = MongoFragment.ofLiteral(100);
        MongoFragment result = MongoFragment.function("$sum", Arrays.asList(value));

        assertTrue(result.isHasAggregate());
        assertEquals("SUM", result.getAggregationType());
        assertEquals(DbColumnType.NUMBER, result.getInferredType());

        log.info("SUM函数: sum(100) -> {} (hasAggregate={})", result, result.isHasAggregate());
    }

    @Test
    @Order(54)
    @DisplayName("函数调用 - 聚合函数 AVG")
    void testFunction_Avg() {
        MongoFragment value = MongoFragment.ofLiteral(50);
        MongoFragment result = MongoFragment.function("$avg", Arrays.asList(value));

        assertTrue(result.isHasAggregate());
        assertEquals("AVG", result.getAggregationType());
        assertEquals(DbColumnType.NUMBER, result.getInferredType());

        log.info("AVG函数: avg(50) -> {} (hasAggregate={})", result, result.isHasAggregate());
    }

    // ==========================================
    // 条件表达式测试
    // ==========================================

    @Test
    @Order(60)
    @DisplayName("条件表达式 - COND")
    void testCond() {
        MongoFragment condition = MongoFragment.comparison(
                MongoFragment.ofLiteral(100),
                "$gt",
                MongoFragment.ofLiteral(50)
        );
        MongoFragment ifTrue = MongoFragment.ofLiteral("High");
        MongoFragment ifFalse = MongoFragment.ofLiteral("Low");

        MongoFragment result = MongoFragment.cond(condition, ifTrue, ifFalse);

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$cond"));

        List<?> args = (List<?>) doc.get("$cond");
        assertEquals(3, args.size());

        log.info("COND表达式: cond(100 > 50, 'High', 'Low') -> {}", result);
    }

    @Test
    @Order(61)
    @DisplayName("条件表达式 - IFNULL")
    void testIfNull() {
        MongoFragment value = MongoFragment.ofLiteral(null);
        MongoFragment defaultValue = MongoFragment.ofLiteral(0);

        MongoFragment result = MongoFragment.ifNull(value, defaultValue);

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$ifNull"));

        log.info("IFNULL表达式: ifNull(null, 0) -> {}", result);
    }

    // ==========================================
    // 复合表达式测试
    // ==========================================

    @Test
    @Order(70)
    @DisplayName("复合表达式 - 嵌套算术运算")
    void testComplex_NestedArithmetic() {
        // (10 * 5) - 8
        MongoFragment ten = MongoFragment.ofLiteral(10);
        MongoFragment five = MongoFragment.ofLiteral(5);
        MongoFragment eight = MongoFragment.ofLiteral(8);

        MongoFragment multiply = MongoFragment.binary(ten, "$multiply", five);
        MongoFragment result = MongoFragment.binary(multiply, "$subtract", eight);

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$subtract"));

        List<?> args = (List<?>) doc.get("$subtract");
        assertTrue(args.get(0) instanceof Document); // 嵌套的 $multiply

        log.info("嵌套算术: (10 * 5) - 8 -> {}", result);
    }

    @Test
    @Order(71)
    @DisplayName("复合表达式 - 聚合传播")
    void testComplex_AggregatePropagation() {
        // SUM(100) / 10
        MongoFragment sum = MongoFragment.function("$sum", Arrays.asList(MongoFragment.ofLiteral(100)));
        MongoFragment ten = MongoFragment.ofLiteral(10);
        MongoFragment result = MongoFragment.binary(sum, "$divide", ten);

        // 聚合状态应该传播
        assertTrue(result.isHasAggregate());

        log.info("聚合传播: sum(100) / 10 -> {} (hasAggregate={})", result, result.isHasAggregate());
    }

    @Test
    @Order(72)
    @DisplayName("复合表达式 - 多级嵌套")
    void testComplex_MultiLevel() {
        // ROUND(ABS((100 - 30) / 100) * 100, 2)
        MongoFragment hundred = MongoFragment.ofLiteral(100);
        MongoFragment thirty = MongoFragment.ofLiteral(30);
        MongoFragment two = MongoFragment.ofLiteral(2);

        MongoFragment diff = MongoFragment.binary(hundred, "$subtract", thirty);
        MongoFragment ratio = MongoFragment.binary(diff, "$divide", MongoFragment.ofLiteral(100));
        MongoFragment abs = MongoFragment.unary("$abs", ratio);
        MongoFragment percentage = MongoFragment.binary(abs, "$multiply", hundred);
        MongoFragment result = MongoFragment.function("$round", Arrays.asList(percentage, two));

        Document doc = (Document) result.getExpression();
        assertTrue(doc.containsKey("$round"));

        log.info("多级嵌套: round(abs((100-30)/100)*100, 2) -> {}", result);
    }

    // ==========================================
    // 辅助方法测试
    // ==========================================

    @Test
    @Order(80)
    @DisplayName("辅助方法 - asAddFieldsEntry")
    void testAsAddFieldsEntry() {
        MongoFragment fragment = MongoFragment.ofLiteral(42);
        Document entry = fragment.asAddFieldsEntry("myField");

        assertTrue(entry.containsKey("myField"));
        assertEquals(42, entry.get("myField"));

        log.info("asAddFieldsEntry: {}", entry);
    }

    @Test
    @Order(81)
    @DisplayName("辅助方法 - mergeReferences")
    void testMergeReferences() {
        MongoFragment fragment1 = MongoFragment.ofLiteral(10);
        MongoFragment fragment2 = MongoFragment.ofLiteral(20);

        // 初始应该没有引用
        assertTrue(fragment1.getReferencedColumns().isEmpty());
        assertTrue(fragment2.getReferencedColumns().isEmpty());

        // 合并不会改变（因为都没有引用）
        fragment1.mergeReferences(fragment2);
        assertTrue(fragment1.getReferencedColumns().isEmpty());

        log.info("mergeReferences 测试通过");
    }
}
