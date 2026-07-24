package com.foggyframework.dataset.model.odoo;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.impl.dimension.DbModelParentChildDimensionImpl;
import com.foggyframework.dataset.model.service.JdbcService;
import com.foggyframework.dataset.model.spi.DbDimension;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Odoo 闭包表层级查询测试
 *
 * <p>验证：
 * <ul>
 *   <li>闭包表配置加载（company, department, employee 维度）</li>
 *   <li>selfAndDescendantsOf（后代方向 — child_of 场景）</li>
 *   <li>selfAndAncestorsOf（祖先方向 — parent_of 场景，新增能力）</li>
 *   <li>ancestorsOf / descendantsOf 距离条件</li>
 * </ul>
 *
 * <p>测试数据层级：
 * <pre>
 * res_company:
 *   1 "My Company (Shanghai)" (root)
 *   ├── 2 "My Company (Beijing)"
 *   └── 3 "Overseas Branch"
 *
 * hr_employee:
 *   1 "Admin User" (root)
 *   ├── 2 "Zhang Wei"
 *   │   ├── 3 "Li Na"
 *   │   └── 4 "Wang Jun"
 *   ├── 5 "Chen Mei"
 *   └── 6 "Liu Fang"
 * </pre>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Odoo 层级查询测试")
class OdooHierarchyQueryTest extends EcommerceTestSupport {

    @Resource
    private JdbcService jdbcService;

    // ==========================================
    // 闭包表数据验证
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("验证闭包表数据已加载")
    void testClosureDataLoaded() {
        assertTrue(getTableCount("res_company_closure") > 0, "res_company_closure 表无数据");
        assertTrue(getTableCount("hr_department_closure") > 0, "hr_department_closure 表无数据");
        assertTrue(getTableCount("hr_employee_closure") > 0, "hr_employee_closure 表无数据");
        assertTrue(getTableCount("res_partner_closure") > 0, "res_partner_closure 表无数据");

        log.info("闭包表数据: company={}, department={}, employee={}, partner={}",
            getTableCount("res_company_closure"),
            getTableCount("hr_department_closure"),
            getTableCount("hr_employee_closure"),
            getTableCount("res_partner_closure"));
    }

    @Test
    @Order(2)
    @DisplayName("验证 company 闭包表层级关系正确")
    void testCompanyClosureRelations() {
        // 公司 1 应能访问所有 3 个公司（含自身）
        String sql = "SELECT COUNT(*) FROM res_company_closure WHERE parent_id = 1";
        Long count = executeQueryForObject(sql, Long.class);
        assertEquals(3L, count, "公司1应能访问3个节点（自身 + 2个子公司）");

        // 公司 2 是叶子节点，只有自身
        sql = "SELECT COUNT(*) FROM res_company_closure WHERE parent_id = 2";
        count = executeQueryForObject(sql, Long.class);
        assertEquals(1L, count, "公司2是叶子节点，只有自身");

        // 自引用数量 = 公司总数
        sql = "SELECT COUNT(*) FROM res_company_closure WHERE parent_id = company_id AND distance = 0";
        count = executeQueryForObject(sql, Long.class);
        assertEquals(3L, count, "每个公司都应有到自身的关系(distance=0)");
    }

    // ==========================================
    // TM 模型闭包表配置验证
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("验证 SaleOrder company 维度有闭包表配置")
    void testSaleOrderCompanyClosureConfig() {
        TableModel model = tableModelLoaderManager.load("OdooSaleOrderModel");
        assertNotNull(model);

        DbDimension companyDim = model.findJdbcDimensionByName("company");
        assertNotNull(companyDim, "应存在 company 维度");

        DbModelParentChildDimensionImpl pcDim = companyDim.getDecorate(DbModelParentChildDimensionImpl.class);
        assertNotNull(pcDim, "company 维度应为父子维度类型");
        assertEquals("res_company_closure", pcDim.getClosureTableName());
        assertEquals("parent_id", pcDim.getParentKey());
        assertEquals("company_id", pcDim.getChildKey());
        assertNotNull(pcDim.getClosureQueryObject(), "closureQueryObject 不应为空");
        assertNotNull(pcDim.getAncestorClosureQueryObject(), "ancestorClosureQueryObject 不应为空");

        log.info("SaleOrder company 闭包表配置验证通过");
    }

    @Test
    @Order(11)
    @DisplayName("验证 HrEmployee 多维度闭包表配置")
    void testHrEmployeeClosureConfig() {
        TableModel model = tableModelLoaderManager.load("OdooHrEmployeeModel");
        assertNotNull(model);

        // department 维度
        DbDimension deptDim = model.findJdbcDimensionByName("department");
        assertNotNull(deptDim);
        DbModelParentChildDimensionImpl deptPc = deptDim.getDecorate(DbModelParentChildDimensionImpl.class);
        assertNotNull(deptPc, "department 维度应为父子维度");
        assertEquals("hr_department_closure", deptPc.getClosureTableName());

        // parent（manager）维度
        DbDimension parentDim = model.findJdbcDimensionByName("parent");
        assertNotNull(parentDim);
        DbModelParentChildDimensionImpl parentPc = parentDim.getDecorate(DbModelParentChildDimensionImpl.class);
        assertNotNull(parentPc, "parent 维度应为父子维度");
        assertEquals("hr_employee_closure", parentPc.getClosureTableName());

        // company 维度
        DbDimension companyDim = model.findJdbcDimensionByName("company");
        assertNotNull(companyDim);
        DbModelParentChildDimensionImpl companyPc = companyDim.getDecorate(DbModelParentChildDimensionImpl.class);
        assertNotNull(companyPc, "company 维度应为父子维度");
        assertEquals("res_company_closure", companyPc.getClosureTableName());

        log.info("HrEmployee 多维度闭包表配置验证通过");
    }

    // ==========================================
    // selfAndDescendantsOf 后代方向查询（child_of 场景）
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("selfAndDescendantsOf - 查询公司1及子公司的销售订单")
    void testSelfAndDescendantsOf_SaleOrder() {
        // 1. 原生 SQL：公司1的子公司有2和3，所以应查到 company_id IN (1,2,3) 的订单
        String nativeSql = """
            SELECT COUNT(*) as cnt
            FROM sale_order so
            INNER JOIN res_company_closure cc ON so.company_id = cc.company_id
            WHERE cc.parent_id = 1
            """;
        Long expectedCount = executeQueryForObject(nativeSql, Long.class);
        log.info("原生SQL公司1及子公司订单数: {}", expectedCount);
        assertTrue(expectedCount > 0, "应有订单数据");

        // 2. 通过服务查询（使用 selfAndDescendantsOf 操作符）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooSaleOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("company$caption", "amountTotal", "orderCount"));
        queryRequest.setReturnTotal(true);

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("company$id");
        slice.setOp("selfAndDescendantsOf");
        slice.setValue(1);
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("selfAndDescendantsOf 查询结果: {} 条", items.size());

        // 3. 验证 — 总数应与原生 SQL 一致
        assertEquals(expectedCount.intValue(), items.size(),
            "selfAndDescendantsOf 结果数量应与原生 SQL 一致");

        // 额外验证：应包含公司2的订单（北京子公司）
        String bj_sql = "SELECT COUNT(*) FROM sale_order WHERE company_id = 2";
        Long bjCount = executeQueryForObject(bj_sql, Long.class);
        assertTrue(bjCount > 0 && items.size() > bjCount.intValue(),
            "结果应包含子公司订单且总数多于仅子公司数");
    }

    // ==========================================
    // selfAndAncestorsOf 祖先方向查询（parent_of 场景，新增能力）
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("selfAndAncestorsOf - 查询公司2的所有祖先及自身的订单")
    void testSelfAndAncestorsOf_SaleOrder() {
        // 公司2（北京）的祖先是公司1（上海），selfAndAncestorsOf 应返回 company_id IN (1,2) 的订单

        // 1. 原生 SQL：祖先方向查询
        String nativeSql = """
            SELECT COUNT(*) as cnt
            FROM sale_order so
            INNER JOIN res_company_closure cc ON so.company_id = cc.parent_id
            WHERE cc.company_id = 2
            """;
        Long expectedCount = executeQueryForObject(nativeSql, Long.class);
        log.info("原生SQL公司2及祖先订单数: {}", expectedCount);
        assertTrue(expectedCount > 0, "应有订单数据");

        // 2. 通过服务查询（使用 selfAndAncestorsOf 操作符）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooSaleOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("company$caption", "amountTotal", "orderCount"));
        queryRequest.setReturnTotal(true);

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("company$id");
        slice.setOp("selfAndAncestorsOf");
        slice.setValue(2);
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("selfAndAncestorsOf 查询结果: {} 条", items.size());

        // 3. 验证
        assertEquals(expectedCount.intValue(), items.size(),
            "selfAndAncestorsOf 结果数量应与原生 SQL 一致");

        // 公司2的祖先包含公司1，所以结果应多于仅公司2自身的订单
        String selfSql = "SELECT COUNT(*) FROM sale_order WHERE company_id = 2";
        Long selfCount = executeQueryForObject(selfSql, Long.class);
        assertTrue(items.size() > selfCount.intValue(),
            "祖先查询结果应包含上级公司订单");
    }

    @Test
    @Order(31)
    @DisplayName("ancestorsOf - 查询公司2的祖先（不含自身）的订单")
    void testAncestorsOf_SaleOrder() {
        // 公司2（北京）的祖先是公司1（上海），ancestorsOf 不含自身

        // 1. 原生 SQL
        String nativeSql = """
            SELECT COUNT(*) as cnt
            FROM sale_order so
            INNER JOIN res_company_closure cc ON so.company_id = cc.parent_id
            WHERE cc.company_id = 2 AND cc.distance > 0
            """;
        Long expectedCount = executeQueryForObject(nativeSql, Long.class);
        log.info("原生SQL公司2的祖先（不含自身）订单数: {}", expectedCount);

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooSaleOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("company$caption", "amountTotal"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("company$id");
        slice.setOp("ancestorsOf");
        slice.setValue(2);
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("ancestorsOf 查询结果: {} 条", items.size());

        // 3. 验证 — 不含公司2自身的订单
        assertEquals(expectedCount.intValue(), items.size(),
            "ancestorsOf 结果应仅包含祖先公司的订单");

        // ancestorsOf 结果应仅包含 company_id=1 的订单
        String parentSql = "SELECT COUNT(*) FROM sale_order WHERE company_id = 1";
        Long parentCount = executeQueryForObject(parentSql, Long.class);
        assertEquals(parentCount.intValue(), items.size(),
            "ancestorsOf 结果应仅包含直接上级公司1的订单");
    }

    @Test
    @Order(32)
    @DisplayName("selfAndDescendantsOf vs selfAndAncestorsOf - 叶子节点行为一致")
    void testLeafNodeBehavior() {
        // 公司3（Overseas Branch）是叶子节点，没有子公司也没有子公司
        // selfAndDescendantsOf(3) 应只返回 company_id=3 的订单
        // selfAndAncestorsOf 对于根节点(1) 应只返回自身

        // selfAndDescendantsOf(3) — 叶子节点只返回自身
        DbQueryRequestDef queryRequest1 = new DbQueryRequestDef();
        queryRequest1.setQueryModel("OdooSaleOrderQueryModel");
        queryRequest1.setColumns(Arrays.asList("amountTotal"));

        SliceRequestDef slice1 = new SliceRequestDef();
        slice1.setField("company$id");
        slice1.setOp("selfAndDescendantsOf");
        slice1.setValue(3);
        queryRequest1.setSlice(Collections.singletonList(slice1));

        PagingRequest<DbQueryRequestDef> form1 = PagingRequest.buildPagingRequest(queryRequest1, 50);
        PagingResultImpl result1 = jdbcService.queryModelData(form1);
        int descendantCount = ((List<?>) result1.getItems()).size();

        // 直接 SQL 对照
        Long directCount = executeQueryForObject("SELECT COUNT(*) FROM sale_order WHERE company_id = 3", Long.class);
        assertEquals(directCount.intValue(), descendantCount,
            "叶子节点 selfAndDescendantsOf 应只返回自身订单");

        log.info("叶子节点 selfAndDescendantsOf(3): {} 条, 直接查询: {} 条", descendantCount, directCount);
    }

    // ==========================================
    // 分组聚合 + 层级操作符
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("selfAndDescendantsOf + 分组聚合 - 按公司分组查看子公司销售")
    void testDescendantsOfWithGroupBy() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooSaleOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("company$caption", "amountTotal", "orderCount"));

        // 过滤：公司1及其子公司
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("company$id");
        slice.setOp("selfAndDescendantsOf");
        slice.setValue(1);
        queryRequest.setSlice(Collections.singletonList(slice));

        // 按公司分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("company$caption");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("company$caption");
        order.setDir("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();

        log.info("分组聚合结果:");
        for (Map<String, Object> item : items) {
            log.info("  {} - 总额: {}, 订单数: {}",
                item.get("company$caption"), item.get("amountTotal"), item.get("orderCount"));
        }

        // 公司1有3个公司（1,2,3），至少应分组出2个以上（排除可能没订单的）
        assertTrue(items.size() >= 2, "至少应有2个公司分组（上海+北京有订单）");

        // 验证每条记录的金额 > 0
        for (Map<String, Object> item : items) {
            BigDecimal amount = toBigDecimal(item.get("amountTotal"));
            assertTrue(amount.compareTo(BigDecimal.ZERO) > 0,
                "每个公司的总额应 > 0: " + item.get("company$caption"));
        }
    }

    // ==========================================
    // 工具方法
    // ==========================================

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return new BigDecimal(value.toString());
    }
}
