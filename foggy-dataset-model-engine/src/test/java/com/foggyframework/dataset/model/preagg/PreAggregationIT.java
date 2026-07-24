package com.foggyframework.dataset.model.preagg;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.preagg.internal.PreAggWatermarkResolver;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.model.engine.preagg.*;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.DbColumn;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.spi.TableModelLoaderManager;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.model.spi.preagg.TimeGranularity;
import com.foggyframework.dataset.model.spi.support.AggregationDbColumn;
import com.foggyframework.dataset.model.test.JdbcModelTestApplication;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

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
class PreAggregationIT {

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

    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

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
        assertEquals("salesDate$caption", dailyProductSales.getWatermarkColumn(),
                "增量水位必须绑定 DATE caption，不能将 LocalDate 用于数值代理键");
        assertEquals("full_date", dailyProductSales.getExplicitDimensionPropertyColumnNames()
                .get("salesDate$caption"));
        assertEquals("date_key", dailyProductSales.getExplicitDimensionPropertyColumnNames()
                .get("salesDate$id"));
        assertEquals("product_key", dailyProductSales.getExplicitDimensionPropertyColumnNames()
                .get("product$id"));
        PreAggWatermarkResolver.Resolution salesWatermark = PreAggWatermarkResolver.resolve(
                dailyProductSales, tableModel, dailyProductSales.getRefreshConfig());
        assertDoesNotThrow(() -> PreAggWatermarkResolver.requireLocalDateBounds(
                salesWatermark, queryModel.getDialect()),
                "loaded built-in sales TM must expose a governed SQLite date caption");
        assertEquals("business_date", salesWatermark.dimension().getTimeRole());

        // 验证特定预聚合 - monthly_category_sales
        PreAggregation monthlyCategory = preAggregations.stream()
                .filter(p -> "monthly_category_sales".equals(p.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(monthlyCategory, "应存在monthly_category_sales预聚合");
        assertEquals(60, monthlyCategory.getPriority(), "monthly_category_sales优先级应为60");
        assertEquals(Set.of("caption"), monthlyCategory.getDimensionProperties("salesDate"));
        assertEquals(Set.of("id", "categoryName"), monthlyCategory.getDimensionProperties("product"));
        assertEquals("year_month", monthlyCategory.getExplicitDimensionPropertyColumnNames()
                .get("salesDate$caption"));
        assertEquals("product_key", monthlyCategory.getExplicitDimensionPropertyColumnNames()
                .get("product$id"));

        // 验证特定预聚合 - daily_customer_channel_sales
        PreAggregation dailyCustomerChannel = preAggregations.stream()
                .filter(p -> "daily_customer_channel_sales".equals(p.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(dailyCustomerChannel, "应存在daily_customer_channel_sales预聚合");
        assertEquals(70, dailyCustomerChannel.getPriority());
        assertTrue(dailyCustomerChannel.getDimensionNames().contains("customer"));
        assertTrue(dailyCustomerChannel.getDimensionNames().contains("channel"));
        assertEquals(Set.of("channelType"),
                dailyCustomerChannel.getDimensionProperties("channel"),
                "dimensionProperties 必须使用模型语义名而不是物理 snake_case");
        assertEquals("salesDate$caption", dailyCustomerChannel.getWatermarkColumn());
        assertEquals("full_date", dailyCustomerChannel.getExplicitDimensionPropertyColumnNames()
                .get("salesDate$caption"));
        assertEquals("customer_key", dailyCustomerChannel.getExplicitDimensionPropertyColumnNames()
                .get("customer$id"));
        assertEquals("channel_key", dailyCustomerChannel.getExplicitDimensionPropertyColumnNames()
                .get("channel$id"));
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
        assertEquals("returnDate$caption", dailyReturn.getWatermarkColumn());
        assertEquals("full_date", dailyReturn.getExplicitDimensionPropertyColumnNames()
                .get("returnDate$caption"));
        PreAggWatermarkResolver.Resolution returnWatermark = PreAggWatermarkResolver.resolve(
                dailyReturn, tableModel, dailyReturn.getRefreshConfig());
        assertDoesNotThrow(() -> PreAggWatermarkResolver.requireLocalDateBounds(
                returnWatermark, queryModel.getDialect()),
                "loaded built-in return TM must expose a governed SQLite date caption");
        assertEquals("business_date", returnWatermark.dimension().getTimeRole());

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

        PreAggRewriteResult result = rewriteWithSnapshotOnlyPolicy(queryModel, queryEngine, queryRequest);

        // 严格断言：必须匹配
        assertTrue(result.isApplied(), "按日期+商品查询应匹配预聚合");
        assertNotNull(result.getPreAggregation(), "匹配的预聚合不应为空");
        assertEquals("daily_product_sales", result.getPreAggregation().getName(),
                "应匹配daily_product_sales预聚合（优先级最高且维度完全匹配）");
        assertTrue(result.isNeedsRollup(),
                "caption can repeat across dimension members, so caption grouping requires rollup");

        // 验证SQL包含预聚合表名
        assertNotNull(result.getSql(), "重写后的SQL不应为空");
        assertTrue(result.getSql().contains("preagg_daily_product_sales"),
                "SQL应查询预聚合表preagg_daily_product_sales");

        log.info("匹配成功: 预聚合={}, SQL={}", result.getPreAggregation().getName(), result.getSql());
    }

    @Test
    @Order(11)
    @DisplayName("粒度Rollup - 月属性必须由日表显式物化，不能猜测月表列")
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

        PreAggQueryRequirement requirement = new PreAggQueryRequirementBuilder()
                .build(queryRequest, queryEngine.getJdbcQuery(), queryModel);
        PreAggregation monthly = queryModel.getJdbcModel().getPreAggregations().stream()
                .filter(preAgg -> "monthly_category_sales".equals(preAgg.getName()))
                .findFirst()
                .orElseThrow();
        assertFalse(requirement.isSatisfiableBy(monthly),
                "MONTH grain does not prove a physical salesDate$month column; year_month is not equivalent");
        assertFalse(new PreAggregationMatcher().findBestMatch(requirement, List.of(monthly)).isMatched(),
                "monthly-only candidate must fail closed instead of emitting pa.month");

        PreAggRewriteResult result = rewriteWithSnapshotOnlyPolicy(queryModel, queryEngine, queryRequest);

        // 严格断言
        assertTrue(result.isApplied(), "按月+品类查询应匹配预聚合");
        assertNotNull(result.getPreAggregation());

        assertEquals("daily_product_sales", result.getPreAggregation().getName(),
                "只有显式物化 month 列的日表可以服务该查询");
        assertTrue(result.isNeedsRollup(), "使用日粒度预聚合查询月数据需要rollup");

        assertNotNull(result.getSql());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                result.getSql(), result.getParams().toArray());
        assertFalse(rows.isEmpty(), "重写 SQL 必须真实执行且返回月度分组");
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

        PreAggRewriteResult result = rewriteWithSnapshotOnlyPolicy(queryModel, queryEngine, queryRequest);

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

        PreAggRewriteResult result = rewriteWithSnapshotOnlyPolicy(queryModel, queryEngine, queryRequest);

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

        PreAggRewriteResult result = rewriteWithSnapshotOnlyPolicy(queryModel, queryEngine, queryRequest);

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

        PreAggRewriteResult result = rewriteWithSnapshotOnlyPolicy(queryModel, queryEngine, queryRequest);

        // 严格断言
        assertTrue(result.isApplied(), "客户+渠道查询应匹配预聚合");
        assertEquals("daily_customer_channel_sales", result.getPreAggregation().getName(),
                "应匹配daily_customer_channel_sales预聚合");
        assertTrue(result.getSql().contains("preagg_daily_customer_channel_sales"));

        log.info("客户+渠道匹配成功: {}", result.getPreAggregation().getName());
    }

    @Test
    @Order(16)
    @DisplayName("formulaDef语义度量命中预聚合后与真实SQL结果一致")
    void testFormulaSemanticMeasurePreAggResultMatchesNativeSql() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate$caption",
                "product$caption",
                "salesAmountFormulaYuan"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        interceptor.setHybridQueryEnabled(false);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        assertTrue(result.isApplied(), "formulaDef语义度量应命中显式物化后的预聚合");
        assertNotNull(result.getPreAggregation(), "匹配的预聚合不应为空");
        assertEquals("daily_product_sales", result.getPreAggregation().getName(),
                "应使用包含公式语义结果物化列的daily_product_sales");
        assertTrue(result.isNeedsRollup(),
                "date/product captions are coarser than physical dimension keys and require rollup");
        assertTrue(result.getSql().contains("preagg_daily_product_sales"),
                "重写SQL应读取真实预聚合表");
        assertTrue(result.getSql().contains("sales_amount_formula_yuan_sum"),
                "重写SQL应读取公式语义度量的物化列");

        List<Map<String, Object>> preAggRows = jdbcTemplate.queryForList(
                result.getSql(),
                result.getParams() == null ? new Object[0] : result.getParams().toArray()
        );
        List<Map<String, Object>> nativeRows = jdbcTemplate.queryForList("""
                SELECT d.full_date AS "salesDate$caption",
                       p.product_name AS "product$caption",
                       SUM((fs.sales_amount + 0) / 100.0) AS "salesAmountFormulaYuan"
                FROM fact_sales fs
                LEFT JOIN dim_date d ON fs.date_key = d.date_key
                LEFT JOIN dim_product p ON fs.product_key = p.product_key
                GROUP BY d.full_date, p.product_name
                """);

        assertFormulaRowsMatch(nativeRows, preAggRows);
        log.info("formulaDef语义度量预聚合SQL结果验证通过: preAgg={}, rows={}",
                result.getPreAggregation().getName(), preAggRows.size());
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

        PreAggRewriteResult result = rewriteWithSnapshotOnlyPolicy(queryModel, queryEngine, queryRequest);

        assertTrue(result.isApplied(), "仅查询主模型字段时必须命中主模型预聚合");
        assertEquals("daily_product_sales", result.getPreAggregation().getName());
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

        PreAggRewriteResult result = rewriteWithSnapshotOnlyPolicy(queryModel, queryEngine, queryRequest);

        assertFalse(result.isApplied(), "跨模型字段不得命中单表预聚合");
        assertNull(result.getPreAggregation());
    }

    // ==========================================
    // 混合查询测试（严格断言版）
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("混合查询 - watermark检测应正确标记数据过期")
    void testHybridQueryWatermarkDetection() {
        assertTrue(ModelResultContext.QueryCacheConfig.defaultConfig().isHybridQueryEnabled(),
                "production query defaults must preserve the source tail");
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        TableModel tableModel = queryModel.getJdbcModel();
        List<PreAggregation> preAggregations = tableModel.getPreAggregations();

        assertNotNull(preAggregations);
        assertFalse(preAggregations.isEmpty());

        PreAggregation preAgg = preAggregations.stream()
                .filter(PreAggregation::supportsHybridQuery)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "fixture must contain a hybrid-capable pre-aggregation"));

        withRestoredWatermarks(List.of(preAgg), () -> {
            // 测试1: 设置watermark为过去的日期
            LocalDate pastDate = LocalDate.now().minusDays(5);
            preAgg.setDataWatermark(pastDate);

            log.info("设置watermark为过去日期: {}", pastDate);
            log.info("支持混合查询: {}", preAgg.supportsHybridQuery());
            log.info("数据是否过期: {}", preAgg.isDataStale());

            assertTrue(preAgg.isDataStale(), "watermark为过去日期时数据应标记为过期");

            // 测试2: exclusive watermark 为今天时，当天仍由源表 tail 负责
            LocalDate today = LocalDate.now();
            preAgg.setDataWatermark(today);
            log.info("设置watermark为今天: {}", today);
            log.info("数据是否过期: {}", preAgg.isDataStale());

            assertTrue(preAgg.isDataStale(), "exclusive watermark为今天时仍有当天源表tail");

            preAgg.setDataWatermark(today.plusDays(1));
            assertTrue(preAgg.isDataStale(),
                    "future boundary is invalid and must not masquerade as a fresh snapshot");
        });
    }

    @Test
    @Order(31)
    @DisplayName("混合查询 - 两分支真实执行结果应与源表分组一致")
    void testHybridQuerySqlGeneration() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        TableModel tableModel = queryModel.getJdbcModel();
        List<PreAggregation> preAggregations = tableModel.getPreAggregations();

        assertNotNull(preAggregations);
        assertFalse(preAggregations.isEmpty());

        withRestoredWatermarks(preAggregations, () -> {
            LocalDate watermark = LocalDate.of(2024, 1, 2);
            // 排他边界两侧都必须有夹具数据，才能证明 materialized/source 两分支真实合并。
            for (PreAggregation preAgg : preAggregations) {
                if (preAgg.supportsHybridQuery()) {
                    preAgg.setDataWatermark(watermark);
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

            assertTrue(result.isApplied(), "应匹配预聚合");
            assertNotNull(result.getSql(), "SQL不应为空");
            assertTrue(result.isHybridQuery(), "stale watermark 必须进入 hybrid，而不是静默回落单表模式");
            assertTrue(result.getSql().toUpperCase().contains("UNION ALL"),
                    "混合查询SQL应包含预聚合表与源表两分支");
            assertEquals(watermark, result.getWatermark());
            assertIterableEquals(List.of(watermark, watermark), result.getParams(),
                    "DATE watermark must bind once per branch");
            assertTrue(result.getSql().contains("pa.full_date < ?"),
                    "history branch must use the materialized DATE column");
            assertTrue(result.getSql().contains(".full_date >= ?"),
                    "source branch must use the date-dimension physical column");

            Integer materializedRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM preagg_daily_product_sales WHERE full_date < ?",
                    Integer.class, watermark);
            Integer sourceRows = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM fact_sales fs
                    LEFT JOIN dim_date d ON fs.date_key = d.date_key
                    WHERE d.full_date >= ?
                    """, Integer.class, watermark);
            assertNotNull(materializedRows);
            assertNotNull(sourceRows);
            assertTrue(materializedRows > 0, "history branch fixture must contribute rows");
            assertTrue(sourceRows > 0, "fresh source branch fixture must contribute rows");

            List<Map<String, Object>> hybridRows = jdbcTemplate.queryForList(
                    result.getSql(), result.getParams().toArray());
            List<Map<String, Object>> nativeRows = jdbcTemplate.queryForList("""
                    SELECT d.full_date AS "salesDate$caption",
                           p.product_name AS "product$caption",
                           SUM(fs.sales_amount) AS "salesAmount"
                    FROM fact_sales fs
                    LEFT JOIN dim_date d ON fs.date_key = d.date_key
                    LEFT JOIN dim_product p ON fs.product_key = p.product_key
                    GROUP BY d.full_date, p.product_name
                    """);
            assertFalse(nativeRows.isEmpty());
            assertEquals(metricRowsByKey(nativeRows, "salesAmount"),
                    metricRowsByKey(hybridRows, "salesAmount"),
                    "hybrid materialized/source UNION must equal native grouped semantics");

            DbQueryRequestDef watermarkOnlyJoinRequest = new DbQueryRequestDef();
            watermarkOnlyJoinRequest.setQueryModel("FactSalesPreAggQueryModel");
            watermarkOnlyJoinRequest.setColumns(List.of("product$caption", "salesAmount"));
            JdbcModelQueryEngine watermarkOnlyJoinEngine =
                    new JdbcModelQueryEngine(queryModel, sqlFormulaService);
            watermarkOnlyJoinEngine.analysisQueryRequest(
                    systemBundlesContext, watermarkOnlyJoinRequest);
            PreAggRewriteResult watermarkOnlyJoinResult = interceptor.tryRewrite(
                    watermarkOnlyJoinEngine, queryModel, watermarkOnlyJoinRequest);

            assertTrue(watermarkOnlyJoinResult.isApplied());
            assertTrue(watermarkOnlyJoinResult.isHybridQuery());
            assertTrue(watermarkOnlyJoinResult.getSql().toUpperCase(Locale.ROOT)
                            .contains("JOIN DIM_DATE"),
                    "caption watermark must add its proven JOIN even when date is not selected");
            assertTrue(watermarkOnlyJoinResult.getSql().contains(".full_date >= ?"));
            List<Map<String, Object>> watermarkOnlyJoinRows = jdbcTemplate.queryForList(
                    watermarkOnlyJoinResult.getSql(),
                    watermarkOnlyJoinResult.getParams().toArray());
            List<Map<String, Object>> nativeProductRows = jdbcTemplate.queryForList("""
                    SELECT p.product_name AS "product$caption",
                           SUM(fs.sales_amount) AS "salesAmount"
                    FROM fact_sales fs
                    LEFT JOIN dim_product p ON fs.product_key = p.product_key
                    GROUP BY p.product_name
                    """);
            assertEquals(metricRowsByKey(nativeProductRows, "salesAmount"),
                    metricRowsByKey(watermarkOnlyJoinRows, "salesAmount"),
                    "watermark-only JOIN must retain native product grouping semantics");

            JdbcModelQueryEngine noJoinProofEngine =
                    new JdbcModelQueryEngine(queryModel, sqlFormulaService);
            noJoinProofEngine.analysisQueryRequest(
                    systemBundlesContext, watermarkOnlyJoinRequest);
            noJoinProofEngine.getJdbcQuery().setJoinGraph(null);
            PreAggRewriteResult noJoinProofResult = new PreAggQueryRewriter(
                    queryModel, applicationContext).rewrite(
                    PreAggregationMatchResult.hybrid(
                            result.getPreAggregation(), true, watermark, 1),
                    noJoinProofEngine.getJdbcQuery(), watermarkOnlyJoinRequest,
                    noJoinProofEngine);
            assertFalse(noJoinProofResult.isApplied(),
                    "dimension watermark without a provable JOIN path must fail closed");
            assertNull(noJoinProofResult.getSql());

            log.info("混合查询真实执行通过: watermark={}, rows={}",
                    result.getWatermark(), hybridRows.size());
        });
    }

    // ==========================================
    // #005 回归：hybrid 双分支谓词等价性
    // ==========================================

    @Test
    @Order(32)
    @DisplayName("#005 回归 — hybrid+slice 在双分支谓词等价前必须 fail closed")
    void testHybridQueryShouldIncludeOriginalWhereInSourcePart() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        TableModel tableModel = queryModel.getJdbcModel();
        List<PreAggregation> preAggregations = tableModel.getPreAggregations();

        withStaleHybridWatermarks(
                preAggregations, LocalDate.of(2024, 1, 1), () -> {
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

            PreAggQueryRequirement requirement = new PreAggQueryRequirementBuilder()
                    .build(queryRequest, queryEngine.getJdbcQuery(), queryModel);
            PreAggregationMatchResult candidate = new PreAggregationMatcher()
                    .findBestMatch(requirement, preAggregations);
            assertTrue(candidate.isMatched(), "fixture must expose a matching pre-aggregation");
            assertTrue(candidate.isHybridQuery(), "stale watermark must select a hybrid candidate");

            PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
            interceptor.setHybridQueryEnabled(true);
            PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

            assertFalse(result.isApplied(),
                    "slice applied only to the source branch would leak historical rows");
            assertNull(result.getSql(), "fail-closed path must not expose partial hybrid SQL");
            assertTrue(result.getParams().isEmpty(), "fail-closed path must not expose partial params");
        });
    }

    @Test
    @Order(33)
    @DisplayName("#005 回归 — hybrid+多参数 slice 不得生成部分过滤 SQL")
    void testHybridQueryWhereParamsOrderCorrect() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        TableModel tableModel = queryModel.getJdbcModel();
        List<PreAggregation> preAggregations = tableModel.getPreAggregations();

        withStaleHybridWatermarks(
                preAggregations, LocalDate.of(2024, 1, 1), () -> {
            JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("FactSalesPreAggQueryModel");
            queryRequest.setColumns(Arrays.asList(
                    "salesDate$caption",
                    "product$caption",
                    "salesAmount"
            ));

            // 添加日期过滤条件（范围条件产生2个参数）
            List<SliceRequestDef> slices = new ArrayList<>();
            SliceRequestDef dateSlice = new SliceRequestDef();
            dateSlice.setField("salesDate$caption");
            dateSlice.setOp("[)");
            dateSlice.setValue(Arrays.asList("2024-01-01", "2024-06-30"));
            slices.add(dateSlice);
            queryRequest.setSlice(slices);

            queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

            PreAggQueryRequirement requirement = new PreAggQueryRequirementBuilder()
                    .build(queryRequest, queryEngine.getJdbcQuery(), queryModel);
            PreAggregationMatchResult candidate = new PreAggregationMatcher()
                    .findBestMatch(requirement, preAggregations);
            assertTrue(candidate.isMatched(), "fixture must expose a matching pre-aggregation");
            assertTrue(candidate.isHybridQuery(), "stale watermark must select a hybrid candidate");

            PreAggRewriteResult result = new PreAggQueryRewriter(queryModel, applicationContext)
                    .rewrite(candidate, queryEngine.getJdbcQuery(), queryRequest, queryEngine);

            assertFalse(result.isApplied(),
                    "public rewriter entry must refuse predicates not rebuilt on both branches");
            assertNull(result.getSql(), "fail-closed path must not expose partial hybrid SQL");
            assertTrue(result.getParams().isEmpty(), "fail-closed path must not expose partial params");
        });
    }

    @Test
    @Order(34)
    @DisplayName("public hybrid rewriter 对无效 watermark 必须 fail closed")
    void testHybridRewriteRejectsInvalidWatermark() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        PreAggregation preAgg = queryModel.getJdbcModel().getPreAggregations().stream()
                .filter(PreAggregation::supportsHybridQuery)
                .findFirst()
                .orElseThrow();

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(List.of(
                "salesDate$caption",
                "product$caption",
                "salesAmount"
        ));
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationMatchResult unsafeMatch = PreAggregationMatchResult.hybrid(
                preAgg, false, null, 100);
        PreAggRewriteResult result = new PreAggQueryRewriter(queryModel, applicationContext)
                .rewrite(unsafeMatch, queryEngine.getJdbcQuery(), queryRequest, queryEngine);

        assertFalse(result.isApplied());
        assertNull(result.getSql());
        assertTrue(result.getParams().isEmpty());

        PreAggregationMatchResult futureMatch = PreAggregationMatchResult.hybrid(
                preAgg, false, LocalDate.now().plusDays(1), 100);
        PreAggRewriteResult futureResult = new PreAggQueryRewriter(queryModel, applicationContext)
                .rewrite(futureMatch, queryEngine.getJdbcQuery(), queryRequest, queryEngine);
        assertFalse(futureResult.isApplied());
        assertNull(futureResult.getSql());
        assertTrue(futureResult.getParams().isEmpty());

        PreAggregation coarsePreAgg = spy(preAgg);
        when(coarsePreAgg.getGranularity("salesDate")).thenReturn(TimeGranularity.MONTH);
        PreAggregationMatchResult coarseMatch = PreAggregationMatchResult.hybrid(
                coarsePreAgg, false, LocalDate.now(), 100);
        PreAggRewriteResult coarseResult = new PreAggQueryRewriter(queryModel, applicationContext)
                .rewrite(coarseMatch, queryEngine.getJdbcQuery(), queryRequest, queryEngine);
        assertFalse(coarseResult.isApplied(),
                "daily LocalDate split must not be applied to a monthly materialized bucket");
        assertNull(coarseResult.getSql());
        assertTrue(coarseResult.getParams().isEmpty());
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

        PreAggRewriteResult result = snapshotOnlyInterceptor()
                .tryRewrite(queryEngine, queryModel, queryRequest);

        // Explicit snapshot-only policy permits a direct materialized query.
        assertTrue(result.isApplied(), "snapshot-only slice 查询应使用预聚合");
        assertFalse(result.isHybridQuery(), "snapshot-only policy 不应进入 hybrid");
        assertNotNull(result.getPreAggregation(), "应匹配预聚合");
        assertEquals("daily_product_sales", result.getPreAggregation().getName(),
                "应匹配daily_product_sales预聚合");

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
    @DisplayName("包含不支持维度的查询 + returnTotal 时主查询与聚合查询均 fail closed")
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
        interceptor.setHybridQueryEnabled(false);

        // 主查询不应匹配预聚合（包含门店维度，无预聚合支持）
        PreAggRewriteResult mainResult = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);
        assertFalse(mainResult.isApplied(),
                "包含门店维度的查询不应匹配主查询预聚合（无预聚合支持门店维度）");

        // 聚合 total 必须保留门店粒度；直接 COUNT 预聚合行并不等价。
        PreAggQueryRewriter.PreAggAggregateSqlResult aggResult =
                interceptor.tryBuildAggregateSql(queryEngine, queryModel, queryRequest);

        assertNull(aggResult,
                "unsupported store grain must fall back instead of counting materialized rows");
    }

    @Test
    @Order(61)
    @DisplayName("包含不支持维度与 slice 的 returnTotal 聚合查询 fail closed")
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
        interceptor.setHybridQueryEnabled(false);

        // 主查询不应匹配（包含门店维度）
        PreAggRewriteResult mainResult = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);
        assertFalse(mainResult.isApplied(), "包含门店维度的查询不应匹配主查询预聚合");

        // 日期 slice 可重建，但门店粒度仍不可证明。
        PreAggQueryRewriter.PreAggAggregateSqlResult aggResult =
                interceptor.tryBuildAggregateSql(queryEngine, queryModel, queryRequest);

        assertNull(aggResult,
                "a provable slice must not mask an unsupported result grain");
    }

    @Test
    @Order(62)
    @DisplayName("分组查询可用预聚合，但 legacy returnTotal 对 rollup fail closed")
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
        interceptor.setHybridQueryEnabled(false);

        // 主查询应匹配预聚合
        PreAggRewriteResult mainResult = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);
        assertTrue(mainResult.isApplied(), "分组查询应匹配主查询预聚合");
        assertEquals("daily_product_sales", mainResult.getPreAggregation().getName());
        assertTrue(mainResult.isNeedsRollup(),
                "caption 分组可能合并同名 key，必须保留 rollup 语义");

        // Legacy total 直接 COUNT 物化行无法证明 caption rollup 后的行数。
        PreAggQueryRewriter.PreAggAggregateSqlResult aggResult =
                interceptor.tryBuildAggregateSql(queryEngine, queryModel, queryRequest);

        assertNull(aggResult,
                "legacy returnTotal must not count physical rows before caption rollup");

        PreAggregation differentCandidate = queryModel.getJdbcModel().getPreAggregations().stream()
                .filter(preAgg -> preAgg != mainResult.getPreAggregation())
                .findFirst()
                .orElseThrow();
        PreAggregationMatchResult mismatched = PreAggregationMatchResult.matched(
                mainResult.getPreAggregation(), false, 100);
        assertNull(new PreAggQueryRewriter(queryModel, applicationContext)
                        .buildAggregateSql(differentCandidate, queryEngine.getJdbcQuery(),
                                queryRequest, mismatched),
                "aggregate entry must not apply one candidate's proof to another candidate");
    }

    @Test
    @Order(63)
    @DisplayName("legacy returnTotal 在 hybrid 模式下 fail closed")
    void testHybridAggregateQueryWithStaleWatermark() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        TableModel tableModel = queryModel.getJdbcModel();
        List<PreAggregation> preAggregations = tableModel.getPreAggregations();

        assertNotNull(preAggregations);
        assertFalse(preAggregations.isEmpty());

        LocalDate pastDate = LocalDate.of(2024, 1, 1);
        withStaleHybridWatermarks(preAggregations, pastDate, () -> {
            JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

            // 使用与日+商品物化相同的粒度，确保 matcher 确实进入 hybrid。
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
            interceptor.setHybridQueryEnabled(true);

            // 主查询自己的 hybrid builder 能重建分组粒度，仍应保持正向覆盖。
            PreAggRewriteResult mainResult = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);
            assertTrue(mainResult.isApplied(), "exact-grain main query should match pre-aggregation");
            assertTrue(mainResult.isHybridQuery(), "stale watermark should select main hybrid mode");

            // Hybrid aggregate SQL would COUNT materialized group rows together
            // with raw source rows, so it is not equivalent to the final result.
            PreAggQueryRewriter.PreAggAggregateSqlResult aggResult =
                    interceptor.tryBuildAggregateSql(queryEngine, queryModel, queryRequest);

            assertNull(aggResult,
                    "legacy returnTotal must not mix materialized groups with raw source rows");
            assertNull(new PreAggQueryRewriter(queryModel, applicationContext)
                            .buildAggregateSql(mainResult.getPreAggregation(),
                                    queryEngine.getJdbcQuery(), queryRequest),
                    "convenience aggregate entry must not bypass hybrid equivalence checks");
        });
    }

    @Test
    @Order(64)
    @DisplayName("legacy returnTotal 在 hybrid + slice 模式下 fail closed")
    void testHybridAggregateQueryWithSlice() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        TableModel tableModel = queryModel.getJdbcModel();
        List<PreAggregation> preAggregations = tableModel.getPreAggregations();

        LocalDate pastDate = LocalDate.of(2024, 1, 1);
        withStaleHybridWatermarks(preAggregations, pastDate, () -> {
            JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("FactSalesPreAggQueryModel");
            queryRequest.setColumns(Arrays.asList(
                    "salesDate$caption",
                    "product$caption",
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

            assertFalse(interceptor.tryRewrite(queryEngine, queryModel, queryRequest).isApplied(),
                    "main hybrid rewrite must refuse a predicate missing from its history branch");

            PreAggQueryRewriter.PreAggAggregateSqlResult aggResult =
                    interceptor.tryBuildAggregateSql(queryEngine, queryModel, queryRequest);

            assertNull(aggResult,
                    "slice reconstruction does not prove hybrid row-grain equivalence");
        });
    }

    @Test
    @Order(65)
    @DisplayName("显式 groupBy 包装器仅允许 FULL 主查询，hybrid 与 legacy returnTotal fail closed")
    void testExplicitGroupByWrappersFailClosedOutsideFullMainRewrite() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(List.of("salesDate$caption", "product$categoryName", "salesAmount"));
        queryRequest.setGroupBy(List.of(
                group("salesDate$caption", null),
                group("product$categoryName", null),
                group("salesAmount", "SUM")
        ));
        queryRequest.setReturnTotal(true);

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        List<DbColumn> selectColumns = queryEngine.getJdbcQuery().getSelect().getColumns();
        assertTrue(selectColumns.stream().allMatch(AggregationDbColumn.class::isInstance),
                "explicit groupBy must exercise AggregationDbColumn wrappers");
        assertTrue(selectColumns.stream().map(DbColumn::getAggregation)
                        .anyMatch(DbAggregation.NONE::equals));
        assertTrue(selectColumns.stream().map(DbColumn::getAggregation)
                        .anyMatch(DbAggregation.SUM::equals));

        PreAggregationInterceptor fullInterceptor = new PreAggregationInterceptor(applicationContext);
        fullInterceptor.setHybridQueryEnabled(false);
        PreAggRewriteResult fullMain = fullInterceptor.tryRewrite(queryEngine, queryModel, queryRequest);
        assertTrue(fullMain.isApplied(), "FULL main rewrite supports resolved aggregate wrappers");
        assertNull(fullInterceptor.tryBuildAggregateSql(queryEngine, queryModel, queryRequest),
                "legacy returnTotal must fall back when aggregate wrappers require semantic proof");

        List<PreAggregation> preAggregations = queryModel.getJdbcModel().getPreAggregations();
        Map<PreAggregation, Object> originalWatermarks = new IdentityHashMap<>();
        try {
            for (PreAggregation preAgg : preAggregations) {
                if (preAgg.supportsHybridQuery()) {
                    originalWatermarks.put(preAgg, preAgg.getDataWatermark());
                    preAgg.setDataWatermark(LocalDate.of(2024, 1, 1));
                }
            }
            assertFalse(originalWatermarks.isEmpty(), "fixture must contain a hybrid-capable pre-aggregation");

            PreAggregationInterceptor hybridInterceptor = new PreAggregationInterceptor(applicationContext);
            hybridInterceptor.setHybridQueryEnabled(true);
            assertFalse(hybridInterceptor.tryRewrite(queryEngine, queryModel, queryRequest).isApplied(),
                    "hybrid main rewrite must fail closed for aggregate wrappers");
            assertNull(hybridInterceptor.tryBuildAggregateSql(queryEngine, queryModel, queryRequest),
                    "hybrid legacy returnTotal must fail closed for aggregate wrappers");
        } finally {
            originalWatermarks.forEach(PreAggregation::setDataWatermark);
        }
    }

    @Test
    @Order(66)
    @DisplayName("final-stage 候选必须证明时间粒度与物化聚合兼容")
    void testFinalStageCandidateRequiresCompatibleGrainAndAggregation() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        PreAggregation daily = queryModel.getJdbcModel().getPreAggregations().stream()
                .filter(preAgg -> "daily_product_sales".equals(preAgg.getName()))
                .findFirst()
                .orElseThrow();
        PreAggregation monthly = queryModel.getJdbcModel().getPreAggregations().stream()
                .filter(preAgg -> "monthly_category_sales".equals(preAgg.getName()))
                .findFirst()
                .orElseThrow();

        DbQueryRequestDef dayRequest = new DbQueryRequestDef();
        dayRequest.setQueryModel("FactSalesPreAggQueryModel");
        dayRequest.setColumns(List.of("salesDate$caption", "sum(salesAmount) as teamSales"));
        dayRequest.setGroupBy(List.of(group("salesDate$caption", null)));
        dayRequest.setReturnTotal(true);
        JdbcModelQueryEngine dayEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        dayEngine.analysisQueryRequest(systemBundlesContext, dayRequest);

        PreAggQueryRequirement dayRequirement = new PreAggQueryRequirementBuilder()
                .buildFinalStage(dayRequest, dayEngine.getJdbcQuery(), queryModel);
        assertTrue(dayRequirement.isSatisfiableBy(daily));
        assertFalse(dayRequirement.isSatisfiableBy(monthly),
                "MONTH materialization must not serve a DAY caption final stage");
        PreAggQueryRewriter.PreAggAggregateSqlResult dayBuilt = snapshotOnlyInterceptor()
                .tryBuildFinalStageAggregateSql(dayEngine, queryModel, dayRequest);
        assertNotNull(dayBuilt,
                "explicit snapshot policy should serve a compatible DAY materialization");
        assertEquals(daily.getName(), dayBuilt.getPreAggName());
        PreAggQueryRewriter dayRewriter = new PreAggQueryRewriter(queryModel, applicationContext);
        withStaleHybridWatermarks(List.of(daily), LocalDate.of(2024, 1, 1),
                () -> assertNull(dayRewriter.buildFinalStageAggregateSql(
                                daily, dayEngine.getJdbcQuery(), dayRequest),
                        "public final-stage entry must refuse a stale materialization"));

        DbQueryRequestDef maxRequest = new DbQueryRequestDef();
        maxRequest.setQueryModel("FactSalesPreAggQueryModel");
        maxRequest.setColumns(List.of("product$categoryName", "max(salesAmount) as peakSales"));
        maxRequest.setGroupBy(List.of(group("product$categoryName", null)));
        maxRequest.setReturnTotal(true);
        JdbcModelQueryEngine maxEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        maxEngine.analysisQueryRequest(systemBundlesContext, maxRequest);

        assertNull(snapshotOnlyInterceptor().tryBuildFinalStageAggregateSql(
                        maxEngine, queryModel, maxRequest),
                "SUM materialization must not masquerade as a MAX final-stage measure");
    }

    @Test
    @Order(67)
    @DisplayName("日级时间 slice 必须拒绝月级 final-stage 候选")
    void testFinalStageCandidateRequiresCompatibleSliceGrain() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        assertEquals("business_date",
                queryModel.getJdbcModel().findJdbcDimensionByName("salesDate").getTimeRole(),
                "TM timeRole must survive definition-to-runtime model loading");
        PreAggregation daily = queryModel.getJdbcModel().getPreAggregations().stream()
                .filter(preAgg -> "daily_product_sales".equals(preAgg.getName()))
                .findFirst()
                .orElseThrow();
        PreAggregation monthly = queryModel.getJdbcModel().getPreAggregations().stream()
                .filter(preAgg -> "monthly_category_sales".equals(preAgg.getName()))
                .findFirst()
                .orElseThrow();

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesPreAggQueryModel");
        request.setColumns(List.of("product$categoryName", "sum(salesAmount) as teamSales"));
        request.setGroupBy(List.of(group("product$categoryName", null)));
        request.setSlice(List.of(new SliceRequestDef(
                "salesDate$caption", "[)", Arrays.asList("2024-01-01", "2024-01-04"))));
        request.setReturnTotal(true);

        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        engine.analysisQueryRequest(systemBundlesContext, request);
        PreAggQueryRequirement requirement = new PreAggQueryRequirementBuilder()
                .buildFinalStage(request, engine.getJdbcQuery(), queryModel);

        assertEquals(TimeGranularity.DAY, requirement.getQueryGranularities().get("salesDate"));
        assertTrue(requirement.isSatisfiableBy(daily),
                "DAY materialization should satisfy a DAY caption predicate");
        assertFalse(requirement.isSatisfiableBy(monthly),
                "MONTH materialization must not satisfy a DAY caption predicate");

        PreAggQueryRewriter.PreAggAggregateSqlResult built = snapshotOnlyInterceptor()
                .tryBuildFinalStageAggregateSql(engine, queryModel, request);
        assertNotNull(built);
        assertEquals(daily.getName(), built.getPreAggName());
    }

    @Test
    @Order(68)
    @DisplayName("final-stage SQL 对 typed/open-range/LIKE slice 与 native 结果一致")
    void testFinalStagePredicateSqlExecutesWithFormattedParams() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        PreAggregation daily = queryModel.getJdbcModel().getPreAggregations().stream()
                .filter(preAgg -> "daily_product_sales".equals(preAgg.getName()))
                .findFirst()
                .orElseThrow();

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesPreAggQueryModel");
        request.setColumns(List.of("product$categoryName", "sum(salesAmount) as teamSales"));
        request.setGroupBy(List.of(group("product$categoryName", null)));
        request.setSlice(List.of(
                new SliceRequestDef("salesDate$id", "[)", List.of("20240102", "")),
                new SliceRequestDef("product$categoryName", "like", "数码")
        ));
        request.setReturnTotal(true);

        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        engine.analysisQueryRequest(systemBundlesContext, request);
        PreAggQueryRewriter.PreAggAggregateSqlResult built = snapshotOnlyInterceptor()
                .tryBuildFinalStageAggregateSql(engine, queryModel, request);

        assertNotNull(built);
        assertEquals(daily.getName(), built.getPreAggName());
        assertTrue(built.getSql().contains("date_key >= ?"));
        assertFalse(built.getSql().contains("date_key < ?"),
                "empty range end must remain an open endpoint");
        assertTrue(built.getSql().contains("category_name LIKE ?"));
        assertEquals(2, built.getParams().size());
        assertEquals(20240102, built.getParams().get(0));
        assertInstanceOf(Integer.class, built.getParams().get(0));
        assertEquals("%数码%", built.getParams().get(1));

        Map<String, Object> preAggRow = jdbcTemplate.queryForMap(
                built.getSql(), built.getParams().toArray());
        Map<String, Object> nativeRow = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total, SUM(native_final.teamSales) AS teamSales
                FROM (
                    SELECT p.category_name, SUM(fs.sales_amount) AS teamSales
                    FROM fact_sales fs
                    LEFT JOIN dim_product p ON fs.product_key = p.product_key
                    WHERE fs.date_key >= ? AND p.category_name LIKE ?
                    GROUP BY p.category_name
                ) native_final
                """, 20240102, "%数码%");
        Integer unfilteredGroups = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT p.category_name
                    FROM fact_sales fs
                    LEFT JOIN dim_product p ON fs.product_key = p.product_key
                    GROUP BY p.category_name
                ) native_all
                """, Integer.class);

        assertEquals(((Number) nativeRow.get("total")).longValue(),
                ((Number) preAggRow.get("total")).longValue());
        assertEquals(toBigDecimal(nativeRow.get("teamSales")),
                toBigDecimal(preAggRow.get("teamSales")));
        assertNotNull(unfilteredGroups);
        assertTrue(((Number) preAggRow.get("total")).longValue() > 0);
        assertTrue(((Number) preAggRow.get("total")).longValue() < unfilteredGroups);
    }

    private GroupRequestDef group(String field, String aggregation) {
        GroupRequestDef group = new GroupRequestDef();
        group.setField(field);
        group.setAgg(aggregation);
        return group;
    }

    private PreAggRewriteResult rewriteWithSnapshotOnlyPolicy(JdbcQueryModel queryModel,
                                                               JdbcModelQueryEngine queryEngine,
                                                               DbQueryRequestDef queryRequest) {
        return snapshotOnlyInterceptor().tryRewrite(queryEngine, queryModel, queryRequest);
    }

    private PreAggregationInterceptor snapshotOnlyInterceptor() {
        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        interceptor.setHybridQueryEnabled(false);
        return interceptor;
    }

    private void withStaleHybridWatermarks(Collection<PreAggregation> preAggregations,
                                           Object watermark,
                                           Runnable action) {
        withRestoredWatermarks(preAggregations, () -> {
            for (PreAggregation preAggregation : preAggregations) {
                if (preAggregation.supportsHybridQuery()) {
                    preAggregation.setDataWatermark(watermark);
                }
            }
            action.run();
        });
    }

    private void withRestoredWatermarks(Collection<PreAggregation> preAggregations,
                                        Runnable action) {
        Map<PreAggregation, Object> originalWatermarks = new IdentityHashMap<>();
        for (PreAggregation preAggregation : preAggregations) {
            originalWatermarks.put(preAggregation, preAggregation.getDataWatermark());
        }
        try {
            action.run();
        } finally {
            originalWatermarks.forEach(PreAggregation::setDataWatermark);
        }
    }

    private void assertFormulaRowsMatch(List<Map<String, Object>> expectedRows,
                                        List<Map<String, Object>> actualRows) {
        Map<String, BigDecimal> expected = formulaRowsByKey(expectedRows);
        Map<String, BigDecimal> actual = formulaRowsByKey(actualRows);

        assertFalse(expected.isEmpty(), "真实SQL应返回公式语义度量数据");
        assertEquals(expected, actual,
                "预聚合物化列结果应与原始fact表公式SQL结果一致");
    }

    private Map<String, BigDecimal> formulaRowsByKey(List<Map<String, Object>> rows) {
        return metricRowsByKey(rows, "salesAmountFormulaYuan");
    }

    private Map<String, BigDecimal> metricRowsByKey(List<Map<String, Object>> rows,
                                                     String metricName) {
        Map<String, BigDecimal> result = new TreeMap<>();
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.get("salesDate$caption"))
                    + "|"
                    + String.valueOf(row.get("product$caption"));
            result.put(key, toBigDecimal(row.get(metricName)));
        }
        return result;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue())
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private JdbcQueryModel getQueryModel(String queryModelName) {
        JdbcQueryModel model = (JdbcQueryModel) queryModelLoader.getJdbcQueryModel(queryModelName, null);
        assertNotNull(model, "查询模型 " + queryModelName + " 加载失败");
        return model;
    }
}
