package com.foggyframework.dataset.jdbc.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.def.query.request.*;
import com.foggyframework.dataset.jdbc.model.impl.dimension.JdbcModelParentChildDimensionImpl;
import com.foggyframework.dataset.jdbc.model.service.JdbcService;
import com.foggyframework.dataset.jdbc.model.spi.JdbcDimension;
import com.foggyframework.dataset.jdbc.model.spi.JdbcModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

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
        BigDecimal totalAmount = executeQueryForObject(totalSql, BigDecimal.class);
        log.info("销售总额: {}", totalAmount);
        assertTrue(totalAmount.compareTo(BigDecimal.ZERO) > 0, "销售总额应大于0");
    }

    // ==========================================
    // 模型加载测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("验证父子维度模型加载")
    void testParentChildDimensionModelLoad() {
        // 获取模型
        JdbcModel model = tableModelLoaderManager.getJdbcModel("FactTeamSalesModel");
        assertNotNull(model, "模型应成功加载");
        log.info("加载模型: {}", model.getName());

        // 验证team维度是父子维度
        JdbcDimension teamDimension = model.findJdbcDimensionByName("team");
        assertNotNull(teamDimension, "应存在team维度");

        JdbcModelParentChildDimensionImpl parentChildDim = teamDimension.getDecorate(JdbcModelParentChildDimensionImpl.class);
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
        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "team$caption",
                "team$teamLevel",
                "date$caption",
                "avgSalesAmount",
                "totalSalesCount"
        ));

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$teamLevel", "ASC"));
        orders.add(createOrder("team$caption", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
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
        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("totalSalesAmount", "totalSalesCount"));
        queryRequest.setReturnTotal(true);  // 启用汇总数据返回

        // 过滤条件：团队 = T001（总公司）
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("in");
        slice.setValue(Arrays.asList("T001"));
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        // items返回的是明细数据（分页后的数据）
        log.info("明细数据条数: {}", result.getItems().size());

        // 3. 从totalData获取聚合数据
        assertNotNull(result.getTotalData(), "应返回汇总数据（需设置totalColumn=true）");
        assertTrue(result.getTotalData() instanceof Map, "汇总数据应为Map类型");

        Map<String, Object> totalData = (Map<String, Object>) result.getTotalData();
        BigDecimal serviceTotal = toBigDecimal(totalData.get("totalSalesAmount"));
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
        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("totalSalesAmount"));
        queryRequest.setReturnTotal(true);  // 启用汇总数据返回

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("in");
        slice.setValue(Arrays.asList("T002"));
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        // 从totalData获取聚合数据
        assertNotNull(result.getTotalData(), "应返回汇总数据");
        Map<String, Object> totalData = (Map<String, Object>) result.getTotalData();
        BigDecimal serviceTotal = toBigDecimal(totalData.get("totalSalesAmount"));
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
        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("totalSalesAmount"));
        queryRequest.setReturnTotal(true);  // 启用汇总数据返回

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("in");
        slice.setValue(Arrays.asList("T006"));
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        // 从totalData获取聚合数据
        assertNotNull(result.getTotalData(), "应返回汇总数据");
        Map<String, Object> totalData = (Map<String, Object>) result.getTotalData();
        BigDecimal serviceTotal = toBigDecimal(totalData.get("totalSalesAmount"));
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
        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("totalSalesAmount"));
        queryRequest.setReturnTotal(true);  // 启用汇总数据返回

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
        slice.setOp("in");
        slice.setValue(Arrays.asList("T002", "T005"));
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        // 从totalData获取聚合数据
        assertNotNull(result.getTotalData(), "应返回汇总数据");
        Map<String, Object> totalData = (Map<String, Object>) result.getTotalData();
        BigDecimal serviceTotal = toBigDecimal(totalData.get("totalSalesAmount"));
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
        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$teamLevel", "totalSalesAmount", "recordCount"));

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("team$teamLevel"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("team$teamLevel", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
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
            assertDecimalEquals(nativeRow.get("total_amount"), serviceRow.get("totalSalesAmount"),
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
        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("team$id", "team$caption", "totalSalesAmount"));

        // 过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("team$id");
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
        orders.add(createOrder("team$caption", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
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
        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactTeamSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("date$caption", "team$teamLevel", "totalSalesAmount"));

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("date$caption"));
        groups.add(createGroup("team$teamLevel"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("date$caption", "ASC"));
        orders.add(createOrder("team$teamLevel", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
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
}
