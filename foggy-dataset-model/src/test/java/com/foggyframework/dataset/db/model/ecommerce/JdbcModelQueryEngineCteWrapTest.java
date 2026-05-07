package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.def.query.request.*;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CTE Wrapping 集成测试
 * <p>
 * Validates the two-stage SQL generation architecture for Window Calculated Fields.
 * When a CF uses explicit partitionBy/windowOrderBy, the engine should:
 * <ul>
 *   <li>Stage 1 (CTE): Generate base SQL without window CFs</li>
 *   <li>Stage 2 (outer): Wrap Stage 1 in a CTE, add window CFs with alias-based references</li>
 *   <li>Elevate ORDER BY to Stage 2</li>
 *   <li>Preserve backward compatibility for non-windowed queries (single-pass)</li>
 * </ul>
 * </p>
 *
 * @author Foggy
 * @since 9.2.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("CTE Wrapping 两阶段 SQL 生成测试")
class JdbcModelQueryEngineCteWrapTest extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    // ==========================================
    // CTE Structure Validation
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("Window CF with partitionBy/windowOrderBy generates CTE-wrapped SQL")
    void testCteStructureForRankWindow() {
        if (!supportsWindowFunctions()) {
            log.info("当前数据库不支持窗口函数，跳过");
            return;
        }
        JdbcModelQueryEngine engine = buildRankWindowEngine();
        String sql = engine.getSql();
        assertNotNull(sql, "SQL 生成失败");
        printSql(sql, "CTE Wrapping: RANK()");

        // Verify CTE structure
        assertTrue(sql.toUpperCase().contains("WITH STAGE1 AS"),
                "Should generate CTE wrapper: " + sql);
        assertTrue(sql.toUpperCase().contains("FROM STAGE1"),
                "Outer query should reference stage1 CTE: " + sql);
        assertTrue(sql.toUpperCase().contains("RANK()"),
                "Should contain RANK() window function: " + sql);
        assertTrue(sql.toUpperCase().contains("OVER"),
                "Should contain OVER clause: " + sql);
    }

    @Test
    @Order(2)
    @DisplayName("CTE inner SQL does NOT contain window CF column")
    void testCteInnerSqlExcludesWindowColumn() {
        if (!supportsWindowFunctions()) {
            return;
        }
        JdbcModelQueryEngine engine = buildRankWindowEngine();
        String sql = engine.getSql();

        // The CTE inner SQL (between "AS (" and ")") should NOT contain RANK()
        int cteStart = sql.toUpperCase().indexOf("AS (");
        int cteEnd = sql.toUpperCase().indexOf(")\nSELECT");
        if (cteEnd < 0) {
            cteEnd = sql.toUpperCase().indexOf(")\r\nSELECT");
        }
        assertTrue(cteStart > 0 && cteEnd > cteStart, "CTE block should be present");

        String cteSql = sql.substring(cteStart + 4, cteEnd);
        assertFalse(cteSql.toUpperCase().contains("RANK()"),
                "CTE inner SQL should NOT contain RANK(): " + cteSql);
        assertFalse(cteSql.toUpperCase().contains("OVER"),
                "CTE inner SQL should NOT contain OVER clause: " + cteSql);
    }

    @Test
    @Order(3)
    @DisplayName("Window CF base expression uses alias-based column references (not physical)")
    void testCteWindowExprUsesAliases() {
        if (!supportsWindowFunctions()) {
            return;
        }
        JdbcModelQueryEngine engine = buildRankWindowEngine();
        String sql = engine.getSql();

        // The outer SELECT's window expression should reference aliases, not table.column
        // e.g., ORDER BY "salesAmount" not ORDER BY t1.sales_amount
        String outerSql = extractOuterSelect(sql);
        assertFalse(outerSql.contains("t1.sales_amount"),
                "Window OVER clause should NOT reference physical column t1.sales_amount: " + outerSql);
        assertTrue(outerSql.contains("\"salesAmount\""),
                "Window OVER clause should reference alias \"salesAmount\": " + outerSql);
    }

    @Test
    @Order(4)
    @DisplayName("ORDER BY is elevated to Stage 2 outer query")
    void testOrderByElevatedToStage2() {
        if (!supportsWindowFunctions()) {
            return;
        }
        DbQueryRequestDef request = buildRankWindowRequest();
        request.setOrderBy(new ArrayList<>(List.of(
                orderAsc("product$categoryName"),
                orderDesc("salesAmount")
        )));

        JdbcModelQueryEngine engine = analyze(request);
        String sql = engine.getSql();

        // ORDER BY should appear AFTER the CTE wrapper, not inside it
        int cteEnd = sql.toUpperCase().indexOf("FROM STAGE1");
        // Use lastIndexOf because ORDER BY also appears inside OVER() clause
        int orderByIdx = sql.toUpperCase().lastIndexOf("ORDER BY");
        assertTrue(cteEnd > 0 && orderByIdx > cteEnd,
                "ORDER BY should be elevated to outer query (after FROM stage1): " + sql);
    }
    @Test
    @Order(5)
    @DisplayName("Regex edge case: Substring collision prevention in CTE rewriting")
    void testRegexSubstringCollision() {
        String sql = "SUM(t1.sales) / MAX(t1.sales_tax) + t1.sales";
        String physicalSql = "t1.sales";
        String aliasSql = "\"sales\"";

        String regex = "(?<![\\\\p{L}0-9_$])" + java.util.regex.Pattern.quote(physicalSql) + "(?![\\\\p{L}0-9_$])";
        String replaced = sql.replaceAll(regex, java.util.regex.Matcher.quoteReplacement(aliasSql));

        assertEquals("SUM(\"sales\") / MAX(t1.sales_tax) + \"sales\"", replaced, 
                "Should only replace exact matches on word boundaries");

        // Edge case: end of string
        assertEquals("\"sales\"", "t1.sales".replaceAll(regex, aliasSql));

        // Edge case: start of string
        assertEquals("\"sales\" / 2", "t1.sales / 2".replaceAll(regex, aliasSql));
        
        // Edge case: unicode letters
        String unicodeSql = "t1.销售额 + t1.销售额_tax";
        String unicodePhysical = "t1.销售额";
        String unicodeAlias = "\"sales\"";
        String unicodeRegex = "(?<![\\\\p{L}0-9_$])" + java.util.regex.Pattern.quote(unicodePhysical) + "(?![\\\\p{L}0-9_$])";
        assertEquals("\"sales\" + t1.销售额_tax", unicodeSql.replaceAll(unicodeRegex, unicodeAlias));
    }

    // ==========================================
    // Execution Correctness
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("CTE-wrapped RANK() query executes correctly and returns results")
    void testCteRankExecutes() {
        if (!supportsWindowFunctions()) {
            return;
        }
        JdbcModelQueryEngine engine = buildRankWindowEngine();
        String sql = engine.getSql();
        List<Map<String, Object>> results = executeQuery(sql);
        assertNotNull(results);
        assertFalse(results.isEmpty(), "CTE-wrapped RANK query should return results");
        printResults(results);

        // Verify each row has a salesRank
        for (Map<String, Object> row : results) {
            assertNotNull(row.get("salesRank"), "Each row should have a salesRank value");
        }
    }

    @Test
    @Order(11)
    @DisplayName("CTE-wrapped LAG() query executes correctly")
    void testCteLagExecutes() {
        if (!supportsWindowFunctions()) {
            return;
        }
        JdbcModelQueryEngine engine = buildLagWindowEngine();
        String sql = engine.getSql();
        printSql(sql, "CTE Wrapping: LAG()");

        assertTrue(sql.toUpperCase().contains("WITH STAGE1 AS"),
                "LAG should trigger CTE wrapping: " + sql);
        assertTrue(sql.toUpperCase().contains("LAG("),
                "Should contain LAG function: " + sql);

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty(), "CTE-wrapped LAG query should return results");
    }

    @Test
    @Order(12)
    @DisplayName("CTE-wrapped moving average with window frame executes correctly")
    void testCteMovingAverageExecutes() {
        if (!supportsWindowFunctions()) {
            return;
        }
        JdbcModelQueryEngine engine = buildMovingAverageEngine();
        String sql = engine.getSql();
        printSql(sql, "CTE Wrapping: Moving Average");

        assertTrue(sql.toUpperCase().contains("WITH STAGE1 AS"),
                "Moving average should trigger CTE wrapping: " + sql);
        assertTrue(sql.toUpperCase().contains("ROWS BETWEEN"),
                "Should contain window frame: " + sql);

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
    }

    // ==========================================
    // Backward Compatibility: Single-Pass
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("Non-windowed query remains single-pass (no CTE)")
    void testSinglePassForNonWindowQuery() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(Arrays.asList("product$categoryName", "salesAmount"));
        request.setGroupBy(buildGroupBy("product$categoryName"));

        engine.analysisQueryRequest(systemBundlesContext, request);
        String sql = engine.getSql();

        assertFalse(sql.toUpperCase().contains("WITH STAGE1 AS"),
                "Non-windowed query should NOT use CTE wrapping: " + sql);
    }

    @Test
    @Order(21)
    @DisplayName("CALCULATE-generated window (SUM(SUM(x)) OVER()) remains single-pass")
    void testSinglePassForCalculateWindow() {
        if (!supportsWindowFunctions()) {
            return;
        }
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(new ArrayList<>(List.of(
                "customer$customerType", "salesAmount", "totalShare"
        )));
        request.setCalculatedFields(new ArrayList<>(List.of(
                new CalculatedFieldDef(
                        "totalShare", "总占比",
                        "SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(customer$customerType)), 0)"
                )
        )));
        request.setGroupBy(buildGroupBy("customer$customerType"));
        request.setOrderBy(new ArrayList<>(List.of(orderAsc("customer$customerType"))));

        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        engine.analysisQueryRequest(systemBundlesContext, request);

        String sql = engine.getSql();
        assertFalse(sql.toUpperCase().contains("WITH STAGE1 AS"),
                "CALCULATE-generated window should NOT use CTE wrapping: " + sql);
        assertTrue(sql.toUpperCase().contains("OVER ()"),
                "CALCULATE should use inline window: " + sql);

        // Verify execution
        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
    }

    // ==========================================
    // QM Predefined Window CFs
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("QM predefined window CF uses CTE wrapping and executes correctly")
    void testQmPredefinedWindowWithCte() {
        if (!supportsWindowFunctions()) {
            return;
        }
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(Arrays.asList(
                "product$categoryName", "product$caption", "salesAmount", "salesRank"
        ));

        engine.analysisQueryRequest(systemBundlesContext, request);
        String sql = engine.getSql();
        printSql(sql, "QM Predefined salesRank via CTE");

        assertTrue(sql.toUpperCase().contains("WITH STAGE1 AS"),
                "QM predefined window CF should use CTE wrapping: " + sql);
        assertTrue(sql.toUpperCase().contains("RANK()"),
                "Should contain RANK(): " + sql);

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
    }

    // ==========================================
    // Helpers
    // ==========================================

    private JdbcModelQueryEngine buildRankWindowEngine() {
        return analyze(buildRankWindowRequest());
    }

    private DbQueryRequestDef buildRankWindowRequest() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");

        CalculatedFieldDef rank = new CalculatedFieldDef();
        rank.setName("salesRank");
        rank.setCaption("销售排名");
        rank.setExpression("RANK()");
        rank.setPartitionBy(Arrays.asList("product$categoryName"));
        rank.setWindowOrderBy(Arrays.asList(
                new WindowOrderDef("salesAmount", "desc")
        ));
        request.setCalculatedFields(new ArrayList<>(List.of(rank)));
        request.setColumns(Arrays.asList(
                "product$categoryName", "product$caption", "salesAmount", "salesRank"
        ));
        return request;
    }

    private JdbcModelQueryEngine buildLagWindowEngine() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");

        CalculatedFieldDef lag = new CalculatedFieldDef();
        lag.setName("prevAmount");
        lag.setCaption("上期销售额");
        lag.setExpression("LAG(salesAmount, 1)");
        lag.setPartitionBy(Arrays.asList("product$caption"));
        lag.setWindowOrderBy(Arrays.asList(
                new WindowOrderDef("salesDate$caption", "asc")
        ));
        request.setCalculatedFields(new ArrayList<>(List.of(lag)));
        request.setColumns(Arrays.asList(
                "product$caption", "salesDate$caption", "salesAmount", "prevAmount"
        ));
        return analyze(request);
    }

    private JdbcModelQueryEngine buildMovingAverageEngine() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");

        CalculatedFieldDef ma = new CalculatedFieldDef();
        ma.setName("ma3");
        ma.setCaption("3行移动平均");
        ma.setExpression("AVG(salesAmount)");
        ma.setPartitionBy(Arrays.asList("product$caption"));
        ma.setWindowOrderBy(Arrays.asList(
                new WindowOrderDef("salesDate$caption", "asc")
        ));
        ma.setWindowFrame("ROWS BETWEEN 2 PRECEDING AND CURRENT ROW");
        request.setCalculatedFields(new ArrayList<>(List.of(ma)));
        request.setColumns(Arrays.asList(
                "product$caption", "salesDate$caption", "salesAmount", "ma3"
        ));
        return analyze(request);
    }

    private JdbcModelQueryEngine analyze(DbQueryRequestDef request) {
        JdbcQueryModel queryModel = getQueryModel(request.getQueryModel());
        assertNotNull(queryModel, "查询模型加载失败");
        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        engine.analysisQueryRequest(systemBundlesContext, request);
        return engine;
    }

    private List<GroupRequestDef> buildGroupBy(String... fields) {
        List<GroupRequestDef> groups = new ArrayList<>();
        for (String field : fields) {
            GroupRequestDef g = new GroupRequestDef();
            g.setField(field);
            groups.add(g);
        }
        return groups;
    }

    private OrderRequestDef orderAsc(String field) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir("ASC");
        return order;
    }

    private OrderRequestDef orderDesc(String field) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir("DESC");
        return order;
    }

    private String extractOuterSelect(String sql) {
        int fromStage1 = sql.toUpperCase().indexOf("FROM STAGE1");
        if (fromStage1 < 0) return sql;
        // Find the SELECT before FROM stage1
        int outerSelect = sql.toUpperCase().lastIndexOf("SELECT ", fromStage1);
        return outerSelect >= 0 ? sql.substring(outerSelect, fromStage1) : sql;
    }

    private JdbcModelQueryEngine analyze(JdbcModelQueryEngine engine) {
        return engine;
    }

    @Test
    @Order(100)
    @DisplayName("测试隐式依赖注入：Window CF 引用了未在 columns 选中的字段")
    void testHiddenDependencyInWindowFunction() throws Exception {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        // Request DOES NOT explicitly ask for product$categoryName, but explicitly asks for dimension product$caption and metric salesAmount
        queryRequest.setColumns(new ArrayList<>(Arrays.asList("product$caption", "salesAmount", "salesRank")));

        CalculatedFieldDef cf = new CalculatedFieldDef();
        cf.setName("salesRank");
        cf.setExpression("RANK()");
        // Hidden dependency: product$categoryName is NOT in columns
        cf.setPartitionBy(Arrays.asList("product$categoryName"));
        cf.setWindowOrderBy(Arrays.asList(new WindowOrderDef("salesAmount", "desc")));
        queryRequest.setCalculatedFields(new ArrayList<>(Arrays.asList(cf)));

        JdbcModelQueryEngine engine = analyze(queryRequest);
        String sql = engine.getSql();
        log.debug("执行SQL: {}", sql);

        assertTrue(sql.contains("PARTITION BY") && sql.contains("product$categoryName"),
                "Outer stage must use correct projected alias for hidden dependency. Actual SQL:\n" + sql);
        assertTrue(sql.contains("category_name") &&
                        (sql.contains("\"product$categoryName\"") || sql.contains("`product$categoryName`")),
                "Stage 1 must project the hidden dependency. Actual SQL:\n" + sql);
        assertFalse(sql.contains("stage1.\"product$categoryName\"") || sql.contains("stage1.`product$categoryName`"),
                "Hidden dependency must not be exposed as a final output column. Actual SQL:\n" + sql);
    }
}
