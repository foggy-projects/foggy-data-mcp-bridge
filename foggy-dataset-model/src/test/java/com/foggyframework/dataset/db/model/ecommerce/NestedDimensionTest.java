package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.common.query.CondType;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.service.JdbcService;
import com.foggyframework.dataset.db.model.spi.DbDimension;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 嵌套维度测试（Nested Dimension / Snowflake Schema）
 *
 * <p>测试嵌套维度功能，包括：
 * <ul>
 *   <li>模型加载：验证嵌套维度结构正确解析</li>
 *   <li>路径访问：验证通过 product.category 路径语法访问嵌套维度</li>
 *   <li>别名格式：验证列名使用下划线分隔（如 product_category$id）</li>
 *   <li>多层级 JOIN：验证三层雪花结构的 SQL 生成</li>
 * </ul>
 *
 * <p>测试数据结构：
 * <pre>
 * 事实表: fact_sales_nested
 *   │
 *   ├── 产品维度: dim_product_nested (一级)
 *   │       │
 *   │       └── 品类维度: dim_category_nested (二级, 路径: product.category)
 *   │               │
 *   │               └── 品类组维度: dim_category_group (三级, 路径: product.category.group)
 *   │
 *   └── 门店维度: dim_store_nested (一级)
 *           │
 *           └── 区域维度: dim_region_nested (二级, 路径: store.region)
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
        TableModel model = tableModelLoaderManager.load("FactSalesNestedDimModel");

        assertNotNull(model, "模型应能正确加载");
        assertEquals("FactSalesNestedDimModel", model.getName());

        // 验证维度数量（包括嵌套维度，共应有7个维度：salesDate, product, category, group, store, region）
        List<DbDimension> dimensions = model.getDimensions();
        log.info("模型维度数量: {}", dimensions.size());
        for (DbDimension dim : dimensions) {
            log.info("  维度: {}, 完整路径: {}, 别名路径: {}, 是否嵌套: {}, 父维度: {}",
                    dim.getName(),
                    dim.getFullPath(),
                    dim.getFullPathForAlias(),
                    dim.isNestedDimension(),
                    dim.getParentDimension() != null ? dim.getParentDimension().getName() : "无");
        }

        // 验证顶层维度
        DbDimension productDim = model.findJdbcDimensionByName("product");
        assertNotNull(productDim, "应有 product 维度");
        assertFalse(productDim.isNestedDimension(), "product 应是顶层维度");
        assertTrue(productDim.hasChildDimensions(), "product 应有子维度");

        // 验证嵌套维度 - 品类
        DbDimension categoryDim = model.findJdbcDimensionByName("category");
        assertNotNull(categoryDim, "应有 category 维度");
        assertTrue(categoryDim.isNestedDimension(), "category 应是嵌套维度");
        assertEquals("product", categoryDim.getParentDimension().getName(), "category 的父维度应是 product");

        // 验证嵌套维度 - 品类组
        DbDimension groupDim = model.findJdbcDimensionByName("group");
        assertNotNull(groupDim, "应有 group 维度");
        assertTrue(groupDim.isNestedDimension(), "group 应是嵌套维度");
        assertEquals("category", groupDim.getParentDimension().getName(), "group 的父维度应是 category");

        // 验证完整路径（使用 . 分隔，用于 QM ref 语法）
        assertEquals("product.category", categoryDim.getFullPath(), "category 的完整路径应是 product.category");
        assertEquals("product.category.group", groupDim.getFullPath(), "group 的完整路径应是 product.category.group");

        // 验证别名路径（使用 _ 分隔，用于列名）
        assertEquals("product_category", categoryDim.getFullPathForAlias(), "category 的别名路径应是 product_category");
        assertEquals("product_category_group", groupDim.getFullPathForAlias(), "group 的别名路径应是 product_category_group");
    }

    // ==========================================
    // 查询测试 - 路径访问
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("查询测试 - 通过路径访问嵌套维度")
    void testQuery_AccessByPath() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel");

        // 使用下划线分隔的列名（对应 QM 中的 fs.product.category 语法）
        queryRequest.setColumns(Arrays.asList(
                "product$caption",              // 一级维度
                "product_category$caption",     // 二级维度（路径：product.category）
                "product_category_group$caption", // 三级维度（路径：product.category.group）
                "salesAmount"
        ));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
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
                    row.get("product_category$caption"),
                    row.get("product_category_group$caption"),
                    row.get("salesAmount"));
        }

        // 验证返回的列名包含下划线分隔的路径
        Map<String, Object> firstRow = (Map<String, Object>) result.getItems().get(0);
        assertTrue(firstRow.containsKey("product_category$caption"), "结果应包含 product_category$caption 列");
        assertTrue(firstRow.containsKey("product_category_group$caption"), "结果应包含 product_category_group$caption 列");
    }

    @Test
    @Order(21)
    @DisplayName("查询测试 - 门店区域嵌套维度")
    void testQuery_StoreRegionNestedDimension() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel");

        // 使用下划线分隔的列名
        queryRequest.setColumns(Arrays.asList(
                "store$caption",          // 一级维度
                "store_region$caption",   // 二级维度（路径：store.region）
                "store_region$province",  // 二级维度属性
                "store_region$city",      // 二级维度属性
                "salesAmount"
        ));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        assertTrue(result.getItems().size() > 0, "应有查询结果");

        log.info("门店区域查询结果数量: {}", result.getItems().size());
        for (int i = 0; i < Math.min(5, result.getItems().size()); i++) {
            Map<String, Object> row = (Map<String, Object>) result.getItems().get(i);
            log.info("Row {}: 门店={}, 区域={}, 省份={}, 城市={}, 销售额={}",
                    i + 1,
                    row.get("store$caption"),
                    row.get("store_region$caption"),
                    row.get("store_region$province"),
                    row.get("store_region$city"),
                    row.get("salesAmount"));
        }

        // 验证返回的列名包含下划线分隔的路径
        Map<String, Object> firstRow = (Map<String, Object>) result.getItems().get(0);
        assertTrue(firstRow.containsKey("store_region$caption"), "结果应包含 store_region$caption 列");
        assertTrue(firstRow.containsKey("store_region$province"), "结果应包含 store_region$province 列");
    }

    // ==========================================
    // 查询测试 - 切片过滤
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("切片查询 - 按嵌套维度过滤")
    void testQuery_SliceByNestedDimension() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "product$caption",
                "product_category$caption",
                "salesAmount"
        ));
        queryRequest.setReturnTotal(true);

        // 按品类组过滤（使用下划线分隔的路径）- 只查询电子产品组
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("product_category_group$id");
        slice.setOp(CondType.EQ.getCode());
        slice.setValue(1);  // 电子产品组的 group_key
        queryRequest.setSlice(Collections.singletonList(slice));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        log.info("电子产品组销售记录数: {}", result.getItems().size());

        // 验证所有结果都是电子产品组的
        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            String categoryCaption = (String) row.get("product_category$caption");
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

        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "product_category_group$caption",
                "salesAmount"
        ));
        queryRequest.setReturnTotal(true);

        // 按品类组分组（使用下划线分隔的路径）
        GroupRequestDef groupBy = new GroupRequestDef();
        groupBy.setField("product_category_group$caption");
        queryRequest.setGroupBy(Collections.singletonList(groupBy));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        log.info("按品类组分组查询结果数量: {}", result.getItems().size());

        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            log.info("品类组={}, 销售总额={}",
                    row.get("product_category_group$caption"),
                    row.get("salesAmount"));
        }

        // 验证分组数量应该等于品类组数量（3个）
        assertEquals(3, result.getItems().size(), "应有3个品类组的分组结果");
    }

    // ==========================================
    // 别名测试 - alias 特性
    // ==========================================

    @Test
    @Order(50)
    @DisplayName("模型加载 - 验证嵌套维度别名")
    void testModelLoading_NestedDimensionAlias() {
        TableModel model = tableModelLoaderManager.load("FactSalesNestedDimModel");

        // 验证可以通过别名查找维度
        DbDimension productCategory = model.findJdbcDimensionByName("productCategory");
        assertNotNull(productCategory, "应能通过别名 productCategory 找到维度");
        assertEquals("category", productCategory.getName(), "维度名称应是 category");
        assertEquals("productCategory", productCategory.getAlias(), "维度别名应是 productCategory");

        DbDimension categoryGroup = model.findJdbcDimensionByName("categoryGroup");
        assertNotNull(categoryGroup, "应能通过别名 categoryGroup 找到维度");
        assertEquals("group", categoryGroup.getName(), "维度名称应是 group");
        assertEquals("categoryGroup", categoryGroup.getAlias(), "维度别名应是 categoryGroup");

        DbDimension storeRegion = model.findJdbcDimensionByName("storeRegion");
        assertNotNull(storeRegion, "应能通过别名 storeRegion 找到维度");
        assertEquals("region", storeRegion.getName(), "维度名称应是 region");
        assertEquals("storeRegion", storeRegion.getAlias(), "维度别名应是 storeRegion");

        log.info("别名查找测试通过：productCategory={}, categoryGroup={}, storeRegion={}",
                productCategory.getFullPath(), categoryGroup.getFullPath(), storeRegion.getFullPath());
    }

    @Test
    @Order(51)
    @DisplayName("查询测试 - 通过别名访问嵌套维度")
    void testQuery_AccessByAlias() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel");

        // 使用别名访问嵌套维度（文档推荐方式）
        queryRequest.setColumns(Arrays.asList(
                "product$caption",           // 一级维度
                "productCategory$caption",   // 二级维度（通过 alias）
                "categoryGroup$caption",     // 三级维度（通过 alias）
                "salesAmount"
        ));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        assertNotNull(result.getItems(), "明细数据不应为空");
        assertTrue(result.getItems().size() > 0, "应有查询结果");

        log.info("通过别名访问查询结果数量: {}", result.getItems().size());
        for (int i = 0; i < Math.min(5, result.getItems().size()); i++) {
            Map<String, Object> row = (Map<String, Object>) result.getItems().get(i);
            log.info("Row {}: 产品={}, 品类={}, 品类组={}, 销售额={}",
                    i + 1,
                    row.get("product$caption"),
                    row.get("productCategory$caption"),
                    row.get("categoryGroup$caption"),
                    row.get("salesAmount"));
        }

        // 验证返回的列名使用别名格式
        Map<String, Object> firstRow = (Map<String, Object>) result.getItems().get(0);
        assertTrue(firstRow.containsKey("productCategory$caption"), "结果应包含 productCategory$caption 列");
        assertTrue(firstRow.containsKey("categoryGroup$caption"), "结果应包含 categoryGroup$caption 列");
    }

    @Test
    @Order(52)
    @DisplayName("切片查询 - 通过别名过滤")
    void testQuery_SliceByAlias() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "product$caption",
                "productCategory$caption",
                "salesAmount"
        ));
        queryRequest.setReturnTotal(true);

        // 使用别名过滤 - 只查询电子产品组
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("categoryGroup$id");  // 使用别名
        slice.setOp(CondType.EQ.getCode());
        slice.setValue(1);  // 电子产品组的 group_key
        queryRequest.setSlice(Collections.singletonList(slice));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        log.info("通过别名过滤电子产品组销售记录数: {}", result.getItems().size());

        // 验证结果数量与使用路径格式一致
        assertTrue(result.getItems().size() > 0, "应有查询结果");
    }

    @Test
    @Order(53)
    @DisplayName("汇总查询 - 通过别名分组")
    void testQuery_GroupByAlias() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesNestedDimQueryModel");
        queryRequest.setColumns(Arrays.asList(
                "categoryGroup$caption",  // 使用别名
                "salesAmount"
        ));
        queryRequest.setReturnTotal(true);

        // 使用别名分组
        GroupRequestDef groupBy = new GroupRequestDef();
        groupBy.setField("categoryGroup$caption");  // 使用别名
        queryRequest.setGroupBy(Collections.singletonList(groupBy));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 20);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertNotNull(result, "查询结果不应为空");
        log.info("通过别名分组查询结果数量: {}", result.getItems().size());

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
