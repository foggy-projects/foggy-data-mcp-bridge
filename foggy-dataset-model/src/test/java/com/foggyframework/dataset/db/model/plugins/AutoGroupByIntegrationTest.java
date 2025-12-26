package com.foggyframework.dataset.db.model.plugins;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.expression.InlineExpressionParser;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultFilterManager;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultStep;
import com.foggyframework.dataset.db.model.service.QueryFacade;
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
 * AutoGroupBy 集成测试
 * <p>
 * 通过实际执行 JDBC 查询，验证 autoGroupBy 功能：
 * <ul>
 *     <li>自动处理的查询结果与手动指定 groupBy 的查询结果一致</li>
 *     <li>查询结果与原生 SQL 执行结果一致</li>
 *     <li>支持多种聚合函数：SUM, AVG, COUNT, MAX, MIN</li>
 *     <li>支持多维度分组</li>
 * </ul>
 * </p>
 * <p>
 * 注意：autoGroupBy 参数已废弃，系统始终自动处理 groupBy。
 * </p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("AutoGroupBy 集成测试 - 执行查询对比")
class AutoGroupByIntegrationTest extends EcommerceTestSupport {

    @Resource
    private QueryFacade queryFacade;

    @Resource
    private DataSetResultFilterManager dataSetResultFilterManager;

    @Resource
    private List<DataSetResultStep> allSteps;

    // ==========================================
    // autoGroupBy 与手动 groupBy 对比测试
    // ==========================================

    @Test
    @Order(0)
    @DisplayName("验证 Step 注册情况")
    void testStepRegistration() {
        log.info("========== Step 注册情况检查 ==========");

        // 1. 检查注入的 steps
        log.info("注入的 DataSetResultStep 列表: {} 个", allSteps.size());
        for (DataSetResultStep step : allSteps) {
            log.info("  - {}", step.getClass().getSimpleName());
        }

        // 验证关键 Step 存在
        boolean hasInlineExpressionStep = allSteps.stream()
                .anyMatch(s -> s.getClass().getSimpleName().contains("InlineExpression"));
        boolean hasAutoGroupByStep = allSteps.stream()
                .anyMatch(s -> s.getClass().getSimpleName().contains("AutoGroupBy"));

        log.info("InlineExpressionPreprocessStep 注册: {}", hasInlineExpressionStep);
        log.info("AutoGroupByStep 注册: {}", hasAutoGroupByStep);

        assertTrue(hasInlineExpressionStep, "InlineExpressionPreprocessStep 应已注册");
        assertTrue(hasAutoGroupByStep, "AutoGroupByStep 应已注册");

        // 2. 检查 DataSetResultFilterManager 的类型
        log.info("DataSetResultFilterManager 实现类: {}", dataSetResultFilterManager.getClass().getName());

        // 3. 测试内联表达式解析
        String testExpr = "sum(totalAmount) as sumTotalAmount";
        InlineExpressionParser.InlineExpression parsed =
                InlineExpressionParser.parse(testExpr);
        log.info("测试解析 '{}': {}", testExpr, parsed);
        assertNotNull(parsed, "InlineExpressionParser 应能解析聚合表达式");
    }

    @Test
    @Order(1)
    @DisplayName("autoGroupBy 与手动 groupBy 对比 - 单维度 SUM")
    void testAutoGroupBy_vs_ManualGroupBy_SingleDimension_Sum() {
        // 1. autoGroupBy=true 的查询
        DbQueryRequestDef autoRequest = new DbQueryRequestDef();
        autoRequest.setQueryModel("FactOrderQueryModel");
        autoRequest.setAutoGroupBy(true);
        autoRequest.setColumns(Arrays.asList(
                "customer$customerType",
                "sum(totalAmount) as sumTotalAmount"
        ));
        autoRequest.setOrderBy(createOrderList("customer$customerType", "ASC"));

        PagingResultImpl autoResult = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(autoRequest, 100));
        List<Map<String, Object>> autoItems = autoResult.getItems();

        log.info("autoGroupBy 查询结果: {} 条", autoItems.size());

        // 2. 手动指定 groupBy 的查询
        DbQueryRequestDef manualRequest = new DbQueryRequestDef();
        manualRequest.setQueryModel("FactOrderQueryModel");
        manualRequest.setAutoGroupBy(false);
        manualRequest.setColumns(Arrays.asList("customer$customerType", "totalAmount"));

        List<GroupRequestDef> groups = new ArrayList<>();
        groups.add(createGroup("customer$customerType", null));
        groups.add(createGroup("totalAmount", "SUM"));
        manualRequest.setGroupBy(groups);
        manualRequest.setOrderBy(createOrderList("customer$customerType", "ASC"));

        PagingResultImpl manualResult = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(manualRequest, 100));
        List<Map<String, Object>> manualItems = manualResult.getItems();

        log.info("手动 groupBy 查询结果: {} 条", manualItems.size());

        // 3. 验证结果一致
        assertEquals(manualItems.size(), autoItems.size(), "结果行数应一致");

        for (int i = 0; i < autoItems.size(); i++) {
            Map<String, Object> autoRow = autoItems.get(i);
            Map<String, Object> manualRow = manualItems.get(i);

            assertEquals(manualRow.get("customer$customerType"), autoRow.get("customer$customerType"),
                    "客户类型应一致: 行 " + i);
            assertDecimalEquals(manualRow.get("totalAmount"), autoRow.get("sumTotalAmount"),
                    "SUM(totalAmount) 应一致: 行 " + i);

            log.info("行 {}: customerType={}, autoSum={}, manualSum={}",
                    i, autoRow.get("customer$customerType"),
                    autoRow.get("sumTotalAmount"), manualRow.get("totalAmount"));
        }
    }

    @Test
    @Order(2)
    @DisplayName("autoGroupBy 与原生 SQL 对比 - 单维度 SUM")
    void testAutoGroupBy_vs_NativeSQL_SingleDimension_Sum() {
        // 1. 原生 SQL 查询
        String nativeSql = """
                SELECT
                    dc.customer_type,
                    SUM(fo.total_amount) as sum_total_amount
                FROM fact_order fo
                LEFT JOIN dim_customer dc ON fo.customer_key = dc.customer_key
                GROUP BY dc.customer_type
                ORDER BY dc.customer_type
                """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生 SQL 结果: {} 条", nativeResults.size());

        // 2. autoGroupBy 查询
        DbQueryRequestDef autoRequest = new DbQueryRequestDef();
        autoRequest.setQueryModel("FactOrderQueryModel");
        autoRequest.setAutoGroupBy(true);
        autoRequest.setColumns(Arrays.asList(
                "customer$customerType",
                "sum(totalAmount) as sumTotalAmount"
        ));
        autoRequest.setOrderBy(createOrderList("customer$customerType", "ASC"));

        PagingResultImpl autoResult = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(autoRequest, 100));
        List<Map<String, Object>> autoItems = autoResult.getItems();

        log.info("autoGroupBy 查询结果: {} 条", autoItems.size());

        // 3. 验证结果一致
        assertEquals(nativeResults.size(), autoItems.size(), "结果行数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> autoRow = autoItems.get(i);

            assertEquals(nativeRow.get("customer_type"), autoRow.get("customer$customerType"),
                    "客户类型应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("sum_total_amount"), autoRow.get("sumTotalAmount"),
                    "SUM(totalAmount) 应一致: 行 " + i);
        }
    }

    @Test
    @Order(3)
    @DisplayName("autoGroupBy 多聚合函数 - AVG, COUNT, MAX, MIN")
    void testAutoGroupBy_MultipleAggFunctions() {
        // 1. 原生 SQL 查询
        String nativeSql = """
                SELECT
                    dc.customer_type,
                    AVG(fo.total_amount) as avg_amount,
                    COUNT(*) as order_count,
                    MAX(fo.total_amount) as max_amount,
                    MIN(fo.total_amount) as min_amount
                FROM fact_order fo
                LEFT JOIN dim_customer dc ON fo.customer_key = dc.customer_key
                GROUP BY dc.customer_type
                ORDER BY dc.customer_type
                """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生 SQL 结果: {} 条", nativeResults.size());

        // 2. autoGroupBy 查询（多个聚合）
        DbQueryRequestDef autoRequest = new DbQueryRequestDef();
        autoRequest.setQueryModel("FactOrderQueryModel");
        autoRequest.setAutoGroupBy(true);
        autoRequest.setColumns(Arrays.asList(
                "customer$customerType",
                "avg(totalAmount) as avgAmount",
                "count(orderId) as orderCount",
                "max(totalAmount) as maxAmount",
                "min(totalAmount) as minAmount"
        ));
        autoRequest.setOrderBy(createOrderList("customer$customerType", "ASC"));

        PagingResultImpl autoResult = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(autoRequest, 100));
        List<Map<String, Object>> autoItems = autoResult.getItems();

        log.info("autoGroupBy 多聚合查询结果: {} 条", autoItems.size());

        // 3. 验证结果一致
        assertEquals(nativeResults.size(), autoItems.size(), "结果行数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> autoRow = autoItems.get(i);

            String customerType = (String) nativeRow.get("customer_type");
            assertEquals(customerType, autoRow.get("customer$customerType"),
                    "客户类型应一致: 行 " + i);

            // AVG 对比（允许小数精度差异）
            assertDecimalEquals(nativeRow.get("avg_amount"), autoRow.get("avgAmount"),
                    "AVG(totalAmount) 应一致: " + customerType);

            // COUNT 对比
            assertEquals(toLong(nativeRow.get("order_count")), toLong(autoRow.get("orderCount")),
                    "COUNT 应一致: " + customerType);

            // MAX/MIN 对比
            assertDecimalEquals(nativeRow.get("max_amount"), autoRow.get("maxAmount"),
                    "MAX(totalAmount) 应一致: " + customerType);
            assertDecimalEquals(nativeRow.get("min_amount"), autoRow.get("minAmount"),
                    "MIN(totalAmount) 应一致: " + customerType);

            log.info("行 {}: customerType={}, avg={}, count={}, max={}, min={}",
                    i, customerType,
                    autoRow.get("avgAmount"), autoRow.get("orderCount"),
                    autoRow.get("maxAmount"), autoRow.get("minAmount"));
        }
    }

    @Test
    @Order(4)
    @DisplayName("autoGroupBy 多维度分组")
    void testAutoGroupBy_MultiDimension() {
        // 1. 原生 SQL 查询
        String nativeSql = """
                SELECT
                    dd.year,
                    dc.customer_type,
                    SUM(fo.total_amount) as sum_amount
                FROM fact_order fo
                LEFT JOIN dim_date dd ON fo.date_key = dd.date_key
                LEFT JOIN dim_customer dc ON fo.customer_key = dc.customer_key
                GROUP BY dd.year, dc.customer_type
                ORDER BY dd.year DESC, dc.customer_type ASC
                """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生 SQL 多维度结果: {} 条", nativeResults.size());

        // 2. autoGroupBy 多维度查询
        DbQueryRequestDef autoRequest = new DbQueryRequestDef();
        autoRequest.setQueryModel("FactOrderQueryModel");
        autoRequest.setAutoGroupBy(true);
        autoRequest.setColumns(Arrays.asList(
                "orderDate$year",
                "customer$customerType",
                "sum(totalAmount) as sumAmount"
        ));

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("orderDate$year", "DESC"));
        orders.add(createOrder("customer$customerType", "ASC"));
        autoRequest.setOrderBy(orders);

        PagingResultImpl autoResult = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(autoRequest, 100));
        List<Map<String, Object>> autoItems = autoResult.getItems();

        log.info("autoGroupBy 多维度查询结果: {} 条", autoItems.size());

        // 3. 验证结果一致
        assertEquals(nativeResults.size(), autoItems.size(), "结果行数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> autoRow = autoItems.get(i);

            assertEquals(toInt(nativeRow.get("year")), toInt(autoRow.get("orderDate$year")),
                    "年份应一致: 行 " + i);
            assertEquals(nativeRow.get("customer_type"), autoRow.get("customer$customerType"),
                    "客户类型应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("sum_amount"), autoRow.get("sumAmount"),
                    "SUM(totalAmount) 应一致: 行 " + i);
        }
    }

    @Test
    @Order(5)
    @DisplayName("autoGroupBy 混合场景 - 普通列 + 多个聚合")
    void testAutoGroupBy_MixedScenario() {
        // 1. 原生 SQL 查询
        String nativeSql = """
                SELECT
                    dc.customer_type,
                    dch.channel_name,
                    SUM(fo.total_amount) as sum_amount,
                    COUNT(*) as order_count
                FROM fact_order fo
                LEFT JOIN dim_customer dc ON fo.customer_key = dc.customer_key
                LEFT JOIN dim_channel dch ON fo.channel_key = dch.channel_key
                GROUP BY dc.customer_type, dch.channel_name
                ORDER BY dc.customer_type, dch.channel_name
                """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生 SQL 混合场景结果: {} 条", nativeResults.size());

        // 2. autoGroupBy 混合查询
        DbQueryRequestDef autoRequest = new DbQueryRequestDef();
        autoRequest.setQueryModel("FactOrderQueryModel");
        autoRequest.setAutoGroupBy(true);
        autoRequest.setColumns(Arrays.asList(
                "customer$customerType",
                "channel$caption",
                "sum(totalAmount) as sumAmount",
                "count(orderId) as orderCount"
        ));

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("customer$customerType", "ASC"));
        orders.add(createOrder("channel$caption", "ASC"));
        autoRequest.setOrderBy(orders);

        PagingResultImpl autoResult = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(autoRequest, 100));
        List<Map<String, Object>> autoItems = autoResult.getItems();

        log.info("autoGroupBy 混合查询结果: {} 条", autoItems.size());

        // 3. 验证结果一致
        assertEquals(nativeResults.size(), autoItems.size(), "结果行数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> autoRow = autoItems.get(i);

            assertEquals(nativeRow.get("customer_type"), autoRow.get("customer$customerType"),
                    "客户类型应一致: 行 " + i);
            assertEquals(nativeRow.get("channel_name"), autoRow.get("channel$caption"),
                    "渠道名称应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("sum_amount"), autoRow.get("sumAmount"),
                    "SUM 应一致: 行 " + i);
            assertEquals(toLong(nativeRow.get("order_count")), toLong(autoRow.get("orderCount")),
                    "COUNT 应一致: 行 " + i);
        }
    }

    @Test
    @Order(6)
    @DisplayName("无聚合表达式时返回明细数据")
    void testNoAggregation_ReturnsDetailData() {
        // 不含聚合表达式或聚合字段时，应返回明细数据
        // 注意：使用 orderId（属性，无聚合）而非 totalAmount（度量，有聚合）
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactOrderQueryModel");
        request.setColumns(Arrays.asList(
                "customer$customerType",
                "orderId"  // 属性字段，无聚合定义
        ));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(request, 100));
        List<Map<String, Object>> items = result.getItems();

        // 原生 SQL 获取总记录数
        Long totalCount = executeQueryForObject("SELECT COUNT(*) FROM fact_order", Long.class);

        log.info("无聚合表达式结果: {} 条, 总记录数: {}", items.size(), totalCount);

        // 应返回明细数据，不是分组数据
        // SQLite 测试数据只有 10 条
        int expectedSize = Math.min(totalCount.intValue(), 100);
        assertEquals(expectedSize, items.size(), "应返回明细数据而非分组数据");
    }

    @Test
    @Order(7)
    @DisplayName("autoGroupBy 大小写不敏感 - SUM/sum/Sum")
    void testAutoGroupBy_CaseInsensitive() {
        // 测试不同大小写的聚合函数
        String[] cases = {"sum", "SUM", "Sum"};

        List<Map<String, Object>> previousResult = null;

        for (String aggCase : cases) {
            DbQueryRequestDef request = new DbQueryRequestDef();
            request.setQueryModel("FactOrderQueryModel");
            request.setAutoGroupBy(true);
            request.setColumns(Arrays.asList(
                    "customer$customerType",
                    aggCase + "(totalAmount) as sumAmount"
            ));
            request.setOrderBy(createOrderList("customer$customerType", "ASC"));

            PagingResultImpl result = queryFacade.queryModelData(
                    PagingRequest.buildPagingRequest(request, 100));
            List<Map<String, Object>> items = result.getItems();

            log.info("聚合函数 '{}' 结果: {} 条", aggCase, items.size());

            if (previousResult != null) {
                assertEquals(previousResult.size(), items.size(),
                        aggCase + " 结果行数应与前一个一致");

                for (int i = 0; i < items.size(); i++) {
                    assertDecimalEquals(
                            previousResult.get(i).get("sumAmount"),
                            items.get(i).get("sumAmount"),
                            aggCase + " 结果值应与前一个一致: 行 " + i);
                }
            }

            previousResult = items;
        }
    }

    @Test
    @Order(8)
    @DisplayName("混合聚合与非聚合列 - 无聚合函数的列自动处理")
    void testMixedAggregation_ColumnsWithoutAggFunction() {
        /**
         * 这个测试验证当查询包含混合列时的处理：
         * - 普通维度列（customer$customerType）
         * - 带聚合函数的计算列（count(orderId) as orderCount）
         * - 带表达式的计算列（totalAmount+2 as plusAmount）- 无聚合函数，应自动推断 SUM
         * - 带聚合函数的计算列（min(totalAmount) as minAmount）
         *
         * 当存在聚合表达式时，没有显式聚合函数的内联表达式应自动推断为 SUM。
         */

        // 1. 原生 SQL - 展示预期行为
        String nativeSql = """
                SELECT
                    dc.customer_type,
                    COUNT(fo.order_id) as order_count,
                    SUM(fo.total_amount + 2) as plus_amount,
                    MIN(fo.total_amount) as min_amount
                FROM fact_order fo
                LEFT JOIN dim_customer dc ON fo.customer_key = dc.customer_key
                GROUP BY dc.customer_type
                ORDER BY dc.customer_type
                """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生 SQL 结果: {} 条", nativeResults.size());

        // 2. 自动 groupBy 查询
        DbQueryRequestDef autoRequest = new DbQueryRequestDef();
        autoRequest.setQueryModel("FactOrderQueryModel");
        autoRequest.setColumns(Arrays.asList(
                "customer$customerType",
                "count(orderId) as orderCount",
                "totalAmount+2 as plusAmount",      // 无聚合函数的表达式，应自动推断 SUM
                "min(totalAmount) as minAmount"
        ));
        autoRequest.setOrderBy(createOrderList("customer$customerType", "ASC"));

        PagingResultImpl autoResult = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(autoRequest, 100));
        List<Map<String, Object>> autoItems = autoResult.getItems();

        log.info("混合聚合查询结果: {} 条", autoItems.size());

        // 3. 验证结果
        assertEquals(nativeResults.size(), autoItems.size(), "结果行数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> autoRow = autoItems.get(i);

            String customerType = (String) nativeRow.get("customer_type");
            assertEquals(customerType, autoRow.get("customer$customerType"),
                    "客户类型应一致: 行 " + i);

            log.info("行 {}: customerType={}, orderCount={}, plusAmount={}, minAmount={}",
                    i, customerType,
                    autoRow.get("orderCount"),
                    autoRow.get("plusAmount"),
                    autoRow.get("minAmount"));

            // 验证各列值
            assertEquals(toLong(nativeRow.get("order_count")), toLong(autoRow.get("orderCount")),
                    "COUNT 应一致: " + customerType);
            assertDecimalEquals(nativeRow.get("plus_amount"), autoRow.get("plusAmount"),
                    "plusAmount (表达式+SUM) 应一致: " + customerType);
            assertDecimalEquals(nativeRow.get("min_amount"), autoRow.get("minAmount"),
                    "MIN 应一致: " + customerType);
        }
    }

    // ==========================================
    // formulaDef 字段测试（TM 中定义的计算字段）
    // ==========================================

    @Test
    @Order(9)
    @DisplayName("formulaDef 字段 - 无计算字段的 groupBy 查询")
    void testFormulaDef_GroupByWithoutCalculatedField() {
        // 测试场景：使用 FactSalesQueryModel 中的 taxAmount2（带 formulaDef）
        // taxAmount2 定义: tax_amount+1，聚合类型 sum
        // 这个测试验证 formulaDef 字段在普通 groupBy 查询中的行为

        // 1. 原生 SQL 查询
        String nativeSql = """
                SELECT
                    dp.category_name as category_name,
                    SUM(fs.tax_amount + 1) as tax_amount2
                FROM fact_sales fs
                LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                GROUP BY dp.category_name
                ORDER BY dp.category_name
                """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生 SQL 结果 (formulaDef groupBy): {} 条", nativeResults.size());

        // 2. 使用 QueryModel 查询（无内联计算字段）
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(Arrays.asList(
                "product$categoryName",
                "taxAmount2"  // formulaDef 字段
        ));
        request.setOrderBy(createOrderList("product$categoryName", "ASC"));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(request, 100));
        List<Map<String, Object>> items = result.getItems();

        log.info("QueryModel 查询结果 (formulaDef groupBy): {} 条", items.size());

        // 3. 验证结果
        assertEquals(nativeResults.size(), items.size(), "结果行数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> row = items.get(i);

            String categoryName = (String) nativeRow.get("category_name");
            log.info("行 {}: categoryName={}, taxAmount2={}",
                    i, categoryName, row.get("taxAmount2"));

            assertEquals(categoryName, row.get("product$categoryName"),
                    "品类名称应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("tax_amount2"), row.get("taxAmount2"),
                    "taxAmount2 (formulaDef) 应一致: " + categoryName);
        }
    }

    @Test
    @Order(10)
    @DisplayName("formulaDef 字段 - 作为内联计算字段使用")
    void testFormulaDef_AsCalculatedField() {
        // 测试场景：在 columns 中同时使用 formulaDef 字段和内联计算字段
        // 验证 formulaDef 字段与内联表达式的兼容性

        // 1. 原生 SQL 查询
        String nativeSql = """
                SELECT
                    dp.category_name as category_name,
                    SUM(fs.quantity) as total_quantity,
                    SUM(fs.tax_amount + 1) as tax_amount2,
                    SUM(fs.sales_amount) as total_sales
                FROM fact_sales fs
                LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                GROUP BY dp.category_name
                ORDER BY dp.category_name
                """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生 SQL 结果 (formulaDef + 聚合): {} 条", nativeResults.size());

        // 2. 使用 QueryModel 查询（混合 formulaDef 和聚合函数）
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(Arrays.asList(
                "product$categoryName",
                "sum(quantity) as totalQuantity",  // 内联聚合表达式
                "taxAmount2",                       // formulaDef 字段
                "salesAmount"                       // 普通度量（有默认聚合）
        ));
        request.setOrderBy(createOrderList("product$categoryName", "ASC"));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(request, 100));
        List<Map<String, Object>> items = result.getItems();

        log.info("QueryModel 查询结果 (formulaDef + 聚合): {} 条", items.size());

        // 3. 验证结果
        assertEquals(nativeResults.size(), items.size(), "结果行数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> row = items.get(i);

            String categoryName = (String) nativeRow.get("category_name");
            log.info("行 {}: categoryName={}, totalQuantity={}, taxAmount2={}, salesAmount={}",
                    i, categoryName,
                    row.get("totalQuantity"),
                    row.get("taxAmount2"),
                    row.get("salesAmount"));

            assertEquals(categoryName, row.get("product$categoryName"),
                    "品类名称应一致: 行 " + i);
            assertEquals(toLong(nativeRow.get("total_quantity")), toLong(row.get("totalQuantity")),
                    "totalQuantity 应一致: " + categoryName);
            assertDecimalEquals(nativeRow.get("tax_amount2"), row.get("taxAmount2"),
                    "taxAmount2 (formulaDef) 应一致: " + categoryName);
            assertDecimalEquals(nativeRow.get("total_sales"), row.get("salesAmount"),
                    "salesAmount 应一致: " + categoryName);
        }
    }

    // ==========================================
    // Engine 层 orderBy 处理测试
    // 测试 addOrderByForGroupBy 方法
    // ==========================================

    @Test
    @Order(11)
    @DisplayName("Engine层orderBy - 普通维度字段排序")
    void testEngineOrderBy_DimensionField() {
        // 测试：普通维度字段在 GROUP BY 场景下的排序
        // 验证 Engine 层 addOrderByForGroupBy 正确处理维度字段

        // 1. 原生 SQL 查询（按品类名称降序）
        String nativeSql = """
                SELECT
                    dp.category_name as category_name,
                    SUM(fs.sales_amount) as total_sales
                FROM fact_sales fs
                LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                GROUP BY dp.category_name
                ORDER BY dp.category_name DESC
                """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生 SQL 结果 (维度字段排序 DESC): {} 条", nativeResults.size());

        // 2. QueryModel 查询
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(Arrays.asList(
                "product$categoryName",
                "salesAmount"
        ));
        request.setOrderBy(createOrderList("product$categoryName", "DESC"));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(request, 100));
        List<Map<String, Object>> items = result.getItems();

        log.info("QueryModel 查询结果 (维度字段排序 DESC): {} 条", items.size());

        // 3. 验证结果顺序一致
        assertEquals(nativeResults.size(), items.size(), "结果行数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> row = items.get(i);

            String categoryName = (String) nativeRow.get("category_name");
            log.info("行 {}: categoryName={}, salesAmount={}",
                    i, categoryName, row.get("salesAmount"));

            assertEquals(categoryName, row.get("product$categoryName"),
                    "品类名称应一致（顺序验证）: 行 " + i);
            assertDecimalEquals(nativeRow.get("total_sales"), row.get("salesAmount"),
                    "salesAmount 应一致: " + categoryName);
        }
    }

    @Test
    @Order(12)
    @DisplayName("Engine层orderBy - 聚合字段排序")
    void testEngineOrderBy_AggregateField() {
        // 测试：聚合字段在 GROUP BY 场景下的排序
        // 验证 Engine 层 addOrderByForGroupBy 正确处理聚合字段

        // 1. 原生 SQL 查询（按销售额降序）
        String nativeSql = """
                SELECT
                    dp.category_name as category_name,
                    SUM(fs.sales_amount) as total_sales
                FROM fact_sales fs
                LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                GROUP BY dp.category_name
                ORDER BY total_sales DESC
                """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生 SQL 结果 (聚合字段排序 DESC): {} 条", nativeResults.size());

        // 2. QueryModel 查询
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(Arrays.asList(
                "product$categoryName",
                "salesAmount"
        ));
        request.setOrderBy(createOrderList("salesAmount", "DESC"));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(request, 100));
        List<Map<String, Object>> items = result.getItems();

        log.info("QueryModel 查询结果 (聚合字段排序 DESC): {} 条", items.size());

        // 3. 验证结果顺序一致（按销售额降序）
        assertEquals(nativeResults.size(), items.size(), "结果行数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> row = items.get(i);

            String categoryName = (String) nativeRow.get("category_name");
            log.info("行 {}: categoryName={}, salesAmount={}",
                    i, categoryName, row.get("salesAmount"));

            assertEquals(categoryName, row.get("product$categoryName"),
                    "品类名称应一致（按销售额排序后）: 行 " + i);
            assertDecimalEquals(nativeRow.get("total_sales"), row.get("salesAmount"),
                    "salesAmount 应一致: " + categoryName);
        }

        // 4. 额外验证：结果确实是按销售额降序
        if (items.size() >= 2) {
            BigDecimal first = toBigDecimal(items.get(0).get("salesAmount"));
            BigDecimal second = toBigDecimal(items.get(1).get("salesAmount"));
            assertTrue(first.compareTo(second) >= 0,
                    "第一行销售额应 >= 第二行（降序）");
        }
    }

    @Test
    @Order(13)
    @DisplayName("Engine层orderBy - formulaDef计算字段排序")
    void testEngineOrderBy_FormulaDefField() {
        // 测试：formulaDef 字段在 GROUP BY 场景下的排序
        // taxAmount2 定义: tax_amount+1，聚合类型 sum

        // 1. 原生 SQL 查询（按 taxAmount2 降序）
        String nativeSql = """
                SELECT
                    dp.category_name as category_name,
                    SUM(fs.tax_amount + 1) as tax_amount2,
                    SUM(fs.sales_amount) as total_sales
                FROM fact_sales fs
                LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                GROUP BY dp.category_name
                ORDER BY tax_amount2 DESC
                """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生 SQL 结果 (formulaDef字段排序 DESC): {} 条", nativeResults.size());

        // 2. QueryModel 查询
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(Arrays.asList(
                "product$categoryName",
                "taxAmount2",   // formulaDef 字段
                "salesAmount"
        ));
        request.setOrderBy(createOrderList("taxAmount2", "DESC"));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(request, 100));
        List<Map<String, Object>> items = result.getItems();

        log.info("QueryModel 查询结果 (formulaDef字段排序 DESC): {} 条", items.size());

        // 3. 验证结果顺序一致（按 taxAmount2 降序）
        assertEquals(nativeResults.size(), items.size(), "结果行数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> row = items.get(i);

            String categoryName = (String) nativeRow.get("category_name");
            log.info("行 {}: categoryName={}, taxAmount2={}, salesAmount={}",
                    i, categoryName, row.get("taxAmount2"), row.get("salesAmount"));

            assertEquals(categoryName, row.get("product$categoryName"),
                    "品类名称应一致（按taxAmount2排序后）: 行 " + i);
            assertDecimalEquals(nativeRow.get("tax_amount2"), row.get("taxAmount2"),
                    "taxAmount2 (formulaDef) 应一致: " + categoryName);
            assertDecimalEquals(nativeRow.get("total_sales"), row.get("salesAmount"),
                    "salesAmount 应一致: " + categoryName);
        }

        // 4. 额外验证：结果确实是按 taxAmount2 降序
        if (items.size() >= 2) {
            BigDecimal first = toBigDecimal(items.get(0).get("taxAmount2"));
            BigDecimal second = toBigDecimal(items.get(1).get("taxAmount2"));
            assertTrue(first.compareTo(second) >= 0,
                    "第一行 taxAmount2 应 >= 第二行（降序）");
        }
    }

    @Test
    @Order(14)
    @DisplayName("Engine层orderBy - 内联聚合表达式排序")
    void testEngineOrderBy_InlineAggregateExpression() {
        // 测试：内联聚合表达式在 GROUP BY 场景下的排序
        // 例如：sum(quantity) as totalQuantity

        // 1. 原生 SQL 查询（按总数量升序）
        String nativeSql = """
                SELECT
                    dp.category_name as category_name,
                    SUM(fs.quantity) as total_quantity,
                    SUM(fs.sales_amount) as total_sales
                FROM fact_sales fs
                LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                GROUP BY dp.category_name
                ORDER BY total_quantity ASC
                """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生 SQL 结果 (内联聚合表达式排序 ASC): {} 条", nativeResults.size());

        // 2. QueryModel 查询
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(Arrays.asList(
                "product$categoryName",
                "sum(quantity) as totalQuantity",  // 内联聚合表达式
                "salesAmount"
        ));
        request.setOrderBy(createOrderList("totalQuantity", "ASC"));

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(request, 100));
        List<Map<String, Object>> items = result.getItems();

        log.info("QueryModel 查询结果 (内联聚合表达式排序 ASC): {} 条", items.size());

        // 3. 验证结果顺序一致（按 totalQuantity 升序）
        assertEquals(nativeResults.size(), items.size(), "结果行数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> row = items.get(i);

            String categoryName = (String) nativeRow.get("category_name");
            log.info("行 {}: categoryName={}, totalQuantity={}, salesAmount={}",
                    i, categoryName, row.get("totalQuantity"), row.get("salesAmount"));

            assertEquals(categoryName, row.get("product$categoryName"),
                    "品类名称应一致（按totalQuantity排序后）: 行 " + i);
            assertEquals(toLong(nativeRow.get("total_quantity")), toLong(row.get("totalQuantity")),
                    "totalQuantity 应一致: " + categoryName);
            assertDecimalEquals(nativeRow.get("total_sales"), row.get("salesAmount"),
                    "salesAmount 应一致: " + categoryName);
        }

        // 4. 额外验证：结果确实是按 totalQuantity 升序
        if (items.size() >= 2) {
            Long first = toLong(items.get(0).get("totalQuantity"));
            Long second = toLong(items.get(1).get("totalQuantity"));
            assertTrue(first <= second,
                    "第一行 totalQuantity 应 <= 第二行（升序）");
        }
    }

    @Test
    @Order(15)
    @DisplayName("Engine层orderBy - 多字段排序")
    void testEngineOrderBy_MultipleFields() {
        // 测试：多字段排序在 GROUP BY 场景下的处理
        // 先按品类名称升序，再按销售额降序

        // 1. 原生 SQL 查询
        String nativeSql = """
                SELECT
                    dp.category_name as category_name,
                    dp.brand as brand,
                    SUM(fs.sales_amount) as total_sales
                FROM fact_sales fs
                LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                GROUP BY dp.category_name, dp.brand
                ORDER BY dp.category_name ASC, total_sales DESC
                """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生 SQL 结果 (多字段排序): {} 条", nativeResults.size());

        // 2. QueryModel 查询
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(Arrays.asList(
                "product$categoryName",
                "product$brand",
                "salesAmount"
        ));

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("product$categoryName", "ASC"));
        orders.add(createOrder("salesAmount", "DESC"));
        request.setOrderBy(orders);

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(request, 100));
        List<Map<String, Object>> items = result.getItems();

        log.info("QueryModel 查询结果 (多字段排序): {} 条", items.size());

        // 3. 验证结果顺序一致
        assertEquals(nativeResults.size(), items.size(), "结果行数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> row = items.get(i);

            String categoryName = (String) nativeRow.get("category_name");
            String brand = (String) nativeRow.get("brand");
            log.info("行 {}: categoryName={}, brand={}, salesAmount={}",
                    i, categoryName, brand, row.get("salesAmount"));

            assertEquals(categoryName, row.get("product$categoryName"),
                    "品类名称应一致: 行 " + i);
            assertEquals(brand, row.get("product$brand"),
                    "品牌应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("total_sales"), row.get("salesAmount"),
                    "salesAmount 应一致: 行 " + i);
        }
    }

    @Test
    @Order(16)
    @DisplayName("Engine层orderBy - 不在SELECT中的字段被忽略")
    void testEngineOrderBy_FieldNotInSelect_Ignored() {
        // 测试：orderBy 字段不在 SELECT 中时应被忽略
        // 这验证了 Engine 层的最后一道防线

        // 1. 原生 SQL 查询（只按品类名称排序，因为 orderId 不在 SELECT 中）
        String nativeSql = """
                SELECT
                    dp.category_name as category_name,
                    SUM(fs.sales_amount) as total_sales
                FROM fact_sales fs
                LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                GROUP BY dp.category_name
                ORDER BY dp.category_name ASC
                """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生 SQL 结果: {} 条", nativeResults.size());

        // 2. QueryModel 查询 - 请求按 orderId 排序（不在 SELECT 中）
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(Arrays.asList(
                "product$categoryName",
                "salesAmount"
        ));

        // 故意添加一个不在 SELECT 中的排序字段
        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("product$categoryName", "ASC"));
        orders.add(createOrder("orderId", "DESC"));  // orderId 不在 SELECT 中
        request.setOrderBy(orders);

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(request, 100));
        List<Map<String, Object>> items = result.getItems();

        log.info("QueryModel 查询结果 (含无效排序字段): {} 条", items.size());

        // 3. 验证结果：应该成功执行，只是 orderId 排序被忽略
        assertEquals(nativeResults.size(), items.size(), "结果行数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> row = items.get(i);

            String categoryName = (String) nativeRow.get("category_name");
            log.info("行 {}: categoryName={}, salesAmount={}",
                    i, categoryName, row.get("salesAmount"));

            assertEquals(categoryName, row.get("product$categoryName"),
                    "品类名称应一致: 行 " + i);
            assertDecimalEquals(nativeRow.get("total_sales"), row.get("salesAmount"),
                    "salesAmount 应一致: " + categoryName);
        }
    }

    @Test
    @Order(17)
    @DisplayName("Engine层orderBy - 混合场景：维度+聚合+formulaDef排序")
    void testEngineOrderBy_MixedScenario() {
        // 测试：混合使用维度字段、聚合字段、formulaDef 字段进行排序

        // 1. 原生 SQL 查询
        String nativeSql = """
                SELECT
                    dp.category_name as category_name,
                    SUM(fs.quantity) as total_quantity,
                    SUM(fs.tax_amount + 1) as tax_amount2,
                    SUM(fs.sales_amount) as total_sales
                FROM fact_sales fs
                LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                GROUP BY dp.category_name
                ORDER BY dp.category_name ASC, tax_amount2 DESC
                """;
        List<Map<String, Object>> nativeResults = executeQuery(nativeSql);
        log.info("原生 SQL 结果 (混合排序): {} 条", nativeResults.size());

        // 2. QueryModel 查询
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(Arrays.asList(
                "product$categoryName",
                "sum(quantity) as totalQuantity",
                "taxAmount2",
                "salesAmount"
        ));

        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder("product$categoryName", "ASC"));
        orders.add(createOrder("taxAmount2", "DESC"));
        request.setOrderBy(orders);

        PagingResultImpl result = queryFacade.queryModelData(
                PagingRequest.buildPagingRequest(request, 100));
        List<Map<String, Object>> items = result.getItems();

        log.info("QueryModel 查询结果 (混合排序): {} 条", items.size());

        // 3. 验证结果
        assertEquals(nativeResults.size(), items.size(), "结果行数应一致");

        for (int i = 0; i < nativeResults.size(); i++) {
            Map<String, Object> nativeRow = nativeResults.get(i);
            Map<String, Object> row = items.get(i);

            String categoryName = (String) nativeRow.get("category_name");
            log.info("行 {}: categoryName={}, totalQuantity={}, taxAmount2={}, salesAmount={}",
                    i, categoryName, row.get("totalQuantity"), row.get("taxAmount2"), row.get("salesAmount"));

            assertEquals(categoryName, row.get("product$categoryName"),
                    "品类名称应一致: 行 " + i);
            assertEquals(toLong(nativeRow.get("total_quantity")), toLong(row.get("totalQuantity")),
                    "totalQuantity 应一致: " + categoryName);
            assertDecimalEquals(nativeRow.get("tax_amount2"), row.get("taxAmount2"),
                    "taxAmount2 应一致: " + categoryName);
            assertDecimalEquals(nativeRow.get("total_sales"), row.get("salesAmount"),
                    "salesAmount 应一致: " + categoryName);
        }
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private GroupRequestDef createGroup(String field, String agg) {
        GroupRequestDef group = new GroupRequestDef();
        group.setField(field);
        group.setAgg(agg);
        return group;
    }

    private OrderRequestDef createOrder(String field, String order) {
        OrderRequestDef orderDef = new OrderRequestDef();
        orderDef.setField(field);
        orderDef.setOrder(order);
        return orderDef;
    }

    private List<OrderRequestDef> createOrderList(String field, String order) {
        List<OrderRequestDef> orders = new ArrayList<>();
        orders.add(createOrder(field, order));
        return orders;
    }

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

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }
}
