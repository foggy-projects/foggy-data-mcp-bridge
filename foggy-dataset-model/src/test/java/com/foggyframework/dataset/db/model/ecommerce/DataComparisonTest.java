package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.jdbc.model.def.query.request.*;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据对比测试
 *
 * <p>执行数据模型查询，与原生SQL直查结果进行比较，验证查询引擎生成的SQL准确性</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("数据对比测试 - 模型查询 vs 原生SQL")
class DataComparisonTest extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    // ==========================================
    // 基础聚合查询对比
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("订单总数对比")
    void testOrderCountComparison() {
        // 1. 原生SQL直查
        String nativeSql = "SELECT COUNT(*) as cnt FROM fact_order";
        Long nativeCount = executeQueryForObject(nativeSql, Long.class);
        log.info("原生SQL订单总数: {}", nativeCount);

        // 2. 模型查询 - 获取聚合SQL的总数
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql();
        printSql(modelSql, "模型生成SQL");

        // 执行模型生成的SQL，统计记录数
        String countSql = "SELECT COUNT(*) FROM (" + modelSql + ") t";
        Long modelCount = jdbcTemplate.queryForObject(countSql, Long.class);
        log.info("模型查询订单总数: {}", modelCount);

        // 3. 对比
        assertEquals(nativeCount, modelCount, "订单总数应一致");
    }

    @Test
    @Order(2)
    @DisplayName("订单金额汇总对比 - 使用getAggSql")
    void testOrderAmountSumComparison() {
        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                COUNT(*) as total,
                SUM(total_amount) as total_amount,
                SUM(discount_amount) as discount_amount,
                SUM(pay_amount) as pay_amount
            FROM fact_order
            """;
        Map<String, Object> nativeResult = jdbcTemplate.queryForMap(nativeSql);
        log.info("原生SQL结果: {}", nativeResult);

        // 2. 模型查询 - 使用 getAggSql() 获取全表汇总
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("totalAmount", "discountAmount", "orderPayAmount"));
        queryRequest.setReturnTotal(true);  // 启用汇总

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        // 使用 getAggSql() 而不是 getSql()
        String aggSql = queryEngine.getAggSql();
        printSql(aggSql, "模型汇总SQL (getAggSql)");

        Map<String, Object> modelResult = jdbcTemplate.queryForMap(aggSql);
        log.info("模型查询结果: {}", modelResult);

        // 3. 对比（使用BigDecimal精确比较）
        assertDecimalEquals(nativeResult.get("total"), modelResult.get("total"), "总记录数");
        assertDecimalEquals(nativeResult.get("total_amount"), modelResult.get("totalAmount"), "订单总额");
        assertDecimalEquals(nativeResult.get("discount_amount"), modelResult.get("discountAmount"), "折扣金额");
        assertDecimalEquals(nativeResult.get("pay_amount"), modelResult.get("orderPayAmount"), "应付金额");
    }

    @Test
    @Order(3)
    @DisplayName("销售数量和金额汇总对比 - 使用getAggSql")
    void testSalesAmountSumComparison() {
        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                COUNT(*) as total,
                SUM(quantity) as quantity,
                SUM(sales_amount) as sales_amount,
                SUM(cost_amount) as cost_amount,
                SUM(profit_amount) as profit_amount
            FROM fact_sales
            """;
        Map<String, Object> nativeResult = jdbcTemplate.queryForMap(nativeSql);
        log.info("原生SQL结果: {}", nativeResult);

        // 2. 模型查询 - 使用 getAggSql()
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("quantity", "salesAmount", "costAmount", "profitAmount"));
        queryRequest.setReturnTotal(true);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String aggSql = queryEngine.getAggSql();
        printSql(aggSql, "模型汇总SQL (getAggSql)");

        Map<String, Object> modelResult = jdbcTemplate.queryForMap(aggSql);
        log.info("模型查询结果: {}", modelResult);

        // 3. 对比
        assertDecimalEquals(nativeResult.get("total"), modelResult.get("total"), "总记录数");
        assertDecimalEquals(nativeResult.get("quantity"), modelResult.get("quantity"), "销售数量");
        assertDecimalEquals(nativeResult.get("sales_amount"), modelResult.get("salesAmount"), "销售金额");
        assertDecimalEquals(nativeResult.get("cost_amount"), modelResult.get("costAmount"), "成本金额");
        assertDecimalEquals(nativeResult.get("profit_amount"), modelResult.get("profitAmount"), "利润金额");
    }

    // ==========================================
    // 维度关联查询对比
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("按客户类型分组汇总对比")
    void testGroupByCustomerTypeComparison() {
        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                dc.customer_type,
                SUM(fo.total_amount) as total_amount,
                SUM(fo.pay_amount) as pay_amount,
                COUNT(*) as order_count
            FROM fact_order fo
            LEFT JOIN dim_customer dc ON fo.customer_key = dc.customer_key
            GROUP BY dc.customer_type
            ORDER BY dc.customer_type
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("customer$customerType", "totalAmount", "orderPayAmount"));

        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("customer$customerType");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("customer$customerType");
        order.setOrder("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql();
        printSql(modelSql, "模型生成SQL");

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql);
        log.info("模型查询结果: {} 条", modelResults.size());

        // 3. 对比
        assertEquals(nativeResults.size(), modelResults.size(), "分组数量应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);

            assertEquals(nativeRow.get("customer_type"), modelRow.get("customer$customerType"),
                "客户类型应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("total_amount"), modelRow.get("totalAmount"),
                "订单总额应一致: " + nativeRow.get("customer_type"));
            assertDecimalEquals(nativeRow.get("pay_amount"), modelRow.get("orderPayAmount"),
                "应付金额应一致: " + nativeRow.get("customer_type"));
        }
    }

    @Test
    @Order(11)
    @DisplayName("按商品品类分组汇总对比")
    void testGroupByProductCategoryComparison() {
        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                dp.category_name,
                SUM(fs.quantity) as quantity,
                SUM(fs.sales_amount) as sales_amount,
                SUM(fs.profit_amount) as profit_amount
            FROM fact_sales fs
            LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
            GROUP BY dp.category_name
            ORDER BY dp.category_name
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("product$categoryName", "quantity", "salesAmount", "profitAmount"));

        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("product$categoryName");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("product$categoryName");
        order.setOrder("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql();
        printSql(modelSql, "模型生成SQL");

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql);
        log.info("模型查询结果: {} 条", modelResults.size());

        // 3. 对比
        assertEquals(nativeResults.size(), modelResults.size(), "分组数量应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);

            assertEquals(nativeRow.get("category_name"), modelRow.get("product$categoryName"),
                "品类名称应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("quantity"), modelRow.get("quantity"),
                "销售数量应一致: " + nativeRow.get("category_name"));
            assertDecimalEquals(nativeRow.get("sales_amount"), modelRow.get("salesAmount"),
                "销售金额应一致: " + nativeRow.get("category_name"));
            assertDecimalEquals(nativeRow.get("profit_amount"), modelRow.get("profitAmount"),
                "利润金额应一致: " + nativeRow.get("category_name"));
        }
    }

    @Test
    @Order(12)
    @DisplayName("按年月分组汇总对比")
    void testGroupByYearMonthComparison() {
        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                dd.year,
                dd.month,
                SUM(fo.total_amount) as total_amount,
                SUM(fo.pay_amount) as pay_amount,
                COUNT(*) as order_count
            FROM fact_order fo
            LEFT JOIN dim_date dd ON fo.date_key = dd.date_key
            GROUP BY dd.year, dd.month
            ORDER BY dd.year DESC, dd.month ASC
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("orderDate$year", "orderDate$month", "totalAmount", "orderPayAmount"));

        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group1 = new GroupRequestDef();
        group1.setField("orderDate$year");
        groups.add(group1);
        GroupRequestDef group2 = new GroupRequestDef();
        group2.setField("orderDate$month");
        groups.add(group2);
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order1 = new OrderRequestDef();
        order1.setField("orderDate$year");
        order1.setOrder("DESC");
        orders.add(order1);
        OrderRequestDef order2 = new OrderRequestDef();
        order2.setField("orderDate$month");
        order2.setOrder("ASC");
        orders.add(order2);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql();
        printSql(modelSql, "模型生成SQL");

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql);
        log.info("模型查询结果: {} 条", modelResults.size());

        // 3. 对比
        assertEquals(nativeResults.size(), modelResults.size(), "分组数量应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);

            assertEquals(toInt(nativeRow.get("year")), toInt(modelRow.get("orderDate$year")),
                "年份应一致: 行 " + i);
            assertEquals(toInt(nativeRow.get("month")), toInt(modelRow.get("orderDate$month")),
                "月份应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("total_amount"), modelRow.get("totalAmount"),
                "订单总额应一致: " + nativeRow.get("year") + "-" + nativeRow.get("month"));
            assertDecimalEquals(nativeRow.get("pay_amount"), modelRow.get("orderPayAmount"),
                "应付金额应一致: " + nativeRow.get("year") + "-" + nativeRow.get("month"));
        }
    }

    // ==========================================
    // 条件过滤查询对比
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("等值条件过滤对比 - 使用getAggSql")
    void testEqualFilterComparison() {
        String status = "COMPLETED";

        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                COUNT(*) as total,
                SUM(total_amount) as total_amount,
                SUM(pay_amount) as pay_amount
            FROM fact_order
            WHERE order_status = ?
            """;
        Map<String, Object> nativeResult = jdbcTemplate.queryForMap(nativeSql, status);
        log.info("原生SQL结果: {}", nativeResult);

        // 2. 模型查询 - 使用 getAggSql()
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("totalAmount", "orderPayAmount"));
        queryRequest.setReturnTotal(true);

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("orderStatus");
        slice.setOp("=");
        slice.setValue(status);
        slices.add(slice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String aggSql = queryEngine.getAggSql();
        List<Object> values = queryEngine.getValues();
        printSql(aggSql, "模型汇总SQL (getAggSql)");
        log.info("参数: {}", values);

        Map<String, Object> modelResult = jdbcTemplate.queryForMap(aggSql, values.toArray());
        log.info("模型查询结果: {}", modelResult);

        // 3. 对比
        assertDecimalEquals(nativeResult.get("total"), modelResult.get("total"), "总记录数");
        assertDecimalEquals(nativeResult.get("total_amount"), modelResult.get("totalAmount"), "订单总额");
        assertDecimalEquals(nativeResult.get("pay_amount"), modelResult.get("orderPayAmount"), "应付金额");
    }

    @Test
    @Order(21)
    @DisplayName("IN条件过滤对比 - 使用getAggSql")
    void testInFilterComparison() {
        List<String> statuses = Arrays.asList("COMPLETED", "SHIPPED", "PAID");

        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                COUNT(*) as total,
                SUM(total_amount) as total_amount
            FROM fact_order
            WHERE order_status IN (?, ?, ?)
            """;
        Map<String, Object> nativeResult = jdbcTemplate.queryForMap(nativeSql, statuses.toArray());
        log.info("原生SQL结果: {}", nativeResult);

        // 2. 模型查询 - 使用 getAggSql()
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("totalAmount"));
        queryRequest.setReturnTotal(true);

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("orderStatus");
        slice.setOp("in");
        slice.setValue(statuses);
        slices.add(slice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String aggSql = queryEngine.getAggSql();
        List<Object> values = queryEngine.getValues();
        printSql(aggSql, "模型汇总SQL (getAggSql)");
        log.info("参数: {}", values);

        Map<String, Object> modelResult = jdbcTemplate.queryForMap(aggSql, values.toArray());
        log.info("模型查询结果: {}", modelResult);

        // 3. 对比
        assertDecimalEquals(nativeResult.get("total"), modelResult.get("total"), "总记录数");
        assertDecimalEquals(nativeResult.get("total_amount"), modelResult.get("totalAmount"), "订单总额");
    }

    @Test
    @Order(22)
    @DisplayName("比较运算符条件过滤对比 - 使用getAggSql")
    void testComparisonFilterComparison() {
        BigDecimal minAmount = new BigDecimal("1000");

        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                COUNT(*) as total,
                SUM(sales_amount) as sales_amount,
                SUM(profit_amount) as profit_amount
            FROM fact_sales
            WHERE sales_amount >= ?
            """;
        Map<String, Object> nativeResult = jdbcTemplate.queryForMap(nativeSql, minAmount);
        log.info("原生SQL结果: {}", nativeResult);

        // 2. 模型查询 - 使用 getAggSql()
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("salesAmount", "profitAmount"));
        queryRequest.setReturnTotal(true);

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesAmount");
        slice.setOp(">=");
        slice.setValue(minAmount);
        slices.add(slice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String aggSql = queryEngine.getAggSql();
        List<Object> values = queryEngine.getValues();
        printSql(aggSql, "模型汇总SQL (getAggSql)");
        log.info("参数: {}", values);

        Map<String, Object> modelResult = jdbcTemplate.queryForMap(aggSql, values.toArray());
        log.info("模型查询结果: {}", modelResult);

        // 3. 对比
        assertDecimalEquals(nativeResult.get("total"), modelResult.get("total"), "总记录数");
        assertDecimalEquals(nativeResult.get("sales_amount"), modelResult.get("salesAmount"), "销售金额");
        assertDecimalEquals(nativeResult.get("profit_amount"), modelResult.get("profitAmount"), "利润金额");
    }

    @Test
    @Order(23)
    @DisplayName("范围条件过滤对比 - 使用getAggSql")
    void testRangeFilterComparison() {
        // 2024年上半年

        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                COUNT(*) as total,
                SUM(total_amount) as total_amount
            FROM fact_order fo
            LEFT JOIN dim_date dd ON fo.date_key = dd.date_key
            WHERE dd.full_date >= ? AND dd.full_date < ?
            """;
        Map<String, Object> nativeResult = jdbcTemplate.queryForMap(nativeSql, "2024-01-01", "2024-07-01");
        log.info("原生SQL结果: {}", nativeResult);

        // 2. 模型查询 - 使用 getAggSql()
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("totalAmount"));
        queryRequest.setReturnTotal(true);

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("orderDate$caption");
        slice.setOp("[)");
        slice.setValue(Arrays.asList("2024-01-01", "2024-07-01"));
        slices.add(slice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String aggSql = queryEngine.getAggSql();
        List<Object> values = queryEngine.getValues();
        printSql(aggSql, "模型汇总SQL (getAggSql)");
        log.info("参数: {}", values);

        Map<String, Object> modelResult = jdbcTemplate.queryForMap(aggSql, values.toArray());
        log.info("模型查询结果: {}", modelResult);

        // 3. 对比
        assertDecimalEquals(nativeResult.get("total"), modelResult.get("total"), "总记录数");
        assertDecimalEquals(nativeResult.get("total_amount"), modelResult.get("totalAmount"), "订单总额");
    }

    // ==========================================
    // 多维度组合查询对比
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("按年月+品类多维度分组对比")
    void testMultiDimensionGroupByComparison() {
        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                dd.year,
                dd.month,
                dp.category_name,
                SUM(fs.quantity) as quantity,
                SUM(fs.sales_amount) as sales_amount
            FROM fact_sales fs
            LEFT JOIN dim_date dd ON fs.date_key = dd.date_key
            LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
            GROUP BY dd.year, dd.month, dp.category_name
            ORDER BY dd.year DESC, dd.month ASC, dp.category_name ASC
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList(
            "salesDate$year", "salesDate$month", "product$categoryName",
            "quantity", "salesAmount"
        ));

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("salesDate$year"));
        groups.add(createGroup("salesDate$month"));
        groups.add(createGroup("product$categoryName"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("salesDate$year", "DESC"));
        orders.add(createOrder("salesDate$month", "ASC"));
        orders.add(createOrder("product$categoryName", "ASC"));
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql();
        printSql(modelSql, "模型生成SQL");

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql);
        log.info("模型查询结果: {} 条", modelResults.size());

        // 3. 对比
        assertEquals(nativeResults.size(), modelResults.size(), "分组数量应一致");

        // 对比前10条数据
        int compareCount = Math.min(10, nativeResults.size());
        for (int i = 0; i < compareCount; i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);

            assertEquals(toInt(nativeRow.get("year")), toInt(modelRow.get("salesDate$year")),
                "年份应一致: 行 " + i);
            assertEquals(toInt(nativeRow.get("month")), toInt(modelRow.get("salesDate$month")),
                "月份应一致: 行 " + i);
            assertEquals(nativeRow.get("category_name"), modelRow.get("product$categoryName"),
                "品类应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("quantity"), modelRow.get("quantity"),
                "数量应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("sales_amount"), modelRow.get("salesAmount"),
                "金额应一致: 行 " + i);
        }
    }

    @Test
    @Order(31)
    @DisplayName("带条件的多维度分组对比")
    void testMultiDimensionGroupByWithFilterComparison() {
        String status = "COMPLETED";

        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                dc.customer_type,
                dch.channel_type,
                SUM(fo.total_amount) as total_amount,
                SUM(fo.pay_amount) as pay_amount
            FROM fact_order fo
            LEFT JOIN dim_customer dc ON fo.customer_key = dc.customer_key
            LEFT JOIN dim_channel dch ON fo.channel_key = dch.channel_key
            WHERE fo.order_status = ?
            GROUP BY dc.customer_type, dch.channel_type
            ORDER BY dc.customer_type ASC, dch.channel_type ASC
            """;
        List<Map<String, Object>> nativeResults = jdbcTemplate.queryForList(nativeSql, status);
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询
        JdbcQueryModel queryModel = getQueryModel("FactOrderQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList(
            "customer$customerType", "channel$channelType",
            "totalAmount", "orderPayAmount"
        ));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("orderStatus");
        slice.setOp("=");
        slice.setValue(status);
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("customer$customerType"));
        groups.add(createGroup("channel$channelType"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("customer$customerType", "ASC"));
        orders.add(createOrder("channel$channelType", "ASC"));
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql();
        List<Object> values = queryEngine.getValues();
        printSql(modelSql, "模型生成SQL");
        log.info("参数: {}", values);

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql, values.toArray());
        log.info("模型查询结果: {} 条", modelResults.size());

        // 3. 对比
        assertEquals(nativeResults.size(), modelResults.size(), "分组数量应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);

            assertEquals(nativeRow.get("customer_type"), modelRow.get("customer$customerType"),
                "客户类型应一致: 行 " + i);
            assertEquals(nativeRow.get("channel_type"), modelRow.get("channel$channelType"),
                "渠道类型应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("total_amount"), modelRow.get("totalAmount"),
                "订单总额应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("pay_amount"), modelRow.get("orderPayAmount"),
                "应付金额应一致: 行 " + i);
        }
    }

    // ==========================================
    // 明细查询对比
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("明细查询带维度字段对比")
    void testDetailQueryWithDimensionComparison() {
        // 1. 原生SQL直查 (取前100条)
        String nativeSql = """
            SELECT
                fs.order_id,
                fs.order_line_no,
                dp.product_name,
                dp.category_name,
                dc.customer_name,
                dc.customer_type,
                fs.quantity,
                fs.sales_amount
            FROM fact_sales fs
            LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
            LEFT JOIN dim_customer dc ON fs.customer_key = dc.customer_key
            ORDER BY fs.order_id ASC, fs.order_line_no ASC
            LIMIT 100
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList(
            "orderId", "orderLineNo",
            "product$caption", "product$categoryName",
            "customer$caption", "customer$customerType",
            "quantity", "salesAmount"
        ));

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("orderId", "ASC"));
        orders.add(createOrder("orderLineNo", "ASC"));
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql() + " LIMIT 100";
        printSql(modelSql, "模型生成SQL");

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql);
        log.info("模型查询结果: {} 条", modelResults.size());

        // 3. 对比
        assertEquals(nativeResults.size(), modelResults.size(), "记录数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);

            assertEquals(nativeRow.get("order_id"), modelRow.get("orderId"),
                "订单ID应一致: 行 " + i);
            assertEquals(toInt(nativeRow.get("order_line_no")), toInt(modelRow.get("orderLineNo")),
                "行号应一致: 行 " + i);
            assertEquals(nativeRow.get("product_name"), modelRow.get("product$caption"),
                "商品名称应一致: 行 " + i);
            assertEquals(nativeRow.get("category_name"), modelRow.get("product$categoryName"),
                "品类名称应一致: 行 " + i);
            assertEquals(nativeRow.get("customer_name"), modelRow.get("customer$caption"),
                "客户名称应一致: 行 " + i);
            assertEquals(nativeRow.get("customer_type"), modelRow.get("customer$customerType"),
                "客户类型应一致: 行 " + i);
        }
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    /**
     * 比较两个数值是否相等（支持不同数值类型）
     */
    private void assertDecimalEquals(Object expected, Object actual, String message) {
        BigDecimal expectedDecimal = toBigDecimal(expected);
        BigDecimal actualDecimal = toBigDecimal(actual);

        if (expectedDecimal == null && actualDecimal == null) {
            return;
        }

        assertNotNull(expectedDecimal, message + " - 期望值不应为null");
        assertNotNull(actualDecimal, message + " - 实际值不应为null");

        // 使用2位小数精度比较
        assertEquals(
            expectedDecimal.setScale(2, RoundingMode.HALF_UP),
            actualDecimal.setScale(2, RoundingMode.HALF_UP),
            message
        );
    }

    /**
     * 转换为BigDecimal
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return new BigDecimal(value.toString());
    }

    /**
     * 转换为Integer
     */
    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    /**
     * 创建分组定义
     */
    private GroupRequestDef createGroup(String name) {
        GroupRequestDef group = new GroupRequestDef();
        group.setField(name);
        return group;
    }

    /**
     * 创建排序定义
     */
    private OrderRequestDef createOrder(String name, String order) {
        OrderRequestDef orderDef = new OrderRequestDef();
        orderDef.setField(name);
        orderDef.setOrder(order);
        return orderDef;
    }
}
