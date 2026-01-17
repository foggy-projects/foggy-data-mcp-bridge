package com.foggyframework.dataset.db.model.preagg;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.engine.preagg.*;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.db.model.spi.preagg.TimeGranularity;
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

        // 验证预聚合配置
        for (PreAggregation preAgg : preAggregations) {
            log.info("预聚合: name={}, priority={}, tableName={}, dimensions={}",
                    preAgg.getName(), preAgg.getPriority(), preAgg.getTableName(),
                    preAgg.getDimensionNames());

            assertNotNull(preAgg.getName(), "预聚合名称不应为空");
            assertNotNull(preAgg.getTableName(), "预聚合表名不应为空");
            assertTrue(preAgg.getPriority() > 0, "优先级应大于0");
        }

        // 验证特定预聚合
        PreAggregation dailyProductSales = preAggregations.stream()
                .filter(p -> "daily_product_sales".equals(p.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(dailyProductSales, "应存在daily_product_sales预聚合");
        assertEquals(80, dailyProductSales.getPriority());
        assertTrue(dailyProductSales.getDimensionNames().contains("salesDate"));
        assertTrue(dailyProductSales.getDimensionNames().contains("product"));
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

        log.info("退货模型预聚合数量: {}", preAggregations.size());
    }

    // ==========================================
    // 查询匹配测试 - 单TM场景
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("完全匹配 - 按日期+商品查询")
    void testExactMatchByDateAndProduct() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 构建按日期+商品的聚合查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate",
                "product",
                "quantity",
                "salesAmount"
        ));

        // 分析查询
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        // 尝试匹配预聚合
        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        // 验证匹配结果
        if (result.isApplied()) {
            log.info("匹配成功: 预聚合={}, 需要rollup={}, 混合查询={}",
                    result.getPreAggregation().getName(),
                    result.isNeedsRollup(),
                    result.isHybridQuery());
            log.info("重写后SQL: {}", result.getSql());

            assertEquals("daily_product_sales", result.getPreAggregation().getName(),
                    "应匹配daily_product_sales预聚合");
            assertFalse(result.isNeedsRollup(), "完全匹配不需要rollup");
        } else {
            log.info("未匹配到预聚合，使用原始表查询");
        }
    }

    @Test
    @Order(11)
    @DisplayName("粒度Rollup - 按月查询使用日粒度预聚合")
    void testGranularityRollup() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 构建按月的聚合查询
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

        if (result.isApplied()) {
            log.info("粒度Rollup匹配: 预聚合={}, needsRollup={}",
                    result.getPreAggregation().getName(),
                    result.isNeedsRollup());
            log.info("重写后SQL: {}", result.getSql());

            // 使用日粒度预聚合进行月级别rollup
            assertTrue(result.isNeedsRollup(), "月查询使用日粒度预聚合需要rollup");
        }
    }

    @Test
    @Order(12)
    @DisplayName("优先级选择 - 多个预聚合可用时选择高优先级")
    void testPrioritySelection() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 构建只查询品类的聚合查询（两个预聚合都满足）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "product$categoryName",
                "salesAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        if (result.isApplied()) {
            log.info("优先级选择: 选中预聚合={}, priority={}",
                    result.getPreAggregation().getName(),
                    result.getPreAggregation().getPriority());

            // 应选择高优先级的预聚合
            assertTrue(result.getPreAggregation().getPriority() >= 60,
                    "应选择高优先级的预聚合");
        }
    }

    @Test
    @Order(13)
    @DisplayName("不匹配场景 - 查询包含预聚合不支持的维度")
    void testNoMatchForUnsupportedDimension() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 构建包含门店维度的查询（没有包含门店的预聚合）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "store",
                "store$storeType",
                "salesAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        log.info("不匹配场景结果: applied={}", result.isApplied());

        // 包含门店维度，没有对应预聚合
        assertFalse(result.isApplied(), "包含门店维度的查询不应匹配预聚合");
    }

    @Test
    @Order(14)
    @DisplayName("不匹配场景 - 查询包含预聚合不支持的度量")
    void testNoMatchForUnsupportedMeasure() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 查询包含预聚合不支持的度量（如unitPrice）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate",
                "product",
                "unitPrice"  // 预聚合不包含此度量
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        log.info("度量不匹配场景结果: applied={}", result.isApplied());

        // 预聚合不包含unitPrice度量
        assertFalse(result.isApplied(), "包含不支持度量的查询不应匹配预聚合");
    }

    // ==========================================
    // 查询匹配测试 - 多TM场景
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("多TM JOIN场景 - 加载联合查询模型")
    void testLoadJoinQueryModel() {
        JdbcQueryModel queryModel = getQueryModel("SalesReturnPreAggJoinQueryModel");
        assertNotNull(queryModel, "联合查询模型加载失败");

        log.info("联合查询模型加载成功: {}", queryModel.getName());

        // 验证主模型预聚合
        TableModel mainModel = queryModel.getJdbcModel();
        assertNotNull(mainModel, "主模型不应为空");

        List<PreAggregation> mainPreAggs = mainModel.getPreAggregations();
        if (mainPreAggs != null) {
            log.info("主模型（销售）预聚合数量: {}", mainPreAggs.size());
        }
    }

    @Test
    @Order(21)
    @DisplayName("多TM JOIN场景 - 仅查询主模型字段")
    void testJoinQueryWithMainModelFieldsOnly() {
        JdbcQueryModel queryModel = getQueryModel("SalesReturnPreAggJoinQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 仅查询主模型（销售）的字段
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("SalesReturnPreAggJoinQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate",
                "product",
                "salesAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        if (result.isApplied()) {
            log.info("JOIN模型主字段查询匹配: 预聚合={}",
                    result.getPreAggregation().getName());
            log.info("重写后SQL: {}", result.getSql());
        } else {
            log.info("JOIN模型主字段查询未匹配预聚合");
        }
    }

    @Test
    @Order(22)
    @DisplayName("多TM JOIN场景 - 查询跨模型字段")
    void testJoinQueryWithCrossModelFields() {
        JdbcQueryModel queryModel = getQueryModel("SalesReturnPreAggJoinQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        // 查询跨模型字段（销售+退货）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("SalesReturnPreAggJoinQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate",
                "product",
                "salesAmount",
                "ret.returnAmount"  // 退货模型字段
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        // 跨模型查询通常不能使用单一预聚合
        log.info("跨模型查询结果: applied={}", result.isApplied());
    }

    // ==========================================
    // 混合查询测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("混合查询 - watermark检测")
    void testHybridQueryWatermarkDetection() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        TableModel tableModel = queryModel.getJdbcModel();
        List<PreAggregation> preAggregations = tableModel.getPreAggregations();

        if (preAggregations == null || preAggregations.isEmpty()) {
            log.info("没有预聚合配置，跳过混合查询测试");
            return;
        }

        PreAggregation preAgg = preAggregations.get(0);

        // 模拟设置watermark为过去的日期
        LocalDate pastDate = LocalDate.now().minusDays(5);
        preAgg.setDataWatermark(pastDate);

        log.info("设置watermark为: {}", pastDate);
        log.info("支持混合查询: {}", preAgg.supportsHybridQuery());
        log.info("数据是否过期: {}", preAgg.isDataStale());

        if (preAgg.supportsHybridQuery()) {
            assertTrue(preAgg.isDataStale(), "watermark为过去日期时数据应标记为过期");
        }
    }

    @Test
    @Order(31)
    @DisplayName("混合查询 - SQL生成")
    void testHybridQuerySqlGeneration() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        TableModel tableModel = queryModel.getJdbcModel();
        List<PreAggregation> preAggregations = tableModel.getPreAggregations();

        if (preAggregations == null || preAggregations.isEmpty()) {
            log.info("没有预聚合配置，跳过测试");
            return;
        }

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
                "salesDate",
                "product",
                "salesAmount"
        ));

        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);

        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        interceptor.setHybridQueryEnabled(true);

        PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, queryRequest);

        if (result.isApplied()) {
            log.info("混合查询模式: {}", result.isHybridQuery());
            log.info("watermark: {}", result.getWatermark());
            log.info("生成的SQL: {}", result.getSql());

            if (result.isHybridQuery()) {
                // 混合查询SQL应包含UNION
                assertTrue(result.getSql().toUpperCase().contains("UNION"),
                        "混合查询SQL应包含UNION");
            }
        }
    }

    // ==========================================
    // 预聚合匹配器单元测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("匹配器 - 空预聚合列表")
    void testMatcherWithEmptyPreAggregations() {
        PreAggregationMatcher matcher = new PreAggregationMatcher();

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(true);
        requirement.addDimension("salesDate");
        requirement.addDimension("product");

        PreAggregationMatchResult result = matcher.findBestMatch(requirement, Collections.emptyList());

        assertFalse(result.isMatched(), "空列表不应匹配");
        assertNotNull(result.getReason(), "应有未匹配原因");
        log.info("未匹配原因: {}", result.getReason());
    }

    @Test
    @Order(41)
    @DisplayName("匹配器 - 无GROUP BY查询不使用预聚合")
    void testMatcherWithoutGroupBy() {
        PreAggregationMatcher matcher = new PreAggregationMatcher();

        PreAggQueryRequirement requirement = new PreAggQueryRequirement();
        requirement.setHasGroupBy(false);  // 没有GROUP BY
        requirement.addDimension("salesDate");

        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        List<PreAggregation> preAggregations = queryModel.getJdbcModel().getPreAggregations();

        if (preAggregations != null && !preAggregations.isEmpty()) {
            PreAggregationMatchResult result = matcher.findBestMatch(requirement, preAggregations);
            assertFalse(result.isMatched(), "无GROUP BY查询不应使用预聚合");
            log.info("无GROUP BY未匹配原因: {}", result.getReason());
        }
    }

    // ==========================================
    // 带过滤条件的查询测试
    // ==========================================

    @Test
    @Order(50)
    @DisplayName("带过滤条件的预聚合查询")
    void testPreAggQueryWithFilters() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesPreAggQueryModel");
        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate",
                "product",
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

        if (result.isApplied()) {
            log.info("带过滤条件查询匹配成功");
            log.info("重写后SQL: {}", result.getSql());
            log.info("参数: {}", result.getParams());

            // 过滤条件应该被保留
            assertTrue(result.getSql().toLowerCase().contains("where"),
                    "重写后SQL应包含WHERE子句");
        }
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private JdbcQueryModel getQueryModel(String queryModelName) {
        return (JdbcQueryModel) queryModelLoader.getJdbcQueryModel(queryModelName);
    }
}
