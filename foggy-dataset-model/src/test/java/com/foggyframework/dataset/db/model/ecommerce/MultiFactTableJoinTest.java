package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 查询引擎测试 - 多事实表JOIN
 *
 * <p>测试多个事实表关联查询的场景</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("查询引擎测试 - 多事实表JOIN")
class MultiFactTableJoinTest extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    // ==========================================
    // 订单-支付关联测试（同粒度）
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("订单-支付简单关联查询")
    void testOrderPaymentSimpleJoin() {
        JdbcQueryModel queryModel = getQueryModel("OrderPaymentJoinQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderPaymentJoinQueryModel");

        // 设置查询列：订单信息 + 支付信息
        List<String> columns = Arrays.asList(
            "orderId",
            "orderStatus",
            "paymentId",
            "payMethod",
            "payStatus",
            "totalAmount",
            "payAmount"
        );
        queryRequest.setColumns(columns);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toLowerCase().contains("join"), "SQL应包含JOIN");
        assertTrue(sql.contains("fact_order") || sql.contains("fo"), "SQL应包含订单表");
        assertTrue(sql.contains("fact_payment") || sql.contains("fp"), "SQL应包含支付表");

        printSql(sql, "订单-支付简单关联查询SQL");
    }

    @Test
    @Order(2)
    @DisplayName("订单-支付关联带维度查询")
    void testOrderPaymentJoinWithDimensions() {
        JdbcQueryModel queryModel = getQueryModel("OrderPaymentJoinQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderPaymentJoinQueryModel");

        // 设置查询列：包含维度
        List<String> columns = Arrays.asList(
            "orderId",
            "orderDate$caption",
            "orderDate$year",
            "orderDate$month",
            "customer$caption",
            "customer$customerType",
            "payMethod",
            "payStatus",
            "totalAmount",
            "payAmount"
        );
        queryRequest.setColumns(columns);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "订单-支付关联带维度查询SQL");
    }

    @Test
    @Order(3)
    @DisplayName("订单-支付关联带条件查询")
    void testOrderPaymentJoinWithCondition() {
        JdbcQueryModel queryModel = getQueryModel("OrderPaymentJoinQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderPaymentJoinQueryModel");

        List<String> columns = Arrays.asList(
            "orderId",
            "orderStatus",
            "payMethod",
            "payStatus",
            "totalAmount",
            "payAmount"
        );
        queryRequest.setColumns(columns);

        // 添加条件
        List<SliceRequestDef> slices = new ArrayList<>();

        // 订单状态条件
        SliceRequestDef statusSlice = new SliceRequestDef();
        statusSlice.setField("orderStatus");
        statusSlice.setOp("=");
        statusSlice.setValue("COMPLETED");
        slices.add(statusSlice);

        // 支付状态条件
        SliceRequestDef payStatusSlice = new SliceRequestDef();
        payStatusSlice.setField("payStatus");
        payStatusSlice.setOp("=");
        payStatusSlice.setValue("SUCCESS");
        slices.add(payStatusSlice);

        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toLowerCase().contains("where"), "SQL应包含WHERE子句");

        printSql(sql, "订单-支付关联带条件查询SQL");
        log.info("参数值: {}", queryEngine.getValues());
    }

    @Test
    @Order(4)
    @DisplayName("订单-支付关联分组汇总")
    void testOrderPaymentJoinGroupBy() {
        JdbcQueryModel queryModel = getQueryModel("OrderPaymentJoinQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderPaymentJoinQueryModel");

        List<String> columns = Arrays.asList(
            "orderDate$year",
            "orderDate$month",
            "payMethod",
            "totalAmount",
            "payAmount"
        );
        queryRequest.setColumns(columns);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("orderDate$year");
        groups.add(group1);

        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("orderDate$month");
        groups.add(group2);

        GroupRequestDef group3 = new GroupRequestDef();
        group3.setField("payMethod");
        groups.add(group3);

        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toLowerCase().contains("group by"), "SQL应包含GROUP BY子句");

        printSql(sql, "订单-支付关联分组汇总SQL");
    }

    // ==========================================
    // 销售-退货关联测试（不同粒度）
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("销售-退货LEFT JOIN查询")
    void testSalesReturnLeftJoin() {
        JdbcQueryModel queryModel = getQueryModel("SalesReturnJoinQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("SalesReturnJoinQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "orderId",
            "orderLineNo",
            "returnId",
            "returnType",
            "returnStatus",
            "quantity",
            "salesAmount",
            "returnQuantity",
            "returnAmount"
        );
        queryRequest.setColumns(columns);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toLowerCase().contains("left join") || sql.toLowerCase().contains("left outer join"),
            "SQL应包含LEFT JOIN");

        printSql(sql, "销售-退货LEFT JOIN查询SQL");
    }

    @Test
    @Order(11)
    @DisplayName("销售-退货带商品维度查询")
    void testSalesReturnJoinWithProductDimension() {
        JdbcQueryModel queryModel = getQueryModel("SalesReturnJoinQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("SalesReturnJoinQueryModel");

        List<String> columns = Arrays.asList(
            "orderId",
            "orderLineNo",
            "product$caption",
            "product$categoryName",
            "product$brand",
            "quantity",
            "salesAmount",
            "returnQuantity",
            "returnAmount"
        );
        queryRequest.setColumns(columns);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "销售-退货带商品维度查询SQL");
    }

    @Test
    @Order(12)
    @DisplayName("销售-退货筛选有退货的记录")
    void testSalesReturnFilterWithReturn() {
        JdbcQueryModel queryModel = getQueryModel("SalesReturnJoinQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("SalesReturnJoinQueryModel");

        List<String> columns = Arrays.asList(
            "orderId",
            "orderLineNo",
            "product$caption",
            "returnId",
            "returnType",
            "returnStatus",
            "salesAmount",
            "returnAmount"
        );
        queryRequest.setColumns(columns);

        // 添加条件：只查询有退货的记录
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("returnId");
        slice.setOp("isNotNull");
        slices.add(slice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toLowerCase().contains("is not null"), "SQL应包含IS NOT NULL条件");

        printSql(sql, "销售-退货筛选有退货的记录SQL");
    }

    @Test
    @Order(13)
    @DisplayName("销售-退货分组汇总退货率")
    void testSalesReturnGroupByForReturnRate() {
        JdbcQueryModel queryModel = getQueryModel("SalesReturnJoinQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("SalesReturnJoinQueryModel");

        List<String> columns = Arrays.asList(
            "product$categoryName",
            "quantity",
            "salesAmount",
            "profitAmount",
            "returnQuantity",
            "returnAmount"
        );
        queryRequest.setColumns(columns);

        // 按品类分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("product$categoryName");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toLowerCase().contains("group by"), "SQL应包含GROUP BY子句");

        printSql(sql, "销售-退货分组汇总退货率SQL");
    }

    // ==========================================
    // 复杂关联场景测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("订单-支付关联带时间范围查询")
    void testOrderPaymentJoinWithDateRange() {
        JdbcQueryModel queryModel = getQueryModel("OrderPaymentJoinQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderPaymentJoinQueryModel");

        List<String> columns = Arrays.asList(
            "orderId",
            "orderTime",
            "payTime",
            "customer$caption",
            "totalAmount",
            "payAmount"
        );
        queryRequest.setColumns(columns);

        // 添加日期范围条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef dateSlice = new SliceRequestDef();
        dateSlice.setField("orderDate$caption");
        dateSlice.setOp("[)");
        dateSlice.setValue(Arrays.asList("2024-01-01", "2024-06-30"));
        slices.add(dateSlice);
        queryRequest.setSlice(slices);

        // 按订单时间排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderTime");
        order.setOrder("DESC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "订单-支付关联带时间范围查询SQL");
        log.info("参数值: {}", queryEngine.getValues());
    }

    @Test
    @Order(21)
    @DisplayName("订单-支付按客户类型和支付方式分组")
    void testOrderPaymentGroupByCustomerAndPayMethod() {
        JdbcQueryModel queryModel = getQueryModel("OrderPaymentJoinQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OrderPaymentJoinQueryModel");

        List<String> columns = Arrays.asList(
            "customer$customerType",
            "customer$memberLevel",
            "payMethod",
            "totalAmount",
            "discountAmount",
            "orderPayAmount",
            "payAmount"
        );
        queryRequest.setColumns(columns);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("customer$customerType");
        groups.add(group1);

        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("customer$memberLevel");
        groups.add(group2);

        GroupRequestDef group3 = new GroupRequestDef();
        group3.setField("payMethod");
        groups.add(group3);

        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "订单-支付按客户类型和支付方式分组SQL");
    }

    @Test
    @Order(22)
    @DisplayName("销售-退货按客户分析退货倾向")
    void testSalesReturnByCustomerAnalysis() {
        JdbcQueryModel queryModel = getQueryModel("SalesReturnJoinQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("SalesReturnJoinQueryModel");

        List<String> columns = Arrays.asList(
            "customer$caption",
            "customer$customerType",
            "quantity",
            "salesAmount",
            "returnQuantity",
            "returnAmount"
        );
        queryRequest.setColumns(columns);

        // 按客户分组
        List<GroupRequestDef> groups = new ArrayList<>();

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("customer$caption");
        groups.add(group1);

        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("customer$customerType");
        groups.add(group2);

        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "销售-退货按客户分析退货倾向SQL");
    }
}
