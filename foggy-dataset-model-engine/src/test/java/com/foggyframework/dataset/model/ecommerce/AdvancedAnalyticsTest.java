package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.*;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.support.CalculatedDbColumn;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 高级分析能力集成测试
 *
 * <p>测试 COUNT(DISTINCT)、窗口函数、QM 预定义计算字段等高级分析特性</p>
 *
 * @author Foggy
 * @since 8.4.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("高级分析能力测试")
class AdvancedAnalyticsTest extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    @Resource
    private AdvancedQueryFacade queryFacade;

    // ==========================================
    // Phase 1A: COUNT(DISTINCT) 测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("COUNTD() 去重计数表达式")
    void testCountDistinctExpression() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "uv", "独立客户数", "COUNTD(customer$id)"
        ));
        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList("product$categoryName", "uv"));
        queryRequest.setGroupBy(buildGroupBy("product$categoryName"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL 生成失败");
        printSql(sql, "COUNTD() 去重计数");

        // 验证 SQL 包含 COUNT(DISTINCT
        assertTrue(sql.toUpperCase().contains("COUNT(DISTINCT"),
                "SQL 应包含 COUNT(DISTINCT 语法");

        // 执行查询验证
        List<Map<String, Object>> results = executeQuery(sql);
        assertNotNull(results, "查询结果不应为空");
        assertFalse(results.isEmpty(), "查询结果不应为空");
        printResults(results);
    }

    @Test
    @Order(2)
    @DisplayName("COUNT_DISTINCT() 别名形式")
    void testCountDistinctAlias() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "uv", "独立客户数", "COUNT_DISTINCT(customer$id)"
        ));
        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList("product$categoryName", "uv"));
        queryRequest.setGroupBy(buildGroupBy("product$categoryName"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);

        assertTrue(sql.toUpperCase().contains("COUNT(DISTINCT"),
                "COUNT_DISTINCT 应转换为 COUNT(DISTINCT 语法");

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
        printResults(results);
    }

    @Test
    @Order(3)
    @DisplayName("TM 中定义的 COUNT_DISTINCT 度量 (uniqueCustomers)")
    void testTmCountDistinctMeasure() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("product$categoryName", "uniqueCustomers"));

        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef g1 = new GroupRequestDef();
        g1.setField("product$categoryName");
        groups.add(g1);
        GroupRequestDef g2 = new GroupRequestDef();
        g2.setField("uniqueCustomers");
        g2.setAgg("COUNT_DISTINCT");
        groups.add(g2);
        queryRequest.setGroupBy(groups);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        printSql(sql, "TM COUNT_DISTINCT 度量");

        assertTrue(sql.toUpperCase().contains("COUNT(DISTINCT"),
                "TM COUNT_DISTINCT 度量应生成 COUNT(DISTINCT SQL");

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
        printResults(results);
    }

    // ==========================================
    // Phase 2A: 窗口函数测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("ROW_NUMBER 窗口函数")
    void testRowNumberWindow() {
        if (!supportsWindowFunctions()) {
            log.info("当前数据库不支持窗口函数，跳过");
            return;
        }
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        CalculatedFieldDef rowNum = new CalculatedFieldDef();
        rowNum.setName("rowNum");
        rowNum.setCaption("行号");
        rowNum.setExpression("ROW_NUMBER()");
        rowNum.setPartitionBy(Arrays.asList("product$categoryName"));
        rowNum.setWindowOrderBy(Arrays.asList(
                new WindowOrderDef("salesAmount", "desc")
        ));
        calculatedFields.add(rowNum);
        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList(
                "product$categoryName", "product$caption", "salesAmount", "rowNum"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        printSql(sql, "ROW_NUMBER 窗口函数");

        assertTrue(sql.toUpperCase().contains("ROW_NUMBER()"),
                "SQL 应包含 ROW_NUMBER()");
        assertTrue(sql.toUpperCase().contains("OVER"),
                "SQL 应包含 OVER 子句");
        assertTrue(sql.toUpperCase().contains("PARTITION BY"),
                "SQL 应包含 PARTITION BY");

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
        printResults(results);
    }

    @Test
    @Order(11)
    @DisplayName("RANK 窗口函数")
    void testRankWindow() {
        if (!supportsWindowFunctions()) {
            log.info("当前数据库不支持窗口函数，跳过");
            return;
        }
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        CalculatedFieldDef rank = new CalculatedFieldDef();
        rank.setName("salesRank");
        rank.setCaption("销售排名");
        rank.setExpression("RANK()");
        rank.setPartitionBy(Arrays.asList("product$categoryName"));
        rank.setWindowOrderBy(Arrays.asList(
                new WindowOrderDef("salesAmount", "desc")
        ));
        calculatedFields.add(rank);
        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList(
                "product$categoryName", "product$caption", "salesAmount", "salesRank"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        printSql(sql, "RANK 窗口函数");

        assertTrue(sql.toUpperCase().contains("RANK()"),
                "SQL 应包含 RANK()");
        assertTrue(sql.toUpperCase().contains("OVER"),
                "SQL 应包含 OVER 子句");

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
        printResults(results);
    }

    @Test
    @Order(111)
    @DisplayName("窗口计算字段别名不能在同一 query_model slice 中过滤")
    void testWindowCalculatedFieldSliceRejectedBeforeSql() {
        if (!supportsWindowFunctions()) {
            log.info("当前数据库不支持窗口函数，跳过");
            return;
        }
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        CalculatedFieldDef rank = new CalculatedFieldDef();
        rank.setName("salesRank");
        rank.setCaption("销售排名");
        rank.setExpression("RANK()");
        rank.setPartitionBy(Arrays.asList("product$categoryName"));
        rank.setWindowOrderBy(Arrays.asList(
                new WindowOrderDef("salesAmount", "desc")
        ));
        calculatedFields.add(rank);
        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList(
                "product$categoryName", "product$caption", "salesAmount", "salesRank"
        ));

        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesRank");
        slice.setOp("=");
        slice.setValue(1);
        queryRequest.setSlice(List.of(slice));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest));
        assertTrue(ex.getMessage().contains("WINDOW_CALCULATED_FIELD_SLICE_NOT_SUPPORTED"));
        assertTrue(ex.getMessage().contains("salesRank"));
    }

    @Test
    @Order(12)
    @DisplayName("LAG 窗口函数 — 环比计算")
    void testLagWindow() {
        if (!supportsWindowFunctions()) {
            log.info("当前数据库不支持窗口函数，跳过");
            return;
        }
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        CalculatedFieldDef prevAmount = new CalculatedFieldDef();
        prevAmount.setName("prevAmount");
        prevAmount.setCaption("上一期销售额");
        prevAmount.setExpression("LAG(salesAmount, 1)");
        prevAmount.setPartitionBy(Arrays.asList("product$caption"));
        prevAmount.setWindowOrderBy(Arrays.asList(
                new WindowOrderDef("salesDate$caption", "asc")
        ));
        calculatedFields.add(prevAmount);
        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList(
                "product$caption", "salesDate$caption", "salesAmount", "prevAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        printSql(sql, "LAG 窗口函数（环比）");

        assertTrue(sql.toUpperCase().contains("LAG("),
                "SQL 应包含 LAG(");
        assertTrue(sql.toUpperCase().contains("OVER"),
                "SQL 应包含 OVER 子句");

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
        printResults(results);
    }

    @Test
    @Order(13)
    @DisplayName("移动平均（带窗口帧）")
    void testMovingAverageWithFrame() {
        if (!supportsWindowFunctions()) {
            log.info("当前数据库不支持窗口函数，跳过");
            return;
        }
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        CalculatedFieldDef ma = new CalculatedFieldDef();
        ma.setName("ma3");
        ma.setCaption("3行移动平均");
        ma.setExpression("AVG(salesAmount)");
        ma.setPartitionBy(Arrays.asList("product$caption"));
        ma.setWindowOrderBy(Arrays.asList(
                new WindowOrderDef("salesDate$caption", "asc")
        ));
        ma.setWindowFrame("ROWS BETWEEN 2 PRECEDING AND CURRENT ROW");
        calculatedFields.add(ma);
        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList(
                "product$caption", "salesDate$caption", "salesAmount", "ma3"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        printSql(sql, "移动平均（窗口帧）");

        assertTrue(sql.toUpperCase().contains("AVG("),
                "SQL 应包含 AVG(");
        assertTrue(sql.toUpperCase().contains("ROWS BETWEEN"),
                "SQL 应包含窗口帧定义");

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
        printResults(results);
    }

    // ==========================================
    // Phase 2B: QM 预定义计算字段测试
    // ==========================================

    @Test
    @Order(24)
    @DisplayName("QM 预定义普通计算字段 (profitRate)")
    void testQmPredefinedFormulaField() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        // 直接引用 QM 中预定义的 profitRate 字段
        queryRequest.setColumns(Arrays.asList(
                "orderId", "salesAmount", "profitAmount", "profitRate"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        printSql(sql, "QM 预定义 profitRate");

        // 验证 SQL 包含利润率计算
        assertTrue(sql.contains("profit") && sql.contains("sales"),
                "SQL 应包含 profitAmount / salesAmount 计算");

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
        printResults(results);
    }

    @Test
    @Order(20)
    @DisplayName("QM 预定义比率字段 - 分组查询中应聚合 measure 依赖")
    void testGroupedQmPredefinedRatioFormulaAggregatesMeasureDependencies() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "customer$customerType", "profitRate"
        ));
        queryRequest.setGroupBy(buildGroupBy("customer$customerType"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        printSql(sql, "QM grouped predefined profitRate");

        String upperSql = sql.toUpperCase();
        assertTrue(upperSql.contains("GROUP BY"), "SQL 应包含 GROUP BY: " + sql);
        assertTrue(upperSql.contains("SUM("), "公式中的 measure 依赖应展开为聚合表达式: " + sql);
        assertFalse(sql.contains("t1.profit_amount / t1.sales_amount"),
                "分组比率公式不应引用裸物理列: " + sql);
    }

    @Test
    @Order(20)
    @DisplayName("QM 预定义普通计算字段可仅在 slice 中引用")
    void testQmPredefinedFormulaFieldReferencedOnlyBySlice() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setSlice(List.of(
                new SliceRequestDef("salesAmount", ">", 0),
                new SliceRequestDef("profitRate", ">", 10)
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        printSql(sql, "QM predefined profitRate slice only");

        assertNotNull(queryRequest.getCalculatedFields(), "slice 引用的 QM 预定义字段应注入 calculatedFields");
        assertTrue(queryRequest.getCalculatedFields().stream()
                        .anyMatch(field -> "profitRate".equals(field.getName())),
                "profitRate should be injected from QM predefined calculated fields");
        assertTrue(sql.contains("profit") && sql.contains("sales"),
                "SQL 应包含 profitRate 公式依赖: " + sql);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, queryEngine.getValues().toArray());
        assertFalse(results.isEmpty());
    }

    @Test
    @Order(21)
    @DisplayName("QM 预定义普通计算字段重复注入时不应误报名称冲突")
    void testDuplicateInjectedQmPredefinedFormulaDoesNotCollide() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        CalculatedFieldDef profitRate = queryModel.getPredefinedCalculatedFields().stream()
                .filter(field -> "profitRate".equals(field.getName()))
                .findFirst()
                .orElseThrow();

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(List.of("orderId", "salesAmount", "profitRate"));
        queryRequest.setSlice(List.of(new SliceRequestDef("profitRate", ">", 10)));
        queryRequest.setCalculatedFields(new ArrayList<>(List.of(profitRate, profitRate)));

        assertDoesNotThrow(() -> queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest));
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        assertTrue(sql.contains("profit") && sql.contains("sales"),
                "SQL 应包含 profitRate 公式依赖: " + sql);
    }

    @Test
    @Order(25)
    @DisplayName("QM 预定义标量字段可被外层 SUM 聚合")
    @SuppressWarnings("unchecked")
    void testQmPredefinedScalarFormulaOuterAggregation() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(List.of("sum(profitRate) as totalProfitRate"));
        queryRequest.setSlice(List.of(new SliceRequestDef("salesAmount", ">", 0)));

        PagingRequest<DbQueryRequestDef> form = new PagingRequest<>();
        form.setParam(queryRequest);
        form.setPageSize(1);

        PagingResultImpl result = queryFacade.queryModelData(form);

        assertNotNull(result);
        assertNotNull(result.getItems());
        assertEquals(1, result.getItems().size());

        Map<String, Object> actualRow = (Map<String, Object>) result.getItems().get(0);
        Object actual = valueIgnoreCase(actualRow, "totalProfitRate");
        Object expected = jdbcTemplate.queryForObject("""
                SELECT SUM(profit_amount / sales_amount * 100) AS totalProfitRate
                FROM fact_sales
                WHERE sales_amount > 0
                """, Object.class);

        assertDecimalClose(expected, actual);
    }

    @Test
    @Order(25)
    @DisplayName("QM 预定义标量字段可同时用于外层 SUM 聚合与 slice")
    @SuppressWarnings("unchecked")
    void testQmPredefinedScalarFormulaOuterAggregationWithSlice() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(List.of("sum(profitRate) as totalProfitRate"));
        queryRequest.setSlice(List.of(
                new SliceRequestDef("salesAmount", ">", 0),
                new SliceRequestDef("profitRate", ">", 10)
        ));

        PagingRequest<DbQueryRequestDef> form = new PagingRequest<>();
        form.setParam(queryRequest);
        form.setPageSize(1);

        PagingResultImpl result = queryFacade.queryModelData(form);

        assertNotNull(result);
        assertNotNull(result.getItems());
        assertEquals(1, result.getItems().size());

        Map<String, Object> actualRow = (Map<String, Object>) result.getItems().get(0);
        Object actual = valueIgnoreCase(actualRow, "totalProfitRate");
        Object expected = jdbcTemplate.queryForObject("""
                SELECT SUM(profit_amount / sales_amount * 100) AS totalProfitRate
                FROM fact_sales
                WHERE sales_amount > 0
                  AND profit_amount / sales_amount * 100 > 10
                """, Object.class);

        assertDecimalClose(expected, actual);
    }

    @Test
    @Order(26)
    @DisplayName("QM v2 普通 TM 支持同模型多别名 join")
    @SuppressWarnings("unchecked")
    void testQmV2SameTableModelMultipleAliases() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesSelfAliasJoinQueryModelTest");
        assertNotNull(queryModel.findJdbcColumnForCond("leftSales.orderLineNo", true));
        assertNotNull(queryModel.findJdbcColumnForCond("rightSales.orderLineNo", true));
        assertNotNull(queryModel.findJdbcColumnForCond("aggregateSalesByLine.salesAmount", true));

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesSelfAliasJoinQueryModelTest");
        queryRequest.setColumns(List.of(
                "orderId",
                "orderLineNo",
                "leftSales.orderLineNo",
                "rightSales.orderLineNo",
                "aggregateSalesByLine.orderLineNo",
                "leftSales.salesAmount",
                "rightSales.salesAmount",
                "aggregateSalesByLine.salesAmount"
        ));
        queryRequest.setSlice(List.of(
                new SliceRequestDef("leftSales.orderLineNo", "=", 1),
                new SliceRequestDef("rightSales.orderLineNo", ">", 1)
        ));

        OrderRequestDef order = new OrderRequestDef();
        order.setField("rightSales.orderLineNo");
        order.setDir("asc");
        queryRequest.setOrderBy(List.of(order));

        PagingRequest<DbQueryRequestDef> form = new PagingRequest<>();
        form.setParam(queryRequest);
        form.setPageSize(10);

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        String normalizedSql = sql.replaceAll("\\s+", " ");
        String lowerSql = normalizedSql.toLowerCase();
        String aggregateOnCondition = "leftsales.order_line_no = aggregatesalesbyline.orderlineno";
        int aggregateOnIndex = lowerSql.indexOf(aggregateOnCondition);
        int whereIndex = lowerSql.indexOf(" where ");
        assertTrue(aggregateOnIndex >= 0,
                "聚合 relation ON 应包含已 join RHS alias 字段: " + normalizedSql);
        assertTrue(whereIndex < 0 || aggregateOnIndex < whereIndex,
                "聚合 relation 条件必须渲染在 JOIN ON 内，不能提升到 WHERE: " + normalizedSql);

        PagingResultImpl result = queryFacade.queryModelData(form);
        assertNotNull(result);
        assertNotNull(result.getItems());
        assertFalse(result.getItems().isEmpty());

        Map<String, Object> row = (Map<String, Object>) result.getItems().get(0);
        assertEquals(1, ((Number) valueIgnoreCase(row, "leftSales.orderLineNo")).intValue());
        assertTrue(((Number) valueIgnoreCase(row, "rightSales.orderLineNo")).intValue() > 1);
        assertNotNull(valueIgnoreCase(row, "leftSales.salesAmount"));
        assertNotNull(valueIgnoreCase(row, "rightSales.salesAmount"));
        assertNotNull(valueIgnoreCase(row, "aggregateSalesByLine.salesAmount"));
    }

    @Test
    @Order(21)
    @DisplayName("QM 预定义窗口字段 (salesRank)")
    void testQmPredefinedWindowField() {
        if (!supportsWindowFunctions()) {
            log.info("当前数据库不支持窗口函数，跳过");
            return;
        }
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        // 直接引用 QM 中预定义的 salesRank 窗口字段
        queryRequest.setColumns(Arrays.asList(
                "product$categoryName", "product$caption", "salesAmount", "salesRank"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        printSql(sql, "QM 预定义 salesRank");

        assertTrue(sql.toUpperCase().contains("RANK()"),
                "SQL 应包含 RANK()");
        assertTrue(sql.toUpperCase().contains("OVER"),
                "SQL 应包含 OVER 子句");

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
        printResults(results);
    }

    @Test
    @Order(22)
    @DisplayName("QM 预定义移动平均字段 (ma7)")
    void testQmPredefinedMovingAverage() {
        if (!supportsWindowFunctions()) {
            log.info("当前数据库不支持窗口函数，跳过");
            return;
        }
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "product$caption", "salesDate$caption", "salesAmount", "ma7"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        printSql(sql, "QM 预定义 ma7");

        assertTrue(sql.toUpperCase().contains("AVG("),
                "SQL 应包含 AVG(");
        assertTrue(sql.toUpperCase().contains("ROWS BETWEEN 6 PRECEDING"),
                "SQL 应包含 7日窗口帧");

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
        printResults(results);
    }

    @Test
    @Order(23)
    @DisplayName("DSL 同名预定义字段被丢弃，使用 QM 预定义版本（安全策略）")
    void testDslOverrideQmPredefined() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // DSL 中定义同名的 profitRate，但安全策略会丢弃用户版本，使用 QM 预定义公式
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "profitRate", "利润率(override)", "profitAmount / salesAmount * 200"
        ));
        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList(
                "orderId", "salesAmount", "profitAmount", "profitRate"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        printSql(sql, "预定义字段不可覆盖");

        // 安全策略：预定义字段名 = 固定语义，用户自定义的 *200 被丢弃，使用 QM 预定义的 *100
        assertTrue(sql.contains("100"), "SQL 应使用 QM 预定义的 *100，用户自定义版本应被丢弃");
        assertFalse(sql.contains("200"), "用户自定义的 *200 不应出现在 SQL 中");

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
    }

    // ==========================================
    // 回归测试：新功能不破坏现有逻辑
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("回归：简单聚合查询不受影响")
    void testRegressionSimpleAggregation() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "totalSales", "总销售额", "SUM(salesAmount)"
        ));
        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList("product$categoryName", "totalSales"));
        queryRequest.setGroupBy(buildGroupBy("product$categoryName"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        printSql(sql, "回归：简单聚合");

        assertTrue(sql.toUpperCase().contains("SUM("));
        assertTrue(sql.toUpperCase().contains("GROUP BY"));

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
        printResults(results);
    }

    @Test
    @Order(31)
    @DisplayName("回归：普通计算字段不受影响")
    void testRegressionCalculatedField() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "netAmount", "净销售额", "salesAmount - discountAmount"
        ));
        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList("orderId", "salesAmount", "discountAmount", "netAmount"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        assertNotNull(sql);
        printSql(sql, "回归：普通计算字段");

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private List<GroupRequestDef> buildGroupBy(String... fields) {
        List<GroupRequestDef> groups = new ArrayList<>();
        for (String field : fields) {
            GroupRequestDef g = new GroupRequestDef();
            g.setField(field);
            groups.add(g);
        }
        return groups;
    }

    private static Object valueIgnoreCase(Map<String, Object> row, String field) {
        if (row.containsKey(field)) {
            return row.get(field);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(field)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void assertDecimalClose(Object expected, Object actual) {
        assertNotNull(expected, "expected value should not be null");
        assertNotNull(actual, "actual value should not be null");

        BigDecimal delta = BigDecimal.valueOf(((Number) actual).doubleValue())
                .subtract(BigDecimal.valueOf(((Number) expected).doubleValue()))
                .abs();
        assertTrue(delta.compareTo(BigDecimal.valueOf(0.000001)) <= 0,
                "expected=" + expected + ", actual=" + actual + ", delta=" + delta);
    }
}
