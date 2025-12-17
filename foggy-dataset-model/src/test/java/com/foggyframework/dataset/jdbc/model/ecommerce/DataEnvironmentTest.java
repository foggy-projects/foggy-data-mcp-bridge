package com.foggyframework.dataset.jdbc.model.ecommerce;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据环境验证测试
 *
 * <p>验证Docker测试环境是否正确初始化</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("数据环境验证测试")
class DataEnvironmentTest extends EcommerceTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    @DisplayName("验证数据库连接")
    void testDatabaseConnection() {
        // 测试数据库连接
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertEquals(1, result, "数据库连接测试失败");
        log.info("数据库连接测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("验证维度表存在")
    void testDimensionTablesExist() {
        String[] dimensionTables = {
            "dim_date",
            "dim_product",
            "dim_customer",
            "dim_store",
            "dim_channel",
            "dim_promotion"
        };

        for (String tableName : dimensionTables) {
            Long count = getTableCount(tableName);
            assertNotNull(count, String.format("表 %s 不存在", tableName));
            log.info("维度表 {} 存在，记录数: {}", tableName, count);
        }
    }

    @Test
    @Order(3)
    @DisplayName("验证事实表存在")
    void testFactTablesExist() {
        String[] factTables = {
            "fact_order",
            "fact_sales",
            "fact_payment",
            "fact_return",
            "fact_inventory_snapshot"
        };

        for (String tableName : factTables) {
            Long count = getTableCount(tableName);
            assertNotNull(count, String.format("表 %s 不存在", tableName));
            log.info("事实表 {} 存在，记录数: {}", tableName, count);
        }
    }

    @Test
    @Order(4)
    @DisplayName("验证字典表存在")
    void testDictTablesExist() {
        String[] dictTables = {
            "dict_status",
            "dict_category",
            "dict_region"
        };

        for (String tableName : dictTables) {
            Long count = getTableCount(tableName);
            assertNotNull(count, String.format("表 %s 不存在", tableName));
            log.info("字典表 {} 存在，记录数: {}", tableName, count);
        }
    }

    @Test
    @Order(5)
    @DisplayName("验证测试数据量")
    void testDataVolume() {
        // SQLite 使用轻量级测试数据，Docker/MySQL 使用完整测试数据
        boolean isLightweight = isLightweightMode();

        // 日期维度：SQLite至少10条，Docker至少365条
        Long dateCount = getTableCount("dim_date");
        int minDateRecords = isLightweight ? 10 : 365;
        assertTrue(dateCount >= minDateRecords,
            String.format("日期维度数据不足，预期至少%d条，实际: %d (模式: %s)",
                minDateRecords, dateCount, databaseType));
        log.info("日期维度数据: {} 条 (预期至少 {} 条)", dateCount, minDateRecords);

        // 商品维度：至少10条
        Long productCount = getTableCount("dim_product");
        assertTrue(productCount >= 10, "商品维度数据不足，预期至少10条，实际: " + productCount);
        log.info("商品维度数据: {} 条", productCount);

        // 客户维度：至少10条
        Long customerCount = getTableCount("dim_customer");
        assertTrue(customerCount >= 10, "客户维度数据不足，预期至少10条，实际: " + customerCount);
        log.info("客户维度数据: {} 条", customerCount);

        // 订单事实：SQLite至少10条，Docker至少100条
        Long orderCount = getTableCount("fact_order");
        int minOrderRecords = isLightweight ? 10 : 100;
        assertTrue(orderCount >= minOrderRecords,
            String.format("订单事实数据不足，预期至少%d条，实际: %d (模式: %s)",
                minOrderRecords, orderCount, databaseType));
        log.info("订单事实数据: {} 条 (预期至少 {} 条)", orderCount, minOrderRecords);

        // 销售事实：SQLite至少10条，Docker至少100条
        Long salesCount = getTableCount("fact_sales");
        int minSalesRecords = isLightweight ? 10 : 100;
        assertTrue(salesCount >= minSalesRecords,
            String.format("销售事实数据不足，预期至少%d条，实际: %d (模式: %s)",
                minSalesRecords, salesCount, databaseType));
        log.info("销售事实数据: {} 条 (预期至少 {} 条)", salesCount, minSalesRecords);
    }

    @Test
    @Order(6)
    @DisplayName("验证维度表数据完整性")
    void testDimensionDataIntegrity() {
        // 验证日期维度数据完整性
        List<Map<String, Object>> dateData = executeQuery(
            "SELECT COUNT(DISTINCT year) as years, COUNT(DISTINCT month) as months FROM dim_date"
        );
        assertFalse(dateData.isEmpty(), "日期维度数据异常");
        log.info("日期维度数据完整性: {}", dateData.get(0));

        // 验证商品维度数据完整性
        List<Map<String, Object>> productData = executeQuery(
            "SELECT COUNT(DISTINCT category_id) as categories, COUNT(DISTINCT brand) as brands FROM dim_product"
        );
        assertFalse(productData.isEmpty(), "商品维度数据异常");
        log.info("商品维度数据完整性: {}", productData.get(0));

        // 验证客户维度数据完整性
        List<Map<String, Object>> customerData = executeQuery(
            "SELECT COUNT(DISTINCT province) as provinces, COUNT(DISTINCT member_level) as levels FROM dim_customer"
        );
        assertFalse(customerData.isEmpty(), "客户维度数据异常");
        log.info("客户维度数据完整性: {}", customerData.get(0));
    }

    @Test
    @Order(7)
    @DisplayName("验证事实表外键关联")
    void testFactTableForeignKeys() {
        // 验证订单事实表与维度表的关联
        String sql = """
            SELECT
                (SELECT COUNT(*) FROM fact_order fo WHERE fo.customer_key IS NOT NULL
                    AND NOT EXISTS (SELECT 1 FROM dim_customer dc WHERE dc.customer_key = fo.customer_key)) as orphan_customer,
                (SELECT COUNT(*) FROM fact_order fo WHERE fo.store_key IS NOT NULL
                    AND NOT EXISTS (SELECT 1 FROM dim_store ds WHERE ds.store_key = fo.store_key)) as orphan_store,
                (SELECT COUNT(*) FROM fact_order fo WHERE fo.channel_key IS NOT NULL
                    AND NOT EXISTS (SELECT 1 FROM dim_channel dc WHERE dc.channel_key = fo.channel_key)) as orphan_channel
            """;

        List<Map<String, Object>> result = executeQuery(sql);
        if (!result.isEmpty()) {
            Map<String, Object> row = result.get(0);
            Long orphanCustomer = ((Number) row.get("orphan_customer")).longValue();
            Long orphanStore = ((Number) row.get("orphan_store")).longValue();
            Long orphanChannel = ((Number) row.get("orphan_channel")).longValue();

            assertEquals(0L, orphanCustomer, "存在孤立的客户外键");
            assertEquals(0L, orphanStore, "存在孤立的门店外键");
            assertEquals(0L, orphanChannel, "存在孤立的渠道外键");
            log.info("事实表外键关联验证通过");
        }
    }

    @Test
    @Order(8)
    @DisplayName("验证状态字典数据")
    void testDictStatusData() {
        // 验证订单状态字典
        List<Map<String, Object>> orderStatus = executeQuery(
            "SELECT status_code, status_name FROM dict_status WHERE status_type = 'ORDER_STATUS' ORDER BY sort_order"
        );
        assertFalse(orderStatus.isEmpty(), "订单状态字典为空");
        log.info("订单状态字典: {}", orderStatus);

        // 验证支付方式字典
        List<Map<String, Object>> payMethod = executeQuery(
            "SELECT status_code, status_name FROM dict_status WHERE status_type = 'PAY_METHOD' ORDER BY sort_order"
        );
        assertFalse(payMethod.isEmpty(), "支付方式字典为空");
        log.info("支付方式字典: {}", payMethod);
    }

    @Test
    @Order(9)
    @DisplayName("验证品类字典数据")
    void testDictCategoryData() {
        // 验证品类层级结构
        List<Map<String, Object>> categories = executeQuery(
            "SELECT category_level, COUNT(*) as count FROM dict_category GROUP BY category_level ORDER BY category_level"
        );
        assertFalse(categories.isEmpty(), "品类字典为空");
        log.info("品类层级分布: {}", categories);
    }

    @Test
    @Order(10)
    @DisplayName("验证地区字典数据")
    void testDictRegionData() {
        // 验证地区层级结构
        List<Map<String, Object>> regions = executeQuery(
            "SELECT region_level, COUNT(*) as count FROM dict_region GROUP BY region_level ORDER BY region_level"
        );
        assertFalse(regions.isEmpty(), "地区字典为空");
        log.info("地区层级分布: {}", regions);
    }
}
