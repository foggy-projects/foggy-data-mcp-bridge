package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.jdbc.model.def.query.request.*;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 查询引擎测试 - 基本查询
 *
 * <p>测试单表查询和简单维度关联查询</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("查询引擎测试 - 基本查询")
class BasicQueryTest extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    // ==========================================
    // 单表基本查询测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("简单字段查询")
    void testSimpleFieldQuery() {
        // 获取订单查询模型
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        // 创建查询引擎
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 创建查询请求
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "orderId",
            "orderStatus",
            "paymentStatus",
            "orderTime"
        );
        queryRequest.setColumns(columns);

        // 分析并生成SQL
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        // 验证SQL生成
        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.contains("fact_order"), "SQL应包含fact_order表");
        assertTrue(sql.contains("order_id"), "SQL应包含order_id字段");

        printSql(sql, "简单字段查询SQL");
        log.info("聚合SQL: {}", queryEngine.getAggSql());
    }

    @Test
    @Order(2)
    @DisplayName("维度关联查询")
    void testDimensionJoinQuery() {
        // 获取销售查询模型
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 创建查询请求
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置查询列，包含维度属性
        List<String> columns = Arrays.asList(
            "orderId",
            "orderLineNo",
            "product$caption",      // 商品维度
            "product$categoryName",     // 商品品类
            "customer$caption",    // 客户维度
            "salesAmount"
        );
        queryRequest.setColumns(columns);

        // 分析并生成SQL
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        // 验证SQL生成
        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.contains("fact_sales"), "SQL应包含fact_sales表");
        assertTrue(sql.contains("dim_product") || sql.toLowerCase().contains("join"), "SQL应包含商品维度关联");
        assertTrue(sql.contains("dim_customer") || sql.toLowerCase().contains("join"), "SQL应包含客户维度关联");

        printSql(sql, "维度关联查询SQL");
    }

    @Test
    @Order(3)
    @DisplayName("多维度关联查询")
    void testMultipleDimensionJoinQuery() {
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");

        // 设置多个维度的列
        List<String> columns = Arrays.asList(
            "orderId",
            "orderDate$caption",       // 日期维度
            "orderDate$year",
            "orderDate$month",
            "customer$caption",    // 客户维度
            "customer$customerType",
            "store$caption",          // 门店维度
            "store$storeType",
            "channel$caption",      // 渠道维度
            "totalAmount",
            "orderPayAmount"
        );
        queryRequest.setColumns(columns);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "多维度关联查询SQL");
    }

    // ==========================================
    // 条件过滤测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("等值条件过滤")
    void testEqualConditionFilter() {
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderStatus", "totalAmount"));

        // 添加等值条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("orderStatus");
        slice.setOp("=");
        slice.setValue("COMPLETED");
        slices.add(slice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toLowerCase().contains("where"), "SQL应包含WHERE子句");

        printSql(sql, "等值条件过滤SQL");
        log.info("参数值: {}", queryEngine.getValues());
    }

    @Test
    @Order(11)
    @DisplayName("范围条件过滤")
    void testRangeConditionFilter() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderLineNo", "salesAmount", "salesDate$caption"));

        // 添加日期范围条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef dateSlice = new SliceRequestDef();
        dateSlice.setField("salesDate$caption");
        dateSlice.setOp("[)");  // 左闭右开
        dateSlice.setValue(Arrays.asList("2024-01-01", "2024-12-31"));
        slices.add(dateSlice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "范围条件过滤SQL");
        log.info("参数值: {}", queryEngine.getValues());
    }

    @Test
    @Order(12)
    @DisplayName("IN条件过滤")
    void testInConditionFilter() {
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderStatus", "totalAmount"));

        // 添加IN条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("orderStatus");
        slice.setOp("in");
        slice.setValue(Arrays.asList("COMPLETED", "PAID", "SHIPPED"));
        slices.add(slice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toLowerCase().contains("in"), "SQL应包含IN条件");

        printSql(sql, "IN条件过滤SQL");
        log.info("参数值: {}", queryEngine.getValues());
    }

    @Test
    @Order(13)
    @DisplayName("组合条件过滤")
    void testCombinedConditionFilter() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList(
            "orderId",
            "product$caption",
            "customer$caption",
            "salesAmount"
        ));

        // 添加多个条件
        List<SliceRequestDef> slices = new ArrayList<>();

        // 条件1: 订单状态
        SliceRequestDef statusSlice = new SliceRequestDef();
        statusSlice.setField("orderStatus");
        statusSlice.setOp("=");
        statusSlice.setValue("COMPLETED");
        slices.add(statusSlice);

        // 条件2: 销售金额范围
        SliceRequestDef amountSlice = new SliceRequestDef();
        amountSlice.setField("salesAmount");
        amountSlice.setOp(">=");
        amountSlice.setValue(100);
        slices.add(amountSlice);

        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "组合条件过滤SQL");
        log.info("参数值: {}", queryEngine.getValues());
    }

    // ==========================================
    // 排序测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("单字段排序")
    void testSingleFieldSort() {
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderTime", "totalAmount"));

        // 添加排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("totalAmount");
        order.setOrder("DESC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toLowerCase().contains("order by"), "SQL应包含ORDER BY子句");
        assertTrue(sql.toLowerCase().contains("desc"), "SQL应包含DESC关键字");

        printSql(sql, "单字段排序SQL");
    }

    @Test
    @Order(21)
    @DisplayName("多字段排序")
    void testMultipleFieldSort() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderLineNo", "salesDate$caption", "salesAmount"));

        // 添加多个排序
        List<OrderRequestDef> orders = new ArrayList<>();

        OrderRequestDef order1 = new OrderRequestDef();
        order1.setField("salesDate$caption");
        order1.setOrder("DESC");
        orders.add(order1);

        OrderRequestDef order2 = new OrderRequestDef();
        order2.setField("orderId");
        order2.setOrder("ASC");
        orders.add(order2);

        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toLowerCase().contains("order by"), "SQL应包含ORDER BY子句");

        printSql(sql, "多字段排序SQL");
    }

    @Test
    @Order(22)
    @DisplayName("NULL值排序")
    void testNullValueSort() {
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "promotion$caption", "discountAmount"));

        // 添加排序，指定NULL值处理
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("discountAmount");
        order.setOrder("DESC");
        order.setNullLast(true);  // NULL值放最后
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "NULL值排序SQL");
    }

    @Test
    @Order(30)
    @DisplayName("基本查询生成聚合SQL")
    void testQueryWithAggregationSql() {
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderTime", "totalAmount"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        // 验证聚合SQL（用于获取总数）
        String aggSql = queryEngine.getAggSql();
        assertNotNull(aggSql, "聚合SQL生成失败");

        printSql(sql, "基本查询SQL");
        log.info("聚合SQL (用于总数): {}", aggSql);
    }
}
