package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.def.query.request.*;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.support.CalculatedDbColumn;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

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
    @Order(12)
    @DisplayName("LAG 窗口函数 — 环比计算")
    void testLagWindow() {
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
    @Order(20)
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
    @Order(21)
    @DisplayName("QM 预定义窗口字段 (salesRank)")
    void testQmPredefinedWindowField() {
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
    @DisplayName("DSL 覆盖 QM 预定义字段")
    void testDslOverrideQmPredefined() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // DSL 中定义同名的 profitRate，覆盖 QM 预定义
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
        printSql(sql, "DSL 覆盖 QM 预定义");

        // 验证是 *200 而非 QM 预定义的 *100
        assertTrue(sql.contains("200"), "SQL 应使用 DSL 覆盖的 *200 而非 QM 预定义的 *100");

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
}
