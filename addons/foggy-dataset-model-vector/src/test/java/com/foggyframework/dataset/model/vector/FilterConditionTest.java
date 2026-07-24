package com.foggyframework.dataset.model.vector;

import com.foggyframework.dataset.model.engine.VectorModelQueryEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FilterCondition 单元测试
 *
 * <p>测试向量查询过滤条件类的基本功能</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@DisplayName("FilterCondition 单元测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FilterConditionTest {

    // ==========================================
    // 基本过滤条件测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("等值过滤 - 字符串")
    void testEqualString() {
        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("category");
        condition.setOp("=");
        condition.setValue("report");

        assertEquals("category", condition.getField());
        assertEquals("=", condition.getOp());
        assertEquals("report", condition.getValue());

        log.info("字符串等值过滤测试通过: {} {} {}", condition.getField(), condition.getOp(), condition.getValue());
    }

    @Test
    @Order(2)
    @DisplayName("等值过滤 - 整数")
    void testEqualInteger() {
        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("status");
        condition.setOp("=");
        condition.setValue(1);

        assertEquals("status", condition.getField());
        assertEquals("=", condition.getOp());
        assertEquals(1, condition.getValue());

        log.info("整数等值过滤测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("不等于过滤 - !=")
    void testNotEqual() {
        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("type");
        condition.setOp("!=");
        condition.setValue("draft");

        assertEquals("type", condition.getField());
        assertEquals("!=", condition.getOp());
        assertEquals("draft", condition.getValue());

        log.info("不等于过滤测试通过");
    }

    @Test
    @Order(4)
    @DisplayName("不等于过滤 - <>")
    void testNotEqualAlternative() {
        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("status");
        condition.setOp("<>");
        condition.setValue(0);

        assertEquals("<>", condition.getOp());

        log.info("<>过滤测试通过");
    }

    // ==========================================
    // 比较过滤条件测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("大于过滤")
    void testGreaterThan() {
        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("price");
        condition.setOp(">");
        condition.setValue(100.0);

        assertEquals("price", condition.getField());
        assertEquals(">", condition.getOp());
        assertEquals(100.0, condition.getValue());

        log.info("大于过滤测试通过");
    }

    @Test
    @Order(11)
    @DisplayName("大于等于过滤")
    void testGreaterThanOrEqual() {
        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("quantity");
        condition.setOp(">=");
        condition.setValue(10);

        assertEquals(">=", condition.getOp());
        assertEquals(10, condition.getValue());

        log.info("大于等于过滤测试通过");
    }

    @Test
    @Order(12)
    @DisplayName("小于过滤")
    void testLessThan() {
        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("age");
        condition.setOp("<");
        condition.setValue(30);

        assertEquals("<", condition.getOp());
        assertEquals(30, condition.getValue());

        log.info("小于过滤测试通过");
    }

    @Test
    @Order(13)
    @DisplayName("小于等于过滤")
    void testLessThanOrEqual() {
        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("score");
        condition.setOp("<=");
        condition.setValue(100);

        assertEquals("<=", condition.getOp());
        assertEquals(100, condition.getValue());

        log.info("小于等于过滤测试通过");
    }

    // ==========================================
    // 集合过滤条件测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("IN 过滤 - 字符串列表")
    void testInStringList() {
        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("category");
        condition.setOp("in");
        condition.setValue(Arrays.asList("report", "manual", "faq"));

        assertEquals("category", condition.getField());
        assertEquals("in", condition.getOp());
        assertTrue(condition.getValue() instanceof List);
        assertEquals(3, ((List<?>) condition.getValue()).size());

        log.info("IN字符串列表过滤测试通过");
    }

    @Test
    @Order(21)
    @DisplayName("IN 过滤 - 整数列表")
    void testInIntegerList() {
        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("status");
        condition.setOp("in");
        condition.setValue(Arrays.asList(1, 2, 3));

        assertEquals("in", condition.getOp());
        assertTrue(condition.getValue() instanceof List);

        log.info("IN整数列表过滤测试通过");
    }

    @Test
    @Order(22)
    @DisplayName("NOT IN 过滤")
    void testNotIn() {
        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("type");
        condition.setOp("not in");
        condition.setValue(Arrays.asList("deleted", "archived"));

        assertEquals("not in", condition.getOp());

        log.info("NOT IN过滤测试通过");
    }

    // ==========================================
    // 模糊过滤条件测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("LIKE 过滤")
    void testLike() {
        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("title");
        condition.setOp("like");
        condition.setValue("销售");

        assertEquals("title", condition.getField());
        assertEquals("like", condition.getOp());
        assertEquals("销售", condition.getValue());

        log.info("LIKE过滤测试通过");
    }

    // ==========================================
    // 综合测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("综合 - 多种类型值")
    void testVariousValueTypes() {
        // 字符串值
        VectorModelQueryEngine.FilterCondition c1 = new VectorModelQueryEngine.FilterCondition();
        c1.setField("name");
        c1.setOp("=");
        c1.setValue("test");
        assertTrue(c1.getValue() instanceof String);

        // 整数值
        VectorModelQueryEngine.FilterCondition c2 = new VectorModelQueryEngine.FilterCondition();
        c2.setField("count");
        c2.setOp(">");
        c2.setValue(100);
        assertTrue(c2.getValue() instanceof Integer);

        // 浮点值
        VectorModelQueryEngine.FilterCondition c3 = new VectorModelQueryEngine.FilterCondition();
        c3.setField("price");
        c3.setOp("<=");
        c3.setValue(99.99);
        assertTrue(c3.getValue() instanceof Double);

        // 布尔值
        VectorModelQueryEngine.FilterCondition c4 = new VectorModelQueryEngine.FilterCondition();
        c4.setField("active");
        c4.setOp("=");
        c4.setValue(true);
        assertTrue(c4.getValue() instanceof Boolean);

        // 列表值
        VectorModelQueryEngine.FilterCondition c5 = new VectorModelQueryEngine.FilterCondition();
        c5.setField("tags");
        c5.setOp("in");
        c5.setValue(Arrays.asList("a", "b", "c"));
        assertTrue(c5.getValue() instanceof List);

        log.info("多种类型值测试通过");
    }
}
