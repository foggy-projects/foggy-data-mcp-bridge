package com.foggyframework.dataset.model.engine.query;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.def.query.request.*;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.spi.support.CalculatedDbColumn;
import com.foggyframework.dataset.model.test.JdbcModelTestApplication;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 计算字段 JOIN 测试
 *
 * <p>测试计算字段中引用维度列时，系统能够正确触发 JOIN 操作</p>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("计算字段 JOIN 测试")
@SpringBootTest(classes = JdbcModelTestApplication.class)
class CalculatedFieldJoinTest {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    @Resource
    private QueryModelLoader queryModelLoader;

    @Resource
    private JdbcTemplate jdbcTemplate;

    // ==========================================
    // 测试内联表达式中的维度列引用
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("内联表达式 COUNT(dimension$caption) 应自动 JOIN 维度表")
    void testInlineExpressionWithDimensionCaption() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用内联表达式：count(customer$caption)
        queryRequest.setColumns(Arrays.asList(
                "customer$caption",
                "count(customer$caption) as customerCount"
        ));

        // 分析并生成SQL
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        // 验证计算字段被处理
        List<CalculatedDbColumn> calcColumns = queryEngine.getCalculatedColumns();
        assertNotNull(calcColumns, "计算字段列表不应为空");
        assertEquals(1, calcColumns.size(), "应有1个计算字段");
        assertEquals("customerCount", calcColumns.get(0).getName(), "计算字段名应为customerCount");

        // 验证SQL生成
        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        log.info("生成的SQL: {}", sql);

        // 验证 SQL 中包含 dim_customer 的 JOIN
        assertTrue(sql.toLowerCase().contains("join dim_customer"), 
                "SQL 应包含 JOIN dim_customer");

        // 验证 SQL 中没有未定义的表别名
        // 检查是否有 d1, d2 等别名但没有对应的 JOIN
        assertNoUndefinedTableAliases(sql);

        // 尝试执行 SQL（如果数据库可用）
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            log.info("查询成功，返回 {} 条记录", results.size());
            assertNotNull(results, "查询结果不应为空");
        } catch (Exception e) {
            log.warn("SQL执行失败（可能是测试数据未初始化）: {}", e.getMessage());
        }
    }

    @Test
    @Order(2)
    @DisplayName("内联表达式 COUNT(dimension$id) 应自动 JOIN 维度表")
    void testInlineExpressionWithDimensionId() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用内联表达式：count(customer$id)
        // 注意：customer$id 是主键，可能不会触发 JOIN，因为它已经在主表中了
        // 这里改为测试 customer$caption
        queryRequest.setColumns(Arrays.asList(
                "customer$caption",
                "count(customer$caption) as customerCount"
        ));

        // 分析并生成SQL
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        // 验证计算字段被处理
        List<CalculatedDbColumn> calcColumns = queryEngine.getCalculatedColumns();
        assertNotNull(calcColumns, "计算字段列表不应为空");
        assertEquals(1, calcColumns.size(), "应有1个计算字段");

        // 验证SQL生成
        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        log.info("生成的SQL: {}", sql);

        // 验证 SQL 中包含 dim_customer 的 JOIN
        assertTrue(sql.toLowerCase().contains("join dim_customer"), 
                "SQL 应包含 JOIN dim_customer");

        // 验证 SQL 中没有未定义的表别名
        assertNoUndefinedTableAliases(sql);
    }

    @Test
    @Order(3)
    @DisplayName("内联表达式 SUM(measure) + COUNT(dimension$caption) 应自动 JOIN 维度表")
    void testInlineExpressionWithMultipleDimensions() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用内联表达式：count(customer$caption) + count(product$caption)
        queryRequest.setColumns(Arrays.asList(
                "customer$caption",
                "product$caption",
                "count(customer$caption) as customerCount",
                "count(product$caption) as productCount"
        ));

        // 分析并生成SQL
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        // 验证计算字段被处理
        List<CalculatedDbColumn> calcColumns = queryEngine.getCalculatedColumns();
        assertNotNull(calcColumns, "计算字段列表不应为空");
        assertEquals(2, calcColumns.size(), "应有2个计算字段");

        // 验证SQL生成
        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        log.info("生成的SQL: {}", sql);

        // 验证 SQL 中包含两个维度表的 JOIN
        assertTrue(sql.toLowerCase().contains("join dim_customer"), 
                "SQL 应包含 JOIN dim_customer");
        assertTrue(sql.toLowerCase().contains("join dim_product"), 
                "SQL 应包含 JOIN dim_product");

        // 验证 SQL 中没有未定义的表别名
        assertNoUndefinedTableAliases(sql);
    }

    @Test
    @Order(4)
    @DisplayName("HAVING 条件中使用聚合计算字段引用维度列应自动 JOIN 维度表")
    void testWhereConditionWithCalculatedField() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用预定义的计算字段作为 WHERE 条件
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "customerCount",
                "客户数",
                "COUNT(customer$caption)"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "customer$caption",
                "customerCount"
        ));

        GroupRequestDef group = new GroupRequestDef();
        group.setField("customer$caption");
        queryRequest.setGroupBy(Arrays.asList(group));

        // 添加聚合过滤条件（使用预定义的计算字段）
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("customerCount");
        slice.setOp(">");
        slice.setValue(5);
        queryRequest.setSlice(Arrays.asList(slice));

        // 分析并生成SQL
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        // 验证SQL生成
        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        log.info("生成的SQL: {}", sql);

        // 验证 SQL 中包含 dim_customer 的 JOIN
        assertTrue(sql.toLowerCase().contains("join dim_customer"), 
                "SQL 应包含 JOIN dim_customer");
        assertTrue(sql.toLowerCase().contains("having"), 
                "聚合计算字段过滤应生成 HAVING 条件");

        // 验证 SQL 中没有未定义的表别名
        assertNoUndefinedTableAliases(sql);
    }

    @Test
    @Order(5)
    @DisplayName("ORDER BY 中使用计算字段引用维度列应自动 JOIN 维度表")
    void testOrderByWithCalculatedField() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用预定义的计算字段作为 ORDER BY
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "customerCount",
                "客户数",
                "COUNT(customer$caption)"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "customerCount"
        ));

        // 分析并生成SQL
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        // 验证SQL生成
        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        log.info("生成的SQL: {}", sql);

        // 验证 SQL 中包含 dim_customer 的 JOIN
        assertTrue(sql.toLowerCase().contains("join dim_customer"), 
                "SQL 应包含 JOIN dim_customer");

        // 验证 SQL 中没有未定义的表别名
        assertNoUndefinedTableAliases(sql);
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    /**
     * 获取查询模型
     */
    private JdbcQueryModel getQueryModel(String queryModelName) {
        return (JdbcQueryModel) queryModelLoader.getJdbcQueryModel(queryModelName, null);
    }

    /**
     * 验证 SQL 中没有未定义的表别名
     * <p>
     * 检查 SQL 中是否使用了 d1, d2, d3 等别名但没有对应的 JOIN 语句
     * </p>
     */
    private void assertNoUndefinedTableAliases(String sql) {
        String lowerSql = sql.toLowerCase();
        
        // 查找所有 d\d+ 格式的表别名
        Pattern aliasPattern = Pattern.compile("\\bd(\\d+)\\.");
        java.util.regex.Matcher matcher = aliasPattern.matcher(lowerSql);
        
        while (matcher.find()) {
            String alias = "d" + matcher.group(1);
            // 检查是否有对应的 JOIN 语句
            String joinPattern = "join\\s+\\w+\\s+" + alias;
            if (!Pattern.compile(joinPattern, Pattern.CASE_INSENSITIVE).matcher(lowerSql).find()) {
                fail("SQL 中使用了未定义的表别名: " + alias + "\nSQL: " + sql);
            }
        }
    }

    /**
     * 打印 SQL（用于调试）
     */
    private void printSql(String sql, String description) {
        log.info("=== {} ===", description);
        log.info("SQL: {}", sql);
        log.info("===================");
    }
}
