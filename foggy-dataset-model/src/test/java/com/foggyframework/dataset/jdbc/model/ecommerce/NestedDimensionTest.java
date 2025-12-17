package com.foggyframework.dataset.jdbc.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.common.query.CondType;
import com.foggyframework.dataset.jdbc.model.def.query.request.*;
import com.foggyframework.dataset.jdbc.model.service.JdbcService;
import com.foggyframework.dataset.jdbc.model.spi.JdbcDimension;
import com.foggyframework.dataset.jdbc.model.spi.JdbcModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 嵌套维度测试（Nested Dimension / Snowflake Schema）
 *
 * <p>测试嵌套维度功能，包括：
 * <ul>
 *   <li>模型加载：验证嵌套维度结构正确解析</li>
 *   <li>别名访问：验证通过 alias 访问嵌套维度</li>
 *   <li>完整路径访问：验证通过 parent.child 路径访问</li>
 *   <li>多层级 JOIN：验证三层雪花结构的 SQL 生成</li>
 * </ul>
 *
 * <p>测试数据结构：
 * <pre>
 * 事实表: fact_sales_nested
 *   │
 *   ├── 产品维度: dim_product_nested (一级)
 *   │       │
 *   │       └── 品类维度: dim_category_nested (二级, alias: productCategory)
 *   │               │
 *   │               └── 品类组维度: dim_category_group (三级, alias: categoryGroup)
 *   │
 *   └── 门店维度: dim_store_nested (一级)
 *           │
 *           └── 区域维度: dim_region_nested (二级, alias: storeRegion)
 * </pre>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("嵌套维度测试 - Nested Dimension (Snowflake Schema)")
class NestedDimensionTest extends EcommerceTestSupport {

    @Resource
    private JdbcService jdbcService;

    // ==========================================
    // 数据环境验证
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("验证嵌套维度测试表数据")
    void testNestedDimensionData() {
        // 验证品类组维度表
        Long groupCount = getTableCount("dim_category_group");
        assertEquals(3L, groupCount, "应有3个品类组");
        log.info("品类组维度表记录数: {}", groupCount);

        // 验证品类维度表
        Long categoryCount = getTableCount("dim_category_nested");
        assertEquals(7L, categoryCount, "应有7个品类");
        log.info("品类维度表记录数: {}", categoryCount);

        // 验证嵌套产品维度表
        Long productCount = getTableCount("dim_product_nested");
        assertEquals(10L, productCount, "应有10个产品");
        log.info("嵌套产品维度表记录数: {}", productCount);

        // 验证区域维度表
        Long regionCount = getTableCount("dim_region_nested");
        assertEquals(5L, regionCount, "应有5个区域");
        log.info("区域维度表记录数: {}", regionCount);

        // 验证嵌套门店维度表
        Long storeCount = getTableCount("dim_store_nested");
        assertEquals(6L, storeCount, "应有6个门店");
        log.info("嵌套门店维度表记录数: {}", storeCount);

        // 验证嵌套销售事实表
        Long salesCount = getTableCount("fact_sales_nested");
        assertTrue(salesCount > 0, "销售事实表应有数据");
        log.info("嵌套销售事实表记录数: {}", salesCount);
    }

    @Test
    @Order(2)
    @DisplayName("验证维度关联关系")
    void testDimensionRelationships() {
        // 验证产品 -> 品类的关联
        String productCategorySql = """
            SELECT COUNT(*) FROM dim_product_nested p
            JOIN dim_category_nested c ON p.category_key = c.category_key
            """;
        Long productCategoryCount = executeQueryForObject(productCategorySql, Long.class);
        assertEquals(10L, productCategoryCount, "所有产品应能关联到品类");

        // 验证品类 -> 品类组的关联
        String categoryGroupSql = """
            SELECT COUNT(*) FROM dim_category_nested c
            JOIN dim_category_group g ON c.group_key = g.group_key
            """;
        Long categoryGroupCount = executeQueryForObject(categoryGroupSql, Long.class);
        assertEquals(7L, categoryGroupCount, "所有品类应能关联到品类组");

        // 验证门店 -> 区域的关联
        String storeRegionSql = """
            SELECT COUNT(*) FROM dim_store_nested s
            JOIN dim_region_nested r ON s.region_key = r.region_key
            """;
        Long storeRegionCount = executeQueryForObject(storeRegionSql, Long.class);
        assertEquals(6L, storeRegionCount, "所有门店应能关联到区域");
    }

    // ==========================================
    // 模型加载测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("模型加载 - 验证嵌套维度结构")
    void testModelLoading_NestedDimensionStructure() {
        JdbcModel model = jdbcModelLoader.load("FactSalesNestedDimModel");

        assertNotNull(model, "模型应能正确加载");
        assertEquals("FactSalesNestedDimModel", model.getName());

        // 验证维度数量（包括嵌套维度，共应有7个维度：salesDate, product, category, group, store, region）
        List<JdbcDimension> dimensions = model.getDimensions();
        log.info("模型维度数量: {}", dimensions.size());
        for (JdbcDimension dim : dimensions) {
            log.info("  维度: {}, 别名: {}, 是否嵌套: {}, 父维度: {}",
                    dim.getName(),
                    dim.getAlias(),
                    dim.isNestedDimension(),
                    dim.getParentDimension() != null ? dim.getParentDimension().getName() : "无");
        }

        // 验证顶层维度
        JdbcDimension productDim = model.findJdbcDimensionByName("product");
        assertNotNull(productDim, "应有 product 维度");
        assertFalse(productDim.isNestedDimension(), "product 应是顶层维度");
        assertTrue(productDim.hasChildDimensions(), "product 应有子维度");

        // 验证嵌套维度 - 品类
        JdbcDimension categoryDim = model.findJdbcDimensionByName("category");
        assertNotNull(categoryDim, "应有 category 维度");
        assertTrue(categoryDim.isNestedDimension(), "category 应是嵌套维度");
        assertEquals("productCategory", categoryDim.getAlias(), "category 的别名应是 productCategory");
        assertEquals("product", categoryDim.getParentDimension().getName(), "category 的父维度应是 product");

        // 验证嵌套维度 - 品类组
        JdbcDimension groupDim = model.findJdbcDimensionByName("group");
        assertNotNull(groupDim, "应有 group 维度");
        assertTrue(groupDim.isNestedDimension(), "group 应是嵌套维度");
        assertEquals("categoryGroup", groupDim.getAlias(), "group 的别名应是 categoryGroup");
        assertEquals("category", groupDim.getParentDimension().getName(), "group 的父维度应是 category");

        // 验证完整路径
        assertEquals("product.category", categoryDim.getFullPath(), "category 的完整路径应是 product.category");
        assertEquals("product.category.group", groupDim.getFullPath(), "group 的完整路径应是 product.category.group");
    }

    // ==========================================
    // 查询测试 - 别名访问
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("查询测试 - 通过别名访问嵌套维度")
    void testQuery_AccessByAlias() {
        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel");

        // 使用别名访问嵌套维度列
        queryRequest.setColumns(Arrays.asList(
                "product$caption",           // 一级维度
                "productCategory$caption",   // 二级维度（通过别名）
                "categoryGroup$caption",     // 三级维度（通过别名）
                "salesAmount"
        ));

        PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        assertNotNull(result.getItems(), "明细数据不应为空");
        assertTrue(result.getItems().size() > 0, "应有查询结果");

        log.info("查询结果数量: {}", result.getItems().size());
        for (int i = 0; i < Math.min(5, result.getItems().size()); i++) {
            Map<String, Object> row = (Map<String, Object>) result.getItems().get(i);
            log.info("Row {}: 产品={}, 品类={}, 品类组={}, 销售额={}",
                    i + 1,
                    row.get("product$caption"),
                    row.get("productCategory$caption"),
                    row.get("categoryGroup$caption"),
                    row.get("salesAmount"));
        }

        // 验证返回的列名包含别名
        Map<String, Object> firstRow = (Map<String, Object>) result.getItems().get(0);
        assertTrue(firstRow.containsKey("productCategory$caption"), "结果应包含 productCategory$caption 列");
        assertTrue(firstRow.containsKey("categoryGroup$caption"), "结果应包含 categoryGroup$caption 列");
    }

    @Test
    @Order(21)
    @DisplayName("查询测试 - 门店区域嵌套维度")
    void testQuery_StoreRegionNestedDimension() {
        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel");

        // 使用别名访问门店和区域维度
        queryRequest.setColumns(Arrays.asList(
                "store$caption",         // 一级维度
                "storeRegion$caption",   // 二级维度（通过别名）
                "storeRegion$province",  // 二级维度属性
                "storeRegion$city",      // 二级维度属性
                "salesAmount"
        ));

        PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        assertTrue(result.getItems().size() > 0, "应有查询结果");

        log.info("门店区域查询结果数量: {}", result.getItems().size());
        for (int i = 0; i < Math.min(5, result.getItems().size()); i++) {
            Map<String, Object> row = (Map<String, Object>) result.getItems().get(i);
            log.info("Row {}: 门店={}, 区域={}, 省份={}, 城市={}, 销售额={}",
                    i + 1,
                    row.get("store$caption"),
                    row.get("storeRegion$caption"),
                    row.get("storeRegion$province"),
                    row.get("storeRegion$city"),
                    row.get("salesAmount"));
        }

        // 验证返回的列名包含别名
        Map<String, Object> firstRow = (Map<String, Object>) result.getItems().get(0);
        assertTrue(firstRow.containsKey("storeRegion$caption"), "结果应包含 storeRegion$caption 列");
        assertTrue(firstRow.containsKey("storeRegion$province"), "结果应包含 storeRegion$province 列");
    }

    // ==========================================
    // 查询测试 - 切片过滤
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("切片查询 - 按嵌套维度过滤")
    void testQuery_SliceByNestedDimension() {
        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "product$caption",
                "productCategory$caption",
                "salesAmount"
        ));
        queryRequest.setReturnTotal(true);

        // 按品类组过滤（通过别名）- 只查询电子产品组
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("categoryGroup$id");
        slice.setOp(CondType.EQ.getCode());
        slice.setValue(1);  // 电子产品组的 group_key
        queryRequest.setSlice(Collections.singletonList(slice));

        PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        log.info("电子产品组销售记录数: {}", result.getItems().size());

        // 验证所有结果都是电子产品组的
        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            String categoryCaption = (String) row.get("productCategory$caption");
            log.info("产品={}, 品类={}, 销售额={}",
                    row.get("product$caption"),
                    categoryCaption,
                    row.get("salesAmount"));
            // 电子产品组下的品类应该是：数码电器、手机通讯、电脑办公
            assertTrue(
                    categoryCaption.contains("数码") || categoryCaption.contains("手机") || categoryCaption.contains("电脑"),
                    "品类应属于电子产品组"
            );
        }
    }

    // ==========================================
    // 汇总测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("汇总查询 - 按嵌套维度分组")
    void testQuery_GroupByNestedDimension() {
        // 使用原生SQL计算预期值：按品类组汇总
        String expectedSql = """
            SELECT g.group_name, SUM(f.sales_amount) as total
            FROM fact_sales_nested f
            JOIN dim_product_nested p ON f.product_key = p.product_key
            JOIN dim_category_nested c ON p.category_key = c.category_key
            JOIN dim_category_group g ON c.group_key = g.group_key
            GROUP BY g.group_key, g.group_name
            ORDER BY total DESC
            """;
        List<Map<String, Object>> expectedResults = executeQuery(expectedSql);
        log.info("预期按品类组汇总结果: {}", expectedResults);

        JdbcQueryRequestDef queryRequest = new JdbcQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "categoryGroup$caption",
                "salesAmount"
        ));
        queryRequest.setReturnTotal(true);

        // 按品类组分组
        GroupRequestDef groupBy = new GroupRequestDef();
        groupBy.setField("categoryGroup$caption");
        queryRequest.setGroupBy(Collections.singletonList(groupBy));

        PagingRequest<JdbcQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        log.info("按品类组分组查询结果数量: {}", result.getItems().size());

        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            log.info("品类组={}, 销售总额={}",
                    row.get("categoryGroup$caption"),
                    row.get("salesAmount"));
        }

        // 验证分组数量应该等于品类组数量（3个）
        assertEquals(3, result.getItems().size(), "应有3个品类组的分组结果");
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return new BigDecimal(value.toString());
    }

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual, String message) {
        assertEquals(
                expected.setScale(2, RoundingMode.HALF_UP),
                actual.setScale(2, RoundingMode.HALF_UP),
                message
        );
    }
}
