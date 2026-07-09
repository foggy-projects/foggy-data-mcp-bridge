package com.foggyframework.dataset.db.model.preagg;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.PostAggregateCalculationDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.engine.stage.QueryStagePlan;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.test.JdbcModelTestApplication;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 预聚合边界情况测试
 *
 * <p>测试场景：</p>
 * <ul>
 *   <li>明细查询（无聚合）不应使用预聚合</li>
 *   <li>包含自定义 WHERE 条件的查询</li>
 *   <li>预聚合表不包含的列被查询时</li>
 * </ul>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@SpringBootTest(classes = JdbcModelTestApplication.class)
@ActiveProfiles({"sqlite"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("预聚合边界情况测试")
class PreAggregationEdgeCaseTest {

    @Resource
    private QueryFacade queryFacade;

    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    // ==========================================
    // 明细查询（无聚合）测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("明细查询（只有维度属性，无度量）不应使用预聚合")
    void testDetailQueryWithoutMeasures_ShouldNotUsePreAgg() {
        // 构建明细查询请求（只有维度属性，没有度量）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "salesDate$caption",
                "product$categoryName",
                "customer$province"
        ));  // 只有维度属性，没有度量
        queryRequest.setReturnTotal(false);

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);
        pagingRequest.setStart(0);
        pagingRequest.setLimit(100);

        // 执行查询
        ModelResultContext context = new ModelResultContext(pagingRequest, null);
        context.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)
                .preAggEnabled(true)  // 启用预聚合
                .build());

        DbQueryResult result = queryFacade.queryModelResult(context);

        // 验证：应该不使用预聚合（因为是明细查询）
        ModelResultContext.QueryCacheConfig cacheConfig = context.getCacheConfig();
        assertFalse(cacheConfig.isPreAggHit(),
                "明细查询（无度量）不应使用预聚合");

        // 应该返回原始表的行数
        List<?> items = result.getPagingResult().getItems();
        log.info("明细查询返回 {} 行, preAggHit={}", items.size(), cacheConfig.isPreAggHit());

        // 原始表有 10 行明细数据
        assertTrue(items.size() >= 1, "明细查询应返回数据");
    }

    @Test
    @Order(2)
    @DisplayName("明细查询（有度量但无维度）不应使用预聚合")
    void testDetailQueryWithOnlyMeasures_ShouldNotUsePreAgg() {
        // 构建查询请求（只有度量，没有维度）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList("salesAmount", "quantity"));  // 只有度量
        queryRequest.setReturnTotal(false);

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);
        pagingRequest.setStart(0);
        pagingRequest.setLimit(100);

        // 执行查询（启用预聚合）
        ModelResultContext contextWithPreAgg = new ModelResultContext(pagingRequest, null);
        contextWithPreAgg.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)
                .preAggEnabled(true)
                .build());

        DbQueryResult resultWithPreAgg = queryFacade.queryModelResult(contextWithPreAgg);

        // 执行查询（禁用预聚合）
        ModelResultContext contextNoPreAgg = new ModelResultContext(pagingRequest, null);
        contextNoPreAgg.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)
                .preAggEnabled(false)
                .build());

        DbQueryResult resultNoPreAgg = queryFacade.queryModelResult(contextNoPreAgg);

        // 记录结果
        List<?> itemsWithPreAgg = resultWithPreAgg.getPagingResult().getItems();
        List<?> itemsNoPreAgg = resultNoPreAgg.getPagingResult().getItems();

        log.info("只有度量查询: preAggEnabled=true 返回 {} 行, preAggHit={}",
                itemsWithPreAgg.size(), contextWithPreAgg.getCacheConfig().isPreAggHit());
        log.info("只有度量查询: preAggEnabled=false 返回 {} 行, preAggHit={}",
                itemsNoPreAgg.size(), contextNoPreAgg.getCacheConfig().isPreAggHit());

        // 验证：两种方式的结果应该一致（行数、总额）
        BigDecimal totalWithPreAgg = calculateTotal(itemsWithPreAgg, "salesAmount");
        BigDecimal totalNoPreAgg = calculateTotal(itemsNoPreAgg, "salesAmount");

        assertEquals(totalNoPreAgg, totalWithPreAgg,
                "只有度量查询，预聚合和原始查询的总额应一致");
    }

    @Test
    @Order(3)
    @DisplayName("聚合查询（有维度+度量）应使用预聚合")
    void testAggregationQueryWithDimensionAndMeasures_ShouldUsePreAgg() {
        // 构建聚合查询请求（有维度+度量）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "product$categoryName",
                "salesAmount",
                "quantity"
        ));
        queryRequest.setReturnTotal(false);

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);
        pagingRequest.setStart(0);
        pagingRequest.setLimit(100);

        // 执行查询
        ModelResultContext context = new ModelResultContext(pagingRequest, null);
        context.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)
                .preAggEnabled(true)
                .build());

        DbQueryResult result = queryFacade.queryModelResult(context);

        // 验证：应该使用预聚合
        ModelResultContext.QueryCacheConfig cacheConfig = context.getCacheConfig();
        assertTrue(cacheConfig.isPreAggHit(),
                "聚合查询（有维度+度量）应使用预聚合");
        assertEquals("daily_product_sales", cacheConfig.getPreAggName(),
                "应使用 daily_product_sales 预聚合");

        List<?> items = result.getPagingResult().getItems();
        log.info("聚合查询返回 {} 行, preAggHit={}, preAggName={}",
                items.size(), cacheConfig.isPreAggHit(), cacheConfig.getPreAggName());
    }

    // ==========================================
    // WHERE 条件处理测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("带 slice 条件的查询：验证预聚合与原始查询结果一致性")
    void testQueryWithSlices_ResultConsistency() {
        // 构建带过滤条件的查询请求
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList("product$categoryName", "salesAmount"));
        queryRequest.setReturnTotal(false);

        // 设置 slice（过滤条件）- 按 categoryName 过滤，使用 = 操作符
        // 注意：这个测试依赖于预聚合表包含 category_name 列
        List<SliceRequestDef> slices = new ArrayList<>();
        slices.add(new SliceRequestDef("product$categoryName", "=", "数码电器"));
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);
        pagingRequest.setStart(0);
        pagingRequest.setLimit(100);

        // 执行查询（启用预聚合）
        ModelResultContext contextWithPreAgg = new ModelResultContext(pagingRequest, null);
        contextWithPreAgg.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)
                .preAggEnabled(true)
                .build());

        DbQueryResult resultWithPreAgg = queryFacade.queryModelResult(contextWithPreAgg);

        // 执行查询（禁用预聚合）
        ModelResultContext contextNoPreAgg = new ModelResultContext(pagingRequest, null);
        contextNoPreAgg.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)
                .preAggEnabled(false)
                .build());

        DbQueryResult resultNoPreAgg = queryFacade.queryModelResult(contextNoPreAgg);

        // 计算两次查询的 salesAmount 总额
        List<?> itemsWithPreAgg = resultWithPreAgg.getPagingResult().getItems();
        List<?> itemsNoPreAgg = resultNoPreAgg.getPagingResult().getItems();

        BigDecimal totalWithPreAgg = calculateTotal(itemsWithPreAgg, "salesAmount");
        BigDecimal totalNoPreAgg = calculateTotal(itemsNoPreAgg, "salesAmount");

        log.info("带 slices 条件查询: preAggEnabled=true 总额={}, preAggHit={}",
                totalWithPreAgg, contextWithPreAgg.getCacheConfig().isPreAggHit());
        log.info("带 slices 条件查询: preAggEnabled=false 总额={}",
                totalNoPreAgg);

        // 验证结果一致性
        assertEquals(totalNoPreAgg, totalWithPreAgg,
                String.format("带 slices 条件的查询，预聚合(%s)与原始查询(%s)的总额应一致",
                        totalWithPreAgg, totalNoPreAgg));
    }

    @Test
    @Order(11)
    @DisplayName("带 date 范围条件的查询：验证预聚合与原始查询结果一致性")
    void testQueryWithDateRangeSlice_ResultConsistency() {
        // 构建带日期范围过滤的查询请求
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList("salesDate$caption", "salesAmount"));
        queryRequest.setReturnTotal(false);

        // 设置日期范围过滤（使用 >= 操作符）
        List<SliceRequestDef> slices = new ArrayList<>();
        slices.add(new SliceRequestDef("salesDate$id", ">=", 20240101));
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);
        pagingRequest.setStart(0);
        pagingRequest.setLimit(100);

        // 执行查询（启用预聚合）
        ModelResultContext contextWithPreAgg = new ModelResultContext(pagingRequest, null);
        contextWithPreAgg.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)
                .preAggEnabled(true)
                .build());

        DbQueryResult resultWithPreAgg = queryFacade.queryModelResult(contextWithPreAgg);

        // 执行查询（禁用预聚合）
        ModelResultContext contextNoPreAgg = new ModelResultContext(pagingRequest, null);
        contextNoPreAgg.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)
                .preAggEnabled(false)
                .build());

        DbQueryResult resultNoPreAgg = queryFacade.queryModelResult(contextNoPreAgg);

        // 计算两次查询的 salesAmount 总额
        List<?> itemsWithPreAgg = resultWithPreAgg.getPagingResult().getItems();
        List<?> itemsNoPreAgg = resultNoPreAgg.getPagingResult().getItems();

        BigDecimal totalWithPreAgg = calculateTotal(itemsWithPreAgg, "salesAmount");
        BigDecimal totalNoPreAgg = calculateTotal(itemsNoPreAgg, "salesAmount");

        log.info("带日期范围查询: preAggEnabled=true 总额={}, 行数={}, preAggHit={}",
                totalWithPreAgg, itemsWithPreAgg.size(),
                contextWithPreAgg.getCacheConfig().isPreAggHit());
        log.info("带日期范围查询: preAggEnabled=false 总额={}, 行数={}",
                totalNoPreAgg, itemsNoPreAgg.size());

        // 验证结果一致性
        assertEquals(totalNoPreAgg, totalWithPreAgg,
                String.format("带日期范围的查询，预聚合(%s)与原始查询(%s)的总额应一致",
                        totalWithPreAgg, totalNoPreAgg));
    }

    // ==========================================
    // 预聚合表不包含的列测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("查询预聚合表不包含的属性时：应正确处理")
    void testQueryPropertyNotInPreAgg_ShouldHandleCorrectly() {
        // 查询 store 维度的属性（预聚合表 daily_product_sales 不包含 store）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "store$storeType",  // daily_product_sales 不包含 store 维度
                "salesAmount"
        ));
        queryRequest.setReturnTotal(false);

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);
        pagingRequest.setStart(0);
        pagingRequest.setLimit(100);

        // 执行查询（启用预聚合）
        ModelResultContext contextWithPreAgg = new ModelResultContext(pagingRequest, null);
        contextWithPreAgg.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)
                .preAggEnabled(true)
                .build());

        DbQueryResult resultWithPreAgg = queryFacade.queryModelResult(contextWithPreAgg);

        // 执行查询（禁用预聚合）
        ModelResultContext contextNoPreAgg = new ModelResultContext(pagingRequest, null);
        contextNoPreAgg.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)
                .preAggEnabled(false)
                .build());

        DbQueryResult resultNoPreAgg = queryFacade.queryModelResult(contextNoPreAgg);

        // 计算两次查询的 salesAmount 总额
        List<?> itemsWithPreAgg = resultWithPreAgg.getPagingResult().getItems();
        List<?> itemsNoPreAgg = resultNoPreAgg.getPagingResult().getItems();

        BigDecimal totalWithPreAgg = calculateTotal(itemsWithPreAgg, "salesAmount");
        BigDecimal totalNoPreAgg = calculateTotal(itemsNoPreAgg, "salesAmount");

        log.info("查询 store 维度: preAggEnabled=true 总额={}, preAggHit={}, preAggName={}",
                totalWithPreAgg,
                contextWithPreAgg.getCacheConfig().isPreAggHit(),
                contextWithPreAgg.getCacheConfig().getPreAggName());
        log.info("查询 store 维度: preAggEnabled=false 总额={}", totalNoPreAgg);

        // 验证结果一致性
        assertEquals(totalNoPreAgg, totalWithPreAgg,
                String.format("查询预聚合表不包含的维度，预聚合(%s)与原始查询(%s)的总额应一致",
                        totalWithPreAgg, totalNoPreAgg));
    }

    @Test
    @Order(21)
    @DisplayName("多阶段 postAggregate 查询保留 final stage 时跳过预聚合")
    void testPostAggregateResultStageSkipsPreAggOptimization() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "product$categoryName",
                "sum(salesAmount) as teamSales",
                "salesShare"
        ));
        queryRequest.setGroupBy(buildGroupBy("product$categoryName"));
        queryRequest.setPostAggregateCalculations(new ArrayList<>(List.of(new PostAggregateCalculationDef(
                "salesShare", "ratioToTotal", "teamSales", "grandTotal", "ratio"
        ))));
        queryRequest.setPostSlice(new ArrayList<>(List.of(new SliceRequestDef("salesShare", ">", 0.2))));
        queryRequest.setReturnTotal(true);

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);
        pagingRequest.setStart(0);
        pagingRequest.setLimit(100);

        ModelResultContext context = new ModelResultContext(pagingRequest, null);
        context.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)
                .preAggEnabled(true)
                .build());

        DbQueryResult result = queryFacade.queryModelResult(context);
        assertNotNull(result.getPagingResult());

        Map<String, Object> plan = queryStagePlan(context);
        assertEquals("preserve-final-stage-sql", plan.get("aggSqlOptimizationPolicy"));
        assertEquals("skip-final-stage-required", plan.get("preAggOptimizationPolicy"));
        assertEquals(Boolean.TRUE, context.getExtData().get("preAggSkippedByStagePlan"));
        assertEquals("skip-final-stage-required", context.getExtData().get("preAggSkipReason"));
        assertFalse(context.getCacheConfig().isPreAggHit());
        assertFalse(context.getExtData().containsKey("preAggUsed"));
        assertFalse(context.getExtData().containsKey("preAggAggregateUsed"));
        assertFalse(result.getPagingResult().getItems().isEmpty());
        assertTrue(result.getPagingResult().getTotal() >= result.getPagingResult().getItems().size(),
                "returnTotal should count the final semantic result set");
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private List<GroupRequestDef> buildGroupBy(String... fields) {
        List<GroupRequestDef> groups = new ArrayList<>();
        for (String field : fields) {
            GroupRequestDef group = new GroupRequestDef();
            group.setField(field);
            groups.add(group);
        }
        return groups;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> queryStagePlan(ModelResultContext context) {
        Object plan = context.getExtData().get(QueryStagePlan.EXT_DATA_KEY);
        assertNotNull(plan, "queryStagePlan diagnostics should be attached to context.extData");
        assertTrue(plan instanceof Map<?, ?>, "queryStagePlan diagnostics should be a map");
        return (Map<String, Object>) plan;
    }

    /**
     * 计算总额
     */
    private BigDecimal calculateTotal(List<?> items, String field) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Object item : items) {
            if (item instanceof Map) {
                Object value = ((Map<?, ?>) item).get(field);
                if (value != null) {
                    total = total.add(toBigDecimal(value));
                }
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 转换为 BigDecimal
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue())
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }
}
