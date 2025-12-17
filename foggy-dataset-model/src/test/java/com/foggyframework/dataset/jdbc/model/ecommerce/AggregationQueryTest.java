package com.foggyframework.dataset.jdbc.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.jdbc.model.def.query.request.*;
import com.foggyframework.dataset.jdbc.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.jdbc.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 查询引擎测试 - 聚合查询
 *
 * <p>测试分组聚合、度量计算、维度钻取等场景</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("查询引擎测试 - 聚合查询")
class AggregationQueryTest extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    // ==========================================
    // 基本聚合测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("按日期分组汇总")
    void testGroupByDate() {
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");

        // 设置查询列：日期维度 + 度量
        List<String> columns = Arrays.asList(
            "orderDate$caption",
            "totalQuantity",
            "totalAmount",
            "orderPayAmount"
        );
        queryRequest.setColumns(columns);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("orderDate$caption");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        // 添加排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderDate$caption");
        order.setOrder("DESC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toLowerCase().contains("group by"), "SQL应包含GROUP BY子句");
        assertTrue(sql.toLowerCase().contains("sum"), "SQL应包含SUM聚合函数");

        printSql(sql, "按日期分组汇总SQL");
    }

    @Test
    @Order(2)
    @DisplayName("按年月分组汇总")
    void testGroupByYearMonth() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置查询列：年、月 + 度量
        List<String> columns = Arrays.asList(
            "salesDate$year",
            "salesDate$month",
            "quantity",
            "salesAmount",
            "profitAmount"
        );
        queryRequest.setColumns(columns);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("salesDate$year");
        groups.add(group1);

        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("salesDate$month");
        groups.add(group2);

        queryRequest.setGroupBy(groups);

        // 排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order1 = new OrderRequestDef();
        order1.setField("salesDate$year");
        order1.setOrder("DESC");
        orders.add(order1);

        OrderRequestDef order2 = new OrderRequestDef();
        order2.setField("salesDate$month");
        order2.setOrder("ASC");
        orders.add(order2);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "按年月分组汇总SQL");
    }

    @Test
    @Order(3)
    @DisplayName("按商品品类分组汇总")
    void testGroupByCategory() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置查询列：品类 + 度量
        List<String> columns = Arrays.asList(
            "product$categoryName",
            "product$subCategoryName",
            "quantity",
            "salesAmount",
            "costAmount",
            "profitAmount"
        );
        queryRequest.setColumns(columns);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("product$categoryName");
        groups.add(group1);

        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("product$subCategoryName");
        groups.add(group2);

        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "按商品品类分组汇总SQL");
    }

    @Test
    @Order(4)
    @DisplayName("按客户类型分组汇总")
    void testGroupByCustomerType() {
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "customer$customerType",
            "customer$memberLevel",
            "totalQuantity",
            "totalAmount",
            "discountAmount",
            "orderPayAmount"
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

        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "按客户类型分组汇总SQL");
    }

    // ==========================================
    // 多维度分组测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("按日期+品类多维度分组")
    void testGroupByDateAndCategory() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "salesDate$year",
            "salesDate$month",
            "product$categoryName",
            "quantity",
            "salesAmount",
            "profitAmount"
        );
        queryRequest.setColumns(columns);

        // 设置多维度分组
        List<GroupRequestDef> groups = new ArrayList<>();

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("salesDate$year");
        groups.add(group1);

        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("salesDate$month");
        groups.add(group2);

        GroupRequestDef group3 = new GroupRequestDef();
        group3.setField("product$categoryName");
        groups.add(group3);

        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "按日期+品类多维度分组SQL");
    }

    @Test
    @Order(11)
    @DisplayName("按门店+渠道多维度分组")
    void testGroupByStoreAndChannel() {
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "store$province",
            "store$city",
            "channel$channelType",
            "totalQuantity",
            "totalAmount"
        );
        queryRequest.setColumns(columns);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("store$province");
        groups.add(group1);

        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("store$city");
        groups.add(group2);

        GroupRequestDef group3 = new GroupRequestDef();
        group3.setField("channel$channelType");
        groups.add(group3);

        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "按门店+渠道多维度分组SQL");
    }

    // ==========================================
    // 条件聚合测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("带条件的分组汇总")
    void testGroupByWithCondition() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "product$categoryName",
            "quantity",
            "salesAmount",
            "profitAmount"
        );
        queryRequest.setColumns(columns);

        // 添加条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("orderStatus");
        slice.setOp("=");
        slice.setValue("COMPLETED");
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("product$categoryName");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toLowerCase().contains("where"), "SQL应包含WHERE子句");
        assertTrue(sql.toLowerCase().contains("group by"), "SQL应包含GROUP BY子句");

        printSql(sql, "带条件的分组汇总SQL");
        log.info("参数值: {}", queryEngine.getValues());
    }

    @Test
    @Order(21)
    @DisplayName("日期范围内的分组汇总")
    void testGroupByWithDateRange() {
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "orderDate$month",
            "customer$customerType",
            "totalAmount",
            "orderPayAmount"
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

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("orderDate$month");
        groups.add(group1);

        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("customer$customerType");
        groups.add(group2);

        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "日期范围内的分组汇总SQL");
        log.info("参数值: {}", queryEngine.getValues());
    }

    // ==========================================
    // 库存分析测试（半可加度量）
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("库存快照按日期分组")
    void testInventoryGroupByDate() {
        JdbcQueryModel queryModel = getQueryModel("FactInventorySnapshotQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactInventorySnapshotQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "snapshotDate$caption",
            "quantityOnHand",
            "quantityAvailable",
            "inventoryValue"
        );
        queryRequest.setColumns(columns);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("snapshotDate$caption");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "库存快照按日期分组SQL");
    }

    @Test
    @Order(31)
    @DisplayName("库存快照按商品品类分组")
    void testInventoryGroupByCategory() {
        JdbcQueryModel queryModel = getQueryModel("FactInventorySnapshotQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactInventorySnapshotQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "product$categoryName",
            "quantityOnHand",
            "quantityReserved",
            "quantityAvailable",
            "inventoryValue"
        );
        queryRequest.setColumns(columns);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("product$categoryName");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "库存快照按商品品类分组SQL");
    }

    // ==========================================
    // 退货分析测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("退货按原因分组统计")
    void testReturnGroupByReason() {
        JdbcQueryModel queryModel = getQueryModel("FactReturnQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactReturnQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "returnType",
            "returnStatus",
            "returnQuantity",
            "returnAmount"
        );
        queryRequest.setColumns(columns);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("returnType");
        groups.add(group1);

        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("returnStatus");
        groups.add(group2);

        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "退货按原因分组统计SQL");
    }

    @Test
    @Order(41)
    @DisplayName("退货按商品+月份分组统计")
    void testReturnGroupByProductAndMonth() {
        JdbcQueryModel queryModel = getQueryModel("FactReturnQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactReturnQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "returnDate$year",
            "returnDate$month",
            "product$categoryName",
            "returnQuantity",
            "returnAmount"
        );
        queryRequest.setColumns(columns);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("returnDate$year");
        groups.add(group1);

        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("returnDate$month");
        groups.add(group2);

        GroupRequestDef group3 = new GroupRequestDef();
        group3.setField("product$categoryName");
        groups.add(group3);

        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "退货按商品+月份分组统计SQL");
    }

    // ==========================================
    // 聚合字段同名条件过滤测试
    // ==========================================

    /**
     * 测试场景：定义 sum(quantity) as quantity，查询条件 quantity = 10
     *
     * <p>验证当聚合字段别名与原始字段同名时，SQL生成是否正确：
     * <ul>
     *   <li>内层SQL：WHERE t1.quantity = 10（过滤明细数据）</li>
     *   <li>外层SQL：sum(tx.quantity) as quantity（聚合已过滤的数据）</li>
     * </ul>
     * </p>
     *
     * <p>预期行为：条件应用于内层WHERE子句，先过滤明细数据再聚合，
     * 别名冲突被子查询作用域隔离，SQL应能正确执行。</p>
     */
    @Test
    @Order(50)
    @DisplayName("聚合字段同名条件过滤 - sum(quantity) where quantity=10")
    void testAggregateFieldWithSameNameCondition() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置查询列：包含聚合度量 quantity (sum(quantity) as quantity)
        List<String> columns = Arrays.asList(
            "product$categoryName",
            "quantity",        // sum(quantity) as quantity
            "salesAmount"      // sum(sales_amount) as salesAmount
        );
        queryRequest.setColumns(columns);

        // 添加条件：quantity = 10（对原始字段过滤）
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("quantity");
        slice.setOp("=");
        slice.setValue(10);
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("product$categoryName");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        // 验证SQL结构
        String lowerSql = sql.toLowerCase();
        assertTrue(lowerSql.contains("where"), "SQL应包含WHERE子句");
        assertTrue(lowerSql.contains("sum"), "SQL应包含SUM聚合函数");
        assertTrue(lowerSql.contains("group by"), "SQL应包含GROUP BY子句");

        printSql(sql, "聚合字段同名条件过滤SQL (quantity=10)");
        log.info("参数值: {}", queryEngine.getValues());

        // 执行SQL验证不会出错
        try {
            List<Map<String, Object>> results = executeQuery(
                sql.replace("?", queryEngine.getValues().get(0).toString())
            );
            log.info("查询结果数量: {}", results.size());
            printResults(results);
        } catch (Exception e) {
            log.error("SQL执行失败: {}", e.getMessage());
            fail("SQL应能正确执行，但出现错误: " + e.getMessage());
        }
    }

    /**
     * 测试场景：定义 sum(salesAmount) as salesAmount，查询条件 salesAmount > 100
     *
     * <p>验证使用大于条件时的SQL生成</p>
     */
    @Test
    @Order(51)
    @DisplayName("聚合字段同名条件过滤 - sum(salesAmount) where salesAmount > 100")
    void testAggregateFieldWithGreaterThanCondition() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "salesDate$month",
            "salesAmount",     // sum(sales_amount) as salesAmount
            "profitAmount"     // sum(profit_amount) as profitAmount
        );
        queryRequest.setColumns(columns);

        // 添加条件：salesAmount > 100（对原始明细数据过滤）
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesAmount");
        slice.setOp(">");
        slice.setValue(100);
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("salesDate$month");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "聚合字段同名条件过滤SQL (salesAmount > 100)");
        log.info("参数值: {}", queryEngine.getValues());
    }

    /**
     * 测试场景：多个聚合字段同时作为条件
     *
     * <p>定义 sum(quantity) as quantity, sum(salesAmount) as salesAmount
     * 查询条件 quantity >= 5 AND salesAmount >= 50</p>
     */
    @Test
    @Order(52)
    @DisplayName("多个聚合字段同名条件过滤")
    void testMultipleAggregateFieldsWithConditions() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "product$categoryName",
            "product$subCategoryName",
            "quantity",        // sum(quantity)
            "salesAmount",     // sum(sales_amount)
            "profitAmount"     // sum(profit_amount)
        );
        queryRequest.setColumns(columns);

        // 添加多个条件
        List<SliceRequestDef> slices = new ArrayList<>();

        SliceRequestDef slice1 = new SliceRequestDef();
        slice1.setField("quantity");
        slice1.setOp(">=");
        slice1.setValue(5);
        slices.add(slice1);

        SliceRequestDef slice2 = new SliceRequestDef();
        slice2.setField("salesAmount");
        slice2.setOp(">=");
        slice2.setValue(50);
        slices.add(slice2);

        queryRequest.setSlice(slices);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();

        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("product$categoryName");
        groups.add(group1);

        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("product$subCategoryName");
        groups.add(group2);

        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        // 验证两个条件都存在
        String lowerSql = sql.toLowerCase();
        assertTrue(lowerSql.contains("where"), "SQL应包含WHERE子句");

        printSql(sql, "多个聚合字段同名条件过滤SQL");
        log.info("参数值: {}", queryEngine.getValues());
    }

    /**
     * 测试场景：聚合字段使用 IN 条件
     *
     * <p>定义 sum(quantity) as quantity，查询条件 quantity IN (1, 2, 3, 5, 10)</p>
     */
    @Test
    @Order(53)
    @DisplayName("聚合字段同名 IN 条件过滤")
    void testAggregateFieldWithInCondition() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "customer$memberLevel",
            "quantity",
            "salesAmount"
        );
        queryRequest.setColumns(columns);

        // 添加 IN 条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("quantity");
        slice.setOp("in");
        slice.setValue(Arrays.asList(1, 2, 3, 5, 10));
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("customer$memberLevel");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toLowerCase().contains("in"), "SQL应包含IN条件");

        printSql(sql, "聚合字段同名IN条件过滤SQL");
        log.info("参数值: {}", queryEngine.getValues());
    }

    /**
     * 测试场景：聚合字段使用范围条件 BETWEEN
     *
     * <p>定义 sum(salesAmount) as salesAmount，查询条件 salesAmount BETWEEN 100 AND 500</p>
     */
    @Test
    @Order(54)
    @DisplayName("聚合字段同名范围条件过滤")
    void testAggregateFieldWithBetweenCondition() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置查询列
        List<String> columns = Arrays.asList(
            "channel$channelType",
            "quantity",
            "salesAmount",
            "profitAmount"
        );
        queryRequest.setColumns(columns);

        // 添加范围条件 [100, 500)
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesAmount");
        slice.setOp("[)");  // 左闭右开区间
        slice.setValue(Arrays.asList(100, 500));
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("channel$channelType");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "聚合字段同名范围条件过滤SQL");
        log.info("参数值: {}", queryEngine.getValues());
    }

    /**
     * 测试场景：不分组情况下的聚合字段条件过滤
     *
     * <p>验证当没有GROUP BY时，对聚合字段原始值的条件过滤</p>
     */
    @Test
    @Order(55)
    @DisplayName("无分组情况下聚合字段条件过滤")
    void testAggregateFieldConditionWithoutGroupBy() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置查询列（明细查询）
        List<String> columns = Arrays.asList(
            "orderId",
            "product$categoryName",
            "quantity",
            "salesAmount"
        );
        queryRequest.setColumns(columns);

        // 添加条件：quantity = 5（明细级别过滤）
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("quantity");
        slice.setOp("=");
        slice.setValue(5);
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 不设置分组 - 明细查询
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String innerSql = queryEngine.getInnerSql();
        assertNotNull(innerSql, "内层SQL生成失败");
        assertTrue(innerSql.toLowerCase().contains("where"), "内层SQL应包含WHERE子句");

        printSql(innerSql, "无分组时的明细查询SQL");
        log.info("参数值: {}", queryEngine.getValues());
    }
}
