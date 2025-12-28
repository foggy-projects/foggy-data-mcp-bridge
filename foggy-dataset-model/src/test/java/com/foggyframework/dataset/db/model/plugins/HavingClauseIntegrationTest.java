package com.foggyframework.dataset.db.model.plugins;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HAVING子句集成测试
 * <p>
 * 测试场景：
 * 1. 纯WHERE条件（普通字段过滤）
 * 2. 纯HAVING条件（聚合字段过滤）
 * 3. 混合WHERE和HAVING条件
 * </p>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("HAVING子句集成测试")
class HavingClauseIntegrationTest extends EcommerceTestSupport {

    @Resource
    private QueryFacade queryFacade;

    @Test
    @Order(1)
    @DisplayName("纯WHERE条件 - 普通维度字段过滤")
    void testPureWhereCondition() {
        // 测试：只有WHERE条件，没有HAVING条件
        // SQL: SELECT ... WHERE customer_type = '个人' GROUP BY ...

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactOrderQueryModel");
        request.setAutoGroupBy(true);
        request.setColumns(Arrays.asList(
                "customer$customerType",
                "sum(amount) as totalAmount"
        ));

        // WHERE条件：customer_type = 'VIP'
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("customer$customerType");
        slice.setOp("=");
        slice.setValue("VIP");
        slices.add(slice);
        request.setSlice(slices);

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(request, 100));
        List<Map<String, Object>> items = result.getItems();

        log.info("纯WHERE条件查询结果: {} 条", items.size());

        // 验证：所有结果的customerType应该都是"VIP"
        assertTrue(items.size() > 0, "应该有查询结果");
        for (Map<String, Object> item : items) {
            assertEquals("VIP", item.get("customer$customerType"),
                    "所有记录的客户类型应该是'VIP'");
            assertNotNull(item.get("totalAmount"), "应该有总金额");
        }
    }

    @Test
    @Order(2)
    @DisplayName("纯HAVING条件 - 聚合字段过滤")
    void testPureHavingCondition() {
        // 测试：只有HAVING条件，没有WHERE条件
        // SQL: SELECT ... GROUP BY ... HAVING SUM(amount) > 1000

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactOrderQueryModel");
        request.setAutoGroupBy(true);
        request.setColumns(Arrays.asList(
                "customer$customerType",
                "sum(amount) as totalAmount"
        ));

        // HAVING条件：totalAmount > 1000
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("totalAmount");
        slice.setOp(">");
        slice.setValue(1000);
        slices.add(slice);
        request.setSlice(slices);

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(request, 100));
        List<Map<String, Object>> items = result.getItems();

        log.info("纯HAVING条件查询结果: {} 条", items.size());

        // 验证：所有结果的totalAmount应该> 1000
        assertTrue(items.size() > 0, "应该有查询结果");
        for (Map<String, Object> item : items) {
            BigDecimal totalAmount = toBigDecimal(item.get("totalAmount"));
            assertTrue(totalAmount.compareTo(BigDecimal.valueOf(1000)) > 0,
                    "所有记录的总金额应该 > 1000，实际: " + totalAmount);
        }
    }

    @Test
    @Order(3)
    @DisplayName("混合WHERE和HAVING条件")
    void testMixedWhereAndHaving() {
        // 测试：同时包含WHERE条件和HAVING条件
        // SQL: SELECT ... WHERE customer_type = 'NORMAL'
        //      GROUP BY ... HAVING SUM(amount) > 1000

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactOrderQueryModel");
        request.setAutoGroupBy(true);
        request.setColumns(Arrays.asList(
                "customer$customerType",
                "sum(amount) as totalAmount"
        ));

        // 混合条件：WHERE (customer_type = 'NORMAL') AND HAVING (totalAmount > 1000)
        List<SliceRequestDef> slices = new ArrayList<>();

        // WHERE条件
        SliceRequestDef whereSlice = new SliceRequestDef();
        whereSlice.setField("customer$customerType");
        whereSlice.setOp("=");
        whereSlice.setValue("NORMAL");
        slices.add(whereSlice);

        // HAVING条件
        SliceRequestDef havingSlice = new SliceRequestDef();
        havingSlice.setField("totalAmount");
        havingSlice.setOp(">");
        havingSlice.setValue(1000);
        slices.add(havingSlice);

        request.setSlice(slices);

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(request, 100));
        List<Map<String, Object>> items = result.getItems();

        log.info("混合WHERE+HAVING条件查询结果: {} 条", items.size());

        // 验证：应该满足两个条件
        assertTrue(items.size() > 0, "应该有查询结果");
        for (Map<String, Object> item : items) {
            // WHERE条件验证
            assertEquals("NORMAL", item.get("customer$customerType"),
                    "所有记录的客户类型应该是'NORMAL'");

            // HAVING条件验证
            BigDecimal totalAmount = toBigDecimal(item.get("totalAmount"));
            assertTrue(totalAmount.compareTo(BigDecimal.valueOf(1000)) > 0,
                    "所有记录的总金额应该 > 1000，实际: " + totalAmount);
        }
    }

    @Test
    @Order(4)
    @DisplayName("验证SQL生成 - 应包含HAVING子句")
    void testSqlGenerationWithHaving() {
        // 测试：验证生成的SQL确实包含HAVING子句

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactOrderQueryModel");
        request.setAutoGroupBy(true);
        request.setColumns(Arrays.asList(
                "customer$customerType",
                "sum(amount) as totalAmount"
        ));

        // HAVING条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("totalAmount");
        slice.setOp(">=");
        slice.setValue(500);
        slices.add(slice);
        request.setSlice(slices);

        try {
            PagingResultImpl result = queryFacade.queryModelData(
                    PagingRequest.buildPagingRequest(request, 10));

            // 如果能成功执行，说明SQL语法正确
            assertNotNull(result, "查询应该成功执行");
            log.info("HAVING子句SQL验证通过，结果: {} 条", result.getItems().size());
        } catch (Exception e) {
            fail("查询不应该失败，可能SQL语法错误: " + e.getMessage());
        }
    }

    @Test
    @Order(5)
    @DisplayName("对比验证 - 与原生SQL结果一致")
    void testHavingWithNativeSQL() {
        // 测试：HAVING条件查询结果应该与原生SQL一致

        // 1. 使用原生SQL
        String nativeSql = """
                SELECT
                    dc.customer_type,
                    SUM(fo.total_amount) as sum_amount
                FROM fact_order fo
                LEFT JOIN dim_customer dc ON fo.customer_key = dc.customer_key
                WHERE dc.customer_type = 'VIP'
                GROUP BY dc.customer_type
                HAVING SUM(fo.total_amount) > 2000
                """;

        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生SQL查询结果: {} 条", nativeResults.size());

        // 2. 使用QueryModel
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactOrderQueryModel");
        request.setAutoGroupBy(true);
        request.setColumns(Arrays.asList(
                "customer$customerType",
                "sum(amount) as totalAmount"
        ));

        List<SliceRequestDef> slices = new ArrayList<>();

        // WHERE条件
        SliceRequestDef whereSlice = new SliceRequestDef();
        whereSlice.setField("customer$customerType");
        whereSlice.setOp("=");
        whereSlice.setValue("VIP");
        slices.add(whereSlice);

        // HAVING条件
        SliceRequestDef havingSlice = new SliceRequestDef();
        havingSlice.setField("totalAmount");
        havingSlice.setOp(">");
        havingSlice.setValue(2000);
        slices.add(havingSlice);

        request.setSlice(slices);

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(request, 100));
        List<Map<String, Object>> items = result.getItems();

        log.info("QueryModel查询结果: {} 条", items.size());

        // 3. 验证结果一致
        assertEquals(nativeResults.size(), items.size(),
                "QueryModel和原生SQL的结果行数应该一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> queryRow = items.get(i);

            assertEquals(nativeRow.get("customer_type"), queryRow.get("customer$customerType"),
                    "客户类型应该一致");

            BigDecimal nativeAmount = toBigDecimal(nativeRow.get("sum_amount"));
            BigDecimal queryAmount = toBigDecimal(queryRow.get("totalAmount"));

            assertEquals(0, nativeAmount.compareTo(queryAmount),
                    "总金额应该一致: native=" + nativeAmount + ", query=" + queryAmount);
        }
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return new BigDecimal(value.toString());
    }
}
