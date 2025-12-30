package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 权限控制（Access Control）单元测试
 *
 * <p>测试 QM 中 accesses 配置的 queryBuilder 功能：
 * <ul>
 *   <li>基于属性的权限过滤（字段引用 API）</li>
 *   <li>基于属性的权限过滤（原生 SQL）</li>
 *   <li>基于维度的权限过滤</li>
 *   <li>与真实数据比对验证</li>
 * </ul>
 *
 * @author foggy-framework
 * @since 8.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("权限控制（Access Control）单元测试")
class AccessControlTest extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    // ==========================================
    // SQL 生成测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("权限过滤 - SQL 生成验证")
    void testAccessControlSqlGeneration() {
        // 获取带权限控制的查询模型
        JdbcQueryModel queryModel = getQueryModel("FactSalesAccessQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        // 创建查询引擎
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 创建查询请求
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesAccessQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "orderStatus",
                "store$caption",
                "store$storeType",
                "salesAmount"
        ));

        // 分析并生成 SQL
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        // 验证 SQL 生成
        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        // 验证权限条件被注入
        // 1. order_status = 'COMPLETED' (字段引用 API)
        assertTrue(sql.contains("order_status") || sql.contains("COMPLETED"),
                "SQL应包含 orderStatus 的权限条件");

        // 2. order_id like 'ORD%' (原生 SQL)
        assertTrue(sql.toLowerCase().contains("like"),
                "SQL应包含 orderId 的 LIKE 条件");

        // 3. store_type = '直营店' (维度权限)
        assertTrue(sql.contains("store_type") || sql.contains("dim_store"),
                "SQL应包含 store 维度的权限条件");

        printSql(sql, "权限过滤 SQL");
        log.info("参数值: {}", queryEngine.getValues());
    }

    @Test
    @Order(2)
    @DisplayName("权限过滤 - 字段引用 API 验证")
    void testFieldReferenceApiInAccess() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesAccessQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesAccessQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderStatus", "salesAmount"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        List<Object> values = queryEngine.getValues();

        // 字段引用 API 应该生成参数化查询
        assertTrue(values.contains("COMPLETED"),
                "参数中应包含 COMPLETED 值");

        printSql(sql, "字段引用 API SQL");
        log.info("参数值: {}", values);
    }

    // ==========================================
    // 数据比对测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("权限过滤 - 与真实数据比对")
    void testAccessControlDataComparison() {
        // 跳过非 SQLite 环境的数据比对测试
        if (!isLightweightMode()) {
            log.info("跳过数据比对测试（非 SQLite 环境）");
            return;
        }

        // 1. 先统计无权限过滤时的总记录数
        Long totalCount = getTableCount("fact_sales");
        log.info("fact_sales 总记录数: {}", totalCount);

        // 2. 统计满足权限条件的记录数
        String countSql = """
                SELECT COUNT(*) FROM fact_sales fs
                JOIN dim_store ds ON fs.store_key = ds.store_key
                WHERE fs.order_status = 'COMPLETED'
                  AND fs.order_id LIKE 'ORD%'
                  AND ds.store_type = '直营店'
                """;
        Long filteredCount = executeQueryForObject(countSql, Long.class);
        log.info("满足权限条件的记录数: {}", filteredCount);

        // 3. 通过 QM 查询，验证返回数量
        JdbcQueryModel queryModel = getQueryModel("FactSalesAccessQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesAccessQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderStatus", "store$storeType", "salesAmount"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        List<Object> values = queryEngine.getValues();

        // 执行生成的 SQL
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, values.toArray());

        log.info("QM 查询返回记录数: {}", results.size());

        // 验证：QM 查询结果应该与手写 SQL 一致
        assertEquals(filteredCount.intValue(), results.size(),
                "QM 查询结果数量应与手写 SQL 一致");

        // 验证每条记录都满足权限条件
        for (Map<String, Object> row : results) {
            assertEquals("COMPLETED", row.get("orderStatus"),
                    "所有记录的 orderStatus 应为 COMPLETED");
            assertTrue(String.valueOf(row.get("orderId")).startsWith("ORD"),
                    "所有记录的 orderId 应以 ORD 开头");
            assertEquals("直营店", row.get("store$storeType"),
                    "所有记录的门店类型应为直营店");
        }

        printResults(results);
    }

    @Test
    @Order(11)
    @DisplayName("权限过滤 - 无权限模型对比")
    void testCompareWithoutAccess() {
        // 跳过非 SQLite 环境的数据比对测试
        if (!isLightweightMode()) {
            log.info("跳过数据比对测试（非 SQLite 环境）");
            return;
        }

        // 使用无权限控制的 FactSalesQueryModel
        JdbcQueryModel noAccessModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine noAccessEngine = new JdbcModelQueryEngine(noAccessModel, sqlFormulaService);

        DbQueryRequestDef noAccessRequest = new DbQueryRequestDef();
        noAccessRequest.setQueryModel("FactSalesQueryModel");
        noAccessRequest.setColumns(Arrays.asList("orderId", "orderStatus", "store$caption", "salesAmount"));

        noAccessEngine.analysisQueryRequest(systemBundlesContext, noAccessRequest);

        String noAccessSql = noAccessEngine.getSql();
        List<Object> noAccessValues = noAccessEngine.getValues();

        List<Map<String, Object>> noAccessResults = jdbcTemplate.queryForList(noAccessSql, noAccessValues.toArray());

        // 使用有权限控制的 FactSalesAccessQueryModel
        JdbcQueryModel withAccessModel = getQueryModel("FactSalesAccessQueryModel");
        JdbcModelQueryEngine withAccessEngine = new JdbcModelQueryEngine(withAccessModel, sqlFormulaService);

        DbQueryRequestDef withAccessRequest = new DbQueryRequestDef();
        withAccessRequest.setQueryModel("FactSalesAccessQueryModel");
        withAccessRequest.setColumns(Arrays.asList("orderId", "orderStatus", "store$caption", "salesAmount"));

        withAccessEngine.analysisQueryRequest(systemBundlesContext, withAccessRequest);

        String withAccessSql = withAccessEngine.getSql();
        List<Object> withAccessValues = withAccessEngine.getValues();

        List<Map<String, Object>> withAccessResults = jdbcTemplate.queryForList(withAccessSql, withAccessValues.toArray());

        log.info("无权限控制查询结果数: {}", noAccessResults.size());
        log.info("有权限控制查询结果数: {}", withAccessResults.size());

        // 有权限控制的结果应该 <= 无权限控制的结果
        assertTrue(withAccessResults.size() <= noAccessResults.size(),
                "有权限控制的查询结果应该小于等于无权限控制的结果");

        printSql(noAccessSql, "无权限控制 SQL");
        printSql(withAccessSql, "有权限控制 SQL");
    }

    // ==========================================
    // 边界情况测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("权限过滤 - 组合用户过滤条件")
    void testAccessWithUserSlice() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesAccessQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesAccessQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderStatus", "salesAmount", "product$caption"));

        // 用户添加额外的过滤条件
        com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef slice =
                new com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef();
        slice.setField("salesAmount");
        slice.setOp(">=");
        slice.setValue(100);
        queryRequest.setSlice(java.util.Collections.singletonList(slice));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        List<Object> values = queryEngine.getValues();

        // 用户条件和权限条件应该都存在
        assertNotNull(sql);
        assertTrue(sql.toLowerCase().contains("where"), "SQL应包含WHERE子句");

        // 参数应该包含用户过滤值和权限过滤值
        boolean hasUserFilter = values.stream()
                .anyMatch(v -> v instanceof Number && ((Number) v).doubleValue() == 100.0);
        assertTrue(hasUserFilter,
                "参数中应包含用户过滤值 100");
        assertTrue(values.contains("COMPLETED"),
                "参数中应包含权限过滤值 COMPLETED");

        printSql(sql, "组合用户过滤条件 SQL");
        log.info("参数值: {}", values);
    }

    @Test
    @Order(21)
    @DisplayName("权限过滤 - 验证表别名正确")
    void testAliasResolution() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesAccessQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesAccessQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "store$caption",
                "store$storeType",
                "product$caption"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();

        // 验证生成的 SQL 包含正确的表别名
        // 主表应该有别名（如 t0）
        assertTrue(sql.contains("t0") || sql.contains("fact_sales"),
                "SQL应包含主表");

        // 维度表应该有别名（如 d1, d2）
        assertTrue(sql.contains("dim_store") || sql.toLowerCase().contains("join"),
                "SQL应包含维度表JOIN");

        printSql(sql, "表别名验证 SQL");
    }
}
