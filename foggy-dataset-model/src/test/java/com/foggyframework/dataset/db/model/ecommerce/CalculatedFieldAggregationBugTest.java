package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.*;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
}
