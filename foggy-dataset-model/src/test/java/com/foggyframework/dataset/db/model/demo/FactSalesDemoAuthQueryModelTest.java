package com.foggyframework.dataset.db.model.demo;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 销售查询模型（权限演示）单元测试
 *
 * <p>测试 FactSalesDemoAuthQueryModel 的权限控制功能：
 * <ul>
 *   <li>Spring Bean 注入（@demoSessionTokenService）</li>
 *   <li>queryBuilder 使用 context 参数</li>
 *   <li>字段引用 API（query.and）</li>
 *   <li>角色分级权限控制</li>
 * </ul>
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("销售查询模型（权限演示）单元测试")
class FactSalesDemoAuthQueryModelTest extends DemoTestSupport {

    @Resource
    private SystemBundlesContext systemBundlesContext;

    // ==========================================
    // SQL 生成测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("权限过滤 - SQL 生成验证")
    void testAccessControlSqlGeneration() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesDemoAuthQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesDemoAuthQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "orderStatus",
                "store$caption",
                "store$storeType",
                "salesAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "权限过滤 SQL");
        log.info("参数值: {}", queryEngine.getValues());
    }

    @Test
    @Order(2)
    @DisplayName("权限过滤 - 字段引用 API 验证")
    void testFieldReferenceApiInAccess() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesDemoAuthQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesDemoAuthQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderStatus", "salesAmount"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        List<Object> values = queryEngine.getValues();

        assertNotNull(sql);
        assertNotNull(values);

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
        if (!isLightweightMode()) {
            log.info("跳过数据比对测试（非 SQLite 环境）");
            return;
        }

        Long totalCount = getTableCount("fact_sales");
        log.info("fact_sales 总记录数: {}", totalCount);

        JdbcQueryModel queryModel = getQueryModel("FactSalesDemoAuthQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesDemoAuthQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderStatus", "store$storeType", "salesAmount"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        List<Object> values = queryEngine.getValues();

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, values.toArray());

        log.info("QM 查询返回记录数: {}", results.size());

        assertNotNull(results);
        assertTrue(results.size() <= totalCount.intValue(),
                "QM 查询结果数量应该小于等于总记录数");

        printResults(results);
    }

    @Test
    @Order(11)
    @DisplayName("权限过滤 - 无权限模型对比")
    void testCompareWithoutAccess() {
        if (!isLightweightMode()) {
            log.info("跳过数据比对测试（非 SQLite 环境）");
            return;
        }

        JdbcQueryModel noAccessModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine noAccessEngine = new JdbcModelQueryEngine(noAccessModel, sqlFormulaService);

        DbQueryRequestDef noAccessRequest = new DbQueryRequestDef();
        noAccessRequest.setQueryModel("FactSalesQueryModel");
        noAccessRequest.setColumns(Arrays.asList("orderId", "orderStatus", "store$caption", "salesAmount"));

        noAccessEngine.analysisQueryRequest(systemBundlesContext, noAccessRequest);

        String noAccessSql = noAccessEngine.getSql();
        List<Object> noAccessValues = noAccessEngine.getValues();

        List<Map<String, Object>> noAccessResults = jdbcTemplate.queryForList(noAccessSql, noAccessValues.toArray());

        JdbcQueryModel withAccessModel = getQueryModel("FactSalesDemoAuthQueryModel");
        JdbcModelQueryEngine withAccessEngine = new JdbcModelQueryEngine(withAccessModel, sqlFormulaService);

        DbQueryRequestDef withAccessRequest = new DbQueryRequestDef();
        withAccessRequest.setQueryModel("FactSalesDemoAuthQueryModel");
        withAccessRequest.setColumns(Arrays.asList("orderId", "orderStatus", "store$caption", "salesAmount"));

        withAccessEngine.analysisQueryRequest(systemBundlesContext, withAccessRequest);

        String withAccessSql = withAccessEngine.getSql();
        List<Object> withAccessValues = withAccessEngine.getValues();

        List<Map<String, Object>> withAccessResults = jdbcTemplate.queryForList(withAccessSql, withAccessValues.toArray());

        log.info("无权限控制查询结果数: {}", noAccessResults.size());
        log.info("有权限控制查询结果数: {}", withAccessResults.size());

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
        JdbcQueryModel queryModel = getQueryModel("FactSalesDemoAuthQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesDemoAuthQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderStatus", "salesAmount", "product$caption"));

        com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef slice =
                new com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef();
        slice.setField("salesAmount");
        slice.setOp(">=");
        slice.setValue(100);
        queryRequest.setSlice(java.util.Collections.singletonList(slice));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        List<Object> values = queryEngine.getValues();

        assertNotNull(sql);
        assertTrue(sql.toLowerCase().contains("where"), "SQL应包含WHERE子句");

        boolean hasUserFilter = values.stream()
                .anyMatch(v -> v instanceof Number && ((Number) v).doubleValue() == 100.0);
        assertTrue(hasUserFilter, "参数中应包含用户过滤值 100");

        printSql(sql, "组合用户过滤条件 SQL");
        log.info("参数值: {}", values);
    }

    @Test
    @Order(21)
    @DisplayName("权限过滤 - 验证表别名正确")
    void testAliasResolution() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesDemoAuthQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesDemoAuthQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "store$caption",
                "store$storeType",
                "product$caption"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();

        assertTrue(sql.contains("t0") || sql.contains("fact_sales"),
                "SQL应包含主表");
        assertTrue(sql.contains("dim_store") || sql.toLowerCase().contains("join"),
                "SQL应包含维度表JOIN");

        printSql(sql, "表别名验证 SQL");
    }
}
