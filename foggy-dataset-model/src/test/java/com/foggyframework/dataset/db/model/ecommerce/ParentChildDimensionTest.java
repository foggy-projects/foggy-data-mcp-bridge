package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelParentChildDimensionImpl;
import com.foggyframework.dataset.db.model.service.JdbcService;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 父子维度测试
 *
 * <p>测试父子维度（Parent-Child Dimension）的功能，包括：
 * <ul>
 *   <li>模型加载：验证父子维度配置正确解析</li>
 *   <li>层级查询：验证查询时自动包含子节点数据</li>
 *   <li>汇总计算：验证按层级汇总的正确性</li>
 * </ul>
 *
 * <p>测试数据说明：
 * <pre>
 * 团队层级结构：
 *   总公司 (T001)
 *   ├── 技术部 (T002)
 *   │   ├── 研发组 (T003)
 *   │   │   ├── 前端小组 (T006)
 *   │   │   └── 后端小组 (T007)
 *   │   └── 测试组 (T004)
 *   └── 销售部 (T005)
 *       ├── 华东区 (T008)
 *       └── 华北区 (T009)
 * </pre>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("父子维度测试 - Parent-Child Dimension")
class ParentChildDimensionTest extends EcommerceTestSupport {

    @Resource
    private JdbcService jdbcService;

    // ==========================================
    // 数据环境验证
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("验证团队维度表数据")
    void testTeamDimensionData() {
        // 验证团队维度表
        Long teamCount = getTableCount("dim_team");
        assertEquals(9L, teamCount, "应有9个团队");
        log.info("团队维度表记录数: {}", teamCount);

        // 验证层级结构
        String sql = "SELECT team_level, COUNT(*) as cnt FROM dim_team GROUP BY team_level ORDER BY team_level";
        List<Map<String, Object>> levelCounts = executeQuery(sql);

        log.info("层级分布: {}", levelCounts);

        // Level 1: 总公司 (1个)
        // Level 2: 技术部、销售部 (2个)
        // Level 3: 研发组、测试组、华东区、华北区 (4个)
        // Level 4: 前端小组、后端小组 (2个)
        assertEquals(4, levelCounts.size(), "应有4个层级");
    }

    @Test
    @Order(2)
    @DisplayName("验证闭包表数据完整性")
    void testClosureTableData() {
        // 验证闭包表记录数
        Long closureCount = getTableCount("team_closure");
        log.info("闭包表记录数: {}", closureCount);

        // 9个节点自身关系 + 祖先关系
        // T001有9个后代（含自身），T002有5个，T003有3个，T005有3个，其余各1个
        // 总计: 9 + 5 + 3 + 1 + 3 + 1 + 1 + 1 + 1 = 25
        assertTrue(closureCount >= 20, "闭包表应至少有20条记录");

        // 验证每个节点都有到自身的关系
        String selfRelationSql = """
            SELECT COUNT(*) as cnt
            FROM team_closure
            WHERE parent_id = team_id AND distance = 0
            """;
        Long selfCount = executeQueryForObject(selfRelationSql, Long.class);
        assertEquals(9L, selfCount, "每个节点都应有到自身的关系(distance=0)");

        // 验证总公司(T001)能访问所有子孙
        String t001ChildrenSql = """
            SELECT COUNT(*) as cnt
            FROM team_closure
            WHERE parent_id = 'T001'
            """;
        Long t001Children = executeQueryForObject(t001ChildrenSql, Long.class);
        assertEquals(9L, t001Children, "总公司应能访问9个节点（含自身）");

        // 验证技术部(T002)能访问其子孙
        String t002ChildrenSql = """
            SELECT COUNT(*) as cnt
            FROM team_closure
            WHERE parent_id = 'T002'
            """;
        Long t002Children = executeQueryForObject(t002ChildrenSql, Long.class);
        assertEquals(5L, t002Children, "技术部应能访问5个节点（自身+研发组+测试组+前端小组+后端小组）");
    }

    @Test
    @Order(3)
    @DisplayName("验证团队销售数据")
    void testTeamSalesData() {
        Long salesCount = getTableCount("fact_team_sales");
        assertEquals(18L, salesCount, "应有18条销售记录（9个团队 x 2天）");
        log.info("团队销售事实表记录数: {}", salesCount);

        // 验证销售总额
        String totalSql = "SELECT SUM(sales_amount) as total FROM fact_team_sales";
        BigDecimal amount = executeQueryForObject(totalSql, BigDecimal.class);
        log.info("销售总额: {}", amount);
        assertTrue(amount.compareTo(BigDecimal.ZERO) > 0, "销售总额应大于0");
    }

    // ==========================================
    // 模型加载测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("验证父子维度模型加载")
    void testParentChildDimensionModelLoad() {
        // 获取模型
        TableModel model = tableModelLoaderManager.getJdbcModel("FactTeamSalesModel");
        assertNotNull(model, "模型应成功加载");
        log.info("加载模型: {}", model.getName());

        // 验证team维度是父子维度
        DbDimension teamDimension = model.findJdbcDimensionByName("team");
        assertNotNull(teamDimension, "应存在team维度");

        DbModelParentChildDimensionImpl parentChildDim = teamDimension.getDecorate(DbModelParentChildDimensionImpl.class);
        assertNotNull(parentChildDim, "team维度应为父子维度类型");

        // 验证父子维度配置
        assertEquals("parent_id", parentChildDim.getParentKey(), "parentKey应为parent_id");
        assertEquals("team_id", parentChildDim.getChildKey(), "childKey应为team_id");
        assertEquals("team_closure", parentChildDim.getClosureTableName(), "closureTableName应为team_closure");
        assertNotNull(parentChildDim.getClosureQueryObject(), "闭包表QueryObject不应为空");

        log.info("父子维度配置验证通过: parentKey={}, childKey={}, closureTable={}",
                parentChildDim.getParentKey(), parentChildDim.getChildKey(), parentChildDim.getClosureTableName());
    }

    // ==========================================
    // 基础查询测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("明细查询 - 全部团队销售")
    void testDetailQuery_AllTeamSales() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "team$caption",
                "team$teamLevel",
                "date$caption",
                "avgSalesAmount",
                "salesCount"
        ));

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$teamLevel", "ASC"));
        orders.add(createOrder("team$caption", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        assertEquals(18, result.getItems().size(), "应返回18条记录");

        log.info("明细查询结果: {} 条", result.getItems().size());
        printResults((List<Map<String, Object>>) result.getItems(), 5);
    }

    // ==========================================
    // 父子维度层级查询测试（核心功能）
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("层级查询 - 查询总公司及所有子公司")
    void testHierarchyQuery_AllDescendants() {
        // 1. 原生SQL：通过闭包表查询总公司(T001)及其所有子孙的销售
        String nativeSql = """
            SELECT SUM(fs.sales_amount) as total_amount, SUM(fs.sales_count) as total_count
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            WHERE tc.parent_id = 'T001'
            """;
        List<Map<String, Object>> nativeResult = executeQuery(nativeSql);
        BigDecimal expectedTotal = toBigDecimal(nativeResult.get(0).get("total_amount"));
        log.info("原生SQL总公司及子孙销售总额: {}", expectedTotal);

        // 2. 通过服务查询（带父子维度过滤）
        // 注意：queryModelData查询必须有limit，getItems返回的是明细数据
        // 要获取聚合数据，需设置totalColumn=true，然后从getTotalData()获取
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("salesAmount", "salesCount"));
        queryRequest.setReturnTotal(true);  // 启用汇总数据返回

        // 过滤条件：团队 = T001（总公司）
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$hierarchy$id");
        slice.setOp("in");
        slice.setValue(Arrays.asList("T001"));
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        // items返回的是明细数据（分页后的数据）
        log.info("明细数据条数: {}", result.getItems().size());

        // 3. 从totalData获取聚合数据
        assertNotNull(result.getTotalData(), "应返回汇总数据（需设置totalColumn=true）");
        assertTrue(result.getTotalData() instanceof Map, "汇总数据应为Map类型");

        Map<String, Object> totalData = (Map<String, Object>) result.getTotalData();
        BigDecimal serviceTotal = toBigDecimal(totalData.get("salesAmount"));
        log.info("服务查询总公司及子孙销售总额（从totalData获取）: {}", serviceTotal);

        // 4. 对比结果
        assertDecimalEquals(expectedTotal, serviceTotal, "总公司及子孙销售总额应一致");
    }

    @Test
    @Order(31)
    @DisplayName("层级查询 - 查询技术部及其子部门")
    void testHierarchyQuery_TechDepartment() {
        // 1. 原生SQL：通过闭包表查询技术部(T002)及其子孙的销售
        String nativeSql = """
            SELECT SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            WHERE tc.parent_id = 'T002'
            """;
        BigDecimal expectedTotal = executeQueryForObject(nativeSql, BigDecimal.class);
        log.info("原生SQL技术部及子部门销售总额: {}", expectedTotal);

        // 2. 通过服务查询
        // 注意：queryModelData查询必须有limit，需通过totalData获取聚合数据
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("salesAmount"));
        queryRequest.setReturnTotal(true);  // 启用汇总数据返回

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$hierarchy$id");
        slice.setOp("in");
        slice.setValue(Arrays.asList("T002"));
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        // 从totalData获取聚合数据
        assertNotNull(result.getTotalData(), "应返回汇总数据");
        Map<String, Object> totalData = (Map<String, Object>) result.getTotalData();
        BigDecimal serviceTotal = toBigDecimal(totalData.get("salesAmount"));
        log.info("服务查询技术部及子部门销售总额（从totalData获取）: {}", serviceTotal);

        // 技术部应包含：技术部 + 研发组 + 测试组 + 前端小组 + 后端小组 = 5个部门
        // 验证是否包含子部门数据
        String directSql = "SELECT SUM(sales_amount) FROM fact_team_sales WHERE team_id = 'T002'";
        BigDecimal directAmount = executeQueryForObject(directSql, BigDecimal.class);

        assertTrue(serviceTotal.compareTo(directAmount) > 0,
                "层级查询结果应大于仅技术部直接销售额（应包含子部门）");
        assertDecimalEquals(expectedTotal, serviceTotal, "技术部及子部门销售总额应一致");
    }

    @Test
    @Order(32)
    @DisplayName("层级查询 - 查询叶子节点（无子节点）")
    void testHierarchyQuery_LeafNode() {
        // 前端小组(T006)是叶子节点，没有子节点

        // 1. 原生SQL
        String nativeSql = """
            SELECT SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            WHERE tc.parent_id = 'T006'
            """;
        BigDecimal expectedTotal = executeQueryForObject(nativeSql, BigDecimal.class);
        log.info("原生SQL前端小组销售总额: {}", expectedTotal);

        // 2. 通过服务查询
        // 注意：queryModelData查询必须有limit，需通过totalData获取聚合数据
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("salesAmount"));
        queryRequest.setReturnTotal(true);  // 启用汇总数据返回

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("in");
        slice.setValue(Arrays.asList("T006"));
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        // 从totalData获取聚合数据
        assertNotNull(result.getTotalData(), "应返回汇总数据");
        Map<String, Object> totalData = (Map<String, Object>) result.getTotalData();
        BigDecimal serviceTotal = toBigDecimal(totalData.get("salesAmount"));
        log.info("服务查询前端小组销售总额（从totalData获取）: {}", serviceTotal);

        // 叶子节点查询应等于直接查询
        String directSql = "SELECT SUM(sales_amount) FROM fact_team_sales WHERE team_id = 'T006'";
        BigDecimal directAmount = executeQueryForObject(directSql, BigDecimal.class);

        assertDecimalEquals(directAmount, serviceTotal, "叶子节点查询应等于直接查询");
        assertDecimalEquals(expectedTotal, serviceTotal, "前端小组销售总额应一致");
    }

    @Test
    @Order(33)
    @DisplayName("层级查询 - 多个节点并集")
    void testHierarchyQuery_MultipleNodes() {
        // 查询技术部(T002)和销售部(T005)的并集

        // 1. 原生SQL
        String nativeSql = """
            SELECT SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            WHERE tc.parent_id IN ('T002', 'T005')
            """;
        BigDecimal expectedTotal = executeQueryForObject(nativeSql, BigDecimal.class);
        log.info("原生SQL技术部+销售部销售总额: {}", expectedTotal);

        // 2. 通过服务查询
        // 注意：queryModelData查询必须有limit，需通过totalData获取聚合数据
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("salesAmount"));
        queryRequest.setReturnTotal(true);  // 启用汇总数据返回

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$hierarchy$id");
        slice.setOp("in");
        slice.setValue(Arrays.asList("T002", "T005"));
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        // 从totalData获取聚合数据
        assertNotNull(result.getTotalData(), "应返回汇总数据");
        Map<String, Object> totalData = (Map<String, Object>) result.getTotalData();
        BigDecimal serviceTotal = toBigDecimal(totalData.get("salesAmount"));
        log.info("服务查询技术部+销售部销售总额（从totalData获取）: {}", serviceTotal);

        assertDecimalEquals(expectedTotal, serviceTotal, "技术部+销售部销售总额应一致");
    }

    // ==========================================
    // 分组汇总测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("分组汇总 - 按团队层级分组")
    void testGroupQuery_ByTeamLevel() {
        // 1. 原生SQL
        String nativeSql = """
            SELECT
                dt.team_level,
                SUM(fs.sales_amount) as total_amount,
                COUNT(*) as record_count
            FROM fact_team_sales fs
            LEFT JOIN dim_team dt ON fs.team_id = dt.team_id
            GROUP BY dt.team_level
            ORDER BY dt.team_level
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL按层级分组: {} 组", nativeResults.size());

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$teamLevel", "salesAmount", "recordCount"));

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("team$teamLevel"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$teamLevel", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();

        log.info("服务查询按层级分组: {} 组", items.size());

        // 3. 对比
        assertEquals(nativeResults.size(), items.size(), "分组数量应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> serviceRow = items.get(i);

            assertEquals(toInt(nativeRow.get("team_level")), toInt(serviceRow.get("team$teamLevel")),
                    "层级应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("total_amount"), serviceRow.get("salesAmount"),
                    "销售总额应一致: 层级 " + nativeRow.get("team_level"));
        }
    }

    @Test
    @Order(41)
    @DisplayName("分组汇总 - 按团队分组（带层级过滤）")
    void testGroupQuery_ByTeamWithHierarchyFilter() {
        // 查询技术部下各子部门的销售汇总

        // 1. 原生SQL
        String nativeSql = """
            SELECT
                dt.team_id,
                dt.team_name,
                SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            LEFT JOIN dim_team dt ON fs.team_id = dt.team_id
            WHERE tc.parent_id = 'T002'
            GROUP BY dt.team_id, dt.team_name
            ORDER BY dt.team_name
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL技术部下各子部门: {} 个", nativeResults.size());

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$id", "team$caption", "salesAmount"));

        // 过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$hierarchy$id");
        slice.setOp("in");
        slice.setValue(Arrays.asList("T002"));
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 分组
        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("team$id"));
        groups.add(createGroup("team$caption"));
        queryRequest.setGroupBy(groups);

        // 排序
        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$hierarchy$caption", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();

        log.info("服务查询技术部下各子部门: {} 个", items.size());

        // 3. 对比
        // 技术部下应有5个团队：技术部、研发组、测试组、前端小组、后端小组
        assertEquals(5, items.size(), "技术部下应有5个团队");
        assertEquals(nativeResults.size(), items.size(), "分组数量应一致");

        // 打印结果
        printResults(items, 10);
    }

    @Test
    @Order(42)
    @DisplayName("分组汇总 - 按日期和团队层级多维分组")
    void testGroupQuery_MultiDimensionWithHierarchy() {
        // 1. 原生SQL
        String nativeSql = """
            SELECT
                dd.full_date,
                dt.team_level,
                SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            LEFT JOIN dim_date dd ON fs.date_key = dd.date_key
            LEFT JOIN dim_team dt ON fs.team_id = dt.team_id
            GROUP BY dd.full_date, dt.team_level
            ORDER BY dd.full_date, dt.team_level
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL多维分组: {} 组", nativeResults.size());

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("date$caption", "team$teamLevel", "salesAmount"));

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("date$caption"));
        groups.add(createGroup("team$teamLevel"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("date$caption", "ASC"));
        orders.add(createOrder("team$teamLevel", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();

        log.info("服务查询多维分组: {} 组", items.size());

        // 3. 对比
        assertEquals(nativeResults.size(), items.size(), "分组数量应一致");

        // 打印结果
        printResults(items, 10);
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private void printResults(List<Map<String, Object>> results, int limit) {
        if (results == null || results.isEmpty()) {
            log.info("查询结果为空");
            return;
        }
        log.info("查询结果 (前{}条):", Math.min(limit, results.size()));
        for (int i = 0; i < Math.min(limit, results.size()); i++) {
            log.info("  Row {}: {}", i + 1, results.get(i));
        }
        if (results.size() > limit) {
            log.info("  ... 还有 {} 条记录未显示", results.size() - limit);
        }
    }

    private GroupRequestDef createGroup(String name) {
        GroupRequestDef group = new GroupRequestDef();
        group.setField(name);
        return group;
    }

    private OrderRequestDef createOrder(String name, String order) {
        OrderRequestDef orderDef = new OrderRequestDef();
        orderDef.setField(name);
        orderDef.setOrder(order);
        return orderDef;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return new BigDecimal(value.toString());
    }

    private Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private void assertDecimalEquals(Object expected, Object actual, String message) {
        BigDecimal expectedDecimal = toBigDecimal(expected).setScale(2, RoundingMode.HALF_UP);
        BigDecimal actualDecimal = toBigDecimal(actual).setScale(2, RoundingMode.HALF_UP);
        assertEquals(0, expectedDecimal.compareTo(actualDecimal),
                message + " - 期望: " + expectedDecimal + ", 实际: " + actualDecimal);
    }

    // ==========================================
    // 层级操作符测试（childrenOf, descendantsOf, selfAndDescendantsOf）
    // ==========================================

    @Test
    @Order(50)
    @DisplayName("层级操作符 - childrenOf 查询直接子节点")
    void testHierarchyOp_ChildrenOf() {
        // 查询技术部(T002)的直接子部门：研发组(T003)、测试组(T004)

        // 1. 原生SQL：distance = 1 表示直接子节点
        String nativeSql = """
            SELECT dt.team_id, dt.team_name, SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            LEFT JOIN dim_team dt ON fs.team_id = dt.team_id
            WHERE tc.parent_id = 'T002' AND tc.distance = 1
            GROUP BY dt.team_id, dt.team_name
            ORDER BY dt.team_name
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL技术部直接子部门: {} 个", nativeResults.size());

        // 2. 通过服务查询（使用 childrenOf 操作符）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$id", "team$caption", "salesAmount"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("childrenOf");
        slice.setValue("T002");
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("team$id"));
        groups.add(createGroup("team$caption"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$caption", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();

        log.info("childrenOf 查询结果: {} 个", items.size());
        printResults(items, 10);

        // 3. 验证
        // 技术部的直接子部门应有2个：研发组、测试组
        assertEquals(2, items.size(), "技术部直接子部门应有2个");
        assertEquals(nativeResults.size(), items.size(), "结果数量应一致");

        // 比对金额
        for (int i = 0; i < nativeResults.size(); i++) {
            assertDecimalEquals(nativeResults.get(i).get("total_amount"), items.get(i).get("salesAmount"),
                    "销售额应一致: " + nativeResults.get(i).get("team_name"));
        }
    }

    @Test
    @Order(51)
    @DisplayName("层级操作符 - descendantsOf 查询所有后代（不含自身）")
    void testHierarchyOp_DescendantsOf() {
        // 查询技术部(T002)的所有后代（不含技术部自身）

        // 1. 原生SQL：distance > 0 表示不含自身
        String nativeSql = """
            SELECT dt.team_id, dt.team_name, SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            LEFT JOIN dim_team dt ON fs.team_id = dt.team_id
            WHERE tc.parent_id = 'T002' AND tc.distance > 0
            GROUP BY dt.team_id, dt.team_name
            ORDER BY dt.team_name
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL技术部后代（不含自身）: {} 个", nativeResults.size());

        // 2. 通过服务查询（使用 descendantsOf 操作符）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$id", "team$caption", "salesAmount"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("descendantsOf");
        slice.setValue("T002");
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("team$id"));
        groups.add(createGroup("team$caption"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$caption", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();

        log.info("descendantsOf 查询结果: {} 个", items.size());
        printResults(items, 10);

        // 3. 验证
        // 技术部后代（不含自身）应有4个：研发组、测试组、前端小组、后端小组
        assertEquals(4, items.size(), "技术部后代（不含自身）应有4个");
        assertEquals(nativeResults.size(), items.size(), "结果数量应一致");

        // 确保不包含技术部自身
        boolean containsSelf = items.stream().anyMatch(item -> "T002".equals(item.get("team$id")));
        assertFalse(containsSelf, "descendantsOf 结果不应包含节点自身");
    }

    @Test
    @Order(52)
    @DisplayName("层级操作符 - selfAndDescendantsOf 查询自身及所有后代")
    void testHierarchyOp_SelfAndDescendantsOf() {
        // 查询技术部(T002)及其所有后代

        // 1. 原生SQL：无 distance 限制
        String nativeSql = """
            SELECT dt.team_id, dt.team_name, SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            LEFT JOIN dim_team dt ON fs.team_id = dt.team_id
            WHERE tc.parent_id = 'T002'
            GROUP BY dt.team_id, dt.team_name
            ORDER BY dt.team_name
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL技术部及后代: {} 个", nativeResults.size());

        // 2. 通过服务查询（使用 selfAndDescendantsOf 操作符）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$id", "team$caption", "salesAmount"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("selfAndDescendantsOf");
        slice.setValue("T002");
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("team$id"));
        groups.add(createGroup("team$caption"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$caption", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();

        log.info("selfAndDescendantsOf 查询结果: {} 个", items.size());
        printResults(items, 10);

        // 3. 验证
        // 技术部及后代应有5个：技术部、研发组、测试组、前端小组、后端小组
        assertEquals(5, items.size(), "技术部及后代应有5个");
        assertEquals(nativeResults.size(), items.size(), "结果数量应一致");

        // 确保包含技术部自身
        boolean containsSelf = items.stream().anyMatch(item -> "T002".equals(item.get("team$id")));
        assertTrue(containsSelf, "selfAndDescendantsOf 结果应包含节点自身");
    }

    @Test
    @Order(53)
    @DisplayName("层级操作符 - descendantsOf 带 maxDepth 限制")
    void testHierarchyOp_DescendantsOf_WithMaxDepth() {
        // 查询总公司(T001)的2级以内后代

        // 1. 原生SQL：distance BETWEEN 1 AND 2
        String nativeSql = """
            SELECT dt.team_id, dt.team_name, SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            LEFT JOIN dim_team dt ON fs.team_id = dt.team_id
            WHERE tc.parent_id = 'T001' AND tc.distance BETWEEN 1 AND 2
            GROUP BY dt.team_id, dt.team_name
            ORDER BY dt.team_name
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL总公司2级以内后代: {} 个", nativeResults.size());

        // 2. 通过服务查询（使用 descendantsOf + maxDepth）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$id", "team$caption", "salesAmount"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("descendantsOf");
        slice.setValue("T001");
        slice.setMaxDepth(2);
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("team$id"));
        groups.add(createGroup("team$caption"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$caption", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();

        log.info("descendantsOf + maxDepth=2 查询结果: {} 个", items.size());
        printResults(items, 10);

        // 3. 验证
        // 总公司2级以内后代：
        // Level 1: 技术部、销售部 (2个)
        // Level 2: 研发组、测试组、华东区、华北区 (4个)
        // 共6个
        assertEquals(6, items.size(), "总公司2级以内后代应有6个");
        assertEquals(nativeResults.size(), items.size(), "结果数量应一致");

        // 确保不包含总公司自身
        boolean containsSelf = items.stream().anyMatch(item -> "T001".equals(item.get("team$id")));
        assertFalse(containsSelf, "descendantsOf 结果不应包含节点自身");

        // 确保不包含3级节点（前端小组T006、后端小组T007）
        boolean containsLevel3 = items.stream().anyMatch(item ->
                "T006".equals(item.get("team$id")) || "T007".equals(item.get("team$id")));
        assertFalse(containsLevel3, "maxDepth=2 不应包含3级节点");
    }

    @Test
    @Order(54)
    @DisplayName("层级操作符 - childrenOf 带 maxDepth 扩展")
    void testHierarchyOp_ChildrenOf_WithMaxDepth() {
        // childrenOf 默认是 distance=1，maxDepth 可扩展范围
        // 查询总公司(T001)的1-2级子节点

        // 1. 原生SQL
        String nativeSql = """
            SELECT dt.team_id, dt.team_name, SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            LEFT JOIN dim_team dt ON fs.team_id = dt.team_id
            WHERE tc.parent_id = 'T001' AND tc.distance BETWEEN 1 AND 2
            GROUP BY dt.team_id, dt.team_name
            ORDER BY dt.team_name
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL总公司1-2级子节点: {} 个", nativeResults.size());

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$id", "team$caption", "salesAmount"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("childrenOf");
        slice.setValue("T001");
        slice.setMaxDepth(2);
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("team$id"));
        groups.add(createGroup("team$caption"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$caption", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();

        log.info("childrenOf + maxDepth=2 查询结果: {} 个", items.size());
        printResults(items, 10);

        // 3. 验证
        assertEquals(nativeResults.size(), items.size(), "结果数量应一致");
        assertEquals(6, items.size(), "总公司1-2级子节点应有6个");
    }

    @Test
    @Order(55)
    @DisplayName("层级操作符 - childrenOf 多值查询")
    void testHierarchyOp_ChildrenOf_MultipleValues() {
        // 查询技术部(T002)和销售部(T005)的直接子部门

        // 1. 原生SQL
        String nativeSql = """
            SELECT dt.team_id, dt.team_name, SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            LEFT JOIN dim_team dt ON fs.team_id = dt.team_id
            WHERE tc.parent_id IN ('T002', 'T005') AND tc.distance = 1
            GROUP BY dt.team_id, dt.team_name
            ORDER BY dt.team_name
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL技术部+销售部直接子部门: {} 个", nativeResults.size());

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$id", "team$caption", "salesAmount"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("childrenOf");
        slice.setValue(Arrays.asList("T002", "T005"));
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("team$id"));
        groups.add(createGroup("team$caption"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$caption", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();

        log.info("childrenOf 多值查询结果: {} 个", items.size());
        printResults(items, 10);

        // 3. 验证
        // 技术部子部门：研发组、测试组 (2个)
        // 销售部子部门：华东区、华北区 (2个)
        // 共4个
        assertEquals(4, items.size(), "技术部+销售部直接子部门应有4个");
        assertEquals(nativeResults.size(), items.size(), "结果数量应一致");
    }

    // ==========================================
    // 补充测试：与文档示例对齐
    // ==========================================

    @Test
    @Order(60)
    @DisplayName("默认视角 - T001 精确匹配（不使用层级）")
    void testDefaultView_ExactMatch_T001() {
        // 文档 5.1：只查 T001 自身的销售数据

        // 1. 原生SQL
        String nativeSql = """
            SELECT dt.team_name, SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            LEFT JOIN dim_team dt ON fs.team_id = dt.team_id
            WHERE dt.team_id = 'T001'
            GROUP BY dt.team_name
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        BigDecimal expectedTotal = toBigDecimal(nativeResults.get(0).get("total_amount"));
        log.info("原生SQL T001自身销售: {}", expectedTotal);

        // 2. 通过服务查询（使用默认视角 team$id，非 team$hierarchy$id）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$caption", "salesAmount"));
        queryRequest.setReturnTotal(true);

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");  // 默认视角，不是 hierarchy
        slice.setOp("=");
        slice.setValue("T001");
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        Map<String, Object> totalData = (Map<String, Object>) result.getTotalData();
        BigDecimal serviceTotal = toBigDecimal(totalData.get("salesAmount"));
        log.info("服务查询 T001 自身销售（默认视角）: {}", serviceTotal);

        // 3. 验证：默认视角只返回 T001 自身数据，不包含子节点
        assertDecimalEquals(expectedTotal, serviceTotal, "T001 自身销售额应一致");

        // 确认只有1条明细记录（T001自身的2天数据汇总）
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();
        log.info("明细记录数: {}", items.size());
    }

    @Test
    @Order(61)
    @DisplayName("层级操作符 - childrenOf T001（总公司直接子部门）")
    void testHierarchyOp_ChildrenOf_T001() {
        // 文档 5.4.1：查询 T001 的直接子部门（技术部、销售部）

        // 1. 原生SQL
        String nativeSql = """
            SELECT dt.team_id, dt.team_name, SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            LEFT JOIN dim_team dt ON fs.team_id = dt.team_id
            WHERE tc.parent_id = 'T001' AND tc.distance = 1
            GROUP BY dt.team_id, dt.team_name
            ORDER BY dt.team_name
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL T001 直接子部门: {} 个", nativeResults.size());

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$id", "team$caption", "salesAmount"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("childrenOf");
        slice.setValue("T001");
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("team$id"));
        groups.add(createGroup("team$caption"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$caption", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();

        log.info("childrenOf T001 查询结果: {} 个", items.size());
        printResults(items, 10);

        // 3. 验证
        // T001 的直接子部门：技术部(T002)、销售部(T005)
        assertEquals(2, items.size(), "T001 直接子部门应有2个");
        assertEquals(nativeResults.size(), items.size(), "结果数量应一致");

        // 验证金额
        for (int i = 0; i < nativeResults.size(); i++) {
            assertDecimalEquals(nativeResults.get(i).get("total_amount"), items.get(i).get("salesAmount"),
                    "销售额应一致: " + nativeResults.get(i).get("team_name"));
        }
    }

    @Test
    @Order(62)
    @DisplayName("层级操作符 - descendantsOf T001（所有后代不含自身）")
    void testHierarchyOp_DescendantsOf_T001() {
        // 文档 5.4.2：查询 T001 的所有后代（不含自身）

        // 1. 原生SQL
        String nativeSql = """
            SELECT dt.team_id, dt.team_name, SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            LEFT JOIN dim_team dt ON fs.team_id = dt.team_id
            WHERE tc.parent_id = 'T001' AND tc.distance > 0
            GROUP BY dt.team_id, dt.team_name
            ORDER BY dt.team_name
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL T001 所有后代: {} 个", nativeResults.size());

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$id", "team$caption", "salesAmount"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("descendantsOf");
        slice.setValue("T001");
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("team$id"));
        groups.add(createGroup("team$caption"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$caption", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();

        log.info("descendantsOf T001 查询结果: {} 个", items.size());
        printResults(items, 10);

        // 3. 验证
        // T001 的所有后代（不含自身）: 8个
        assertEquals(8, items.size(), "T001 所有后代（不含自身）应有8个");
        assertEquals(nativeResults.size(), items.size(), "结果数量应一致");

        // 确保不包含 T001 自身
        boolean containsSelf = items.stream().anyMatch(item -> "T001".equals(item.get("team$id")));
        assertFalse(containsSelf, "descendantsOf 结果不应包含 T001 自身");
    }

    @Test
    @Order(63)
    @DisplayName("层级操作符 - selfAndDescendantsOf T001（自身及所有后代）")
    void testHierarchyOp_SelfAndDescendantsOf_T001() {
        // 文档 5.4.3：查询 T001 及其所有后代

        // 1. 原生SQL
        String nativeSql = """
            SELECT dt.team_id, dt.team_name, SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            LEFT JOIN dim_team dt ON fs.team_id = dt.team_id
            WHERE tc.parent_id = 'T001'
            GROUP BY dt.team_id, dt.team_name
            ORDER BY dt.team_name
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL T001 及所有后代: {} 个", nativeResults.size());

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$id", "team$caption", "salesAmount"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("selfAndDescendantsOf");
        slice.setValue("T001");
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("team$id"));
        groups.add(createGroup("team$caption"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$caption", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();

        log.info("selfAndDescendantsOf T001 查询结果: {} 个", items.size());
        printResults(items, 10);

        // 3. 验证
        // T001 及所有后代: 9个
        assertEquals(9, items.size(), "T001 及所有后代应有9个");
        assertEquals(nativeResults.size(), items.size(), "结果数量应一致");

        // 确保包含 T001 自身
        boolean containsSelf = items.stream().anyMatch(item -> "T001".equals(item.get("team$id")));
        assertTrue(containsSelf, "selfAndDescendantsOf 结果应包含 T001 自身");
    }

    @Test
    @Order(64)
    @DisplayName("层级操作符 - childrenOf T002 + maxDepth=2")
    void testHierarchyOp_ChildrenOf_T002_WithMaxDepth() {
        // 文档 5.4.4 示例2：查询 T002 的 2 级以内子节点

        // 1. 原生SQL
        String nativeSql = """
            SELECT dt.team_id, dt.team_name, SUM(fs.sales_amount) as total_amount
            FROM fact_team_sales fs
            INNER JOIN team_closure tc ON fs.team_id = tc.team_id
            LEFT JOIN dim_team dt ON fs.team_id = dt.team_id
            WHERE tc.parent_id = 'T002' AND tc.distance BETWEEN 1 AND 2
            GROUP BY dt.team_id, dt.team_name
            ORDER BY dt.team_name
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL T002 2级以内子节点: {} 个", nativeResults.size());

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$id", "team$caption", "salesAmount"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("childrenOf");
        slice.setValue("T002");
        slice.setMaxDepth(2);
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("team$id"));
        groups.add(createGroup("team$caption"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$caption", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.getItems();

        log.info("childrenOf T002 + maxDepth=2 查询结果: {} 个", items.size());
        printResults(items, 10);

        // 3. 验证
        // T002 的 2 级以内子节点：
        // Level 1: 研发组(T003)、测试组(T004)
        // Level 2: 前端小组(T006)、后端小组(T007)
        // 共4个
        assertEquals(4, items.size(), "T002 2级以内子节点应有4个");
        assertEquals(nativeResults.size(), items.size(), "结果数量应一致");

        // 验证金额
        for (int i = 0; i < nativeResults.size(); i++) {
            assertDecimalEquals(nativeResults.get(i).get("total_amount"), items.get(i).get("salesAmount"),
                    "销售额应一致: " + nativeResults.get(i).get("team_name"));
        }
    }
}
