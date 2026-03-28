package com.foggyframework.dataset.db.model.odoo;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.service.JdbcService;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 闭包表操作符在 $or 内的 slice 测试
 *
 * <p>复现 Odoo Bridge 团队报告的问题：
 * <ul>
 *   <li>selfAndDescendantsOf 在 $or 条件内与 is null 组合使用时，查询返回 0 行</li>
 *   <li>场景来源：Odoo ir.rule 多公司规则产生的权限条件</li>
 * </ul>
 *
 * <p>Odoo permission_bridge.py 生成的 slice：
 * <pre>
 * {
 *   "$or": [
 *     {"field": "company$id", "op": "is null"},
 *     {"field": "company$id", "op": "selfAndDescendantsOf", "value": [1, 2]}
 *   ]
 * }
 * </pre>
 *
 * <p>期望 SQL：
 * <pre>
 * SELECT ... FROM res_partner rp
 * LEFT JOIN res_company_closure rcc ON rp.company_id = rcc.company_id
 * WHERE rp.company_id IS NULL
 *    OR rcc.parent_id IN (1, 2)
 * </pre>
 *
 * @see <a href="foggy-odoo-bridge/docs/java-team-report-selfAndAncestorsOf-2026-03-22.md">问题报告</a>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("闭包表操作符在 $or 条件内的处理")
class ClosureOperatorOrSliceTest extends EcommerceTestSupport {

    @Resource
    private JdbcService jdbcService;

    // ==========================================
    // 测试数据准备
    // ==========================================

    @BeforeEach
    void ensureTestData() {
        // 添加 company_id 为 NULL 的 partner（模拟 Odoo 个人联系人）
        // 使用 INSERT OR IGNORE 避免重复插入
        try {
            Long count = executeQueryForObject(
                    "SELECT COUNT(*) FROM res_partner WHERE id IN (101, 102)", Long.class);
            if (count == 0) {
                jdbcTemplate.execute(
                        "INSERT INTO res_partner (id, name, display_name, type, is_company, city, company_id, active, customer_rank) VALUES " +
                                "(101, 'Personal Contact A', 'Personal Contact A', 'contact', 0, 'Shanghai', NULL, 1, 1)");
                jdbcTemplate.execute(
                        "INSERT INTO res_partner (id, name, display_name, type, is_company, city, company_id, active, customer_rank) VALUES " +
                                "(102, 'Personal Contact B', 'Personal Contact B', 'contact', 0, 'Beijing', NULL, 1, 0)");
                log.info("插入了 2 条 company_id=NULL 的测试 partner");
            }
        } catch (Exception e) {
            log.warn("测试数据插入跳过（可能已存在）: {}", e.getMessage());
        }
    }

    // ==========================================
    // 基线测试：不带 $or 的闭包操作符正常工作
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("基线: selfAndDescendantsOf 在顶级 slice 正常工作")
    void testBaselineSelfAndDescendantsOfTopLevel() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooResPartnerQueryModel");
        queryRequest.setColumns(Arrays.asList("name", "city", "company$caption"));

        // selfAndDescendantsOf(1) → 应匹配 company_id IN (1,2,3)
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("company$id");
        slice.setOp("selfAndDescendantsOf");
        slice.setValue(1);
        queryRequest.setSlice(Collections.singletonList(slice));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("基线 selfAndDescendantsOf(1) 顶级 slice 返回 {} 条", items.size());

        // 原始 6 条 partner 都有 company_id IN (1,2,3)，应全部返回
        assertTrue(items.size() >= 6, "selfAndDescendantsOf(1) 应至少返回 6 条（所有有公司的 partner）");
    }

    @Test
    @Order(2)
    @DisplayName("基线: is null 在顶级 slice 正常工作")
    void testBaselineIsNullTopLevel() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooResPartnerQueryModel");
        queryRequest.setColumns(Arrays.asList("name", "city"));

        // company$id IS NULL
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("company$id");
        slice.setOp("is null");
        queryRequest.setSlice(Collections.singletonList(slice));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("基线 company$id IS NULL 返回 {} 条", items.size());

        // 我们插入了 2 条 company_id=NULL 的 partner
        assertEquals(2, items.size(), "应返回 2 条 company_id=NULL 的 partner");
    }

    // ==========================================
    // 核心 BUG 复现：$or + 闭包操作符
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("BUG 复现: $or [is null, selfAndDescendantsOf] 应返回所有匹配行")
    void testOrWithIsNullAndSelfAndDescendantsOf() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooResPartnerQueryModel");
        queryRequest.setColumns(Arrays.asList("name", "city", "company$caption"));

        // 模拟 Odoo ir.rule: $or [company_id IS NULL, company_id child_of [1, 2]]
        List<SliceRequestDef> slices = new ArrayList<>();

        SliceRequestDef orGroup = new SliceRequestDef();
        List<CondRequestDef> orConditions = new ArrayList<>();

        // 条件 1: company$id IS NULL
        SliceRequestDef cond1 = new SliceRequestDef();
        cond1.setField("company$id");
        cond1.setOp("is null");
        orConditions.add(cond1);

        // 条件 2: company$id selfAndDescendantsOf [1, 2]
        SliceRequestDef cond2 = new SliceRequestDef();
        cond2.setField("company$id");
        cond2.setOp("selfAndDescendantsOf");
        cond2.setValue(Arrays.asList(1, 2));
        orConditions.add(cond2);

        orGroup.setOr(orConditions);
        slices.add(orGroup);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("$or [is null, selfAndDescendantsOf([1,2])] 返回 {} 条", items.size());
        for (Map<String, Object> item : items) {
            log.info("  {} - city={}, company={}", item.get("name"), item.get("city"), item.get("company$caption"));
        }

        // 期望：6 条有公司的 + 2 条 NULL 公司的 = 8 条
        // selfAndDescendantsOf([1, 2]) 匹配 company 1, 2, 3（因为 3 是 1 的子公司）
        assertTrue(items.size() > 0, "不应返回 0 行！这是 BUG 的症状");
        assertTrue(items.size() >= 8,
                "应返回至少 8 条: 6 条有公司的 + 2 条 company_id=NULL 的, 实际: " + items.size());

        // 验证包含 NULL company_id 的记录
        long nullCompanyCount = items.stream()
                .filter(item -> item.get("company$caption") == null)
                .count();
        assertEquals(2, nullCompanyCount, "应包含 2 条 company_id=NULL 的 partner");
    }

    @Test
    @Order(11)
    @DisplayName("BUG 复现: $or [is null, selfAndDescendantsOf] 单值")
    void testOrWithIsNullAndSelfAndDescendantsOfSingleValue() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooResPartnerQueryModel");
        queryRequest.setColumns(Arrays.asList("name", "city", "company$caption"));

        // $or [company_id IS NULL, company_id selfAndDescendantsOf 1]
        List<SliceRequestDef> slices = new ArrayList<>();

        SliceRequestDef orGroup = new SliceRequestDef();
        List<CondRequestDef> orConditions = new ArrayList<>();

        SliceRequestDef cond1 = new SliceRequestDef();
        cond1.setField("company$id");
        cond1.setOp("is null");
        orConditions.add(cond1);

        SliceRequestDef cond2 = new SliceRequestDef();
        cond2.setField("company$id");
        cond2.setOp("selfAndDescendantsOf");
        cond2.setValue(1);  // 单值
        orConditions.add(cond2);

        orGroup.setOr(orConditions);
        slices.add(orGroup);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("$or [is null, selfAndDescendantsOf(1)] 返回 {} 条", items.size());

        // company 1 有子公司 2, 3 → 匹配 company 1, 2, 3 = 全部 6 条 + 2 条 NULL = 8 条
        assertTrue(items.size() >= 8,
                "应返回至少 8 条, 实际: " + items.size());
    }

    @Test
    @Order(12)
    @DisplayName("BUG 复现: $or [is null, selfAndAncestorsOf]")
    void testOrWithIsNullAndSelfAndAncestorsOf() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooResPartnerQueryModel");
        queryRequest.setColumns(Arrays.asList("name", "city", "company$caption"));

        // $or [company_id IS NULL, company_id selfAndAncestorsOf 2]
        // selfAndAncestorsOf(2) → company 2 的祖先及自身 = company 1, 2
        List<SliceRequestDef> slices = new ArrayList<>();

        SliceRequestDef orGroup = new SliceRequestDef();
        List<CondRequestDef> orConditions = new ArrayList<>();

        SliceRequestDef cond1 = new SliceRequestDef();
        cond1.setField("company$id");
        cond1.setOp("is null");
        orConditions.add(cond1);

        SliceRequestDef cond2 = new SliceRequestDef();
        cond2.setField("company$id");
        cond2.setOp("selfAndAncestorsOf");
        cond2.setValue(2);
        orConditions.add(cond2);

        orGroup.setOr(orConditions);
        slices.add(orGroup);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("$or [is null, selfAndAncestorsOf(2)] 返回 {} 条", items.size());
        for (Map<String, Object> item : items) {
            log.info("  {} - city={}, company={}", item.get("name"), item.get("city"), item.get("company$caption"));
        }

        // selfAndAncestorsOf(2) 匹配 company 1, 2
        // company_id=1 的 partner: 1, 3, 5, 6 = 4 条
        // company_id=2 的 partner: 2 = 1 条
        // company_id=NULL 的 partner: 101, 102 = 2 条
        // 总计 7 条
        assertTrue(items.size() > 0, "不应返回 0 行！这是 BUG 的症状");

        Long expectedFromClosure = executeQueryForObject(
                "SELECT COUNT(*) FROM res_partner rp " +
                        "INNER JOIN res_company_closure cc ON rp.company_id = cc.parent_id " +
                        "WHERE cc.company_id = 2", Long.class);
        long expectedTotal = expectedFromClosure + 2; // +2 for NULL company_id
        assertEquals(expectedTotal, items.size(),
                "应返回 " + expectedTotal + " 条 (闭包匹配 " + expectedFromClosure + " + NULL 公司 2)");
    }

    // ==========================================
    // 进阶场景：带 distance 条件的操作符在 $or 内
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("$or [is null, descendantsOf] - 带 distance 条件")
    void testOrWithIsNullAndDescendantsOf() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooResPartnerQueryModel");
        queryRequest.setColumns(Arrays.asList("name", "city", "company$caption"));

        // $or [company_id IS NULL, company_id descendantsOf 1]
        // descendantsOf(1) → company 1 的后代（不含自身）= company 2, 3
        List<SliceRequestDef> slices = new ArrayList<>();

        SliceRequestDef orGroup = new SliceRequestDef();
        List<CondRequestDef> orConditions = new ArrayList<>();

        SliceRequestDef cond1 = new SliceRequestDef();
        cond1.setField("company$id");
        cond1.setOp("is null");
        orConditions.add(cond1);

        SliceRequestDef cond2 = new SliceRequestDef();
        cond2.setField("company$id");
        cond2.setOp("descendantsOf");
        cond2.setValue(1);
        orConditions.add(cond2);

        orGroup.setOr(orConditions);
        slices.add(orGroup);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("$or [is null, descendantsOf(1)] 返回 {} 条", items.size());
        for (Map<String, Object> item : items) {
            log.info("  {} - city={}, company={}", item.get("name"), item.get("city"), item.get("company$caption"));
        }

        // descendantsOf(1)（不含自身）→ company 2, 3
        // company_id=2: partner 2 = 1 条
        // company_id=3: partner 4 = 1 条
        // company_id=NULL: 101, 102 = 2 条
        // 总计 4 条
        assertTrue(items.size() > 0, "不应返回 0 行");

        // 验证不包含 company_id=1 的 partner（descendantsOf 不含自身）
        long company1Count = items.stream()
                .filter(item -> "My Company (Shanghai)".equals(item.get("company$caption")))
                .count();
        assertEquals(0, company1Count, "descendantsOf 不应包含公司1自身的 partner");

        // 验证包含 NULL company_id
        long nullCompanyCount = items.stream()
                .filter(item -> item.get("company$caption") == null)
                .count();
        assertEquals(2, nullCompanyCount, "应包含 2 条 company_id=NULL 的 partner");
    }

    // ==========================================
    // 完整 Odoo 场景复现
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("完整 Odoo 场景: 带 AND 和 $or 的复合 slice")
    void testFullOdooPermissionSlice() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooResPartnerQueryModel");
        queryRequest.setColumns(Arrays.asList("name", "city", "company$caption", "partnerCount"));

        // Odoo permission_bridge.py 完整场景:
        // slice: [
        //   {"$or": [
        //     {"field": "company$id", "op": "is null"},
        //     {"field": "company$id", "op": "selfAndDescendantsOf", "value": [1, 2]}
        //   ]}
        // ]
        List<SliceRequestDef> slices = new ArrayList<>();

        SliceRequestDef orGroup = new SliceRequestDef();
        List<CondRequestDef> orConditions = new ArrayList<>();

        SliceRequestDef cond1 = new SliceRequestDef();
        cond1.setField("company$id");
        cond1.setOp("is null");
        orConditions.add(cond1);

        SliceRequestDef cond2 = new SliceRequestDef();
        cond2.setField("company$id");
        cond2.setOp("selfAndDescendantsOf");
        cond2.setValue(Arrays.asList(1, 2));
        orConditions.add(cond2);

        orGroup.setOr(orConditions);
        slices.add(orGroup);
        queryRequest.setSlice(slices);

        log.info("执行完整 Odoo 权限 slice 场景...");

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("完整 Odoo 场景返回 {} 条", items.size());
        for (Map<String, Object> item : items) {
            log.info("  {} - city={}, company={}", item.get("name"), item.get("city"), item.get("company$caption"));
        }

        // 所有 partner 都应返回（6 有公司 + 2 无公司 = 8）
        assertTrue(items.size() >= 8,
                "完整 Odoo 场景应返回至少 8 条, 实际: " + items.size());
    }

    @Test
    @Order(32)
    @DisplayName("嵌套组合: $and[$or[is null,selfAndAncestorsOf], city is not null]")
    void testNestedAndOrWithSelfAndAncestorsOf() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooResPartnerQueryModel");
        queryRequest.setColumns(Arrays.asList("name", "city", "company$caption"));

        SliceRequestDef andGroup = new SliceRequestDef();
        List<CondRequestDef> andConditions = new ArrayList<>();

        SliceRequestDef orGroup = new SliceRequestDef();
        List<CondRequestDef> orConditions = new ArrayList<>();

        SliceRequestDef cond1 = new SliceRequestDef();
        cond1.setField("company$id");
        cond1.setOp("is null");
        orConditions.add(cond1);

        SliceRequestDef cond2 = new SliceRequestDef();
        cond2.setField("company$id");
        cond2.setOp("selfAndAncestorsOf");
        cond2.setValue(2);
        orConditions.add(cond2);

        orGroup.setOr(orConditions);
        andConditions.add(orGroup);

        SliceRequestDef cond3 = new SliceRequestDef();
        cond3.setField("city");
        cond3.setOp("is not null");
        andConditions.add(cond3);

        andGroup.setAnd(andConditions);
        queryRequest.setSlice(Collections.singletonList(andGroup));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("嵌套 $and/$or + selfAndAncestorsOf 返回 {} 条", items.size());

        assertTrue(items.size() > 0, "嵌套逻辑条件不应将 hierarchy 条件降级为无结果");
        assertTrue(items.stream().allMatch(item -> item.get("city") != null), "AND 条件 city is not null 应生效");
    }

    @Test
    @Order(33)
    @DisplayName("祖先方向多值: $or [is null, selfAndAncestorsOf([2,3])]")
    void testOrWithIsNullAndSelfAndAncestorsOfListValue() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooResPartnerQueryModel");
        queryRequest.setColumns(Arrays.asList("name", "city", "company$caption"));

        SliceRequestDef orGroup = new SliceRequestDef();
        List<CondRequestDef> orConditions = new ArrayList<>();

        SliceRequestDef cond1 = new SliceRequestDef();
        cond1.setField("company$id");
        cond1.setOp("is null");
        orConditions.add(cond1);

        SliceRequestDef cond2 = new SliceRequestDef();
        cond2.setField("company$id");
        cond2.setOp("selfAndAncestorsOf");
        cond2.setValue(Arrays.asList(2, 3));
        orConditions.add(cond2);

        orGroup.setOr(orConditions);
        queryRequest.setSlice(Collections.singletonList(orGroup));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("$or [is null, selfAndAncestorsOf([2,3])] 返回 {} 条", items.size());

        assertTrue(items.size() >= 8,
                "祖先方向多值列表应匹配 company 1/2/3 再加上 NULL company 的记录, 实际: " + items.size());
    }

    @Test
    @Order(31)
    @DisplayName("完整 Odoo 场景对比: 不带 slice 查询所有 partner")
    void testAllPartnersWithoutSlice() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("OdooResPartnerQueryModel");
        queryRequest.setColumns(Arrays.asList("name", "city", "company$caption"));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("不带 slice 查询返回 {} 条", items.size());

        assertTrue(items.size() >= 8, "应返回所有 partner (至少 8 条)");
    }
}
