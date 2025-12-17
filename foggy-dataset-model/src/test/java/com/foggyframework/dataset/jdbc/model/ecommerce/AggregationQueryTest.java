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
}
