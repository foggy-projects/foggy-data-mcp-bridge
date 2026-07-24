package com.foggyframework.dataset.model.odoo;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.model.impl.dimension.DbModelParentChildDimensionImpl;
import com.foggyframework.dataset.model.spi.DbDimension;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.QueryObject;
import com.foggyframework.dataset.model.spi.TableModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自引用维度 SQL 别名冲突测试
 *
 * <p>验证当 fact table = dimension table（如 hr_employee.parent_id → hr_employee）时，
 * 引擎能否为两个逻辑角色生成不同的 SQL 别名，避免 SQL 冲突。
 *
 * <p>已知受影响模型：
 * <ul>
 *   <li>OdooHrEmployeeModel — parent 维度（hr_employee → hr_employee）</li>
 *   <li>OdooResCompanyModel — parent 维度（res_company → res_company）</li>
 *   <li>OdooResPartnerModel — parentPartner 维度（res_partner → res_partner）</li>
 * </ul>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("自引用维度 SQL 别名冲突测试")
class SelfReferencingDimensionAliasTest extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    // ==========================================
    // TM 层 — QueryObject 实例隔离验证
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("TM 层：自引用维度的 QueryObject 应与 fact 表 QueryObject 不同实例")
    void testTmLayer_QueryObjectInstanceIsolation() {
        TableModel model = tableModelLoaderManager.load("OdooHrEmployeeModel");
        assertNotNull(model);

        QueryObject factQO = model.getQueryObject();
        assertNotNull(factQO, "fact table QueryObject 不应为空");

        DbDimension parentDim = model.findJdbcDimensionByName("parent");
        assertNotNull(parentDim, "应存在 parent 自引用维度");

        QueryObject dimQO = parentDim.getQueryObject();
        assertNotNull(dimQO, "parent 维度 QueryObject 不应为空");

        // 核心断言：两个 QO 实例不同，getRoot() 也不同
        assertNotSame(factQO, dimQO,
            "fact QO 和 dimension QO 应为不同实例");
        assertNotSame(factQO.getRoot(), dimQO.getRoot(),
            "fact QO root 和 dimension QO root 应为不同对象（不同 loadQueryObject 调用）");
        assertFalse(factQO.isRootEqual(dimQO),
            "fact QO 和 dimension QO 的 isRootEqual 应返回 false");

        // 别名应不同
        assertNotEquals(factQO.getAlias(), dimQO.getAlias(),
            "fact QO alias '" + factQO.getAlias() + "' 和 dimension QO alias '" + dimQO.getAlias() + "' 应不同");

        log.info("TM 层 QO 实例隔离验证通过: fact={} (alias={}), dim={} (alias={})",
            System.identityHashCode(factQO), factQO.getAlias(),
            System.identityHashCode(dimQO), dimQO.getAlias());

        // 验证 hierarchyQueryObject 也是独立的
        DbModelParentChildDimensionImpl pcDim = parentDim.getDecorate(DbModelParentChildDimensionImpl.class);
        assertNotNull(pcDim, "parent 维度应为父子维度");
        if (pcDim.getHierarchyQueryObject() != null) {
            QueryObject hierarchyQO = pcDim.getHierarchyQueryObject();
            assertNotSame(factQO.getRoot(), hierarchyQO.getRoot(),
                "hierarchy QO root 应与 fact QO root 不同");
            assertNotEquals(factQO.getAlias(), hierarchyQO.getAlias(),
                "hierarchy QO alias 应与 fact QO alias 不同");
            log.info("  hierarchy QO: {} (alias={})",
                System.identityHashCode(hierarchyQO), hierarchyQO.getAlias());
        }
    }

    // ==========================================
    // QM 层 — SQL 生成别名验证（核心复现测试）
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("QM 层：SELECT parent$caption 应生成不同别名的 SQL（自引用维度核心 bug 复现）")
    void testQmLayer_ParentCaptionShouldUseDistinctAlias() {
        JdbcQueryModel queryModel = getQueryModel("OdooHrEmployeeQueryModel");
        assertNotNull(queryModel, "OdooHrEmployeeQueryModel 应存在");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooHrEmployeeQueryModel");
        // 关键：同时选择 fact 表的 name 和自引用维度的 parent$caption
        queryRequest.setColumns(Arrays.asList("name", "parent$caption"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        List<Object> values = queryEngine.getValues();

        log.info("========== 自引用维度 SQL ==========");
        log.info(sql);
        log.info("参数: {}", values);
        log.info("====================================");

        assertNotNull(sql, "生成的 SQL 不应为空");

        // 核心断言：SQL 中的 FROM 和 JOIN 应使用不同别名
        // 不应出现 "hr_employee xxx LEFT JOIN hr_employee xxx" 相同别名
        String sqlLower = sql.toLowerCase();

        // 应包含 LEFT JOIN hr_employee（自引用维度的 JOIN）
        assertTrue(sqlLower.contains("left join hr_employee"),
            "SQL 应包含对 hr_employee 的 LEFT JOIN（parent 维度）: " + sql);

        // 检查 FROM 和 JOIN 使用的别名不同
        // 提取 FROM hr_employee 后的别名和 LEFT JOIN hr_employee 后的别名
        int fromIdx = sqlLower.indexOf("from hr_employee");
        assertTrue(fromIdx >= 0, "SQL 应包含 FROM hr_employee: " + sql);

        int joinIdx = sqlLower.indexOf("left join hr_employee", fromIdx);
        assertTrue(joinIdx >= 0, "SQL 应包含 LEFT JOIN hr_employee: " + sql);

        // 提取别名
        String afterFrom = sql.substring(fromIdx + "from hr_employee".length()).trim();
        String fromAlias = afterFrom.split("[\\s\t]")[0].trim();

        String afterJoin = sql.substring(joinIdx + "left join hr_employee".length()).trim();
        String joinAlias = afterJoin.split("[\\s\t]")[0].trim();

        log.info("FROM alias: '{}', JOIN alias: '{}'", fromAlias, joinAlias);

        assertNotEquals(fromAlias, joinAlias,
            "FROM 和 LEFT JOIN hr_employee 应使用不同别名，但都是 '" + fromAlias + "'。SQL: " + sql);
    }

    @Test
    @Order(11)
    @DisplayName("QM 层：SELECT parent$caption 的 SQL 应可执行（无 SQL 错误）")
    void testQmLayer_ParentCaptionSqlShouldBeExecutable() {
        JdbcQueryModel queryModel = getQueryModel("OdooHrEmployeeQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooHrEmployeeQueryModel");
        queryRequest.setColumns(Arrays.asList("name", "parent$caption"));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        List<Object> values = queryEngine.getValues();

        // 尝试执行 SQL — 如果别名冲突会抛出 SQL 异常
        assertDoesNotThrow(() -> {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, values.toArray());
            log.info("parent$caption 查询执行成功，返回 {} 条记录", results.size());
            for (Map<String, Object> row : results) {
                log.info("  {} → manager: {}", row.get("name"), row.get("parent$caption"));
            }
        }, "SELECT parent$caption 的 SQL 应可执行，但可能因别名冲突失败。SQL: " + sql);
    }

    @Test
    @Order(12)
    @DisplayName("QM 层：parent$caption + selfAndDescendantsOf 组合查询")
    void testQmLayer_ParentCaptionWithHierarchySlice() {
        JdbcQueryModel queryModel = getQueryModel("OdooHrEmployeeQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooHrEmployeeQueryModel");
        // 同时使用 parent$caption（SELECT）和 parent$id 的层级过滤（SLICE）
        queryRequest.setColumns(Arrays.asList("name", "parent$caption"));

        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("parent$id");
        slice.setOp("selfAndDescendantsOf");
        slice.setValue(1);
        queryRequest.setSlice(Collections.singletonList(slice));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String sql = queryEngine.getSql();
        List<Object> values = queryEngine.getValues();

        log.info("========== 组合查询 SQL ==========");
        log.info(sql);
        log.info("参数: {}", values);
        log.info("==================================");

        // 执行 SQL 验证
        assertDoesNotThrow(() -> {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, values.toArray());
            log.info("组合查询执行成功，返回 {} 条记录", results.size());
            assertTrue(results.size() > 0, "Admin(1) 及其下属应有数据");
        }, "parent$caption + selfAndDescendantsOf 组合查询 SQL 应可执行。SQL: " + sql);
    }

    // ==========================================
    // name2Alias 映射验证
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("QM 层：name2Alias 不应丢失自引用维度的别名映射")
    void testQmLayer_Name2AliasShouldNotLoseMapping() {
        JdbcQueryModel queryModel = getQueryModel("OdooHrEmployeeQueryModel");
        assertNotNull(queryModel);

        // 获取 fact 表和 parent 维度的 QO
        TableModel tm = tableModelLoaderManager.load("OdooHrEmployeeModel");
        QueryObject factQO = tm.getQueryObject();
        DbDimension parentDim = tm.findJdbcDimensionByName("parent");
        QueryObject dimQO = parentDim.getQueryObject();

        // 通过 queryModel 解析别名
        String factAlias = queryModel.getAlias(factQO);
        String dimAlias = queryModel.getAlias(dimQO);

        log.info("QM 层别名映射: factQO → '{}', dimQO → '{}'", factAlias, dimAlias);

        assertNotNull(factAlias, "fact QO 应能解析到别名");
        assertNotNull(dimAlias, "dimension QO 应能解析到别名");
        assertNotEquals(factAlias, dimAlias,
            "fact QO 和 dimension QO 通过 queryModel.getAlias() 应解析到不同别名");
    }
}
