package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 字段比较测试
 *
 * <p>测试 $field 引用和 $expr 表达式两种字段间比较语法</p>
 * <ul>
 *   <li>$field 引用：{@code {"field": "salesAmount", "op": ">", "value": {"$field": "costAmount"}}}</li>
 *   <li>$expr 表达式：{@code {"$expr": "salesAmount > costAmount"}}</li>
 * </ul>
 *
 * @author foggy-framework
 * @since 8.3.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("字段比较测试 - $field 引用和 $expr 表达式")
class FieldComparisonTest extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    // ==========================================
    // $field 引用测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("$field 引用 - 简单字段比较 (salesAmount > costAmount)")
    void testFieldReference_SimpleComparison() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "costAmount",
                "profitAmount"
        ));

        // 使用 $field 引用: salesAmount > costAmount
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesAmount");
        slice.setOp(">");
        // 使用 $field 引用另一个字段
        Map<String, Object> fieldRef = new HashMap<>();
        fieldRef.put("$field", "costAmount");
        slice.setValue(fieldRef);
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 添加排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId");
        order.setDir("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        printSql(sql, "$field 引用 - salesAmount > costAmount");

        // 验证 SQL 包含字段比较
        String lowerSql = sql.toLowerCase();
        assertTrue(lowerSql.contains("sales_amount") && lowerSql.contains("cost_amount"),
                "SQL应包含 sales_amount 和 cost_amount 字段");

        // 执行查询验证
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(paginateSql(sql, 10));
            log.info("查询结果数量: {}", results.size());
            printResults(results);

            // 验证结果: 每行的 salesAmount 应该 > costAmount
            for (Map<String, Object> row : results) {
                BigDecimal salesAmount = toBigDecimal(row.get("salesAmount"));
                BigDecimal costAmount = toBigDecimal(row.get("costAmount"));
                assertTrue(salesAmount.compareTo(costAmount) > 0,
                        String.format("销售金额(%s)应大于成本金额(%s)", salesAmount, costAmount));
            }
        } catch (Exception e) {
            log.error("SQL执行失败: {}", e.getMessage(), e);
            fail("SQL应能正确执行: " + e.getMessage());
        }
    }

    @Test
    @Order(2)
    @DisplayName("$field 引用 - 不同操作符 (salesAmount >= costAmount)")
    void testFieldReference_GreaterOrEqual() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "salesAmount", "costAmount"));

        // 使用 >=
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesAmount");
        slice.setOp(">=");
        Map<String, Object> fieldRef = new HashMap<>();
        fieldRef.put("$field", "costAmount");
        slice.setValue(fieldRef);
        slices.add(slice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        printSql(sql, "$field 引用 - salesAmount >= costAmount");

        // 执行查询验证
        List<Map<String, Object>> results = jdbcTemplate.queryForList(paginateSql(sql, 10));
        log.info("查询结果数量: {}", results.size());

        for (Map<String, Object> row : results) {
            BigDecimal salesAmount = toBigDecimal(row.get("salesAmount"));
            BigDecimal costAmount = toBigDecimal(row.get("costAmount"));
            assertTrue(salesAmount.compareTo(costAmount) >= 0,
                    String.format("销售金额(%s)应大于或等于成本金额(%s)", salesAmount, costAmount));
        }
    }

    @Test
    @Order(3)
    @DisplayName("$field 引用 - 相等比较 (salesAmount = costAmount)")
    void testFieldReference_Equal() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "salesAmount", "costAmount"));

        // 使用 =
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesAmount");
        slice.setOp("=");
        Map<String, Object> fieldRef = new HashMap<>();
        fieldRef.put("$field", "costAmount");
        slice.setValue(fieldRef);
        slices.add(slice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        printSql(sql, "$field 引用 - salesAmount = costAmount");

        // 执行查询
        List<Map<String, Object>> results = jdbcTemplate.queryForList(paginateSql(sql, 10));
        log.info("查询结果数量: {} (可能为0，因为相等的情况可能很少)", results.size());
    }

    @Test
    @Order(4)
    @DisplayName("$field 引用 - 不等比较 (salesAmount != costAmount)")
    void testFieldReference_NotEqual() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "salesAmount", "costAmount"));

        // 使用 !=
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesAmount");
        slice.setOp("!=");
        Map<String, Object> fieldRef = new HashMap<>();
        fieldRef.put("$field", "costAmount");
        slice.setValue(fieldRef);
        slices.add(slice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        printSql(sql, "$field 引用 - salesAmount != costAmount");

        // 执行查询
        List<Map<String, Object>> results = jdbcTemplate.queryForList(paginateSql(sql, 10));
        log.info("查询结果数量: {}", results.size());
        assertTrue(results.size() > 0, "应该有不相等的记录");
    }

    @Test
    @Order(5)
    @DisplayName("$field 引用 - HAVING 聚合比较 (salesAmount > costAmount)")
    void testHavingFieldReference_AggregateComparison() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("product$categoryName", "salesAgg", "costAgg"));
        
        // Define calculated aggregate fields for HAVING to use
        queryRequest.setCalculatedFields(Arrays.asList(
            new com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef("salesAgg", "sum(salesAmount)"),
            new com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef("costAgg", "sum(costAmount)")
        ));
        
        // Group by product category
        List<com.foggyframework.dataset.model.def.query.request.GroupRequestDef> groupBy = new ArrayList<>();
        com.foggyframework.dataset.model.def.query.request.GroupRequestDef group = new com.foggyframework.dataset.model.def.query.request.GroupRequestDef();
        group.setField("product$categoryName");
        groupBy.add(group);
        queryRequest.setGroupBy(groupBy);

        // 使用 HAVING $field 引用: salesAgg > costAgg
        List<SliceRequestDef> havingConds = new ArrayList<>();
        SliceRequestDef havingCond = new SliceRequestDef();
        havingCond.setField("salesAgg");
        havingCond.setOp(">");
        Map<String, Object> fieldRef = new HashMap<>();
        fieldRef.put("$field", "costAgg");
        havingCond.setValue(fieldRef);
        havingConds.add(havingCond);
        queryRequest.setHaving(havingConds);

        // Mock ModelResultContext with ParsedInlineExpressions so isAggregateCondition works
        ModelResultContext context = new ModelResultContext();
        context.setRequest(new PagingRequest<>());
        context.getRequest().setParam(queryRequest);

        ModelResultContext.ParsedInlineExpressions parsed = new ModelResultContext.ParsedInlineExpressions();
        Map<String, String> columnAggs = new HashMap<>();
        columnAggs.put("salesAgg", "SUM");
        columnAggs.put("costAgg", "SUM");
        parsed.setColumnAggregations(columnAggs);
        parsed.setColumns(queryRequest.getColumns());
        context.setParsedInlineExpressions(parsed);

        queryEngine.analysisQueryRequest(systemBundlesContext, context);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        printSql(sql, "HAVING $field 引用 - salesAgg > costAgg");

        // 验证 SQL 包含 HAVING 子句
        String lowerSql = sql.toLowerCase();
        assertTrue(lowerSql.contains("having"), "SQL应包含 HAVING 子句");

        // 执行查询
        List<Map<String, Object>> results = jdbcTemplate.queryForList(paginateSql(sql, 10));
        log.info("查询结果数量: {}", results.size());
        
        for (Map<String, Object> row : results) {
            BigDecimal salesAgg = toBigDecimal(row.get("salesAgg"));
            BigDecimal costAgg = toBigDecimal(row.get("costAgg"));
            assertTrue(salesAgg.compareTo(costAgg) > 0,
                    String.format("聚合销售金额(%s)应大于聚合成本金额(%s)", salesAgg, costAgg));
        }
    }

    // ==========================================
    // $expr 表达式测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("$expr 表达式 - 简单字段比较")
    void testExpression_SimpleComparison() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "costAmount",
                "profitAmount"
        ));

        // 使用 $expr 表达式: salesAmount > costAmount
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setExpr("salesAmount > costAmount");
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 添加排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId");
        order.setDir("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        printSql(sql, "$expr 表达式 - salesAmount > costAmount");

        // 执行查询验证
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(paginateSql(sql, 10));
            log.info("查询结果数量: {}", results.size());
            printResults(results);

            // 验证结果
            for (Map<String, Object> row : results) {
                BigDecimal salesAmount = toBigDecimal(row.get("salesAmount"));
                BigDecimal costAmount = toBigDecimal(row.get("costAmount"));
                assertTrue(salesAmount.compareTo(costAmount) > 0,
                        String.format("销售金额(%s)应大于成本金额(%s)", salesAmount, costAmount));
            }
        } catch (Exception e) {
            log.error("SQL执行失败: {}", e.getMessage(), e);
            fail("SQL应能正确执行: " + e.getMessage());
        }
    }

    @Test
    @Order(11)
    @DisplayName("$expr 表达式 - 带运算的比较")
    void testExpression_WithArithmetic() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "costAmount"
        ));

        // 使用 $expr 表达式: salesAmount > costAmount * 1.2 (利润率超过20%)
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setExpr("salesAmount > costAmount * 1.2");
        slices.add(slice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        printSql(sql, "$expr 表达式 - salesAmount > costAmount * 1.2");

        // 执行查询验证
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(paginateSql(sql, 10));
            log.info("查询结果数量: {}", results.size());

            // 验证结果
            for (Map<String, Object> row : results) {
                BigDecimal salesAmount = toBigDecimal(row.get("salesAmount"));
                BigDecimal costAmount = toBigDecimal(row.get("costAmount"));
                BigDecimal costTimes1_2 = costAmount.multiply(new BigDecimal("1.2"));
                assertTrue(salesAmount.compareTo(costTimes1_2) > 0,
                        String.format("销售金额(%s)应大于成本金额*1.2(%s)", salesAmount, costTimes1_2));
            }
        } catch (Exception e) {
            log.error("SQL执行失败: {}", e.getMessage(), e);
            fail("SQL应能正确执行: " + e.getMessage());
        }
    }

    @Test
    @Order(12)
    @DisplayName("$expr 表达式 - 多字段运算")
    void testExpression_MultiFieldArithmetic() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "costAmount",
                "discountAmount"
        ));

        // 使用 $expr 表达式: salesAmount - discountAmount > costAmount
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setExpr("salesAmount - discountAmount > costAmount");
        slices.add(slice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        printSql(sql, "$expr 表达式 - salesAmount - discountAmount > costAmount");

        // 执行查询验证
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(paginateSql(sql, 10));
            log.info("查询结果数量: {}", results.size());

            // 验证结果
            for (Map<String, Object> row : results) {
                BigDecimal salesAmount = toBigDecimal(row.get("salesAmount"));
                BigDecimal discountAmount = toBigDecimal(row.get("discountAmount"));
                BigDecimal costAmount = toBigDecimal(row.get("costAmount"));
                BigDecimal netAmount = salesAmount.subtract(discountAmount);
                assertTrue(netAmount.compareTo(costAmount) > 0,
                        String.format("净额(%s)应大于成本金额(%s)", netAmount, costAmount));
            }
        } catch (Exception e) {
            log.error("SQL执行失败: {}", e.getMessage(), e);
            fail("SQL应能正确执行: " + e.getMessage());
        }
    }

    // ==========================================
    // 与原生SQL对比测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("结果对比 - $field 引用 vs 原生SQL")
    void testResultComparison_FieldReference() {
        // 1. 原生SQL查询
        String nativeSql = """
            SELECT order_id, sales_amount, cost_amount, profit_amount
            FROM fact_sales
            WHERE sales_amount > cost_amount
            ORDER BY order_id ASC
            """;
        List<Map<String, Object>> nativeResults = jdbcTemplate.queryForList(paginateSql(nativeSql, 10));
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询 - 使用 $field 引用
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "salesAmount", "costAmount", "profitAmount"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesAmount");
        slice.setOp(">");
        Map<String, Object> fieldRef = new HashMap<>();
        fieldRef.put("$field", "costAmount");
        slice.setValue(fieldRef);
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId");
        order.setDir("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = paginateSql(queryEngine.getSql(), 10);
        printSql(modelSql, "模型SQL ($field 引用)");

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql);
        log.info("模型查询结果: {} 条", modelResults.size());

        // 3. 对比结果
        assertEquals(nativeResults.size(), modelResults.size(), "记录数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);

            assertEquals(nativeRow.get("order_id"), modelRow.get("orderId"),
                    "订单ID应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("sales_amount"), modelRow.get("salesAmount"),
                    "销售金额应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("cost_amount"), modelRow.get("costAmount"),
                    "成本金额应一致: 行 " + i);
        }
    }

    @Test
    @Order(21)
    @DisplayName("结果对比 - $expr 表达式 vs 原生SQL")
    void testResultComparison_Expression() {
        // 1. 原生SQL查询 - 利润率超过20%
        String nativeSql = """
            SELECT order_id, sales_amount, cost_amount
            FROM fact_sales
            WHERE sales_amount > cost_amount * 1.2
            ORDER BY order_id ASC
            """;
        List<Map<String, Object>> nativeResults = jdbcTemplate.queryForList(paginateSql(nativeSql, 10));
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询 - 使用 $expr 表达式
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "salesAmount", "costAmount"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setExpr("salesAmount > costAmount * 1.2");
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId");
        order.setDir("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = paginateSql(queryEngine.getSql(), 10);
        printSql(modelSql, "模型SQL ($expr 表达式)");

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql);
        log.info("模型查询结果: {} 条", modelResults.size());

        // 3. 对比结果
        assertEquals(nativeResults.size(), modelResults.size(), "记录数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);

            assertEquals(nativeRow.get("order_id"), modelRow.get("orderId"),
                    "订单ID应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("sales_amount"), modelRow.get("salesAmount"),
                    "销售金额应一致: 行 " + i);
        }
    }

    // ==========================================
    // 组合条件测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("组合条件 - $field 引用 + 普通条件")
    void testCombination_FieldReferenceWithNormalCondition() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "salesAmount", "costAmount", "orderStatus"));

        List<SliceRequestDef> slices = new ArrayList<>();

        // 条件1: 普通条件 - 订单状态
        SliceRequestDef statusSlice = new SliceRequestDef();
        statusSlice.setField("orderStatus");
        statusSlice.setOp("=");
        statusSlice.setValue("COMPLETED");
        slices.add(statusSlice);

        // 条件2: $field 引用 - salesAmount > costAmount
        SliceRequestDef fieldSlice = new SliceRequestDef();
        fieldSlice.setField("salesAmount");
        fieldSlice.setOp(">");
        Map<String, Object> fieldRef = new HashMap<>();
        fieldRef.put("$field", "costAmount");
        fieldSlice.setValue(fieldRef);
        slices.add(fieldSlice);

        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        printSql(sql, "组合条件 - $field 引用 + 普通条件");
        log.info("参数值: {}", queryEngine.getValues());

        // 执行查询
        List<Object> values = queryEngine.getValues();
        List<Map<String, Object>> results = jdbcTemplate.queryForList(paginateSql(sql, 10), values.toArray());
        log.info("查询结果数量: {}", results.size());

        // 验证结果
        for (Map<String, Object> row : results) {
            assertEquals("COMPLETED", row.get("orderStatus"), "订单状态应为COMPLETED");
            BigDecimal salesAmount = toBigDecimal(row.get("salesAmount"));
            BigDecimal costAmount = toBigDecimal(row.get("costAmount"));
            assertTrue(salesAmount.compareTo(costAmount) > 0,
                    "销售金额应大于成本金额");
        }
    }

    @Test
    @Order(31)
    @DisplayName("组合条件 - $expr 表达式 + 普通条件")
    void testCombination_ExpressionWithNormalCondition() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "salesAmount", "costAmount", "quantity"));

        List<SliceRequestDef> slices = new ArrayList<>();

        // 条件1: 普通条件 - 数量 > 1
        SliceRequestDef qtySlice = new SliceRequestDef();
        qtySlice.setField("quantity");
        qtySlice.setOp(">");
        qtySlice.setValue(1);
        slices.add(qtySlice);

        // 条件2: $expr 表达式
        SliceRequestDef exprSlice = new SliceRequestDef();
        exprSlice.setExpr("salesAmount > costAmount * 1.1");
        slices.add(exprSlice);

        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        printSql(sql, "组合条件 - $expr 表达式 + 普通条件");
        log.info("参数值: {}", queryEngine.getValues());

        // 执行查询
        List<Object> values = queryEngine.getValues();
        List<Map<String, Object>> results = jdbcTemplate.queryForList(paginateSql(sql, 10), values.toArray());
        log.info("查询结果数量: {}", results.size());

        // 验证结果
        for (Map<String, Object> row : results) {
            Integer quantity = toInt(row.get("quantity"));
            assertTrue(quantity > 1, "数量应大于1");
        }
    }

    // ==========================================
    // $or / $and 嵌套测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("$or 条件中使用 $field 引用")
    void testOrCondition_WithFieldReference() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "salesAmount", "costAmount", "profitAmount"));

        // $or 条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef orSlice = new SliceRequestDef();

        List<CondRequestDef> orConds = new ArrayList<>();

        // 条件1: salesAmount > costAmount
        CondRequestDef cond1 = new CondRequestDef();
        cond1.setField("salesAmount");
        cond1.setOp(">");
        Map<String, Object> fieldRef1 = new HashMap<>();
        fieldRef1.put("$field", "costAmount");
        cond1.setValue(fieldRef1);
        orConds.add(cond1);

        // 条件2: profitAmount > 100
        CondRequestDef cond2 = new CondRequestDef();
        cond2.setField("profitAmount");
        cond2.setOp(">");
        cond2.setValue(100);
        orConds.add(cond2);

        orSlice.setOr(orConds);
        slices.add(orSlice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        printSql(sql, "$or 条件中使用 $field 引用");
        log.info("参数值: {}", queryEngine.getValues());

        // 执行查询
        List<Object> values = queryEngine.getValues();
        List<Map<String, Object>> results = jdbcTemplate.queryForList(paginateSql(sql, 10), values.toArray());
        log.info("查询结果数量: {}", results.size());
        assertTrue(results.size() > 0, "应有查询结果");
    }

    @Test
    @Order(41)
    @DisplayName("$or 条件中使用 $expr 表达式")
    void testOrCondition_WithExpression() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        queryRequest.setColumns(Arrays.asList("orderId", "salesAmount", "costAmount", "discountAmount"));

        // $or 条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef orSlice = new SliceRequestDef();

        List<CondRequestDef> orConds = new ArrayList<>();

        // 条件1: $expr 表达式
        CondRequestDef cond1 = CondRequestDef.expr("salesAmount > costAmount * 1.5");
        orConds.add(cond1);

        // 条件2: discountAmount > 50
        CondRequestDef cond2 = new CondRequestDef();
        cond2.setField("discountAmount");
        cond2.setOp(">");
        cond2.setValue(50);
        orConds.add(cond2);

        orSlice.setOr(orConds);
        slices.add(orSlice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        printSql(sql, "$or 条件中使用 $expr 表达式");
        log.info("参数值: {}", queryEngine.getValues());

        // 执行查询
        List<Object> values = queryEngine.getValues();
        List<Map<String, Object>> results = jdbcTemplate.queryForList(paginateSql(sql, 10), values.toArray());
        log.info("查询结果数量: {}", results.size());
    }

    // ==========================================
    // 辅助方法
    // ==========================================

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
            return BigDecimal.ZERO;
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
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }
}
