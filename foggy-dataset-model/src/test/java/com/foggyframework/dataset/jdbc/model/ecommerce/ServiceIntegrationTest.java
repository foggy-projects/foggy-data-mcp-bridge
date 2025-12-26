package com.foggyframework.dataset.jdbc.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.common.result.KpiItem;
import com.foggyframework.dataset.jdbc.model.def.query.request.*;
import com.foggyframework.dataset.jdbc.model.service.JdbcService;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务层集成测试
 *
 * <p>通过 JdbcService 进行端到端测试，验证整个查询链路：
 * 请求解析 -> SQL生成 -> SQL执行 -> 结果转换</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("服务层集成测试 - JdbcService端到端")
class ServiceIntegrationTest extends EcommerceTestSupport {

    @Resource
    private JdbcService jdbcService;

    // ==========================================
    // 明细查询测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("明细查询 - 订单列表")
    void testDetailQuery_OrderList() {
        // 1. 原生SQL直查
        String nativeSql = """
            SELECT COUNT(*) as cnt FROM fact_order
            """;
        Long nativeCount = executeQueryForObject(nativeSql, Long.class);
        log.info("原生SQL订单总数: {}", nativeCount);

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderStatus", "totalAmount", "orderPayAmount"));
        queryRequest.setReturnTotal(true);
        // SQLite轻量测试数据只有10条，动态调整期望值
        int expectedSize = isLightweightMode() ? nativeCount.intValue() : 100;
        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, expectedSize);

        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("服务查询结果: {} 条, 总数: {}", result.getItems().size(), result.getTotal());

        // 3. 验证
        assertNotNull(result, "查询结果不应为空");
        assertEquals(expectedSize, result.getItems().size(), "返回条数应为" + expectedSize);
        assertTrue(result.getTotal() > 0, "总数应大于0");

        // 验证字段存在
        Map<String, Object> firstRow = (Map<String, Object>) result.getItems().get(0);
        assertTrue(firstRow.containsKey("orderId"), "应包含orderId字段");
        assertTrue(firstRow.containsKey("orderStatus"), "应包含orderStatus字段");
        assertTrue(firstRow.containsKey("totalAmount"), "应包含totalAmount字段");
        assertTrue(firstRow.containsKey("orderPayAmount"), "应包含orderPayAmount字段");
    }

    @Test
    @Order(2)
    @DisplayName("明细查询 - 带维度字段")
    void testDetailQuery_WithDimensionFields() {
        // 1. 原生SQL直查 (带维度)
        String nativeSql = """
            SELECT
                fo.order_id,
                dc.customer_name,
                dc.customer_type,
                dch.channel_name,
                fo.total_amount
            FROM fact_order fo
            LEFT JOIN dim_customer dc ON fo.customer_key = dc.customer_key
            LEFT JOIN dim_channel dch ON fo.channel_key = dch.channel_key
            ORDER BY fo.order_id
            LIMIT 10
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList(
            "orderId",
            "customer$caption",
            "customer$customerType",
            "channel$caption",
            "totalAmount"
        ));

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId");
        order.setOrder("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);

        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = result.getItems();

        log.info("服务查询结果: {} 条", items.size());

        // 3. 对比
        assertEquals(nativeResults.size(), items.size(), "结果数量应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> serviceRow = items.get(i);

            assertEquals(nativeRow.get("order_id"), serviceRow.get("orderId"),
                "订单ID应一致: 行 " + i);
            assertEquals(nativeRow.get("customer_name"), serviceRow.get("customer$caption"),
                "客户名称应一致: 行 " + i);
            assertEquals(nativeRow.get("customer_type"), serviceRow.get("customer$customerType"),
                "客户类型应一致: 行 " + i);
            assertEquals(nativeRow.get("channel_name"), serviceRow.get("channel$caption"),
                "渠道名称应一致: 行 " + i);
        }
    }

    @Test
    @Order(3)
    @DisplayName("明细查询 - 带条件过滤")
    void testDetailQuery_WithFilter() {
        String status = "COMPLETED";

        // 1. 原生SQL直查
        String nativeSql = """
            SELECT COUNT(*) as cnt
            FROM fact_order
            WHERE order_status = ?
            """;
        Long nativeCount = jdbcTemplate.queryForObject(nativeSql, Long.class, status);
        log.info("原生SQL COMPLETED订单数: {}", nativeCount);

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "orderStatus", "totalAmount"));
        queryRequest.setReturnTotal(true);

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("orderStatus");
        slice.setOp("=");
        slice.setValue(status);
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);

        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("服务查询结果: 总数 {}", result.getTotal());

        // 3. 验证
        assertEquals(nativeCount.intValue(), result.getTotal(), "COMPLETED订单总数应一致");

        // 验证返回的数据都是COMPLETED状态
        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            assertEquals(status, row.get("orderStatus"), "所有返回数据的状态应为COMPLETED");
        }
    }

    // ==========================================
    // 分组汇总查询测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("分组汇总 - 按客户类型")
    void testGroupQuery_ByCustomerType() {
        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                dc.customer_type,
                SUM(fo.total_amount) as total_amount,
                SUM(fo.pay_amount) as pay_amount,
                COUNT(*) as order_count
            FROM fact_order fo
            LEFT JOIN dim_customer dc ON fo.customer_key = dc.customer_key
            GROUP BY dc.customer_type
            ORDER BY dc.customer_type
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL结果: {} 组", nativeResults.size());

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("customer$customerType", "totalAmount", "orderPayAmount"));

        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("customer$customerType");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("customer$customerType");
        order.setOrder("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 100);

        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = result.getItems();

        log.info("服务查询结果: {} 组", items.size());

        // 3. 对比
        assertEquals(nativeResults.size(), items.size(), "分组数量应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> serviceRow = items.get(i);

            assertEquals(nativeRow.get("customer_type"), serviceRow.get("customer$customerType"),
                "客户类型应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("total_amount"), serviceRow.get("totalAmount"),
                "订单总额应一致: " + nativeRow.get("customer_type"));
            assertDecimalEquals(nativeRow.get("pay_amount"), serviceRow.get("orderPayAmount"),
                "应付金额应一致: " + nativeRow.get("customer_type"));
        }
    }

    @Test
    @Order(11)
    @DisplayName("分组汇总 - 多维度分组")
    void testGroupQuery_MultiDimension() {
        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                dd.year,
                dc.customer_type,
                SUM(fo.total_amount) as total_amount,
                COUNT(*) as order_count
            FROM fact_order fo
            LEFT JOIN dim_date dd ON fo.date_key = dd.date_key
            LEFT JOIN dim_customer dc ON fo.customer_key = dc.customer_key
            GROUP BY dd.year, dc.customer_type
            ORDER BY dd.year DESC, dc.customer_type ASC
            """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL结果: {} 组", nativeResults.size());

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("orderDate$year", "customer$customerType", "totalAmount"));

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("orderDate$year"));
        groups.add(createGroup("customer$customerType"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("orderDate$year", "DESC"));
        orders.add(createOrder("customer$customerType", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 100);

        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = result.getItems();

        log.info("服务查询结果: {} 组", items.size());

        // 3. 对比
        assertEquals(nativeResults.size(), items.size(), "分组数量应一致");

        for (int i = 0; i < Math.min(10, nativeResults.size()); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> serviceRow = items.get(i);

            assertEquals(toInt(nativeRow.get("year")), toInt(serviceRow.get("orderDate$year")),
                "年份应一致: 行 " + i);
            assertEquals(nativeRow.get("customer_type"), serviceRow.get("customer$customerType"),
                "客户类型应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("total_amount"), serviceRow.get("totalAmount"),
                "订单总额应一致: 行 " + i);
        }
    }

    @Test
    @Order(12)
    @DisplayName("分组汇总 - 带条件过滤")
    void testGroupQuery_WithFilter() {
        List<String> statuses = Arrays.asList("COMPLETED", "SHIPPED");

        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                dc.customer_type,
                SUM(fo.total_amount) as total_amount
            FROM fact_order fo
            LEFT JOIN dim_customer dc ON fo.customer_key = dc.customer_key
            WHERE fo.order_status IN (?, ?)
            GROUP BY dc.customer_type
            ORDER BY dc.customer_type
            """;
        List<Map<String, Object>> nativeResults = jdbcTemplate.queryForList(nativeSql, statuses.toArray());
        log.info("原生SQL结果: {} 组", nativeResults.size());

        // 2. 通过服务查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("customer$customerType", "totalAmount"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("orderStatus");
        slice.setOp("in");
        slice.setValue(statuses);
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("customer$customerType"));
        queryRequest.setGroupBy(groups);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("customer$customerType", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 100);

        PagingResultImpl result = jdbcService.queryModelData(form);
        List<Map<String, Object>> items = result.getItems();

        log.info("服务查询结果: {} 组", items.size());

        // 3. 对比
        assertEquals(nativeResults.size(), items.size(), "分组数量应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> serviceRow = items.get(i);

            assertEquals(nativeRow.get("customer_type"), serviceRow.get("customer$customerType"),
                "客户类型应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("total_amount"), serviceRow.get("totalAmount"),
                "订单总额应一致: " + nativeRow.get("customer_type"));
        }
    }


    // ==========================================
    // 分页测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("分页查询 - 验证分页正确性")
    void testPagination() {
        // SQLite轻量测试数据只有10条订单，调整pageSize为3以验证分页
        int pageSize = isLightweightMode() ? 3 : 10;

        // 1. 查询第一页
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "totalAmount"));
        queryRequest.setReturnTotal(true);

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("orderId", "ASC"));
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form1 = new PagingRequest<>(1, pageSize, 0, pageSize, queryRequest);
        PagingResultImpl result1 = jdbcService.queryModelData(form1);

        // 2. 查询第二页
        PagingRequest<DbQueryRequestDef> form2 = new PagingRequest<>(2, pageSize, pageSize, pageSize, queryRequest);
        PagingResultImpl result2 = jdbcService.queryModelData(form2);

        log.info("第一页: {} 条, 第二页: {} 条, 总数: {}, pageSize: {}",
            result1.getItems().size(), result2.getItems().size(), result1.getTotal(), pageSize);

        // 3. 验证
        assertEquals(pageSize, result1.getItems().size(), "第一页应返回" + pageSize + "条");
        assertEquals(pageSize, result2.getItems().size(), "第二页应返回" + pageSize + "条");
        assertEquals(result1.getTotal(), result2.getTotal(), "两页的总数应一致");

        // 验证第一页和第二页数据不重复
        Map<String, Object> lastOfPage1 = (Map<String, Object>) result1.getItems().get(pageSize - 1);
        Map<String, Object> firstOfPage2 = (Map<String, Object>) result2.getItems().get(0);
        assertNotEquals(lastOfPage1.get("orderId"), firstOfPage2.get("orderId"), "两页数据不应重复");

        // 验证排序正确 (第一页最后一条的orderId < 第二页第一条的orderId)
        String lastId = (String) lastOfPage1.get("orderId");
        String firstId = (String) firstOfPage2.get("orderId");
        assertTrue(lastId.compareTo(firstId) < 0, "排序应正确: " + lastId + " < " + firstId);
    }

    @Test
    @Order(31)
    @DisplayName("分页查询 - totalColumn返回汇总数据")
    void testPagination_WithTotalColumn() {
        // 1. 原生SQL直查汇总
        String nativeSql = """
            SELECT
                SUM(total_amount) as total_amount
            FROM fact_order
            """;
        Map<String, Object> nativeSummary = jdbcTemplate.queryForMap(nativeSql);

        // 2. 通过服务查询（启用totalColumn）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactOrderQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "totalAmount"));
        queryRequest.setReturnTotal(true);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);

        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("返回条数: {}, 总数: {}, 汇总数据: {}",
            result.getItems().size(), result.getTotal(), result.getTotalData());

        // 3. 验证
        assertNotNull(result.getTotalData(), "应返回汇总数据");
        assertTrue(result.getTotalData() instanceof Map, "汇总数据应为Map类型");

        Map<String, Object> totalData = (Map<String, Object>) result.getTotalData();
        assertDecimalEquals(nativeSummary.get("total_amount"), totalData.get("totalAmount"), "汇总金额应一致");
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    /**
     * 将KpiItems列表转换为Map便于对比
     */
    private Map<String, Object> kpiItemsToMap(List<KpiItem> kpiItems) {
        Map<String, Object> map = new HashMap<>();
        for (KpiItem item : kpiItems) {
            map.put(item.getName(), item.getValue());
        }
        return map;
    }

    /**
     * 比较两个数值是否相等（支持不同数值类型）
     */
    private void assertDecimalEquals(Object expected, Object actual, String message) {
        BigDecimal expectedDecimal = toBigDecimal(expected);
        BigDecimal actualDecimal = toBigDecimal(actual);

        if (expectedDecimal == null && actualDecimal == null) {
            return;
        }

        assertNotNull(expectedDecimal, message + " - 期望值不应为null");
        assertNotNull(actualDecimal, message + " - 实际值不应为null");

        assertEquals(
            expectedDecimal.setScale(2, RoundingMode.HALF_UP),
            actualDecimal.setScale(2, RoundingMode.HALF_UP),
            message
        );
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return new BigDecimal(value.toString());
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
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
}
