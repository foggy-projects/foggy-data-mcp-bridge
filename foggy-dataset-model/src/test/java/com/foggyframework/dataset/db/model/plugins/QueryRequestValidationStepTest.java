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
import java.util.List;
import java.util.Map;

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
        when(mockSqlFormulaService.supports(">")).thenReturn(true);
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

    private static void assertValidationMessage(Exception exception, String requiredToken, String... localizedTokens) {
        String message = exception.getMessage();
        assertTrue(message.contains(requiredToken),
                () -> "Expected validation message to contain '" + requiredToken + "', actual: " + message);
        assertTrue(Arrays.stream(localizedTokens).anyMatch(message::contains),
                () -> "Expected validation message to contain one of " + Arrays.toString(localizedTokens)
                        + ", actual: " + message);
    }

    private DbQueryRequestDef groupedTeamSalesRequest(String extraColumn) {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setColumns(List.of(
                "salesTeam$id",
                "salesTeam$caption",
                "sum(amountTotal) as teamSales",
                extraColumn));

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("salesTeam$id");
        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("salesTeam$caption");
        queryRequest.setGroupBy(List.of(group1, group2));
        return queryRequest;
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
    @Order(7)
    @DisplayName("grouped calculatedFields 引用同层聚合别名并被 slice 过滤应提前拒绝")
    void testPostAggregateCalculatedFieldAliasSliceRejectedBeforeSql() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setColumns(List.of(
                "salesTeam$id",
                "salesTeam$caption",
                "sum(amountTotal) as teamSales",
                "salesShare"));

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("salesTeam$id");
        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("salesTeam$caption");
        queryRequest.setGroupBy(List.of(group1, group2));

        queryRequest.setCalculatedFields(List.of(new CalculatedFieldDef(
                "salesShare",
                "teamSales / NULLIF(CALCULATE(SUM(amountTotal), REMOVE(salesTeam$id)), 0)")));
        queryRequest.setSlice(List.of(new SliceRequestDef("salesShare", ">", 0.2)));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("teamSales");
        order.setDir("desc");
        queryRequest.setOrderBy(List.of(order));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("POST_AGGREGATE_CALCULATED_FIELD_UNSUPPORTED"));
        assertTrue(exception.getMessage().contains("teamSales"));
        assertFalse(exception.getMessage().toLowerCase().contains("column \"teamsales\""));
    }

    @Test
    @Order(8)
    @DisplayName("grouped calculatedFields 简单总额占比公式应允许进入 postAggregate 归一化")
    void testPostAggregateAliasRatioToTotalFormulaAllowed() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setColumns(List.of(
                "salesTeam$id",
                "salesTeam$caption",
                "sum(amountTotal) as teamSales",
                "salesShare"));

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("salesTeam$id");
        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("salesTeam$caption");
        queryRequest.setGroupBy(List.of(group1, group2));

        queryRequest.setCalculatedFields(List.of(new CalculatedFieldDef(
                "salesShare",
                "teamSales / NULLIF(SUM(teamSales) OVER (), 0)")));
        queryRequest.setSlice(List.of(new SliceRequestDef("salesShare", ">", 0.2)));

        ModelResultContext ctx = createContext(queryRequest);

        assertDoesNotThrow(() -> validationStep.beforeQuery(ctx));
    }

    @Test
    @Order(9)
    @DisplayName("grouped calculatedFields CALCULATE 聚合别名占比公式应允许进入 postAggregate 归一化")
    void testPostAggregateAliasCalculateRatioToTotalFormulaAllowed() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setColumns(List.of(
                "salesTeam$id",
                "salesTeam$caption",
                "sum(amountTotal) as teamSales",
                "salesShare"));

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("salesTeam$id");
        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("salesTeam$caption");
        queryRequest.setGroupBy(List.of(group1, group2));

        queryRequest.setCalculatedFields(List.of(new CalculatedFieldDef(
                "salesShare",
                "teamSales / NULLIF(CALCULATE(SUM(teamSales), REMOVE(salesTeam$id, salesTeam$caption)), 0)")));
        queryRequest.setSlice(List.of(new SliceRequestDef("salesShare", ">", 0.2)));

        ModelResultContext ctx = createContext(queryRequest);

        assertDoesNotThrow(() -> validationStep.beforeQuery(ctx));
    }

    @Test
    @Order(10)
    @DisplayName("grouped calculatedFields 累计贡献与排名公式应允许进入 postAggregate 归一化")
    void testPostAggregateCumulativeAndRankFormulasAllowed() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setColumns(List.of(
                "salesTeam$id",
                "salesTeam$caption",
                "sum(amountTotal) as teamSales",
                "salesRank",
                "cumulativeSales",
                "cumulativeShare"));

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("salesTeam$id");
        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("salesTeam$caption");
        queryRequest.setGroupBy(List.of(group1, group2));

        queryRequest.setCalculatedFields(List.of(
                new CalculatedFieldDef("salesRank", "rank_by(teamSales, desc)"),
                new CalculatedFieldDef(
                        "cumulativeSales",
                        "cumulative_sum(teamSales, desc)"),
                new CalculatedFieldDef("cumulativeShare", "cumulative_ratio_to_total(teamSales, desc)")
        ));

        ModelResultContext ctx = createContext(queryRequest);

        assertDoesNotThrow(() -> validationStep.beforeQuery(ctx));
    }

    @Test
    @Order(11)
    @DisplayName("postAggregateCalculations 未签排名 kind 应提前拒绝")
    void testPostAggregateUnsupportedRankingKindRejected() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setColumns(List.of(
                "salesTeam$id",
                "salesTeam$caption",
                "sum(amountTotal) as teamSales",
                "denseRank"));

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("salesTeam$id");
        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("salesTeam$caption");
        queryRequest.setGroupBy(List.of(group1, group2));

        queryRequest.setPostAggregateCalculations(List.of(new PostAggregateCalculationDef(
                "denseRank", "denseRank", "teamSales", "grandTotal", "value")));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("POST_AGGREGATE_CALCULATION_UNSUPPORTED"));
        assertTrue(exception.getMessage().contains("denseRank"));
    }

    @Test
    @Order(12)
    @DisplayName("grouped calculatedFields 未签 dense_rank 公式应提前拒绝")
    void testPostAggregateUnsupportedDenseRankFormulaRejected() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setColumns(List.of(
                "salesTeam$id",
                "salesTeam$caption",
                "sum(amountTotal) as teamSales",
                "denseRank"));

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("salesTeam$id");
        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("salesTeam$caption");
        queryRequest.setGroupBy(List.of(group1, group2));

        queryRequest.setCalculatedFields(List.of(new CalculatedFieldDef(
                "denseRank",
                "dense_rank() over (order by teamSales desc)")));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("POST_AGGREGATE_CALCULATED_FIELD_UNSUPPORTED"));
        assertTrue(exception.getMessage().contains("denseRank"));
    }

    @Test
    @Order(13)
    @DisplayName("grouped calculatedFields 未签 result-stage 公式参数应提前拒绝")
    void testPostAggregateUnsupportedFormulaOptionsRejected() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setColumns(List.of(
                "salesTeam$id",
                "salesTeam$caption",
                "sum(amountTotal) as teamSales",
                "ascendingCumulativeSales"));

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("salesTeam$id");
        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("salesTeam$caption");
        queryRequest.setGroupBy(List.of(group1, group2));

        queryRequest.setCalculatedFields(List.of(new CalculatedFieldDef(
                "ascendingCumulativeSales",
                "cumulative_sum(teamSales, asc)")));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("POST_AGGREGATE_CALCULATED_FIELD_UNSUPPORTED"));
        assertTrue(exception.getMessage().contains("ascendingCumulativeSales"));
    }

    @Test
    @Order(14)
    @DisplayName("postAggregateCalculations value 型 kind 未签 format 应提前拒绝")
    void testPostAggregateValueKindUnsupportedFormatRejected() {
        List<PostAggregateCalculationDef> unsupported = List.of(
                new PostAggregateCalculationDef("rankPercent", "rankByMeasure", "teamSales", "grandTotal", "percent"),
                new PostAggregateCalculationDef("cumulativeRatio", "cumulativeSum", "teamSales", "grandTotal", "ratio")
        );

        for (PostAggregateCalculationDef calculation : unsupported) {
            DbQueryRequestDef queryRequest = groupedTeamSalesRequest(calculation.getName());
            queryRequest.setPostAggregateCalculations(List.of(calculation));

            Exception exception = assertThrows(RuntimeException.class,
                    () -> validationStep.beforeQuery(createContext(queryRequest)));
            assertTrue(exception.getMessage().contains("POST_AGGREGATE_CALCULATION_UNSUPPORTED"));
            assertTrue(exception.getMessage().contains("format must be 'value'"));
            assertTrue(exception.getMessage().contains(calculation.getName()));
        }
    }

    @Test
    @Order(15)
    @DisplayName("grouped calculatedFields 未签 rank/window 邻近公式应提前拒绝")
    void testPostAggregateUnsignedRankingNeighborFormulasRejected() {
        Map<String, String> formulas = Map.of(
                "rowNumber", "row_number() over (order by teamSales desc)",
                "percentRank", "percent_rank() over (order by teamSales desc)",
                "rankWithTieBreaker", "rank_by(teamSales, desc, salesTeam$id)",
                "rankWithFilter", "rank_by(teamSales, desc, filter=salesTeam$id)"
        );

        for (Map.Entry<String, String> entry : formulas.entrySet()) {
            DbQueryRequestDef queryRequest = groupedTeamSalesRequest(entry.getKey());
            queryRequest.setCalculatedFields(List.of(new CalculatedFieldDef(entry.getKey(), entry.getValue())));

            Exception exception = assertThrows(RuntimeException.class,
                    () -> validationStep.beforeQuery(createContext(queryRequest)));
            assertTrue(exception.getMessage().contains("POST_AGGREGATE_CALCULATED_FIELD_UNSUPPORTED"));
            assertTrue(exception.getMessage().contains(entry.getKey()));
        }
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
        assertValidationMessage(exception, "field", "不能为空", "cannot be empty", "required");
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
        assertValidationMessage(exception, "op", "不能为空", "cannot be empty", "required");
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
        assertValidationMessage(exception, "invalid_op", "不合法", "Invalid");
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
        assertValidationMessage(exception, "value", "不能为空", "cannot be empty", "required");
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

    @Test
    @Order(8)
    @DisplayName("calculatedFields 缺少 name 或 expression 应提前返回稳定错误码")
    void testCalculatedFieldMissingRequiredFieldsRejected() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        CalculatedFieldDef calculatedField = new CalculatedFieldDef();
        calculatedField.setExpression("amountTotal");
        queryRequest.setCalculatedFields(List.of(calculatedField));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("CALCULATED_FIELD_EXPRESSION_INVALID"));
        assertTrue(exception.getMessage().contains("name"));
    }

    @Test
    @Order(8)
    @DisplayName("calculatedFields expression 为空应提前返回稳定错误码")
    void testCalculatedFieldMissingExpressionRejected() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setCalculatedFields(List.of(new CalculatedFieldDef("badCalc", "")));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("CALCULATED_FIELD_EXPRESSION_INVALID"));
        assertTrue(exception.getMessage().contains("expression"));
    }

    @Test
    @Order(8)
    @DisplayName("slice.value 为普通对象应该提前拒绝")
    void testSliceValueObjectShapeRejected() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setSlice(Collections.singletonList(
            new SliceRequestDef("orderTime", "=", Map.of("field", "orderTime"))
        ));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("slice.value"));
        assertTrue(exception.getMessage().contains("结构不合法") || exception.getMessage().contains("Invalid slice.value shape"));
        assertFalse(exception.getMessage().contains("ClassCastException"));
    }

    @Test
    @Order(9)
    @DisplayName("slice.value 数组中的对象元素应该提前拒绝")
    void testSliceValueListObjectElementRejected() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setSlice(Collections.singletonList(
            new SliceRequestDef("orderStatus", "in", List.of("PAID", Map.of("value", "DRAFT")))
        ));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertTrue(exception.getMessage().contains("slice.value"));
        assertTrue(exception.getMessage().contains("Map") || exception.getMessage().contains("LinkedHashMap"));
    }

    @Test
    @Order(10)
    @DisplayName("slice.value 的 $field 字段引用对象应该通过结构校验")
    void testSliceValueFieldReferenceShapeAccepted() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setSlice(Collections.singletonList(
            new SliceRequestDef("orderTime", ">", Map.of("$field", "shipTime"))
        ));

        ModelResultContext ctx = createContext(queryRequest);

        assertDoesNotThrow(() -> validationStep.beforeQuery(ctx));
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
        assertValidationMessage(exception, "field", "不能为空", "cannot be empty", "required");
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
        assertValidationMessage(exception, "INVALID_AGG", "不合法", "Invalid");
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
        order1.setDir("desc");

        OrderRequestDef order2 = new OrderRequestDef();
        order2.setField("amount");
        order2.setDir("asc");

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
        order.setDir("asc");

        queryRequest.setOrderBy(Collections.singletonList(order));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertValidationMessage(exception, "field", "不能为空", "cannot be empty", "required");
        log.info("orderBy field 为空校验生效: {}", exception.getMessage());
    }

    @Test
    @Order(23)
    @DisplayName("orderBy 的 order 不合法应该抛出异常")
    void testOrderByDirInvalid() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("createdAt");
        order.setDir("invalid");

        queryRequest.setOrderBy(Collections.singletonList(order));

        ModelResultContext ctx = createContext(queryRequest);

        Exception exception = assertThrows(RuntimeException.class, () -> validationStep.beforeQuery(ctx));
        assertValidationMessage(exception, "invalid", "不合法", "Invalid");
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
        order.setDir("desc");
        queryRequest.setOrderBy(Collections.singletonList(order));

        ModelResultContext ctx = createContext(queryRequest);

        assertDoesNotThrow(() -> validationStep.beforeQuery(ctx));
        log.info("完整合法请求校验通过");
    }
}
