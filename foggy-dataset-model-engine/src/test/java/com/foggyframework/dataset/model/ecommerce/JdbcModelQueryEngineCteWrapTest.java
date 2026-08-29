package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.dialect.SqliteDialect;
import com.foggyframework.dataset.model.def.query.request.*;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.engine.stage.QueryStagePlan;
import com.foggyframework.dataset.model.engine.stage.result.ResultStagePlan;
import com.foggyframework.dataset.model.engine.stage.result.ResultStagePreparation;
import com.foggyframework.dataset.model.engine.stage.result.ResultStageRenderer;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

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
class JdbcModelQueryEngineCteWrapTest extends CteWrapTestSupport {

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
    @DisplayName("postSlice over window alias creates explicit result filter stage")
    void testPostSliceOverWindowAliasCreatesResultStage() {
        if (!supportsWindowFunctions()) {
            return;
        }
        DbQueryRequestDef request = buildRankWindowRequest();
        request.setPostSlice(new ArrayList<>(List.of(new SliceRequestDef("salesRank", "=", 1))));
        request.setOrderBy(new ArrayList<>(List.of(orderDesc("salesRank"))));

        JdbcModelQueryEngine engine = analyze(request);
        String sql = engine.getSql();
        String normalizedSql = sql.replace('`', '"');

        assertTrue(normalizedSql.contains("WITH stage1 AS"), sql);
        assertTrue(normalizedSql.contains("__POST_RESULT_STAGE__ AS"), sql);
        assertTrue(normalizedSql.contains("FROM __POST_RESULT_STAGE__"), sql);
        assertTrue(normalizedSql.contains("WHERE \"salesRank\" = ?"), sql);
        assertTrue(normalizedSql.contains("ORDER BY \"salesRank\" DESC"), sql);
        assertEquals(1, engine.getValues().get(engine.getValues().size() - 1));
        assertEquals(2, engine.getCteStages().size());
        assertEquals(List.of(1), engine.getCteOuterSelectParams());
    }

    @Test
    @Order(6)
    @DisplayName("postSlice without result stage is rejected")
    void testPostSliceWithoutResultStageRejected() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(new ArrayList<>(List.of("product$caption", "salesAmount")));
        request.setPostSlice(new ArrayList<>(List.of(new SliceRequestDef("salesAmount", ">", 100))));

        AnalysisFailure failure = analyzeFailureWithContext(request, null);
        Map<String, Object> plan = queryStagePlan(failure.context());

        assertTrue(failure.exception().getMessage().contains("POST_SLICE_REQUIRES_RESULT_STAGE"),
                failure.exception().getMessage());
        assertTrue(listValue(plan, "unsupported").contains("post-slice-result-stage-required"),
                plan.toString());
    }

    @Test
    @Order(7)
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

    @Test
    @Order(13)
    @DisplayName("Running SUM with ordinary slice and postSlice executes and matches hand-written SQL")
    void testRunningSumPostSliceExecutesAndMatchesHandWrittenSql() {
        assumeCommonTableExpressionsSupported();

        if (!supportsWindowFunctions()) {
            log.info("running SUM postSlice parity not executed on {}", getDialectKey());
            return;
        }

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(new ArrayList<>(List.of("orderId", "salesAmount", "runningSalesAmount")));
        request.setGroupBy(buildGroupBy("orderId"));
        request.setSlice(new ArrayList<>(List.of(new SliceRequestDef("orderStatus", "=", "COMPLETED"))));
        int threshold = runningSumPostSliceThreshold();

        CalculatedFieldDef runningSalesAmount = new CalculatedFieldDef();
        runningSalesAmount.setName("runningSalesAmount");
        runningSalesAmount.setCaption("累计销售额");
        runningSalesAmount.setExpression("SUM(salesAmount)");
        runningSalesAmount.setWindowOrderBy(new ArrayList<>(List.of(
                new WindowOrderDef("salesAmount", "desc"),
                new WindowOrderDef("orderId", "asc")
        )));
        runningSalesAmount.setWindowFrame("ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW");
        request.setCalculatedFields(new ArrayList<>(List.of(runningSalesAmount)));
        request.setPostSlice(new ArrayList<>(List.of(new SliceRequestDef("runningSalesAmount", "<=", threshold))));
        request.setOrderBy(new ArrayList<>(List.of(
                orderDesc("salesAmount"),
                orderAsc("orderId")
        )));

        JdbcModelQueryEngine engine = analyze(request);
        String sql = engine.getSql();
        String normalizedSql = sql.replace('`', '"');
        printSql(sql, "CTE Wrapping: Running SUM postSlice parity");

        assertTrue(normalizedSql.contains("__POST_RESULT_STAGE__ AS"), sql);
        assertTrue(normalizedSql.toUpperCase().contains("SUM("), sql);
        assertTrue(normalizedSql.toUpperCase().contains("ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"), sql);
        assertTrue(engine.getValues().contains("COMPLETED"), "ordinary slice value should be bound");
        assertTrue(engine.getValues().contains(threshold), "postSlice threshold should be bound");

        List<Map<String, Object>> actual = jdbcTemplate.queryForList(
                sql, engine.getValues().toArray(new Object[0]));
        List<Map<String, Object>> expected = executeQuery(handWrittenRunningSumSql(threshold));

        assertFalse(expected.isEmpty(), "hand-written running SUM baseline should not be empty");
        assertRowsEqualInOrder(expected, actual, sql);
    }

    // ==========================================
    // Backward Compatibility: Single-Pass
    // ==========================================

    @Test
    @Order(31)
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

    @Test
    @Order(100)
    @DisplayName("测试隐式依赖注入：Window CF 引用了未在 columns 选中的字段")
    void testHiddenDependencyInWindowFunction() throws Exception {
        if (!supportsWindowFunctions()) {
            return;
        }
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

    @Test
    @Order(101)
    @DisplayName("结果阶段运行时对象不暴露为 JdbcModelQueryEngine 公共 Bean 属性")
    void resultStageRuntimeObjectsShouldNotHavePublicAccessors() {
        assertThrows(NoSuchMethodException.class,
                () -> JdbcModelQueryEngine.class.getMethod("getResultStageRenderer"));
        assertThrows(NoSuchMethodException.class,
                () -> JdbcModelQueryEngine.class.getMethod(
                        "setResultStageRenderer", ResultStageRenderer.class));
        assertThrows(NoSuchMethodException.class,
                () -> JdbcModelQueryEngine.class.getMethod("getResultStagePreparation"));
        assertThrows(NoSuchMethodException.class,
                () -> JdbcModelQueryEngine.class.getMethod(
                        "setResultStagePreparation", ResultStagePreparation.class));
    }
}
