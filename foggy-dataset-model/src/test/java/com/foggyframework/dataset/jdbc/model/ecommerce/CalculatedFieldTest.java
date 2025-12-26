package com.foggyframework.dataset.jdbc.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.jdbc.model.def.query.request.*;
import com.foggyframework.dataset.jdbc.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.jdbc.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.jdbc.model.spi.support.CalculatedJdbcColumn;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 计算字段测试
 *
 * <p>测试动态计算字段的编译和SQL生成</p>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("计算字段测试")
class CalculatedFieldTest extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    // ==========================================
    // 基本计算字段测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("简单算术表达式")
    void testSimpleArithmeticExpression() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置计算字段: salesAmount - discountAmount
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        CalculatedFieldDef calcField = new CalculatedFieldDef(
                "netAmount",
                "净销售额",
                "salesAmount - discountAmount"
        );
        calculatedFields.add(calcField);
        queryRequest.setCalculatedFields(calculatedFields);

        // 设置查询列，包含计算字段
        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "discountAmount",
                "netAmount"  // 计算字段
        ));

        // 分析并生成SQL
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        // 验证计算字段被处理
        List<CalculatedJdbcColumn> calcColumns = queryEngine.getCalculatedColumns();
        assertNotNull(calcColumns, "计算字段列表不应为空");
        assertEquals(1, calcColumns.size(), "应有1个计算字段");
        assertEquals("netAmount", calcColumns.get(0).getName(), "计算字段名应为netAmount");

        // 验证SQL生成
        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "简单算术表达式SQL");
        log.info("计算字段SQL: {}", calcColumns.get(0).getDeclare());
    }

    @Test
    @Order(2)
    @DisplayName("乘法和除法表达式")
    void testMultiplicationDivisionExpression() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 设置计算字段
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();

        // 计算字段1: 单价 * 数量
        calculatedFields.add(new CalculatedFieldDef(
                "calculatedAmount",
                "计算金额",
                "unitPrice * quantity"
        ));

        // 计算字段2: 单位折扣率 (discountAmount / salesAmount)
        calculatedFields.add(new CalculatedFieldDef(
                "discountRate",
                "折扣率",
                "discountAmount / salesAmount"
        ));

        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "unitPrice",
                "quantity",
                "calculatedAmount",
                "discountRate"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        List<CalculatedJdbcColumn> calcColumns = queryEngine.getCalculatedColumns();
        assertEquals(2, calcColumns.size(), "应有2个计算字段");

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "乘法和除法表达式SQL");
    }

    @Test
    @Order(3)
    @DisplayName("复合表达式（多运算符）")
    void testComplexExpression() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 复合表达式: (salesAmount - discountAmount) * 0.9
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "finalAmount",
                "最终金额",
                "(salesAmount - discountAmount) * 0.9"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "discountAmount",
                "finalAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "复合表达式SQL");
    }

    // ==========================================
    // 函数调用测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("日期函数 - YEAR")
    void testYearFunction() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用YEAR函数
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "saleYear",
                "销售年份",
                "YEAR(salesDate$caption)"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesDate$caption",
                "saleYear"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toUpperCase().contains("YEAR"), "SQL应包含YEAR函数");

        printSql(sql, "YEAR函数SQL");
    }

    @Test
    @Order(11)
    @DisplayName("数学函数 - ABS")
    void testAbsFunction() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用ABS函数
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "absoluteDiscount",
                "折扣绝对值",
                "ABS(discountAmount)"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "discountAmount",
                "absoluteDiscount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toUpperCase().contains("ABS"), "SQL应包含ABS函数");

        printSql(sql, "ABS函数SQL");
    }

    @Test
    @Order(12)
    @DisplayName("数学函数 - ROUND")
    void testRoundFunction() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用ROUND函数
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "roundedAmount",
                "四舍五入金额",
                "ROUND(salesAmount, 2)"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "roundedAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toUpperCase().contains("ROUND"), "SQL应包含ROUND函数");

        printSql(sql, "ROUND函数SQL");
    }

    @Test
    @Order(13)
    @DisplayName("字符串函数 - CONCAT")
    void testConcatFunction() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用CONCAT函数
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "orderInfo",
                "订单信息",
                "CONCAT(orderId, '-', orderLineNo)"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "orderLineNo",
                "orderInfo"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toUpperCase().contains("CONCAT"), "SQL应包含CONCAT函数");

        printSql(sql, "CONCAT函数SQL");
    }

    @Test
    @Order(14)
    @DisplayName("空值处理函数 - COALESCE")
    void testCoalesceFunction() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用COALESCE函数
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "safeDiscountAmount",
                "安全折扣金额",
                "COALESCE(discountAmount, 0)"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "discountAmount",
                "safeDiscountAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");
        assertTrue(sql.toUpperCase().contains("COALESCE"), "SQL应包含COALESCE函数");

        printSql(sql, "COALESCE函数SQL");
    }

    // ==========================================
    // 计算字段依赖测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("计算字段引用其他计算字段")
    void testCalculatedFieldDependency() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 定义有依赖关系的计算字段
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();

        // 第一个计算字段
        calculatedFields.add(new CalculatedFieldDef(
                "netSalesAmt",
                "净销售额",
                "salesAmount - discountAmount"
        ));

        // 第二个计算字段引用第一个
        calculatedFields.add(new CalculatedFieldDef(
                "netWithTax",
                "含税金额",
                "netSalesAmt * 1.13"
        ));

        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "discountAmount",
                "netSalesAmt",
                "netWithTax"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        List<CalculatedJdbcColumn> calcColumns = queryEngine.getCalculatedColumns();
        assertEquals(2, calcColumns.size(), "应有2个计算字段");

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "计算字段依赖SQL");
        log.info("第一个计算字段SQL: {}", calcColumns.get(0).getDeclare());
        log.info("第二个计算字段SQL: {}", calcColumns.get(1).getDeclare());
    }

    // ==========================================
    // 安全性测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("禁止不允许的函数")
    void testDisallowedFunction() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用不允许的函数
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "danger",
                "危险字段",
                "EXEC('DROP TABLE users')"
        ));
        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList("orderId", "danger"));

        // 应抛出安全异常（可能被包装）
        Exception ex = assertThrows(Exception.class, () -> {
            queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        }, "不允许的函数应抛出异常");

        // 检查异常链中是否包含 SecurityException
        assertTrue(containsSecurityException(ex),
            "异常链中应包含SecurityException，实际异常: " + ex.getClass().getName());
    }

    @Test
    @Order(31)
    @DisplayName("禁止未知函数")
    void testUnknownFunction() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用未知函数
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "unknown",
                "未知字段",
                "UNKNOWN_FUNC(salesAmount)"
        ));
        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList("orderId", "unknown"));

        // 应抛出安全异常（可能被包装）
        Exception ex = assertThrows(Exception.class, () -> {
            queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        }, "未知函数应抛出异常");

        // 检查异常链中是否包含 SecurityException
        assertTrue(containsSecurityException(ex),
            "异常链中应包含SecurityException，实际异常: " + ex.getClass().getName());
    }

    /**
     * 检查异常链中是否包含 SecurityException
     */
    private boolean containsSecurityException(Throwable ex) {
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current instanceof SecurityException) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    // ==========================================
    // 错误处理测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("引用不存在的列")
    void testNonExistentColumn() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 引用不存在的列
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "invalid",
                "无效字段",
                "nonExistentColumn + 1"
        ));
        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList("orderId", "invalid"));

        // 应抛出异常
        assertThrows(Exception.class, () -> {
            queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        }, "引用不存在的列应抛出异常");
    }

    @Test
    @Order(41)
    @DisplayName("重复的计算字段名称")
    void testDuplicateCalculatedFieldName() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 重复的计算字段名
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef("dup", "重复1", "salesAmount + 1"));
        calculatedFields.add(new CalculatedFieldDef("dup", "重复2", "salesAmount + 2"));
        queryRequest.setCalculatedFields(calculatedFields);
        queryRequest.setColumns(Arrays.asList("orderId", "dup"));

        // 应抛出异常（第二个字段名与第一个冲突，因为第一个已注册）
        assertThrows(Exception.class, () -> {
            queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        }, "重复的计算字段名应抛出异常");
    }

    // ==========================================
    // 与维度关联的计算字段测试
    // ==========================================

    @Test
    @Order(50)
    @DisplayName("计算字段引用维度列")
    void testCalculatedFieldWithDimension() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用维度列的计算字段
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "yearMonth",
                "年月",
                "CONCAT(YEAR(salesDate$caption), '-', MONTH(salesDate$caption))"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesDate$caption",
                "yearMonth",
                "salesAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        printSql(sql, "维度列计算字段SQL");
    }

    // ==========================================
    // 真实查询执行测试
    // ==========================================

    @Test
    @Order(60)
    @DisplayName("执行真实查询 - 简单算术表达式")
    void testRealQuerySimpleArithmetic() {
        // 1. 原生SQL直查 - 计算净销售额
        String nativeSql = """
            SELECT
                order_id,
                sales_amount,
                discount_amount,
                (sales_amount - discount_amount) as net_amount
            FROM fact_sales
            ORDER BY order_id ASC
            LIMIT 10
            """;
        List<Map<String, Object>> nativeResults = jdbcTemplate.queryForList(nativeSql);
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询 - 使用计算字段
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "netAmount",
                "净销售额",
                "salesAmount - discountAmount"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "discountAmount",
                "netAmount"
        ));

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId");
        order.setOrder("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql() + " LIMIT 10";
        printSql(modelSql, "计算字段模型SQL");

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
            assertDecimalEquals(nativeRow.get("discount_amount"), modelRow.get("discountAmount"),
                "折扣金额应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("net_amount"), modelRow.get("netAmount"),
                "净销售额应一致: 行 " + i);
        }
    }

    @Test
    @Order(61)
    @DisplayName("执行真实查询 - 复合算术表达式")
    void testRealQueryComplexArithmetic() {
        // 1. 原生SQL直查 - 计算含税金额
        String nativeSql = """
            SELECT
                order_id,
                sales_amount,
                discount_amount,
                (sales_amount - discount_amount) * 1.13 as tax_amount
            FROM fact_sales
            ORDER BY order_id ASC
            LIMIT 10
            """;
        List<Map<String, Object>> nativeResults = jdbcTemplate.queryForList(nativeSql);
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询 - 使用计算字段
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "taxIncludedAmount",
                "含税金额",
                "(salesAmount - discountAmount) * 1.13"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "discountAmount",
                "taxIncludedAmount"
        ));

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId");
        order.setOrder("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql() + " LIMIT 10";
        printSql(modelSql, "复合计算字段模型SQL");

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql);
        log.info("模型查询结果: {} 条", modelResults.size());

        // 3. 对比结果
        assertEquals(nativeResults.size(), modelResults.size(), "记录数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);

            assertEquals(nativeRow.get("order_id"), modelRow.get("orderId"),
                "订单ID应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("tax_amount"), modelRow.get("taxIncludedAmount"),
                "含税金额应一致: 行 " + i);
        }
    }

    @Test
    @Order(62)
    @DisplayName("执行真实查询 - ABS函数")
    void testRealQueryAbsFunction() {
        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                order_id,
                discount_amount,
                ABS(discount_amount) as abs_discount
            FROM fact_sales
            ORDER BY order_id ASC
            LIMIT 10
            """;
        List<Map<String, Object>> nativeResults = jdbcTemplate.queryForList(nativeSql);
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "absDiscount",
                "折扣绝对值",
                "ABS(discountAmount)"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "discountAmount",
                "absDiscount"
        ));

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId");
        order.setOrder("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql() + " LIMIT 10";
        printSql(modelSql, "ABS函数计算字段SQL");

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql);
        log.info("模型查询结果: {} 条", modelResults.size());

        // 3. 对比结果
        assertEquals(nativeResults.size(), modelResults.size(), "记录数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);

            assertEquals(nativeRow.get("order_id"), modelRow.get("orderId"),
                "订单ID应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("abs_discount"), modelRow.get("absDiscount"),
                "折扣绝对值应一致: 行 " + i);
        }
    }

    @Test
    @Order(63)
    @DisplayName("执行真实查询 - ROUND函数")
    void testRealQueryRoundFunction() {
        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                order_id,
                sales_amount,
                ROUND(sales_amount, 1) as rounded_amount
            FROM fact_sales
            ORDER BY order_id ASC
            LIMIT 10
            """;
        List<Map<String, Object>> nativeResults = jdbcTemplate.queryForList(nativeSql);
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "roundedAmount",
                "四舍五入金额",
                "ROUND(salesAmount, 1)"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "roundedAmount"
        ));

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId");
        order.setOrder("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql() + " LIMIT 10";
        printSql(modelSql, "ROUND函数计算字段SQL");

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql);
        log.info("模型查询结果: {} 条", modelResults.size());

        // 3. 对比结果
        assertEquals(nativeResults.size(), modelResults.size(), "记录数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);

            assertEquals(nativeRow.get("order_id"), modelRow.get("orderId"),
                "订单ID应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("rounded_amount"), modelRow.get("roundedAmount"),
                "四舍五入金额应一致: 行 " + i);
        }
    }

    @Test
    @Order(64)
    @DisplayName("执行真实查询 - COALESCE函数")
    void testRealQueryCoalesceFunction() {
        // 1. 原生SQL直查
        String nativeSql = """
            SELECT
                order_id,
                discount_amount,
                COALESCE(discount_amount, 0) as safe_discount
            FROM fact_sales
            ORDER BY order_id ASC
            LIMIT 10
            """;
        List<Map<String, Object>> nativeResults = jdbcTemplate.queryForList(nativeSql);
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "safeDiscount",
                "安全折扣",
                "COALESCE(discountAmount, 0)"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "discountAmount",
                "safeDiscount"
        ));

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId");
        order.setOrder("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql() + " LIMIT 10";
        printSql(modelSql, "COALESCE函数计算字段SQL");

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql);
        log.info("模型查询结果: {} 条", modelResults.size());

        // 3. 对比结果
        assertEquals(nativeResults.size(), modelResults.size(), "记录数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);

            assertEquals(nativeRow.get("order_id"), modelRow.get("orderId"),
                "订单ID应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("safe_discount"), modelRow.get("safeDiscount"),
                "安全折扣应一致: 行 " + i);
        }
    }

    @Test
    @Order(65)
    @DisplayName("执行真实查询 - 计算字段在条件过滤场景")
    void testRealQueryWithFilterCondition() {
        // 1. 原生SQL直查 - 带条件过滤
        String nativeSql = """
            SELECT
                order_id,
                sales_amount,
                profit_amount,
                profit_amount * 100.0 / sales_amount as profit_rate
            FROM fact_sales
            WHERE sales_amount > 100
            ORDER BY order_id ASC
            LIMIT 10
            """;
        List<Map<String, Object>> nativeResults = jdbcTemplate.queryForList(nativeSql);
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询 - 使用计算字段计算利润率
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "profitRate",
                "利润率",
                "profitAmount * 100.0 / salesAmount"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "profitAmount",
                "profitRate"
        ));

        // 添加过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesAmount");
        slice.setOp(">");
        slice.setValue(100);
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId");
        order.setOrder("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql() + " LIMIT 10";
        List<Object> values = queryEngine.getValues();
        printSql(modelSql, "带过滤条件的计算字段SQL");
        log.info("参数: {}", values);

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql, values.toArray());
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
            assertDecimalEquals(nativeRow.get("profit_amount"), modelRow.get("profitAmount"),
                "利润金额应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("profit_rate"), modelRow.get("profitRate"),
                "利润率应一致: 行 " + i);
        }
    }

    @Test
    @Order(66)
    @DisplayName("执行真实查询 - 计算字段作为过滤条件")
    void testRealQueryCalculatedFieldAsSlice() {
        // 1. 原生SQL直查 - 使用计算字段过滤：利润率 > 10%
        String nativeSql = """
            SELECT
                order_id,
                sales_amount,
                profit_amount,
                profit_amount * 100.0 / sales_amount as profit_rate
            FROM fact_sales
            WHERE sales_amount > 0
              AND profit_amount * 100.0 / sales_amount > 10
            ORDER BY order_id ASC
            LIMIT 10
            """;
        List<Map<String, Object>> nativeResults = jdbcTemplate.queryForList(nativeSql);
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询 - 使用计算字段作为过滤条件
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 定义计算字段：利润率
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        calculatedFields.add(new CalculatedFieldDef(
                "profitRate",
                "利润率",
                "profitAmount * 100.0 / salesAmount"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "profitAmount",
                "profitRate"
        ));

        // 添加过滤条件 - 使用计算字段作为过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        // 确保 salesAmount > 0 避免除零
        SliceRequestDef slice1 = new SliceRequestDef();
        slice1.setField("salesAmount");
        slice1.setOp(">");
        slice1.setValue(0);
        slices.add(slice1);
        // 计算字段过滤：利润率 > 10%
        SliceRequestDef slice2 = new SliceRequestDef();
        slice2.setField("profitRate");  // 计算字段作为过滤条件
        slice2.setOp(">");
        slice2.setValue(10);
        slices.add(slice2);
        queryRequest.setSlice(slices);

        // 排序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId");
        order.setOrder("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql() + " LIMIT 10";
        List<Object> values = queryEngine.getValues();
        printSql(modelSql, "计算字段作为过滤条件SQL");
        log.info("参数: {}", values);

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql, values.toArray());
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
            assertDecimalEquals(nativeRow.get("profit_amount"), modelRow.get("profitAmount"),
                "利润金额应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("profit_rate"), modelRow.get("profitRate"),
                "利润率应一致: 行 " + i);

            // 验证利润率确实 > 10
            BigDecimal profitRate = toBigDecimal(modelRow.get("profitRate"));
            assertTrue(profitRate.compareTo(new BigDecimal("10")) > 0,
                "利润率应大于10%: 行 " + i + ", 实际值: " + profitRate);
        }
    }

    @Test
    @Order(67)
    @DisplayName("执行真实查询 - 计算字段链式依赖")
    void testRealQueryCalculatedFieldChain() {
        // 1. 原生SQL直查 - 链式计算: 净额 -> 含税额
        String nativeSql = """
                        SELECT
                            order_id,
                            sales_amount,
                            discount_amount,
                            tax_amount+2 as tax_amount3,
                            (sales_amount - discount_amount) as net_amount,
                            (sales_amount - discount_amount) * 1.13 as tax_included
                        FROM fact_sales
                        ORDER BY order_id ASC
                        LIMIT 10
                """;
        List<Map<String, Object>> nativeResults = jdbcTemplate.queryForList(nativeSql);
        log.info("原生SQL结果: {} 条", nativeResults.size());

        // 2. 模型查询 - 计算字段链式依赖
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        // 第一个计算字段
        calculatedFields.add(new CalculatedFieldDef(
                "netAmount",
                "净销售额",
                "salesAmount - discountAmount"
        ));
        // 第二个计算字段引用第一个
        calculatedFields.add(new CalculatedFieldDef(
                "taxIncluded",
                "含税金额",
                "netAmount * 1.13"
        ));
        queryRequest.setCalculatedFields(calculatedFields);

        queryRequest.setColumns(Arrays.asList(
                "orderId",
                "salesAmount",
                "discountAmount",
                "netAmount",
                "taxIncluded",
                "taxAmount2+1 as taxAmount3"
        ));

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("orderId");
        order.setOrder("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        String modelSql = queryEngine.getSql() + " LIMIT 10";
        printSql(modelSql, "计算字段链式依赖SQL");

        List<Map<String, Object>> modelResults = jdbcTemplate.queryForList(modelSql);
        log.info("模型查询结果: {} 条", modelResults.size());

        // 3. 对比结果
        assertEquals(nativeResults.size(), modelResults.size(), "记录数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> modelRow = modelResults.get(i);

            assertEquals(nativeRow.get("order_id"), modelRow.get("orderId"),
                "订单ID应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("net_amount"), modelRow.get("netAmount"),
                "净销售额应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("tax_included"), modelRow.get("taxIncluded"),
                "含税金额应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("tax_amount3"), modelRow.get("taxAmount3"),
                    "taxAmount3应一致: 行 " + i);
        }
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    /**
     * 比较两个数值是否相等（支持不同数值类型）
     */
    private void assertDecimalEquals(Object expected, Object actual, String message) {
        java.math.BigDecimal expectedDecimal = toBigDecimal(expected);
        java.math.BigDecimal actualDecimal = toBigDecimal(actual);

        if (expectedDecimal == null && actualDecimal == null) {
            return;
        }

        assertNotNull(expectedDecimal, message + " - 期望值不应为null");
        assertNotNull(actualDecimal, message + " - 实际值不应为null");

        // 使用2位小数精度比较
        assertEquals(
            expectedDecimal.setScale(2, java.math.RoundingMode.HALF_UP),
            actualDecimal.setScale(2, java.math.RoundingMode.HALF_UP),
            message
        );
    }

    /**
     * 转换为BigDecimal
     */
    private java.math.BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.math.BigDecimal) {
            return (java.math.BigDecimal) value;
        }
        if (value instanceof Number) {
            return new java.math.BigDecimal(value.toString());
        }
        return new java.math.BigDecimal(value.toString());
    }

    /**
     * 转换为Integer
     */
    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    // ==========================================
    // 内联聚合表达式 + 同名条件过滤测试
    // ==========================================

    /**
     * 测试场景：内联表达式 sum(salesAmount) as salesAmount，条件 salesAmount > 100
     *
     * <p>验证当用户通过内联表达式定义聚合计算字段，且别名与模型中已有字段同名时，
     * 系统应该报错：计算字段名称已存在。
     * <br>
     * 这是一个保护机制，防止字段名冲突导致歧义。
     * </p>
     */
    @Test
    @Order(80)
    @DisplayName("内联聚合表达式 - sum(salesAmount) as salesAmount 应报错(别名冲突)")
    void testInlineAggregateExpressionWithSameNameCondition() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用内联表达式定义聚合字段：sum(salesAmount) as salesAmount
        // 注意：别名与模型中已有的度量字段同名，应该报错
        List<String> columns = Arrays.asList(
            "product$categoryName",
            "sum(salesAmount) as salesAmount"  // 内联聚合表达式，别名与原字段同名
        );
        queryRequest.setColumns(columns);

        // 添加条件：salesAmount > 100
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesAmount");
        slice.setOp(">");
        slice.setValue(100);
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("product$categoryName");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        // 期望抛出异常：计算字段名称已存在
        Exception exception = assertThrows(Exception.class, () -> {
            queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        });

        String errorMessage = exception.getMessage();
        log.info("捕获到预期异常: {}", errorMessage);
        assertTrue(errorMessage.contains("计算字段名称已存在") || errorMessage.contains("salesAmount"),
            "异常信息应包含字段名冲突提示");
    }

    /**
     * 测试场景：内联表达式 sum(quantity) as quantity，条件 quantity = 10
     *
     * <p>与上一个测试类似，验证别名冲突会报错</p>
     */
    @Test
    @Order(81)
    @DisplayName("内联聚合表达式 - sum(quantity) as quantity 应报错(别名冲突)")
    void testInlineAggregateExpressionWithEqualCondition() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用内联表达式，别名与原字段同名，应该报错
        List<String> columns = Arrays.asList(
            "customer$memberLevel",
            "sum(quantity) as quantity"  // 内联聚合表达式，别名冲突
        );
        queryRequest.setColumns(columns);

        // 添加条件：quantity = 10
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("quantity");
        slice.setOp("=");
        slice.setValue(10);
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("customer$memberLevel");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        // 期望抛出异常
        Exception exception = assertThrows(Exception.class, () -> {
            queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        });

        log.info("捕获到预期异常: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("计算字段名称已存在") || exception.getMessage().contains("quantity"),
            "异常信息应包含字段名冲突提示");
    }

    /**
     * 测试场景：内联表达式使用不同别名 sum(salesAmount) as totalSales，条件 salesAmount > 100
     *
     * <p>验证：别名不冲突时，聚合表达式可以正常工作。</p>
     */
    @Test
    @Order(82)
    @DisplayName("内联聚合表达式 - 不同别名正常工作")
    void testInlineAggregateExpressionWithDifferentAlias() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用内联表达式，别名与原字段不同，应该正常工作
        List<String> columns = Arrays.asList(
            "product$categoryName",
            "sum(salesAmount) as totalSales"  // 别名为 totalSales，不冲突
        );
        queryRequest.setColumns(columns);

        // 添加条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesAmount");
        slice.setOp(">");
        slice.setValue(100);
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 设置分组
        List<GroupRequestDef> groups = new ArrayList<>();
        GroupRequestDef group = new GroupRequestDef();
        group.setField("product$categoryName");
        groups.add(group);
        queryRequest.setGroupBy(groups);

        // 现在应该正常工作
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String sql = queryEngine.getSql();
        assertNotNull(sql, "SQL生成失败");

        // 验证 SQL 包含 SUM 函数
        assertTrue(sql.toUpperCase().contains("SUM"), "SQL应包含SUM函数");

        printSql(sql, "内联聚合表达式(不同别名) - 正常工作");
        log.info("参数值: {}", queryEngine.getValues());

        // 执行SQL验证
        try {
            List<Object> values = queryEngine.getValues();
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, values.toArray());
            log.info("查询结果数量: {}", results.size());
            printResults(results);
        } catch (Exception e) {
            log.error("SQL执行失败: {}", e.getMessage(), e);
            fail("SQL应能正确执行，但出现错误: " + e.getMessage());
        }
    }

    /**
     * 测试场景：无分组情况下的内联聚合表达式
     *
     * <p>验证不带 GROUP BY 时内联聚合表达式的处理</p>
     */
    @Test
    @Order(83)
    @DisplayName("内联聚合表达式 - 无分组情况")
    void testInlineAggregateExpressionWithoutGroupBy() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");

        // 使用内联表达式（不分组时作为计算字段处理）
        List<String> columns = Arrays.asList(
            "orderId",
            "salesAmount * 1.1 as adjustedAmount"  // 非聚合的内联表达式
        );
        queryRequest.setColumns(columns);

        // 添加条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("salesAmount");
        slice.setOp(">");
        slice.setValue(50);
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 不设置分组
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        String innerSql = queryEngine.getInnerSql();
        assertNotNull(innerSql, "内层SQL生成失败");

        printSql(innerSql, "内联表达式(无分组) - 明细查询");
        log.info("参数值: {}", queryEngine.getValues());
    }
}
