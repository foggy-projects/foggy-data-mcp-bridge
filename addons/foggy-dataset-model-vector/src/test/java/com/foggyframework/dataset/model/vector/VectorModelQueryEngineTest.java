package com.foggyframework.dataset.model.vector;

import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.VectorModelQueryEngine;
import com.foggyframework.dataset.model.impl.vector.VectorDbConfig;
import com.foggyframework.dataset.model.impl.vector.VectorQueryModel;
import com.foggyframework.dataset.model.impl.vector.VectorQueryModelImpl;
import com.foggyframework.dataset.model.spi.TableModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * VectorModelQueryEngine 单元测试
 *
 * <p>测试向量查询引擎的核心功能，包括：
 * <ul>
 *   <li>过滤条件解析</li>
 *   <li>向量搜索参数解析</li>
 *   <li>过滤表达式构建</li>
 * </ul>
 * </p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@DisplayName("VectorModelQueryEngine 单元测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VectorModelQueryEngineTest {

    private AutoCloseable mocks;

    @Mock
    private VectorQueryModel mockQueryModel;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    // ==========================================
    // 过滤表达式构建测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("过滤表达式 - 等值字符串")
    void testFilterExpression_EqualString() throws Exception {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(mockQueryModel, null);

        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("category");
        condition.setOp("=");
        condition.setValue("report");

        engine.getFilterConditions().add(condition);

        // 通过反射调用私有方法
        Method method = VectorModelQueryEngine.class.getDeclaredMethod("buildFilterExpression");
        method.setAccessible(true);
        String result = (String) method.invoke(engine);

        assertEquals("category == \"report\"", result);

        log.info("等值字符串过滤表达式: {}", result);
    }

    @Test
    @Order(2)
    @DisplayName("过滤表达式 - 等值整数")
    void testFilterExpression_EqualInteger() throws Exception {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(mockQueryModel, null);

        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("status");
        condition.setOp("=");
        condition.setValue(1);

        engine.getFilterConditions().add(condition);

        Method method = VectorModelQueryEngine.class.getDeclaredMethod("buildFilterExpression");
        method.setAccessible(true);
        String result = (String) method.invoke(engine);

        assertEquals("status == 1", result);

        log.info("等值整数过滤表达式: {}", result);
    }

    @Test
    @Order(3)
    @DisplayName("过滤表达式 - 不等于")
    void testFilterExpression_NotEqual() throws Exception {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(mockQueryModel, null);

        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("type");
        condition.setOp("!=");
        condition.setValue("draft");

        engine.getFilterConditions().add(condition);

        Method method = VectorModelQueryEngine.class.getDeclaredMethod("buildFilterExpression");
        method.setAccessible(true);
        String result = (String) method.invoke(engine);

        assertEquals("type != \"draft\"", result);

        log.info("不等于过滤表达式: {}", result);
    }

    @Test
    @Order(4)
    @DisplayName("过滤表达式 - 比较运算")
    void testFilterExpression_Comparison() throws Exception {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(mockQueryModel, null);

        VectorModelQueryEngine.FilterCondition c1 = new VectorModelQueryEngine.FilterCondition();
        c1.setField("price");
        c1.setOp(">");
        c1.setValue(100);

        VectorModelQueryEngine.FilterCondition c2 = new VectorModelQueryEngine.FilterCondition();
        c2.setField("quantity");
        c2.setOp("<=");
        c2.setValue(50);

        engine.getFilterConditions().add(c1);
        engine.getFilterConditions().add(c2);

        Method method = VectorModelQueryEngine.class.getDeclaredMethod("buildFilterExpression");
        method.setAccessible(true);
        String result = (String) method.invoke(engine);

        assertTrue(result.contains("price > 100"));
        assertTrue(result.contains("quantity <= 50"));
        assertTrue(result.contains(" and "));

        log.info("比较运算过滤表达式: {}", result);
    }

    @Test
    @Order(5)
    @DisplayName("过滤表达式 - IN 操作")
    void testFilterExpression_In() throws Exception {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(mockQueryModel, null);

        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("category");
        condition.setOp("in");
        condition.setValue(Arrays.asList("report", "manual", "faq"));

        engine.getFilterConditions().add(condition);

        Method method = VectorModelQueryEngine.class.getDeclaredMethod("buildFilterExpression");
        method.setAccessible(true);
        String result = (String) method.invoke(engine);

        assertTrue(result.contains("category in"));
        assertTrue(result.contains("\"report\""));
        assertTrue(result.contains("\"manual\""));
        assertTrue(result.contains("\"faq\""));

        log.info("IN过滤表达式: {}", result);
    }

    @Test
    @Order(6)
    @DisplayName("过滤表达式 - NOT IN 操作")
    void testFilterExpression_NotIn() throws Exception {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(mockQueryModel, null);

        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("status");
        condition.setOp("not in");
        condition.setValue(Arrays.asList(0, -1));

        engine.getFilterConditions().add(condition);

        Method method = VectorModelQueryEngine.class.getDeclaredMethod("buildFilterExpression");
        method.setAccessible(true);
        String result = (String) method.invoke(engine);

        assertTrue(result.contains("status not in"));

        log.info("NOT IN过滤表达式: {}", result);
    }

    @Test
    @Order(7)
    @DisplayName("过滤表达式 - LIKE 操作")
    void testFilterExpression_Like() throws Exception {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(mockQueryModel, null);

        VectorModelQueryEngine.FilterCondition condition = new VectorModelQueryEngine.FilterCondition();
        condition.setField("title");
        condition.setOp("like");
        condition.setValue("销售");

        engine.getFilterConditions().add(condition);

        Method method = VectorModelQueryEngine.class.getDeclaredMethod("buildFilterExpression");
        method.setAccessible(true);
        String result = (String) method.invoke(engine);

        assertTrue(result.contains("title like"));
        assertTrue(result.contains("%销售%"));

        log.info("LIKE过滤表达式: {}", result);
    }

    @Test
    @Order(8)
    @DisplayName("过滤表达式 - 空条件")
    void testFilterExpression_Empty() throws Exception {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(mockQueryModel, null);

        Method method = VectorModelQueryEngine.class.getDeclaredMethod("buildFilterExpression");
        method.setAccessible(true);
        String result = (String) method.invoke(engine);

        assertEquals("", result);

        log.info("空条件过滤表达式: '{}'", result);
    }

    @Test
    @Order(9)
    @DisplayName("过滤表达式 - 多条件组合")
    void testFilterExpression_MultipleConditions() throws Exception {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(mockQueryModel, null);

        VectorModelQueryEngine.FilterCondition c1 = new VectorModelQueryEngine.FilterCondition();
        c1.setField("category");
        c1.setOp("=");
        c1.setValue("report");

        VectorModelQueryEngine.FilterCondition c2 = new VectorModelQueryEngine.FilterCondition();
        c2.setField("status");
        c2.setOp("=");
        c2.setValue(1);

        VectorModelQueryEngine.FilterCondition c3 = new VectorModelQueryEngine.FilterCondition();
        c3.setField("priority");
        c3.setOp(">");
        c3.setValue(5);

        engine.getFilterConditions().add(c1);
        engine.getFilterConditions().add(c2);
        engine.getFilterConditions().add(c3);

        Method method = VectorModelQueryEngine.class.getDeclaredMethod("buildFilterExpression");
        method.setAccessible(true);
        String result = (String) method.invoke(engine);

        // 检查所有条件都存在
        assertTrue(result.contains("category == \"report\""));
        assertTrue(result.contains("status == 1"));
        assertTrue(result.contains("priority > 5"));

        // 检查使用 and 连接
        String[] parts = result.split(" and ");
        assertEquals(3, parts.length);

        log.info("多条件组合过滤表达式: {}", result);
    }

    // ==========================================
    // 向量搜索参数解析测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("向量搜索参数 - 基本Map格式")
    void testVectorSearchParams_BasicMap() {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(mockQueryModel, null);

        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("vector", Arrays.asList(0.1f, 0.2f, 0.3f));
        valueMap.put("topK", 10);

        SliceRequestDef slice = new SliceRequestDef("embedding", "similar", valueMap);

        // 通过反射调用私有方法
        try {
            Method method = VectorModelQueryEngine.class.getDeclaredMethod("processVectorSearchSlice", SliceRequestDef.class);
            method.setAccessible(true);
            method.invoke(engine, slice);

            VectorModelQueryEngine.VectorSearchParams params = engine.getVectorSearchParams();
            assertNotNull(params);
            assertEquals("embedding", params.getField());
            assertEquals(10, params.getTopK());
            assertNotNull(params.getVector());
            assertEquals(3, params.getVector().size());

            log.info("基本Map格式向量参数解析通过");
        } catch (Exception e) {
            log.error("测试失败", e);
            fail("测试失败: " + e.getMessage());
        }
    }

    @Test
    @Order(21)
    @DisplayName("向量搜索参数 - 带minScore")
    void testVectorSearchParams_WithMinScore() {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(mockQueryModel, null);

        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("vector", Arrays.asList(0.1f, 0.2f, 0.3f));
        valueMap.put("topK", 20);
        valueMap.put("minScore", 0.7);

        SliceRequestDef slice = new SliceRequestDef("content_embedding", "similar", valueMap);

        try {
            Method method = VectorModelQueryEngine.class.getDeclaredMethod("processVectorSearchSlice", SliceRequestDef.class);
            method.setAccessible(true);
            method.invoke(engine, slice);

            VectorModelQueryEngine.VectorSearchParams params = engine.getVectorSearchParams();
            assertNotNull(params);
            assertEquals("content_embedding", params.getField());
            assertEquals(20, params.getTopK());
            assertEquals(0.7f, params.getMinScore());

            log.info("带minScore向量参数解析通过");
        } catch (Exception e) {
            log.error("测试失败", e);
            fail("测试失败: " + e.getMessage());
        }
    }

    // ==========================================
    // 输出字段测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("输出字段 - 包含 _score")
    void testOutputFields_ContainsScore() {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(mockQueryModel, null);

        engine.getOutputFields().add("docId");
        engine.getOutputFields().add("title");
        engine.getOutputFields().add("_score");

        assertTrue(engine.getOutputFields().contains("_score"));
        assertEquals(3, engine.getOutputFields().size());

        log.info("输出字段包含_score测试通过");
    }

    // ==========================================
    // 边界条件测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("边界条件 - null 查询模型")
    void testNullQueryModel() {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(null, null);

        assertNull(engine.getQueryModel());

        log.info("null查询模型测试通过");
    }

    @Test
    @Order(41)
    @DisplayName("边界条件 - 初始状态")
    void testInitialState() {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(mockQueryModel, null);

        assertNull(engine.getVectorSearchParams());
        assertTrue(engine.getFilterConditions().isEmpty());
        assertTrue(engine.getOutputFields().isEmpty());
        assertEquals(0, engine.getTotalCount());

        log.info("初始状态测试通过");
    }

    // ==========================================
    // 辅助方法测试
    // ==========================================

    @Test
    @Order(50)
    @DisplayName("辅助方法 - Float列表转换")
    void testConvertToFloatList() throws Exception {
        VectorModelQueryEngine engine = new VectorModelQueryEngine(mockQueryModel, null);

        Method method = VectorModelQueryEngine.class.getDeclaredMethod("convertToFloatList", List.class);
        method.setAccessible(true);

        // Integer 列表转换
        List<Integer> intList = Arrays.asList(1, 2, 3);
        @SuppressWarnings("unchecked")
        List<Float> result1 = (List<Float>) method.invoke(engine, intList);
        assertEquals(3, result1.size());
        assertEquals(1.0f, result1.get(0));

        // Double 列表转换
        List<Double> doubleList = Arrays.asList(1.1, 2.2, 3.3);
        @SuppressWarnings("unchecked")
        List<Float> result2 = (List<Float>) method.invoke(engine, doubleList);
        assertEquals(3, result2.size());
        assertEquals(1.1f, result2.get(0), 0.01);

        log.info("Float列表转换测试通过");
    }
}
