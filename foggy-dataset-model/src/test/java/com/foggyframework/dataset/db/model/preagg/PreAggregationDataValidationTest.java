package com.foggyframework.dataset.db.model.preagg;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
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
 * 预聚合数据正确性验证测试
 *
 * <p>核心测试场景：</p>
 * <ul>
 *   <li>对比预聚合查询与原始表查询的结果一致性</li>
 *   <li>验证预聚合表数据正确性</li>
 *   <li>检测预聚合表数据错误（通过故意修改数据）</li>
 * </ul>
 *
 * <p>测试策略：</p>
 * <ol>
 *   <li>执行查询时设置 preAggEnabled=true（使用预聚合表）</li>
 *   <li>执行相同查询时设置 preAggEnabled=false（使用原始表）</li>
 *   <li>对比两次查询结果，验证数值一致性</li>
 * </ol>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@SpringBootTest(classes = JdbcModelTestApplication.class)
@ActiveProfiles({"sqlite"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("预聚合数据正确性验证测试")
class PreAggregationDataValidationTest {

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
    // 数据一致性验证测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("验证日期+商品维度查询：预聚合结果与原始表结果一致")
    void testDateProductQueryConsistency() {
        // 查询配置
        List<String> columns = Arrays.asList(
                "salesDate$caption",
                "product$caption",
                "quantity",
                "salesAmount"
        );

        // 执行对比查询
        ComparisonResult comparison = executeComparisonQuery(
                "FactSalesPreAggQueryModel",
                columns,
                null
        );

        // 验证结果
        assertQueryResultsMatch(comparison, "日期+商品维度查询", "daily_product_sales");
    }

    @Test
    @Order(2)
    @DisplayName("验证按商品分类汇总：预聚合结果与原始表结果一致")
    void testCategoryAggregationConsistency() {
        List<String> columns = Arrays.asList(
                "product$categoryName",
                "quantity",
                "salesAmount"
        );

        ComparisonResult comparison = executeComparisonQuery(
                "FactSalesPreAggQueryModel",
                columns,
                null
        );

        assertQueryResultsMatch(comparison, "商品分类汇总查询", "daily_product_sales");
    }

    @Test
    @Order(3)
    @DisplayName("验证客户+渠道维度查询：预聚合结果与原始表结果一致")
    void testCustomerChannelQueryConsistency() {
        List<String> columns = Arrays.asList(
                "customer$province",
                "channel$channelType",
                "quantity",
                "salesAmount"
        );

        ComparisonResult comparison = executeComparisonQuery(
                "FactSalesPreAggQueryModel",
                columns,
                null
        );

        assertQueryResultsMatch(comparison, "客户+渠道维度查询", "daily_customer_channel_sales");
    }

    @Test
    @Order(4)
    @DisplayName("验证总金额汇总：预聚合与原始表总额一致")
    void testTotalAmountConsistency() {
        // 查询原始表总金额
        Double originalTotal = jdbcTemplate.queryForObject(
                "SELECT SUM(sales_amount) FROM fact_sales",
                Double.class
        );

        // 查询预聚合表总金额
        Double preAggTotal = jdbcTemplate.queryForObject(
                "SELECT SUM(sales_amount_sum) FROM preagg_daily_product_sales",
                Double.class
        );

        assertNotNull(originalTotal, "原始表总金额不应为空");
        assertNotNull(preAggTotal, "预聚合表总金额不应为空");
        assertTrue(originalTotal > 0, "原始表 fixture 总金额必须大于 0");
        assertTrue(preAggTotal > 0, "预聚合 fixture 总金额必须大于 0");

        // 比较（允许小数精度误差）
        BigDecimal originalBd = BigDecimal.valueOf(originalTotal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal preAggBd = BigDecimal.valueOf(preAggTotal).setScale(2, RoundingMode.HALF_UP);

        assertEquals(originalBd, preAggBd,
                String.format("总金额不一致！原始表: %s, 预聚合表: %s", originalBd, preAggBd));

        log.info("总金额验证通过: 原始表={}, 预聚合表={}", originalBd, preAggBd);
    }

    @Test
    @Order(5)
    @DisplayName("验证总数量汇总：预聚合与原始表总量一致")
    void testTotalQuantityConsistency() {
        Long originalTotal = jdbcTemplate.queryForObject(
                "SELECT SUM(quantity) FROM fact_sales",
                Long.class
        );

        Long preAggTotal = jdbcTemplate.queryForObject(
                "SELECT SUM(quantity_sum) FROM preagg_daily_product_sales",
                Long.class
        );

        assertNotNull(originalTotal, "原始表总数量不应为空");
        assertNotNull(preAggTotal, "预聚合表总数量不应为空");
        assertTrue(originalTotal > 0, "原始表 fixture 总数量必须大于 0");
        assertTrue(preAggTotal > 0, "预聚合 fixture 总数量必须大于 0");

        assertEquals(originalTotal, preAggTotal,
                String.format("总数量不一致！原始表: %d, 预聚合表: %d", originalTotal, preAggTotal));

        log.info("总数量验证通过: 原始表={}, 预聚合表={}", originalTotal, preAggTotal);
    }

    @Test
    @Order(6)
    @DisplayName("验证按日期分组的金额：预聚合与原始表按日汇总一致")
    void testDailyAggregationConsistency() {
        // 查询原始表按日汇总
        List<Map<String, Object>> originalDaily = jdbcTemplate.queryForList(
                "SELECT date_key, SUM(quantity) as qty, SUM(sales_amount) as amt " +
                        "FROM fact_sales GROUP BY date_key ORDER BY date_key"
        );

        // 查询预聚合表按日汇总
        List<Map<String, Object>> preAggDaily = jdbcTemplate.queryForList(
                "SELECT date_key, SUM(quantity_sum) as qty, SUM(sales_amount_sum) as amt " +
                        "FROM preagg_daily_product_sales GROUP BY date_key ORDER BY date_key"
        );

        assertFalse(originalDaily.isEmpty(), "原始表按日 fixture 不得为空");
        assertFalse(preAggDaily.isEmpty(), "预聚合按日 fixture 不得为空");
        assertEquals(originalDaily.size(), preAggDaily.size(),
                "按日汇总行数不一致");

        for (int i = 0; i < originalDaily.size(); i++) {
            Map<String, Object> orig = originalDaily.get(i);
            Map<String, Object> preAgg = preAggDaily.get(i);

            assertEquals(orig.get("date_key"), preAgg.get("date_key"),
                    "日期不匹配");

            BigDecimal origAmt = toBigDecimal(orig.get("amt"));
            BigDecimal preAggAmt = toBigDecimal(preAgg.get("amt"));

            assertEquals(origAmt, preAggAmt,
                    String.format("日期 %s 金额不一致: 原始=%s, 预聚合=%s",
                            orig.get("date_key"), origAmt, preAggAmt));
        }

        log.info("按日汇总验证通过，共 {} 天数据一致", originalDaily.size());
    }

    // ==========================================
    // 预聚合表数据错误检测测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("故意修改预聚合数据后，通过QueryFacade检测到预聚合与原始表查询结果差异")
    void testDetectCorruptedPreAggData() {
        // 先备份原始数据
        List<Map<String, Object>> backup = jdbcTemplate.queryForList(
                "SELECT year_month, product_key, sales_amount_sum " +
                        "FROM preagg_monthly_category_sales " +
                        "ORDER BY year_month, product_key LIMIT 1"
        );

        assertEquals(1, backup.size(), "腐化探针要求 monthly 预聚合 fixture 至少有一行");

        Map<String, Object> firstRow = backup.get(0);
        String yearMonth = (String) firstRow.get("year_month");
        Integer productKey = ((Number) firstRow.get("product_key")).intValue();
        Double originalAmount = ((Number) firstRow.get("sales_amount_sum")).doubleValue();

        try {
            // 故意修改预聚合表数据（增加 1000）
            Double corruptedAmount = originalAmount + 1000.0;
            int updatedRows = jdbcTemplate.update(
                    "UPDATE preagg_monthly_category_sales SET sales_amount_sum = ? " +
                            "WHERE year_month = ? AND product_key = ?",
                    corruptedAmount, yearMonth, productKey
            );
            assertEquals(1, updatedRows, "腐化探针必须精确修改一行预聚合数据");

            log.info("已修改预聚合数据: year_month={}, product_key={}, 原值={}, 新值={}",
                    yearMonth, productKey, originalAmount, corruptedAmount);

            // 构建查询请求（需要包含维度列才能匹配预聚合）
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("FactSalesPreAggQueryModel");
            queryRequest.setColumns(Arrays.asList("product$categoryName", "salesAmount"));
            queryRequest.setReturnTotal(false);

            PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
            pagingRequest.setParam(queryRequest);
            pagingRequest.setStart(0);
            pagingRequest.setLimit(1000);

            // 使用 QueryFacade 执行查询（启用预聚合）
            ModelResultContext contextWithPreAgg = new ModelResultContext(pagingRequest, null);
            contextWithPreAgg.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                    .l1Enabled(false)
                    .l2Enabled(false)
                    .preAggEnabled(true)
                    .build());

            DbQueryResult resultWithPreAgg = queryFacade.queryModelResult(contextWithPreAgg);
            assertTrue(contextWithPreAgg.getCacheConfig().isPreAggHit(),
                    "preAggEnabled=true 时应该使用预聚合");
            assertEquals("monthly_category_sales", contextWithPreAgg.getCacheConfig().getPreAggName(),
                    "腐化探针必须命中被修改的预聚合表");

            // 使用 QueryFacade 执行查询（禁用预聚合）
            ModelResultContext contextNoPreAgg = new ModelResultContext(pagingRequest, null);
            contextNoPreAgg.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                    .l1Enabled(false)
                    .l2Enabled(false)
                    .preAggEnabled(false)
                    .build());

            DbQueryResult resultNoPreAgg = queryFacade.queryModelResult(contextNoPreAgg);
            assertFalse(contextNoPreAgg.getCacheConfig().isPreAggHit(),
                    "preAggEnabled=false 时不应使用预聚合");

            // 计算两次查询的 salesAmount 总额
            List<?> itemsWithPreAgg = resultWithPreAgg.getPagingResult().getItems();
            List<?> itemsNoPreAgg = resultNoPreAgg.getPagingResult().getItems();

            BigDecimal totalWithPreAgg = calculateTotal(itemsWithPreAgg, "salesAmount");
            BigDecimal totalNoPreAgg = calculateTotal(itemsNoPreAgg, "salesAmount");
            assertTrue(totalNoPreAgg.signum() > 0, "原始表 fixture 总额必须大于 0");

            log.info("QueryFacade 查询结果: 预聚合查询总额={}, 原始表查询总额={}",
                    totalWithPreAgg, totalNoPreAgg);

            // 预期：数据应该不一致（因为我们修改了预聚合表数据）
            assertNotEquals(totalNoPreAgg, totalWithPreAgg,
                    "修改预聚合数据后，预聚合查询与原始表查询结果应该不一致（测试验证错误检测能力）");

            // 验证差异正好是 1000
            BigDecimal diff = totalWithPreAgg.subtract(totalNoPreAgg).abs();
            assertEquals(new BigDecimal("1000.00"), diff,
                    "差异应该正好是 1000.00");

            log.info("QueryFacade 错误检测验证通过: 预聚合查询={}, 原始查询={}, 差异={}",
                    totalWithPreAgg, totalNoPreAgg, diff);

        } finally {
            // 恢复原始数据
            int restoredRows = jdbcTemplate.update(
                    "UPDATE preagg_monthly_category_sales SET sales_amount_sum = ? " +
                            "WHERE year_month = ? AND product_key = ?",
                    originalAmount, yearMonth, productKey
            );
            assertEquals(1, restoredRows, "腐化探针必须精确恢复一行预聚合数据");
            log.info("已恢复预聚合数据");
        }
    }

    @Test
    @Order(11)
    @DisplayName("验证预聚合行数与预期匹配")
    void testPreAggRowCount() {
        // 查询原始表按 (date_key, product_key) 分组的行数
        Integer expectedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT date_key || '-' || product_key) FROM fact_sales",
                Integer.class
        );

        // 查询预聚合表行数
        Integer preAggRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM preagg_daily_product_sales",
                Integer.class
        );

        assertEquals(expectedRows, preAggRows,
                String.format("预聚合表行数不匹配: 预期 %d 行，实际 %d 行",
                        expectedRows, preAggRows));
        assertTrue(expectedRows > 0, "原始表分组 fixture 行数必须大于 0");
        assertTrue(preAggRows > 0, "预聚合 fixture 行数必须大于 0");

        log.info("预聚合行数验证通过: {} 行", preAggRows);
    }

    // ==========================================
    // 预聚合启用/禁用对比测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("验证 preAggEnabled 开关正常工作（通过 QueryFacade）")
    void testPreAggEnabledSwitch() {
        // 构建查询请求
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesPreAggQueryModel");
        queryRequest.setColumns(Arrays.asList("product$categoryName", "salesAmount"));
        queryRequest.setReturnTotal(false);

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);
        pagingRequest.setStart(0);
        pagingRequest.setLimit(100);

        // 测试1: preAggEnabled = true（通过 QueryFacade）
        ModelResultContext contextWithPreAgg = new ModelResultContext(pagingRequest, null);
        contextWithPreAgg.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)  // 禁用 L2 缓存以确保每次都执行查询
                .preAggEnabled(true)
                .build());

        DbQueryResult resultWithPreAgg = queryFacade.queryModelResult(contextWithPreAgg);

        // 检查是否使用了预聚合
        ModelResultContext.QueryCacheConfig cacheConfigAfter = contextWithPreAgg.getCacheConfig();
        assertTrue(cacheConfigAfter.isPreAggHit(),
                "preAggEnabled=true 时应该使用预聚合");
        assertEquals("monthly_category_sales", cacheConfigAfter.getPreAggName(),
                "默认 hybrid 模式应命中可用的 monthly snapshot 预聚合");
        log.info("preAggEnabled=true 查询后: preAggHit={}, preAggName={}",
                cacheConfigAfter.isPreAggHit(),
                cacheConfigAfter.getPreAggName());

        // 测试2: preAggEnabled = false（通过 QueryFacade）
        ModelResultContext contextNoPreAgg = new ModelResultContext(pagingRequest, null);
        contextNoPreAgg.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)
                .preAggEnabled(false)
                .build());

        DbQueryResult resultNoPreAgg = queryFacade.queryModelResult(contextNoPreAgg);

        // 检查未使用预聚合
        ModelResultContext.QueryCacheConfig cacheConfigNoPreAgg = contextNoPreAgg.getCacheConfig();
        assertFalse(cacheConfigNoPreAgg.isPreAggHit(),
                "preAggEnabled=false 时不应使用预聚合");

        log.info("preAggEnabled=false 查询后: preAggHit={}",
                cacheConfigNoPreAgg.isPreAggHit());

        // 验证两次查询结果一致
        assertNotNull(resultWithPreAgg.getPagingResult());
        assertNotNull(resultNoPreAgg.getPagingResult());

        List<?> itemsWithPreAgg = resultWithPreAgg.getPagingResult().getItems();
        List<?> itemsNoPreAgg = resultNoPreAgg.getPagingResult().getItems();

        // 注意：预聚合表的行数可能与原始表不同（已聚合），所以比较总额而非行数
        log.info("预聚合查询返回 {} 行，原始查询返回 {} 行",
                itemsWithPreAgg.size(), itemsNoPreAgg.size());

        // 验证 salesAmount 总额一致
        BigDecimal totalWithPreAgg = calculateTotal(itemsWithPreAgg, "salesAmount");
        BigDecimal totalNoPreAgg = calculateTotal(itemsNoPreAgg, "salesAmount");
        assertTrue(totalNoPreAgg.signum() > 0, "原始表 fixture 总额必须大于 0");

        assertEquals(totalNoPreAgg, totalWithPreAgg,
                String.format("预聚合查询与原始查询 salesAmount 总额应一致（预聚合=%s, 原始=%s）",
                        totalWithPreAgg, totalNoPreAgg));

        log.info("preAggEnabled 开关验证通过: salesAmount 总额 = {}", totalWithPreAgg);
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    /**
     * 执行对比查询（使用 QueryFacade）
     */
    private ComparisonResult executeComparisonQuery(String queryModelName,
                                                     List<String> columns,
                                                     List<?> slices) {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(queryModelName);
        queryRequest.setColumns(columns);
        queryRequest.setReturnTotal(true);

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);
        pagingRequest.setStart(0);
        pagingRequest.setLimit(1000);

        // 查询1: 使用预聚合（通过 QueryFacade）
        ModelResultContext contextPreAgg = new ModelResultContext(pagingRequest, null);
        contextPreAgg.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)
                .preAggEnabled(true)
                .hybridQueryEnabled(false)
                .build());

        DbQueryResult resultPreAgg = queryFacade.queryModelResult(contextPreAgg);

        // 查询2: 不使用预聚合（通过 QueryFacade）
        ModelResultContext contextNoPreAgg = new ModelResultContext(pagingRequest, null);
        contextNoPreAgg.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                .l1Enabled(false)
                .l2Enabled(false)
                .preAggEnabled(false)
                .build());

        DbQueryResult resultNoPreAgg = queryFacade.queryModelResult(contextNoPreAgg);

        return new ComparisonResult(
                resultPreAgg,
                resultNoPreAgg,
                contextPreAgg.getCacheConfig().isPreAggHit(),
                contextPreAgg.getCacheConfig().getPreAggName()
        );
    }

    /**
     * 验证查询结果匹配
     * <p>
     * 由于预聚合表已经是聚合后的数据，行数可能与原始查询不同。
     * 验证的核心是数值一致性（总和），而不是行数一致性。
     * </p>
     */
    private void assertQueryResultsMatch(ComparisonResult comparison, String testName,
                                         String expectedPreAggName) {
        DbQueryResult preAggResult = comparison.preAggResult;
        DbQueryResult noPreAggResult = comparison.noPreAggResult;

        assertTrue(comparison.preAggUsed, testName + ": 预聚合对比不得退化为 raw-vs-raw");
        assertEquals(expectedPreAggName, comparison.preAggName,
                testName + ": 命中的预聚合与 fixture 契约不一致");
        assertNotNull(preAggResult.getPagingResult(), testName + ": 预聚合结果不应为空");
        assertNotNull(noPreAggResult.getPagingResult(), testName + ": 原始结果不应为空");

        List<?> preAggItems = preAggResult.getPagingResult().getItems();
        List<?> noPreAggItems = noPreAggResult.getPagingResult().getItems();

        log.info("{}: 预聚合结果 {} 行, 原始结果 {} 行, 使用预聚合={}, 预聚合名={}",
                testName, preAggItems.size(), noPreAggItems.size(),
                comparison.preAggUsed, comparison.preAggName);

        // 核心验证：数值一致性（总和应该相等）
        // 预聚合表的行数可能少于原始表（因为已经聚合），但总和必须一致

        // 验证 salesAmount 总额一致
        BigDecimal preAggTotal = calculateTotal(preAggItems, "salesAmount");
        BigDecimal noPreAggTotal = calculateTotal(noPreAggItems, "salesAmount");

        assertTrue(noPreAggTotal.signum() > 0, testName + ": 原始表 fixture salesAmount 必须大于 0");
        assertEquals(noPreAggTotal, preAggTotal,
                testName + ": salesAmount 总额不一致（预聚合=" + preAggTotal + ", 原始=" + noPreAggTotal + "）");
        log.info("{}: salesAmount 总额验证通过 = {}", testName, preAggTotal);

        // 验证 quantity 总量一致
        BigDecimal preAggQty = calculateTotal(preAggItems, "quantity");
        BigDecimal noPreAggQty = calculateTotal(noPreAggItems, "quantity");

        assertTrue(noPreAggQty.signum() > 0, testName + ": 原始表 fixture quantity 必须大于 0");
        assertEquals(noPreAggQty, preAggQty,
                testName + ": quantity 总量不一致（预聚合=" + preAggQty + ", 原始=" + noPreAggQty + "）");
        log.info("{}: quantity 总量验证通过 = {}", testName, preAggQty);
    }

    /**
     * 计算总额
     */
    private BigDecimal calculateTotal(List<?> items, String field) {
        assertNotNull(items, field + ": 查询结果列表不得为空引用");
        assertFalse(items.isEmpty(), field + ": 查询结果列表不得为空");

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            assertTrue(item instanceof Map, field + ": 第 " + i + " 行必须是 Map");
            Map<?, ?> row = (Map<?, ?>) item;
            assertTrue(row.containsKey(field), field + ": 第 " + i + " 行缺少目标字段");
            Object value = row.get(field);
            assertNotNull(value, field + ": 第 " + i + " 行目标字段不得为 null");
            total = total.add(toBigDecimal(value));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 转换为 BigDecimal
     */
    private BigDecimal toBigDecimal(Object value) {
        assertNotNull(value, "数值字段不得为 null");
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).setScale(2, RoundingMode.HALF_UP);
        }
        assertTrue(value instanceof Number,
                "数值字段类型必须是 Number，实际为 " + value.getClass().getName());
        return BigDecimal.valueOf(((Number) value).doubleValue())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 对比结果封装
     */
    private static class ComparisonResult {
        final DbQueryResult preAggResult;
        final DbQueryResult noPreAggResult;
        final boolean preAggUsed;
        final String preAggName;

        ComparisonResult(DbQueryResult preAggResult, DbQueryResult noPreAggResult,
                         boolean preAggUsed, String preAggName) {
            this.preAggResult = preAggResult;
            this.noPreAggResult = noPreAggResult;
            this.preAggUsed = preAggUsed;
            this.preAggName = preAggName;
        }
    }
}
