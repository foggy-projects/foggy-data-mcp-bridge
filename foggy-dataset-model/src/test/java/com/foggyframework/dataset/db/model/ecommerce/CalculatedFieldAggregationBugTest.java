package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.*;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 计算字段聚合 Bug 回归测试
 *
 * <p>测试两个 Bug 的修复：</p>
 * <ol>
 *   <li>Bug 1: 嵌套聚合问题 - 当 calculatedField 引用另一个已含聚合的 calculatedField 时，
 *       生成的 SQL 出现嵌套聚合如 SUM((SUM(a) / SUM(b)))</li>
 *   <li>Bug 2: "找不到列" 问题 - 当 calculatedField 使用 expression: "SUM(salesAmount)"
 *       但没有显式设置 agg 字段时，orderBy 处理阶段报错找不到列</li>
 * </ol>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("计算字段聚合 Bug 回归测试")
class CalculatedFieldAggregationBugTest extends EcommerceTestSupport {

    @Resource
    private QueryFacade queryFacade;

    // ==========================================
    // Bug 1: 嵌套聚合问题
    // ==========================================

    /**
     * Bug 1 复现场景：内联表达式引用其他聚合别名
     *
     * <p>当使用内联表达式如：</p>
     * <pre>
     * columns: [
     *   "sum(salesAmount) as totalSalesAmount",
     *   "sum(quantity) as totalQuantity",
     *   "totalSalesAmount / totalQuantity as avgUnitPrice"
     * ]
     * </pre>
     * <p>期望生成的 SQL 应该是：</p>
     * <pre>
     * (SUM(t1.sales_amount) / SUM(t1.quantity)) as avgUnitPrice
     * </pre>
     * <p>而不是嵌套聚合：</p>
     * <pre>
     * SUM((SUM(t1.sales_amount) / SUM(t1.quantity))) as avgUnitPrice
     * </pre>
     */
    @Test
    @Order(1)
    @DisplayName("Bug 1: 内联表达式引用聚合别名 - 不应产生嵌套聚合")
    void testInlineExpressionReferenceAggregateAlias_NoNestedAggregate() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用内联表达式，avgUnitPrice 引用其他聚合别名
        List<String> columns = Arrays.asList(
            "customer$caption",
            "sum(salesAmount) as totalSalesAmount",
            "sum(quantity) as totalQuantity",
            "totalSalesAmount / totalQuantity as avgUnitPrice"
        );
        queryRequest.setColumns(columns);

        // 设置过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice1 = new SliceRequestDef();
        slice1.setField("salesDate$year");
        slice1.setOp("=");
        slice1.setValue(2024);
        slices.add(slice1);
        queryRequest.setSlice(slices);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("customer$caption");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        // 设置排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("totalSalesAmount");
        order.setDir("desc");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        // 通过 QueryFacade 执行查询
        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(queryRequest, 100));

        assertNotNull(result, "查询结果不应为空");
        log.info("查询返回 {} 条记录", result.getItems().size());

        // 验证结果中包含 avgUnitPrice 字段
        if (!result.getItems().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> firstRow = (Map<String, Object>) result.getItems().get(0);
            assertTrue(firstRow.containsKey("avgUnitPrice"), "结果应包含 avgUnitPrice 字段");

            // 打印部分结果
            for (int i = 0; i < Math.min(3, result.getItems().size()); i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) result.getItems().get(i);
                log.info("行 {}: customer={}, totalSalesAmount={}, totalQuantity={}, avgUnitPrice={}",
                        i, row.get("customer$caption"),
                        row.get("totalSalesAmount"), row.get("totalQuantity"), row.get("avgUnitPrice"));
            }
        }
    }

    /**
     * Bug 1 复现场景：calculatedFields 中使用 SUM() 表达式，然后被其他字段引用
     *
     * <p>当使用 calculatedFields 如：</p>
     * <pre>
     * calculatedFields: [
     *   { name: "totalSalesAmount", expression: "SUM(salesAmount)" },
     *   { name: "totalQuantity", expression: "SUM(quantity)" },
     *   { name: "avgUnitPrice", expression: "totalSalesAmount / totalQuantity" }
     * ]
     * </pre>
     */
    @Test
    @Order(2)
    @DisplayName("Bug 1: calculatedFields 使用 SUM 表达式被引用 - 不应产生嵌套聚合")
    void testCalculatedFieldWithSumExpression_NoNestedAggregate() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置 calculatedFields
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
            "totalSalesAmount", "总销售金额", "SUM(salesAmount)"
        ));
        calculatedFields.add(new CalculatedFieldDef(
            "totalQuantity", "总数量", "SUM(quantity)"
        ));
        calculatedFields.add(new CalculatedFieldDef(
            "avgUnitPrice", "平均单价", "totalSalesAmount / totalQuantity"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        // 设置查询列
        queryRequest.setColumns(Arrays.asList(
            "customer$caption",
            "totalSalesAmount",
            "totalQuantity",
            "avgUnitPrice"
        ));

        // 设置过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice1 = new SliceRequestDef();
        slice1.setField("salesDate$year");
        slice1.setOp("=");
        slice1.setValue(2024);
        slices.add(slice1);
        queryRequest.setSlice(slices);

        // 设置排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("totalSalesAmount");
        order.setDir("desc");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        // 通过 QueryFacade 执行查询
        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(queryRequest, 100));

        assertNotNull(result, "查询结果不应为空");
        log.info("查询返回 {} 条记录", result.getItems().size());

        // 验证结果中包含 avgUnitPrice 字段
        if (!result.getItems().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> firstRow = (Map<String, Object>) result.getItems().get(0);
            assertTrue(firstRow.containsKey("avgUnitPrice"), "结果应包含 avgUnitPrice 字段");
        }
    }

    @Test
    @Order(3)
    @DisplayName("Issue 120: 同名内联聚合别名参与后聚合表达式 - 不应产生嵌套聚合")
    void testInlinePostAggregateExpressionSameNameAliases_NoNestedAggregate() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "product$categoryName",
                "sum(salesAmount) as salesAmount",
                "sum(profitAmount) as profitAmount",
                "sum(profitAmount) / sum(salesAmount) as profitRate",
                "sum(salesAmount) - sum(profitAmount) as salesProfitGap"
        ));

        GroupRequestDef group = new GroupRequestDef();
        group.setField("product$categoryName");
        queryRequest.setGroupBy(List.of(group));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("salesProfitGap");
        order.setDir("DESC");
        queryRequest.setOrderBy(List.of(order));
        queryRequest.setReturnTotal(true);

        DbQueryResult result = assertDoesNotThrow(() ->
                queryFacade.queryModelResult(PagingRequest.buildPagingRequest(queryRequest, 100)));

        assertNotNull(result);
        assertNotNull(result.getQueryEngine());
        JdbcModelQueryEngine queryEngine = (JdbcModelQueryEngine) result.getQueryEngine();
        assertNoNestedAggregate(queryEngine.getSql());
        if (queryEngine.getAggSql() != null) {
            assertNoNestedAggregate(queryEngine.getAggSql());
        }

        PagingResultImpl pagingResult = result.getPagingResult();
        assertNotNull(pagingResult, "查询结果不应为空");
        if (!pagingResult.getItems().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> firstRow = (Map<String, Object>) pagingResult.getItems().get(0);
            assertTrue(firstRow.containsKey("profitRate"), "结果应包含 profitRate 字段");
            assertTrue(firstRow.containsKey("salesProfitGap"), "结果应包含 salesProfitGap 字段");
        }
    }

    // ==========================================
    // Bug 2: "找不到列" 问题
    // ==========================================

    /**
     * Bug 2 复现场景：calculatedField 使用 SUM() 表达式但没有 agg 字段
     *
     * <p>当 calculatedFields 定义如下时：</p>
     * <pre>
     * calculatedFields: [
     *   { name: "totalSalesAmount", expression: "SUM(salesAmount)" },  // 无 agg 字段
     *   { name: "totalQuantity", expression: "SUM(quantity)" },        // 无 agg 字段
     *   { name: "avgUnitPrice", expression: "totalSalesAmount / totalQuantity" }
     * ]
     * </pre>
     * <p>之前的 Bug 会导致：</p>
     * <ul>
     *   <li>系统未能识别 SUM() 是聚合函数</li>
     *   <li>AutoGroupByStep 不触发</li>
     *   <li>orderBy 处理时找不到 totalSalesAmount 列</li>
     * </ul>
     */
    @Test
    @Order(10)
    @DisplayName("Bug 2: calculatedField 使用 SUM 表达式无 agg 字段 - 不应报错找不到列")
    void testCalculatedFieldWithSumNoAgg_ShouldNotThrowColumnNotFound() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置 calculatedFields - 注意：没有显式设置 agg 字段
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();

        CalculatedFieldDef f1 = new CalculatedFieldDef();
        f1.setName("totalSalesAmount");
        f1.setCaption("总销售金额");
        f1.setExpression("SUM(salesAmount)");
        // 不设置 agg 字段！
        calculatedFields.add(f1);

        CalculatedFieldDef f2 = new CalculatedFieldDef();
        f2.setName("totalQuantity");
        f2.setCaption("总数量");
        f2.setExpression("SUM(quantity)");
        // 不设置 agg 字段！
        calculatedFields.add(f2);

        CalculatedFieldDef f3 = new CalculatedFieldDef();
        f3.setName("avgUnitPrice");
        f3.setCaption("平均单价");
        f3.setExpression("totalSalesAmount / totalQuantity");
        calculatedFields.add(f3);

        queryRequest.setCalculatedFields(calculatedFields);

        // 设置查询列
        queryRequest.setColumns(Arrays.asList(
            "customer$caption",
            "totalSalesAmount",
            "totalQuantity",
            "avgUnitPrice"
        ));

        // 设置过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice1 = new SliceRequestDef();
        slice1.setField("salesDate$year");
        slice1.setOp("=");
        slice1.setValue(2024);
        slices.add(slice1);
        queryRequest.setSlice(slices);

        // 设置排序 - 这是触发 Bug 的关键！
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("totalSalesAmount");
        order.setDir("desc");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        // 通过 QueryFacade 执行查询 - 之前这里会抛出 "找不到列 totalSalesAmount" 异常
        PagingResultImpl result = assertDoesNotThrow(() ->
            queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 100)),
            "不应抛出 '找不到列' 异常"
        );

        assertNotNull(result, "查询结果不应为空");
        log.info("Bug 2 测试 - 查询返回 {} 条记录", result.getItems().size());
    }

    /**
     * Bug 2 对比测试：有 agg 字段时应该正常工作（作为对照组）
     */
    @Test
    @Order(11)
    @DisplayName("Bug 2 对照: calculatedField 有 agg 字段 - 应该正常工作")
    void testCalculatedFieldWithAgg_ShouldWork() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置 calculatedFields - 显式设置 agg 字段
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();

        CalculatedFieldDef f1 = new CalculatedFieldDef();
        f1.setName("totalSalesAmount");
        f1.setCaption("总销售金额");
        f1.setExpression("SUM(salesAmount)");
        f1.setAgg("SUM");  // 显式设置 agg
        calculatedFields.add(f1);

        CalculatedFieldDef f2 = new CalculatedFieldDef();
        f2.setName("totalQuantity");
        f2.setCaption("总数量");
        f2.setExpression("SUM(quantity)");
        f2.setAgg("SUM");  // 显式设置 agg
        calculatedFields.add(f2);

        CalculatedFieldDef f3 = new CalculatedFieldDef();
        f3.setName("avgUnitPrice");
        f3.setCaption("平均单价");
        f3.setExpression("totalSalesAmount / totalQuantity");
        calculatedFields.add(f3);

        queryRequest.setCalculatedFields(calculatedFields);

        // 设置查询列
        queryRequest.setColumns(Arrays.asList(
            "customer$caption",
            "totalSalesAmount",
            "totalQuantity",
            "avgUnitPrice"
        ));

        // 设置过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice1 = new SliceRequestDef();
        slice1.setField("salesDate$year");
        slice1.setOp("=");
        slice1.setValue(2024);
        slices.add(slice1);
        queryRequest.setSlice(slices);

        // 设置排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("totalSalesAmount");
        order.setDir("desc");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        // 通过 QueryFacade 执行查询
        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(queryRequest, 100));

        assertNotNull(result, "查询结果不应为空");
        log.info("Bug 2 对照测试 - 查询返回 {} 条记录", result.getItems().size());
    }

    // ==========================================
    // 综合场景测试
    // ==========================================

    /**
     * 综合场景：混合使用各种聚合计算字段
     */
    @Test
    @Order(20)
    @DisplayName("综合场景: 混合使用聚合计算字段")
    void testMixedAggregateCalculatedFields() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 混合使用内联表达式和 calculatedFields
        List<String> columns = Arrays.asList(
            "customer$caption",
            "customer$customerType",
            "sum(salesAmount) as totalSalesAmount",
            "sum(quantity) as totalQuantity",
            "totalSalesAmount / totalQuantity as avgUnitPrice"
        );
        queryRequest.setColumns(columns);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("customer$caption");
        groups.add(group1);
        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("customer$customerType");
        groups.add(group2);
        queryRequest.setGroupBy(groups);

        // 设置过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice1 = new SliceRequestDef();
        slice1.setField("salesDate$year");
        slice1.setOp("=");
        slice1.setValue(2024);
        slices.add(slice1);
        queryRequest.setSlice(slices);

        // 设置排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("totalSalesAmount");
        order.setDir("desc");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryRequest.setReturnTotal(true);

        // 通过 QueryFacade 执行查询
        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(queryRequest, 100));

        assertNotNull(result, "查询结果不应为空");
        log.info("综合场景测试 - 查询返回 {} 条记录", result.getItems().size());

        // 验证结果中包含所有字段
        if (!result.getItems().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> firstRow = (Map<String, Object>) result.getItems().get(0);
            assertTrue(firstRow.containsKey("avgUnitPrice"), "结果应包含 avgUnitPrice 字段");
            assertTrue(firstRow.containsKey("totalSalesAmount"), "结果应包含 totalSalesAmount 字段");
            assertTrue(firstRow.containsKey("totalQuantity"), "结果应包含 totalQuantity 字段");
        }
    }

    /**
     * 测试 AVG 聚合函数场景
     */
    @Test
    @Order(21)
    @DisplayName("AVG 聚合函数场景")
    void testAvgAggregateFunction() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用 AVG 聚合函数
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();

        CalculatedFieldDef f1 = new CalculatedFieldDef();
        f1.setName("avgSalesAmount");
        f1.setCaption("平均销售金额");
        f1.setExpression("AVG(salesAmount)");
        // 不设置 agg 字段
        calculatedFields.add(f1);

        CalculatedFieldDef f2 = new CalculatedFieldDef();
        f2.setName("avgQuantity");
        f2.setCaption("平均数量");
        f2.setExpression("AVG(quantity)");
        calculatedFields.add(f2);

        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
            "customer$caption",
            "avgSalesAmount",
            "avgQuantity"
        ));

        // 设置排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("avgSalesAmount");
        order.setDir("desc");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        // 通过 QueryFacade 执行查询
        PagingResultImpl result = assertDoesNotThrow(() ->
            queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 100)),
            "AVG 聚合函数场景不应抛出异常"
        );

        assertNotNull(result, "查询结果不应为空");
        log.info("AVG 聚合函数场景 - 查询返回 {} 条记录", result.getItems().size());
    }

    /**
     * 测试 COUNT 聚合函数场景
     */
    @Test
    @Order(22)
    @DisplayName("COUNT 聚合函数场景")
    void testCountAggregateFunction() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用 COUNT 聚合函数
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();

        CalculatedFieldDef f1 = new CalculatedFieldDef();
        f1.setName("orderCount");
        f1.setCaption("订单数");
        f1.setExpression("COUNT(orderId)");
        calculatedFields.add(f1);

        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
            "customer$caption",
            "orderCount"
        ));

        // 设置排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderCount");
        order.setDir("desc");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        // 通过 QueryFacade 执行查询
        PagingResultImpl result = assertDoesNotThrow(() ->
            queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 100)),
            "COUNT 聚合函数场景不应抛出异常"
        );

        assertNotNull(result, "查询结果不应为空");
        log.info("COUNT 聚合函数场景 - 查询返回 {} 条记录", result.getItems().size());
    }

    /**
     * 测试混合聚合函数场景：SUM + COUNT 组合计算
     */
    @Test
    @Order(23)
    @DisplayName("混合聚合函数: SUM + COUNT 计算平均值")
    void testMixedAggregateFunctions() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // SUM / COUNT = 手动计算平均值
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();

        CalculatedFieldDef f1 = new CalculatedFieldDef();
        f1.setName("totalAmount");
        f1.setCaption("总金额");
        f1.setExpression("SUM(salesAmount)");
        calculatedFields.add(f1);

        CalculatedFieldDef f2 = new CalculatedFieldDef();
        f2.setName("orderCount");
        f2.setCaption("订单数");
        f2.setExpression("COUNT(orderId)");
        calculatedFields.add(f2);

        CalculatedFieldDef f3 = new CalculatedFieldDef();
        f3.setName("avgOrderAmount");
        f3.setCaption("平均订单金额");
        f3.setExpression("totalAmount / orderCount");
        calculatedFields.add(f3);

        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
            "customer$caption",
            "totalAmount",
            "orderCount",
            "avgOrderAmount"
        ));

        // 设置排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("avgOrderAmount");
        order.setDir("desc");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        // 通过 QueryFacade 执行查询
        PagingResultImpl result = assertDoesNotThrow(() ->
            queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 100)),
            "混合聚合函数场景不应抛出异常"
        );

        assertNotNull(result, "查询结果不应为空");
        log.info("混合聚合函数场景 - 查询返回 {} 条记录", result.getItems().size());

        // 验证结果中包含所有字段
        if (!result.getItems().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> firstRow = (Map<String, Object>) result.getItems().get(0);
            assertTrue(firstRow.containsKey("avgOrderAmount"), "结果应包含 avgOrderAmount 字段");
        }
    }

    // ==========================================
    // 混合引用场景测试（内联表达式 + calculatedFields）
    // ==========================================

    /**
     * 测试混合引用场景：calculatedFields 引用内联表达式的别名
     *
     * <p>场景：</p>
     * <pre>
     * columns: [
     *   "customer$caption",
     *   "SUM(salesAmount) as totalSalesAmount",  // 内联表达式
     *   "totalQuantity",
     *   "avgUnitPrice"
     * ]
     * calculatedFields: [
     *   { name: "totalQuantity", expression: "SUM(quantity)" },
     *   { name: "avgUnitPrice", expression: "totalSalesAmount / totalQuantity" }  // 引用内联表达式别名
     * ]
     * </pre>
     *
     * <p>期望：依赖排序后正确编译，不报 "找不到列 totalSalesAmount" 错误</p>
     */
    @Test
    @Order(30)
    @DisplayName("混合引用: calculatedField 引用内联表达式别名")
    void testCalculatedFieldReferenceInlineAlias() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // columns 中包含内联表达式
        List<String> columns = Arrays.asList(
            "customer$caption",
            "SUM(salesAmount) as totalSalesAmount",  // 内联表达式，别名 totalSalesAmount
            "totalQuantity",
            "avgUnitPrice"
        );
        queryRequest.setColumns(columns);

        // calculatedFields 中的 avgUnitPrice 引用 totalSalesAmount（内联表达式别名）
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();

        CalculatedFieldDef f1 = new CalculatedFieldDef();
        f1.setName("totalQuantity");
        f1.setCaption("总数量");
        f1.setExpression("SUM(quantity)");
        calculatedFields.add(f1);

        CalculatedFieldDef f2 = new CalculatedFieldDef();
        f2.setName("avgUnitPrice");
        f2.setCaption("平均单价");
        f2.setExpression("totalSalesAmount / totalQuantity");  // 引用内联表达式别名
        calculatedFields.add(f2);

        queryRequest.setCalculatedFields(calculatedFields);

        // 设置过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice1 = new SliceRequestDef();
        slice1.setField("salesDate$year");
        slice1.setOp("=");
        slice1.setValue(2024);
        slices.add(slice1);
        SliceRequestDef slice2 = new SliceRequestDef();
        slice2.setField("salesDate$month");
        slice2.setOp("=");
        slice2.setValue(7);
        slices.add(slice2);
        queryRequest.setSlice(slices);

        // 设置排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("totalSalesAmount");
        order.setDir("desc");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryRequest.setReturnTotal(true);

        // 通过 QueryFacade 执行查询 - 之前会报 "找不到列 totalSalesAmount" 错误
        PagingResultImpl result = assertDoesNotThrow(() ->
            queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 10)),
            "混合引用场景不应抛出 '找不到列' 异常"
        );

        assertNotNull(result, "查询结果不应为空");
        log.info("混合引用测试 - 查询返回 {} 条记录", result.getItems().size());

        // 验证结果中包含所有字段
        if (!result.getItems().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> firstRow = (Map<String, Object>) result.getItems().get(0);
            assertTrue(firstRow.containsKey("avgUnitPrice"), "结果应包含 avgUnitPrice 字段");
            assertTrue(firstRow.containsKey("totalSalesAmount"), "结果应包含 totalSalesAmount 字段");
            assertTrue(firstRow.containsKey("totalQuantity"), "结果应包含 totalQuantity 字段");

            // 验证 avgUnitPrice 的计算逻辑（应该是 totalSalesAmount / totalQuantity）
            Object avgUnitPrice = firstRow.get("avgUnitPrice");
            Object totalSalesAmount = firstRow.get("totalSalesAmount");
            Object totalQuantity = firstRow.get("totalQuantity");
            log.info("首行数据: totalSalesAmount={}, totalQuantity={}, avgUnitPrice={}",
                    totalSalesAmount, totalQuantity, avgUnitPrice);
        }
    }

    /**
     * 测试循环引用检测
     *
     * <p>场景：a 依赖 b，b 依赖 a</p>
     */
    @Test
    @Order(31)
    @DisplayName("循环引用检测: 应该抛出异常")
    void testCircularReferenceDetection() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 创建循环引用：fieldA 依赖 fieldB，fieldB 依赖 fieldA
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();

        CalculatedFieldDef fieldA = new CalculatedFieldDef();
        fieldA.setName("fieldA");
        fieldA.setCaption("字段A");
        fieldA.setExpression("fieldB + 1");  // 依赖 fieldB
        calculatedFields.add(fieldA);

        CalculatedFieldDef fieldB = new CalculatedFieldDef();
        fieldB.setName("fieldB");
        fieldB.setCaption("字段B");
        fieldB.setExpression("fieldA + 1");  // 依赖 fieldA
        calculatedFields.add(fieldB);

        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList("customer$caption", "fieldA", "fieldB"));

        // 应该抛出循环引用异常
        Exception exception = assertThrows(Exception.class, () ->
            queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 10)),
            "循环引用应该抛出异常"
        );

        String message = exception.getMessage();
        log.info("循环引用检测 - 捕获到异常: {}", message);
        assertTrue(message.contains("循环引用") || message.contains("circular"),
                "异常消息应包含循环引用提示");
    }

    /**
     * 测试多层依赖排序：a -> b -> c
     */
    @Test
    @Order(32)
    @DisplayName("多层依赖: a -> b -> c 应正确排序")
    void testMultiLevelDependency() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 创建多层依赖：fieldC 依赖 fieldB，fieldB 依赖 fieldA
        // 故意按错误顺序定义，测试排序功能
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();

        // fieldC 定义在最前面，但它依赖 fieldB
        CalculatedFieldDef fieldC = new CalculatedFieldDef();
        fieldC.setName("fieldC");
        fieldC.setCaption("字段C");
        fieldC.setExpression("fieldB * 2");  // 依赖 fieldB
        calculatedFields.add(fieldC);

        // fieldB 定义在中间，它依赖 fieldA
        CalculatedFieldDef fieldB = new CalculatedFieldDef();
        fieldB.setName("fieldB");
        fieldB.setCaption("字段B");
        fieldB.setExpression("fieldA + 100");  // 依赖 fieldA
        calculatedFields.add(fieldB);

        // fieldA 定义在最后，但它不依赖任何其他字段
        CalculatedFieldDef fieldA = new CalculatedFieldDef();
        fieldA.setName("fieldA");
        fieldA.setCaption("字段A");
        fieldA.setExpression("SUM(salesAmount)");  // 不依赖其他 calculatedField
        calculatedFields.add(fieldA);

        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList("customer$caption", "fieldA", "fieldB", "fieldC"));

        // 设置过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesDate$year");
        slice.setOp("=");
        slice.setValue(2024);
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 应该能正确处理多层依赖
        PagingResultImpl result = assertDoesNotThrow(() ->
            queryFacade.queryModelData(PagingRequest.buildPagingRequest(queryRequest, 10)),
            "多层依赖场景不应抛出异常"
        );

        assertNotNull(result, "查询结果不应为空");
        log.info("多层依赖测试 - 查询返回 {} 条记录", result.getItems().size());

        // 验证结果中包含所有字段
        if (!result.getItems().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> firstRow = (Map<String, Object>) result.getItems().get(0);
            assertTrue(firstRow.containsKey("fieldA"), "结果应包含 fieldA");
            assertTrue(firstRow.containsKey("fieldB"), "结果应包含 fieldB");
            assertTrue(firstRow.containsKey("fieldC"), "结果应包含 fieldC");
            log.info("首行数据: fieldA={}, fieldB={}, fieldC={}",
                    firstRow.get("fieldA"), firstRow.get("fieldB"), firstRow.get("fieldC"));
        }
    }

    private void assertNoNestedAggregate(String sql) {
        assertNotNull(sql, "SQL 不应为空");
        String normalized = sql.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        assertFalse(normalized.contains("SUM(SUM("), () -> "SQL 不应包含嵌套 SUM: " + sql);
        assertFalse(normalized.contains("SUM((SUM("), () -> "SQL 不应包含嵌套 SUM: " + sql);
    }
}
