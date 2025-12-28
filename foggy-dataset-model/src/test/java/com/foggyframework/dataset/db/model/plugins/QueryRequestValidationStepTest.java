package com.foggyframework.dataset.db.model.plugins;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.*;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.QueryRequestValidationStep;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * QueryRequestValidationStep 单元测试
 * <p>
 * 测试查询请求参数的校验功能，包括：
 * <ul>
 *   <li>slice 条件的 field、op、value 校验</li>
 *   <li>操作符合法性校验</li>
 *   <li>groupBy 的 field、agg 校验</li>
 *   <li>orderBy 的 field、order 校验</li>
 * </ul>
 * </p>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("QueryRequestValidationStep 参数校验测试")
class QueryRequestValidationStepTest {

    private QueryRequestValidationStep validationStep;
    private SqlFormulaService mockSqlFormulaService;

    @BeforeEach
    void setUp() {
        // 创建 Mock SqlFormulaService
        mockSqlFormulaService = Mockito.mock(SqlFormulaService.class);

        // 配置支持的操作符
        when(mockSqlFormulaService.supports("=")).thenReturn(true);
        when(mockSqlFormulaService.supports(">=")).thenReturn(true);
        when(mockSqlFormulaService.supports("in")).thenReturn(true);
        when(mockSqlFormulaService.supports("like")).thenReturn(true);
        when(mockSqlFormulaService.supports("null")).thenReturn(true);
        when(mockSqlFormulaService.supports("invalid_op")).thenReturn(false);

        // 创建 validationStep 并注入 mock service
        validationStep = new QueryRequestValidationStep();
        try {
            var field = QueryRequestValidationStep.class.getDeclaredField("sqlFormulaService");
            field.setAccessible(true);
            field.set(validationStep, mockSqlFormulaService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock service", e);
        }
    }

    private ModelResultContext createContext(DbQueryRequestDef queryRequest) {
        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);
        return new ModelResultContext(pagingRequest, null);
    }

    // ==============================================
    // Slice 校验测试
    // ==============================================

    @Test
    @Order(1)
    @DisplayName("正常的 slice 条件应该通过校验")
    void testValidSlice() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setSlice(Arrays.asList(
            new SliceRequestDef("orderStatus", "=", "COMPLETED"),
            new SliceRequestDef("amount", ">=", 100)
        ));

        ModelResultContext ctx = createContext(queryRequest);

        assertDoesNotThrow(() -> validationStep.beforeQuery(ctx));
        log.info("正常 slice 条件校验通过");
    }

    @Test
    @Order(2)
    @DisplayName("slice 的 field 为空应该抛出异常")
    void testSliceFieldEmpty() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setSlice(Collections.singletonList(
            new SliceRequestDef("", "=", "value")
        ));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("field") && exception.getMessage().contains("不能为空"));
        log.info("field 为空校验生效: {}", exception.getMessage());
    }

    @Test
    @Order(3)
    @DisplayName("slice 的 op 为空应该抛出异常")
    void testSliceOpEmpty() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setSlice(Collections.singletonList(
            new SliceRequestDef("orderStatus", "", "COMPLETED")
        ));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("op") && exception.getMessage().contains("不能为空"));
        log.info("op 为空校验生效: {}", exception.getMessage());
    }

    @Test
    @Order(4)
    @DisplayName("slice 的 op 不合法应该抛出异常")
    void testSliceOpInvalid() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setSlice(Collections.singletonList(
            new SliceRequestDef("orderStatus", "invalid_op", "COMPLETED")
        ));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("不合法") && exception.getMessage().contains("invalid_op"));
        log.info("op 不合法校验生效: {}", exception.getMessage());
    }

    @Test
    @Order(5)
    @DisplayName("slice 的 value 为空（非null操作符）应该抛出异常")
    void testSliceValueEmpty() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setSlice(Collections.singletonList(
            new SliceRequestDef("orderStatus", "=", null)
        ));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("value") && exception.getMessage().contains("不能为空"));
        log.info("value 为空校验生效: {}", exception.getMessage());
    }

    @Test
    @Order(6)
    @DisplayName("null 操作符不需要 value 应该通过校验")
    void testNullOperatorNoValue() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setSlice(Collections.singletonList(
            new SliceRequestDef("deletedAt", "null", null)
        ));

        ModelResultContext ctx = createContext(queryRequest);

        assertDoesNotThrow(() -> validationStep.beforeQuery(ctx));
        log.info("null 操作符无需 value 校验通过");
    }

    // ==============================================
    // GroupBy 校验测试
    // ==============================================

    @Test
    @Order(10)
    @DisplayName("正常的 groupBy 应该通过校验")
    void testValidGroupBy() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("categoryName");
        group1.setAgg(null);

        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("totalSales");
        group2.setAgg("SUM");

        queryRequest.setGroupBy(Arrays.asList(group1, group2));

        ModelResultContext ctx = createContext(queryRequest);

        assertDoesNotThrow(() -> validationStep.beforeQuery(ctx));
        log.info("正常 groupBy 校验通过");
    }

    @Test
    @Order(11)
    @DisplayName("groupBy 的 field 为空应该抛出异常")
    void testGroupByFieldEmpty() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();

        GroupRequestDef group = new GroupRequestDef();
        group.setField("");
        group.setAgg("SUM");

        queryRequest.setGroupBy(Collections.singletonList(group));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("field") && exception.getMessage().contains("不能为空"));
        log.info("groupBy field 为空校验生效: {}", exception.getMessage());
    }

    @Test
    @Order(12)
    @DisplayName("groupBy 的 agg 不合法应该抛出异常")
    void testGroupByAggInvalid() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();

        GroupRequestDef group = new GroupRequestDef();
        group.setField("totalSales");
        group.setAgg("INVALID_AGG");

        queryRequest.setGroupBy(Collections.singletonList(group));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("不合法") && exception.getMessage().contains("INVALID_AGG"));
        log.info("groupBy agg 不合法校验生效: {}", exception.getMessage());
    }

    // ==============================================
    // OrderBy 校验测试
    // ==============================================

    @Test
    @Order(20)
    @DisplayName("正常的 orderBy 应该通过校验")
    void testValidOrderBy() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        OrderRequestDef order1 = new OrderRequestDef();
        order1.setField("createdAt");
        order1.setOrder("desc");

        OrderRequestDef order2 = new OrderRequestDef();
        order2.setField("amount");
        order2.setOrder("asc");

        queryRequest.setOrderBy(Arrays.asList(order1, order2));

        ModelResultContext ctx = createContext(queryRequest);

        assertDoesNotThrow(() -> validationStep.beforeQuery(ctx));
        log.info("正常 orderBy 校验通过");
    }

    @Test
    @Order(21)
    @DisplayName("orderBy 的 field 为空应该抛出异常")
    void testOrderByFieldEmpty() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("");
        order.setOrder("asc");

        queryRequest.setOrderBy(Collections.singletonList(order));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("field") && exception.getMessage().contains("不能为空"));
        log.info("orderBy field 为空校验生效: {}", exception.getMessage());
    }

    @Test
    @Order(22)
    @DisplayName("orderBy 的 order 为空应该抛出异常")
    void testOrderByDirEmpty() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("createdAt");
        order.setOrder(null);

        queryRequest.setOrderBy(Collections.singletonList(order));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("排序方向") && exception.getMessage().contains("不能为空"));
        log.info("orderBy order 为空校验生效: {}", exception.getMessage());
    }

    @Test
    @Order(23)
    @DisplayName("orderBy 的 order 不合法应该抛出异常")
    void testOrderByDirInvalid() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("createdAt");
        order.setOrder("invalid");

        queryRequest.setOrderBy(Collections.singletonList(order));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("不合法") && exception.getMessage().contains("invalid"));
        log.info("orderBy order 不合法校验生效: {}", exception.getMessage());
    }

    // ==============================================
    // 综合测试
    // ==============================================

    @Test
    @Order(30)
    @DisplayName("空的请求参数应该通过校验")
    void testEmptyRequest() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        ModelResultContext ctx = createContext(queryRequest);

        assertDoesNotThrow(() -> validationStep.beforeQuery(ctx));
        log.info("空请求参数校验通过");
    }

    @Test
    @Order(31)
    @DisplayName("完整的合法请求应该通过校验")
    void testCompleteValidRequest() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();

        // Slice
        queryRequest.setSlice(Arrays.asList(
            new SliceRequestDef("orderStatus", "=", "COMPLETED"),
            new SliceRequestDef("amount", ">=", 100)
        ));

        // GroupBy
        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("categoryName");

        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("totalSales");
        group2.setAgg("SUM");

        queryRequest.setGroupBy(Arrays.asList(group1, group2));

        // OrderBy
        OrderRequestDef order = new OrderRequestDef();
        order.setField("totalSales");
        order.setOrder("desc");
        queryRequest.setOrderBy(Collections.singletonList(order));

        ModelResultContext ctx = createContext(queryRequest);

        assertDoesNotThrow(() -> validationStep.beforeQuery(ctx));
        log.info("完整合法请求校验通过");
    }
}
