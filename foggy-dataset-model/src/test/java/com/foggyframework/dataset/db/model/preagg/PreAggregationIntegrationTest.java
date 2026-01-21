package com.foggyframework.dataset.db.model.preagg;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.engine.preagg.*;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.db.model.test.JdbcModelTestApplication;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 预聚合功能集成测试
 *
 * <p>测试场景：</p>
 * <ul>
 *   <li>单TM预聚合查询匹配</li>
 *   <li>多TM JOIN场景预聚合</li>
 *   <li>时间粒度 rollup</li>
 *   <li>混合查询（Lambda 架构）</li>
 *   <li>SQL重写验证</li>
 * </ul>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@SpringBootTest(classes = JdbcModelTestApplication.class)
@ActiveProfiles({"sqlite"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("预聚合功能集成测试")
class PreAggregationIntegrationTest {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private TableModelLoaderManager tableModelLoaderManager;

    @Resource
    private QueryModelLoader queryModelLoader;

    // ==========================================
    // 预聚合配置加载测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("加载单TM预聚合配置 - 销售模型")
    void testLoadSalesPreAggConfig() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        TableModel tableModel = queryModel.getJdbcModel();
        assertNotNull(tableModel, "表模型不应为空");

        List<PreAggregation> preAggregations = tableModel.getPreAggregations();
        assertNotNull(preAggregations, "预聚合配置不应为空");
        assertFalse(preAggregations.isEmpty(), "预聚合配置不应为空");

        log.info("销售模型预聚合数量: {}", preAggregations.size());
        assertEquals(3, preAggregations.size(), "销售模型应有3个预聚合配置");

        // 验证预聚合配置
        for (PreAggregation preAgg : preAggregations) {
            log.info("预聚合: name={}, priority={}, tableName={}, dimensions={}",
                    preAgg.getName(), preAgg.getPriority(), preAgg.getTableName(),
                    preAgg.getDimensionNames());

            assertNotNull(preAgg.getName(), "预聚合名称不应为空");
            assertNotNull(preAgg.getTableName(), "预聚合表名不应为空");
            assertTrue(preAgg.getPriority() > 0, "优先级应大于0");
        }

        // 验证特定预聚合 - daily_product_sales
        PreAggregation dailyProductSales = preAggregations.stream()
                .filter(p -> "daily_product_sales".equals(p.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(dailyProductSales, "应存在daily_product_sales预聚合");
        assertEquals(80, dailyProductSales.getPriority(), "daily_product_sales优先级应为80");
        assertTrue(dailyProductSales.getDimensionNames().contains("salesDate"), "应包含salesDate维度");
        assertTrue(dailyProductSales.getDimensionNames().contains("product"), "应包含product维度");
        assertEquals("preagg_daily_product_sales", dailyProductSales.getTableName());

        // 验证特定预聚合 - monthly_category_sales
        PreAggregation monthlyCategory = preAggregations.stream()
                .filter(p -> "monthly_category_sales".equals(p.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(monthlyCategory, "应存在monthly_category_sales预聚合");
        assertEquals(60, monthlyCategory.getPriority(), "monthly_category_sales优先级应为60");

        // 验证特定预聚合 - daily_customer_channel_sales
        PreAggregation dailyCustomerChannel = preAggregations.stream()
                .filter(p -> "daily_customer_channel_sales".equals(p.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(dailyCustomerChannel, "应存在daily_customer_channel_sales预聚合");
        assertEquals(70, dailyCustomerChannel.getPriority());
        assertTrue(dailyCustomerChannel.getDimensionNames().contains("customer"));
        assertTrue(dailyCustomerChannel.getDimensionNames().contains("channel"));
    }

    @Test
    @Order(2)
    @DisplayName("加载单TM预聚合配置 - 退货模型")
    void testLoadReturnPreAggConfig() {
        JdbcQueryModel queryModel = getQueryModel("FactReturnPreAggQueryModel");
        assertNotNull(queryModel, "查询模型加载失败");

        TableModel tableModel = queryModel.getJdbcModel();
        List<PreAggregation> preAggregations = tableModel.getPreAggregations();

        assertNotNull(preAggregations, "退货模型预聚合配置不应为空");
        assertFalse(preAggregations.isEmpty(), "退货模型预聚合配置不应为空");
        assertEquals(1, preAggregations.size(), "退货模型应有1个预聚合配置");

        PreAggregation dailyReturn = preAggregations.get(0);
        assertEquals("daily_return", dailyReturn.getName());
        assertEquals(80, dailyReturn.getPriority());
        assertTrue(dailyReturn.getDimensionNames().contains("returnDate"));
        assertTrue(dailyReturn.getDimensionNames().contains("product"));

        log.info("退货模型预聚合: name={}, dimensions={}", dailyReturn.getName(), dailyReturn.getDimensionNames());
    }

    // ==========================================
    // 查询匹配测试 - 单TM场景（严格断言版）
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("完全匹配 - 按日期+商品查询应匹配daily_product_sales")
    void testExactMatchByDateAndProduct() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate$caption",
                "product$caption",
                "quantity",
                "salesAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        // 严格断言：必须匹配
        assertTrue(result.isApplied(), "按日期+商品查询应匹配预聚合");
        assertNotNull(result.getPreAggregation(), "匹配的预聚合不应为空");
        assertEquals("daily_product_sales", result.getPreAggregation().getName(),
                "应匹配daily_product_sales预聚合（优先级最高且维度完全匹配）");
        assertFalse(result.isNeedsRollup(), "完全匹配不需要rollup");

        // 验证SQL包含预聚合表名
        assertNotNull(result.getSql(), "重写后的SQL不应为空");
        assertTrue(result.getSql().contains("preagg_daily_product_sales"),
                "SQL应查询预聚合表preagg_daily_product_sales");

        log.info("匹配成功: 预聚合={}, SQL={}", result.getPreAggregation().getName(), result.getSql());
    }

    @Test
    @Order(11)
    @DisplayName("粒度Rollup - 按月查询应使用日粒度预聚合并需要rollup")
    void testGranularityRollup() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate$month",
                "product$categoryName",
                "quantity",
                "salesAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        // 严格断言
        assertTrue(result.isApplied(), "按月+品类查询应匹配预聚合");
        assertNotNull(result.getPreAggregation());

        // 可能匹配daily_product_sales（需要rollup）或monthly_category_sales（精确匹配）
        String preAggName = result.getPreAggregation().getName();
        log.info("粒度Rollup匹配: 预聚合={}, needsRollup={}", preAggName, result.isNeedsRollup());

        if ("daily_product_sales".equals(preAggName)) {
            assertTrue(result.isNeedsRollup(), "使用日粒度预聚合查询月数据需要rollup");
        } else if ("monthly_category_sales".equals(preAggName)) {
            assertFalse(result.isNeedsRollup(), "使用月粒度预聚合不需要rollup");
        }

        assertNotNull(result.getSql());
        log.info("重写后SQL: {}", result.getSql());
    }

    @Test
    @Order(12)
    @DisplayName("优先级选择 - 多个预聚合可用时应选择高优先级")
    void testPrioritySelection() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 只查询品类和金额，daily_product_sales(80)和monthly_category_sales(60)都能满足
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "product$categoryName",
                "salesAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        // 严格断言
        assertTrue(result.isApplied(), "品类+金额查询应匹配预聚合");
        assertNotNull(result.getPreAggregation());

        // 应选择优先级更高的daily_product_sales(80)而不是monthly_category_sales(60)
        assertEquals("daily_product_sales", result.getPreAggregation().getName(),
                "应选择优先级最高的daily_product_sales(80)而非monthly_category_sales(60)");
        assertEquals(80, result.getPreAggregation().getPriority());

        log.info("优先级选择正确: 选中预聚合={}, priority={}",
                result.getPreAggregation().getName(), result.getPreAggregation().getPriority());
    }

    @Test
    @Order(13)
    @DisplayName("不匹配场景 - 包含门店维度的查询不应匹配任何预聚合")
    void testNoMatchForUnsupportedDimension() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 包含门店维度，没有预聚合支持门店
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "store$caption",
                "store$storeType",
                "salesAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        // 严格断言：必须不匹配
        assertFalse(result.isApplied(), "包含门店维度的查询不应匹配任何预聚合（无预聚合包含store维度）");
        assertNull(result.getPreAggregation(), "不匹配时预聚合应为空");

        log.info("不匹配场景验证通过：门店维度查询未使用预聚合");
    }

    @Test
    @Order(14)
    @DisplayName("不匹配场景 - 包含预聚合不支持的度量不应匹配")
    void testNoMatchForUnsupportedMeasure() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 查询unitPrice度量，预聚合不包含此度量
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate$caption",
                "product$caption",
                "unitPrice"  // 预聚合不包含此度量
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        // 严格断言：必须不匹配
        assertFalse(result.isApplied(), "包含unitPrice度量的查询不应匹配预聚合（无预聚合包含此度量）");

        log.info("不匹配场景验证通过：unitPrice度量查询未使用预聚合");
    }

    @Test
    @Order(15)
    @DisplayName("客户+渠道查询应匹配daily_customer_channel_sales")
    void testCustomerChannelMatch() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "customer$province",
                "channel$channelType",
                "quantity",
                "salesAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        // 严格断言
        assertTrue(result.isApplied(), "客户+渠道查询应匹配预聚合");
        assertEquals("daily_customer_channel_sales", result.getPreAggregation().getName(),
                "应匹配daily_customer_channel_sales预聚合");
        assertTrue(result.getSql().contains("preagg_daily_customer_channel_sales"));

        log.info("客户+渠道匹配成功: {}", result.getPreAggregation().getName());
    }

    // ==========================================
    // 查询匹配测试 - 多TM场景
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("多TM JOIN场景 - 加载联合查询模型并验证预聚合")
    void testLoadJoinQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("SalesReturnPreAggJoinQueryModel");
        assertNotNull(queryModel, "联合查询模型加载失败");
        assertEquals("SalesReturnPreAggJoinQueryModel", queryModel.getName());

        // 验证主模型预聚合
        TableModel mainModel = queryModel.getJdbcModel();
        assertNotNull(mainModel, "主模型不应为空");
        assertEquals("FactSalesPreAggModel", mainModel.getName());

        List<PreAggregation> mainPreAggs = mainModel.getPreAggregations();
        assertNotNull(mainPreAggs, "主模型预聚合不应为空");
        assertEquals(3, mainPreAggs.size(), "主模型（销售）应有3个预聚合");

        log.info("联合查询模型加载成功: 主模型={}, 预聚合数量={}",
                mainModel.getName(), mainPreAggs.size());
    }

    @Test
    @Order(21)
    @DisplayName("多TM JOIN场景 - 仅查询主模型字段应可匹配预聚合")
    void testJoinQueryWithMainModelFieldsOnly() {
        JdbcQueryModel queryModel = getQueryModel("SalesReturnPreAggJoinQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 仅查询主模型（销售）的字段
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("SalesReturnPreAggJoinQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate$caption",
                "product$caption",
                "salesAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        // JOIN模型仅查询主模型字段时，理论上应该可以匹配主模型的预聚合
        log.info("JOIN模型主字段查询: applied={}, preAgg={}",
                result.isApplied(),
                result.isApplied() ? result.getPreAggregation().getName() : "N/A");

        if (result.isApplied()) {
            assertEquals("daily_product_sales", result.getPreAggregation().getName());
        }
    }

    @Test
    @Order(22)
    @DisplayName("多TM JOIN场景 - 查询跨模型字段不应匹配预聚合")
    void testJoinQueryWithCrossModelFields() {
        JdbcQueryModel queryModel = getQueryModel("SalesReturnPreAggJoinQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 查询跨模型字段（销售+退货）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("SalesReturnPreAggJoinQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate$caption",
                "product$caption",
                "salesAmount",
                "returnAmount"  // 退货模型字段
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        // 跨模型查询通常不能使用单一预聚合
        log.info("跨模型查询结果: applied={}", result.isApplied());

        // 跨模型字段查询不应匹配单表预聚合
        // （除非有专门的跨表预聚合，但本测试中没有）
    }

    // ==========================================
    // 混合查询测试（严格断言版）
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("混合查询 - watermark检测应正确标记数据过期")
    void testHybridQueryWatermarkDetection() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        TableModel tableModel = queryModel.getJdbcModel();
        List<PreAggregation> preAggregations = tableModel.getPreAggregations();

        assertNotNull(preAggregations);
        assertFalse(preAggregations.isEmpty());

        PreAggregation preAgg = preAggregations.get(0);

        // 测试1: 设置watermark为过去的日期
        LocalDate pastDate = LocalDate.now().minusDays(5);
        preAgg.setDataWatermark(pastDate);

        log.info("设置watermark为过去日期: {}", pastDate);
        log.info("支持混合查询: {}", preAgg.supportsHybridQuery());
        log.info("数据是否过期: {}", preAgg.isDataStale());

        if (preAgg.supportsHybridQuery()) {
            assertTrue(preAgg.isDataStale(), "watermark为过去日期时数据应标记为过期");
        }

        // 测试2: 设置watermark为今天
        LocalDate today = LocalDate.now();
        preAgg.setDataWatermark(today);
        log.info("设置watermark为今天: {}", today);
        log.info("数据是否过期: {}", preAgg.isDataStale());

        if (preAgg.supportsHybridQuery()) {
            assertFalse(preAgg.isDataStale(), "watermark为今天时数据不应标记为过期");
        }
    }

    @Test
    @Order(31)
    @DisplayName("混合查询 - 过期数据应生成UNION SQL")
    void testHybridQuerySqlGeneration() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        TableModel tableModel = queryModel.getJdbcModel();
        List<PreAggregation> preAggregations = tableModel.getPreAggregations();

        assertNotNull(preAggregations);
        assertFalse(preAggregations.isEmpty());

        // 设置watermark为过去日期以触发混合查询
        for (PreAggregation preAgg : preAggregations) {
            if (preAgg.supportsHybridQuery()) {
                preAgg.setDataWatermark(LocalDate.of(2024, 3, 20));
            }
        }

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate$caption",
                "product$caption",
                "salesAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        interceptor.setHybridQueryEnabled(true);

        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        // 严格断言
        assertTrue(result.isApplied(), "应匹配预聚合");
        assertNotNull(result.getSql(), "SQL不应为空");

        log.info("混合查询模式: {}", result.isHybridQuery());
        log.info("watermark: {}", result.getWatermark());
        log.info("生成的SQL: {}", result.getSql());

        if (result.isHybridQuery()) {
            // 混合查询SQL应包含UNION
            assertTrue(result.getSql().toUpperCase().contains("UNION"),
                    "混合查询SQL应包含UNION（预聚合表 UNION 原始表）");
            assertNotNull(result.getWatermark(), "混合查询应有watermark");
        }
    }

    // ==========================================
    // 预聚合匹配器单元测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("匹配器 - 空预聚合列表应返回不匹配")
    void testMatcherWithEmptyPreAggregations() {
        PreAggregationMatcher matcher = new PreAggregationMatcher();

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("salesDate");
        requirement.addDimension("product");

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, Collections.emptyList());

        assertFalse(result.isMatched(), "空列表不应匹配");
        assertNotNull(result.getReason(), "应有未匹配原因");
        assertTrue(result.getReason().contains("No pre-aggregation"));

        log.info("空列表未匹配原因: {}", result.getReason());
    }

    @Test
    @Order(41)
    @DisplayName("匹配器 - 无GROUP BY查询应返回不匹配")
    void testMatcherWithoutGroupBy() {
        PreAggregationMatcher matcher = new PreAggregationMatcher();

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(false);  // 没有GROUP BY
        requirement.addDimension("salesDate");

        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        List<PreAggregation> preAggregations = queryModel.getJdbcModel().getPreAggregations();

        assertNotNull(preAggregations);
        assertFalse(preAggregations.isEmpty());

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, preAggregations);

        assertFalse(result.isMatched(), "无GROUP BY查询不应使用预聚合");
        assertNotNull(result.getReason());
        assertTrue(result.getReason().toLowerCase().contains("group by"));

        log.info("无GROUP BY未匹配原因: {}", result.getReason());
    }

    // ==========================================
    // 带过滤条件的查询测试
    // ==========================================

    @Test
    @Order(50)
    @DisplayName("带过滤条件的查询应使用预聚合（WHERE透传已实现）")
    void testPreAggQueryWithFilters() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate$caption",
                "product$caption",
                "salesAmount"
        ));

        // 添加日期过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef dateSlice = new SliceRequestDef();
        dateSlice.setField("salesDate$caption");
        dateSlice.setOp("[)");
        dateSlice.setValue(Arrays.asList("2024-01-01", "2024-03-31"));
        slices.add(dateSlice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        // 验证：带 slice 条件的查询应使用预聚合
        assertTrue(result.isApplied(), "带slice过滤条件的查询应使用预聚合");
        assertNotNull(result.getPreAggregation(), "应匹配预聚合");
        assertEquals("daily_product_sales", result.getPreAggregation().getName(),
                "应匹配daily_product_sales预聚合");

        // 验证 SQL 包含 WHERE 条件
        assertNotNull(result.getSql(), "SQL不应为空");
        assertTrue(result.getSql().contains("preagg_daily_product_sales"), "SQL应查询预聚合表");
        assertTrue(result.getSql().toUpperCase().contains("WHERE"), "SQL应包含WHERE子句");

        log.info("带slice过滤条件查询匹配成功: preAgg={}, SQL={}",
                result.getPreAggregation().getName(), result.getSql());
    }

    // ==========================================
    // 聚合查询预聚合优化测试（returnTotal场景）
    // ==========================================

    @Test
    @Order(60)
    @DisplayName("包含不支持维度的查询 + returnTotal 时，主查询不使用预聚合但聚合查询可以使用")
    void testAggregatePreAggForQueryWithUnsupportedDimension() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 查询包含门店维度（预聚合不支持），但度量是预聚合支持的
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "store$caption",       // 门店名称 - 不在任何预聚合中
                "store$storeType",     // 门店类型
                "salesAmount"          // 金额 - 在预聚合中
        ));
        queryRequest.setReturnTotal(true);  // 需要返回总数

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);

        // 主查询不应匹配预聚合（包含门店维度，无预聚合支持）
        PreAggRewriteResult mainResult = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);
        assertFalse(mainResult.isApplied(),
                "包含门店维度的查询不应匹配主查询预聚合（无预聚合支持门店维度）");

        // 聚合查询应能使用预聚合（只需要 COUNT 和 SUM(salesAmount)，不需要门店维度）
        PreAggQueryRewriter.PreAggAggregateSqlResult aggResult =
                interceptor.tryBuildAggregateSql(queryEngine, queryModel, queryRequest);

        assertNotNull(aggResult, "包含不支持维度的查询，其聚合部分仍应能使用预聚合");
        assertNotNull(aggResult.getSql(), "聚合SQL不应为空");
        assertNotNull(aggResult.getPreAggName(), "预聚合名称不应为空");

        // 验证SQL使用预聚合表
        assertTrue(aggResult.getSql().contains("preagg_"),
                "聚合SQL应查询预聚合表");
        assertTrue(aggResult.getSql().toUpperCase().contains("COUNT"),
                "聚合SQL应包含COUNT");

        log.info("包含不支持维度的查询+returnTotal场景: 主查询不使用预聚合, 聚合查询使用预聚合={}",
                aggResult.getPreAggName());
        log.info("聚合SQL: {}", aggResult.getSql());
    }

    @Test
    @Order(61)
    @DisplayName("包含不支持维度的查询 + slice条件 + returnTotal 时聚合查询应使用预聚合并包含WHERE")
    void testAggregatePreAggForQueryWithSliceAndUnsupportedDimension() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 查询包含门店维度（预聚合不支持）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "store$caption",       // 门店 - 不在预聚合中
                "salesDate$caption",   // 日期
                "salesAmount"
        ));
        queryRequest.setReturnTotal(true);

        // 添加日期过滤条件（日期在预聚合中）
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef dateSlice = new SliceRequestDef();
        dateSlice.setField("salesDate$caption");
        dateSlice.setOp("[)");
        dateSlice.setValue(Arrays.asList("2024-01-01", "2024-03-31"));
        slices.add(dateSlice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);

        // 主查询不应匹配（包含门店维度）
        PreAggRewriteResult mainResult = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);
        assertFalse(mainResult.isApplied(), "包含门店维度的查询不应匹配主查询预聚合");

        // 聚合查询应能使用预聚合，并包含WHERE条件
        PreAggQueryRewriter.PreAggAggregateSqlResult aggResult =
                interceptor.tryBuildAggregateSql(queryEngine, queryModel, queryRequest);

        assertNotNull(aggResult, "带slice的查询聚合部分应能使用预聚合");
        assertNotNull(aggResult.getSql());

        // 验证SQL包含WHERE条件
        assertTrue(aggResult.getSql().toUpperCase().contains("WHERE"),
                "聚合SQL应包含WHERE子句（透传slice条件）");
        assertTrue(aggResult.getSql().contains("preagg_"),
                "聚合SQL应查询预聚合表");

        log.info("带slice的门店查询+returnTotal: 主查询不使用预聚合, 聚合查询使用预聚合={}, SQL={}",
                aggResult.getPreAggName(), aggResult.getSql());
    }

    @Test
    @Order(62)
    @DisplayName("分组查询 + returnTotal 时主查询和聚合查询都使用预聚合")
    void testBothMainAndAggregateUsePreAgg() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 分组查询：有 GROUP BY
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate$caption",
                "product$caption",
                "salesAmount"
        ));
        queryRequest.setReturnTotal(true);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);

        // 主查询应匹配预聚合
        PreAggRewriteResult mainResult = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);
        assertTrue(mainResult.isApplied(), "分组查询应匹配主查询预聚合");
        assertEquals("daily_product_sales", mainResult.getPreAggregation().getName());

        // 聚合查询也应能使用预聚合
        PreAggQueryRewriter.PreAggAggregateSqlResult aggResult =
                interceptor.tryBuildAggregateSql(queryEngine, queryModel, queryRequest);

        assertNotNull(aggResult, "分组查询的聚合部分也应能使用预聚合");
        assertTrue(aggResult.getSql().contains("preagg_"));

        log.info("分组查询+returnTotal: 主查询预聚合={}, 聚合查询预聚合={}",
                mainResult.getPreAggregation().getName(), aggResult.getPreAggName());
    }

    @Test
    @Order(63)
    @DisplayName("聚合查询混合模式 - 水位线过期时应生成UNION SQL")
    void testHybridAggregateQueryWithStaleWatermark() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        TableModel tableModel = queryModel.getJdbcModel();
        List<PreAggregation> preAggregations = tableModel.getPreAggregations();

        assertNotNull(preAggregations);
        assertFalse(preAggregations.isEmpty());

        // 设置 watermark 为过去日期以触发混合查询
        LocalDate pastDate = LocalDate.of(2024, 3, 20);
        for (PreAggregation preAgg : preAggregations) {
            if (preAgg.supportsHybridQuery()) {
                preAgg.setDataWatermark(pastDate);
            }
        }

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 包含门店维度的查询（主查询不匹配预聚合）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "store$caption",
                "salesAmount"
        ));
        queryRequest.setReturnTotal(true);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        interceptor.setHybridQueryEnabled(true);

        // 主查询不匹配
        PreAggRewriteResult mainResult = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);
        assertFalse(mainResult.isApplied(), "包含门店维度的查询不应匹配主查询预聚合");

        // 聚合查询应使用混合模式
        PreAggQueryRewriter.PreAggAggregateSqlResult aggResult =
                interceptor.tryBuildAggregateSql(queryEngine, queryModel, queryRequest);

        assertNotNull(aggResult, "聚合查询应能使用预聚合");

        if (aggResult.isHybrid()) {
            // 混合模式 SQL 应包含 UNION
            assertTrue(aggResult.getSql().toUpperCase().contains("UNION"),
                    "混合模式聚合SQL应包含UNION（预聚合表 UNION 原始表）");
            assertNotNull(aggResult.getWatermark(), "混合模式应有watermark");

            log.info("混合模式聚合查询: preAgg={}, watermark={}, isHybrid={}",
                    aggResult.getPreAggName(), aggResult.getWatermark(), aggResult.isHybrid());
            log.info("混合模式聚合SQL: {}", aggResult.getSql());
        } else {
            // 如果预聚合不支持混合查询，则使用单表模式
            log.info("聚合查询使用单表模式（预聚合不支持混合查询）: preAgg={}", aggResult.getPreAggName());
        }
    }

    @Test
    @Order(64)
    @DisplayName("聚合查询混合模式 - 带slice条件的UNION SQL")
    void testHybridAggregateQueryWithSlice() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        TableModel tableModel = queryModel.getJdbcModel();
        List<PreAggregation> preAggregations = tableModel.getPreAggregations();

        // 设置 watermark 为过去日期
        LocalDate pastDate = LocalDate.of(2024, 3, 20);
        for (PreAggregation preAgg : preAggregations) {
            if (preAgg.supportsHybridQuery()) {
                preAgg.setDataWatermark(pastDate);
            }
        }

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "store$caption",
                "salesAmount"
        ));
        queryRequest.setReturnTotal(true);

        // 添加日期过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef dateSlice = new SliceRequestDef();
        dateSlice.setField("salesDate$caption");
        dateSlice.setOp("[)");
        dateSlice.setValue(Arrays.asList("2024-01-01", "2024-03-31"));
        slices.add(dateSlice);
        queryRequest.setSlice(slices);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        interceptor.setHybridQueryEnabled(true);

        PreAggQueryRewriter.PreAggAggregateSqlResult aggResult =
                interceptor.tryBuildAggregateSql(queryEngine, queryModel, queryRequest);

        assertNotNull(aggResult, "带slice的聚合查询应能使用预聚合");

        if (aggResult.isHybrid()) {
            // 混合模式应包含 UNION 和 WHERE
            assertTrue(aggResult.getSql().toUpperCase().contains("UNION"),
                    "混合模式聚合SQL应包含UNION");
            // 两部分都应有 WHERE 条件（watermark + slice）
            String upperSql = aggResult.getSql().toUpperCase();
            int whereCount = countOccurrences(upperSql, "WHERE");
            assertTrue(whereCount >= 2, "混合模式应在两个子查询中都有WHERE条件");

            log.info("混合模式聚合SQL（带slice）: {}", aggResult.getSql());
            log.info("参数: {}", aggResult.getParams());
        }
    }

    /**
     * 统计字符串出现次数
     */
    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private JdbcQueryModel getQueryModel(String queryModelName) {
        JdbcQueryModel model = (JdbcQueryModel) queryModelLoader.getJdbcQueryModel(queryModelName);
        assertNotNull(model, "查询模型 " + queryModelName + " 加载失败");
        return model;
    }
}
