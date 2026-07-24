package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.dataset.model.spi.DbQueryColumn;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 维度自动展开测试
 *
 * <p>验证 QM 中 { ref: fo.dimension } 根据上下文自动展开属性和嵌套子维度：
 * <ul>
 *   <li>无显式属性引用时 → 自动展开 $id + $caption + 所有属性 + 嵌套子维度（递归）</li>
 *   <li>有显式属性引用时 → 仅展开 $id + $caption，属性由用户手动控制</li>
 * </ul>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DimensionAutoExpandTest extends EcommerceTestSupport {

    // ==================== 自动展开（无显式属性引用）====================

    @Test
    @Order(1)
    @DisplayName("加载自动展开查询模型")
    void testLoadAutoExpandQueryModel() {
        JdbcQueryModel qm = getQueryModel("FactSalesAutoExpandQueryModel");
        assertNotNull(qm, "FactSalesAutoExpandQueryModel 加载失败");
        assertNotNull(qm.getColumnGroups(), "列组不应为空");
        log.info("自动展开QM加载成功，列组数={}", qm.getColumnGroups().size());
    }

    @Test
    @Order(10)
    @DisplayName("自动展开 - product 维度应包含 $id + $caption + 所有属性")
    void testAutoExpand_ProductDimension_IncludesAllProperties() {
        JdbcQueryModel qm = getQueryModel("FactSalesAutoExpandQueryModel");
        Set<String> columnNames = getColumnNames(qm);

        // $id + $caption
        assertTrue(columnNames.contains("product$id"), "应包含 product$id");
        assertTrue(columnNames.contains("product$caption"), "应包含 product$caption");

        // 属性: product_id, brand, unit_price
        assertTrue(columnNames.contains("product$productId"), "应自动展开 product$productId");
        assertTrue(columnNames.contains("product$brand"), "应自动展开 product$brand");
        assertTrue(columnNames.contains("product$unitPrice"), "应自动展开 product$unitPrice");

        log.info("product 维度自动展开验证通过: {}", columnNames.stream()
                .filter(n -> n.startsWith("product$")).sorted().collect(Collectors.toList()));
    }

    @Test
    @Order(11)
    @DisplayName("自动展开 - product 维度应递归展开嵌套 category 子维度")
    void testAutoExpand_ProductDimension_ExpandsNestedCategory() {
        JdbcQueryModel qm = getQueryModel("FactSalesAutoExpandQueryModel");
        Set<String> columnNames = getColumnNames(qm);

        // category 嵌套维度的 $id/$caption（注意：维度列名以 DOT 格式注册）
        assertTrue(columnNames.contains("product.category$id"), "应递归展开 product.category$id");
        assertTrue(columnNames.contains("product.category$caption"), "应递归展开 product.category$caption");
        // category 属性（属性列名以 UNDERSCORE 格式注册）
        assertTrue(columnNames.contains("product_category$categoryId"), "应递归展开 product_category$categoryId");
        assertTrue(columnNames.contains("product_category$categoryLevel"), "应递归展开 product_category$categoryLevel");

        log.info("product.category 嵌套维度自动展开验证通过");
    }

    @Test
    @Order(12)
    @DisplayName("自动展开 - product 维度应递归展开三级嵌套 category.group 子维度")
    void testAutoExpand_ProductDimension_ExpandsNestedCategoryGroup() {
        JdbcQueryModel qm = getQueryModel("FactSalesAutoExpandQueryModel");
        Set<String> columnNames = getColumnNames(qm);

        // category.group 三级嵌套维度 $id/$caption（DOT 格式）
        assertTrue(columnNames.contains("product.category.group$id"),
                "应递归展开三级嵌套 product.category.group$id");
        assertTrue(columnNames.contains("product.category.group$caption"),
                "应递归展开三级嵌套 product.category.group$caption");
        // group 属性（UNDERSCORE 格式）
        assertTrue(columnNames.contains("product_category_group$groupId"),
                "应递归展开三级嵌套 product_category_group$groupId");
        assertTrue(columnNames.contains("product_category_group$groupType"),
                "应递归展开三级嵌套 product_category_group$groupType");

        log.info("product.category.group 三级嵌套维度自动展开验证通过");
    }

    @Test
    @Order(13)
    @DisplayName("自动展开 - store 维度应包含属性和嵌套 region 子维度")
    void testAutoExpand_StoreDimension_IncludesPropertiesAndNested() {
        JdbcQueryModel qm = getQueryModel("FactSalesAutoExpandQueryModel");
        Set<String> columnNames = getColumnNames(qm);

        // store $id + $caption + 属性
        assertTrue(columnNames.contains("store$id"), "应包含 store$id");
        assertTrue(columnNames.contains("store$caption"), "应包含 store$caption");
        assertTrue(columnNames.contains("store$storeId"), "应自动展开 store$storeId");
        assertTrue(columnNames.contains("store$storeType"), "应自动展开 store$storeType");

        // region 嵌套维度 $id/$caption（DOT 格式）
        assertTrue(columnNames.contains("store.region$id"), "应递归展开 store.region$id");
        assertTrue(columnNames.contains("store.region$caption"), "应递归展开 store.region$caption");
        // region 属性（UNDERSCORE 格式）
        assertTrue(columnNames.contains("store_region$regionId"), "应递归展开 store_region$regionId");
        assertTrue(columnNames.contains("store_region$province"), "应递归展开 store_region$province");
        assertTrue(columnNames.contains("store_region$city"), "应递归展开 store_region$city");

        log.info("store 维度及 region 嵌套子维度自动展开验证通过");
    }

    @Test
    @Order(14)
    @DisplayName("自动展开 - 度量列不受影响")
    void testAutoExpand_MeasuresUnaffected() {
        JdbcQueryModel qm = getQueryModel("FactSalesAutoExpandQueryModel");
        Set<String> columnNames = getColumnNames(qm);

        assertTrue(columnNames.contains("quantity"), "度量 quantity 应正常存在");
        assertTrue(columnNames.contains("salesAmount"), "度量 salesAmount 应正常存在");
    }

    @Test
    @Order(15)
    @DisplayName("自动展开 - 总列数验证")
    void testAutoExpand_TotalColumnCount() {
        JdbcQueryModel qm = getQueryModel("FactSalesAutoExpandQueryModel");
        Set<String> columnNames = getColumnNames(qm);

        // product: $id + $caption + 3属性 = 5
        // product.category: $id + $caption + 2属性 = 4
        // product.category.group: $id + $caption + 2属性 = 4
        // store: $id + $caption + 2属性 = 4
        // store.region: $id + $caption + 3属性 = 5
        // 度量: quantity + salesAmount = 2
        // 合计 = 24
        assertEquals(24, columnNames.size(), "自动展开后总列数应为 24");
    }

    @Test
    @Order(16)
    @DisplayName("自动展开 - 根维属性字段名不应因复用同一 item 而重复")
    void testAutoExpand_ProductDimension_ExpandedFieldsShouldBeUnique() {
        JdbcQueryModel qm = getQueryModel("FactSalesAutoExpandQueryModel");
        Map<String, String> columnFields = getColumnFields(qm);

        assertDistinctFields(columnFields,
                "product$id",
                "product$caption",
                "product$productId",
                "product$brand",
                "product$unitPrice");
    }

    @Test
    @Order(17)
    @DisplayName("自动展开 - 嵌套维属性字段名不应因递归复用同一 item 而重复")
    void testAutoExpand_NestedDimension_ExpandedFieldsShouldBeUnique() {
        JdbcQueryModel qm = getQueryModel("FactSalesAutoExpandQueryModel");
        Map<String, String> columnFields = getColumnFields(qm);

        assertDistinctFields(columnFields,
                "store.region$id",
                "store.region$caption",
                "store_region$regionId",
                "store_region$province",
                "store_region$city");
    }

    // ==================== 显式属性引用时不自动展开 ====================

    @Test
    @Order(20)
    @DisplayName("显式引用时不自动展开 - FactSalesNestedDimQueryModel 中 product 有显式属性引用")
    void testNoAutoExpand_WhenExplicitPropertiesExist() {
        // FactSalesNestedDimQueryModel 中 product 维度同时有显式属性引用（brand, unitPrice）
        // 此时不应自动展开其他属性（如 productId）
        JdbcQueryModel qm = getQueryModel("FactSalesNestedDimQueryModel");
        Set<String> columnNames = getColumnNames(qm);

        // 显式引用的应存在
        assertTrue(columnNames.contains("product$id"), "应包含 product$id");
        assertTrue(columnNames.contains("product$caption"), "应包含 product$caption");
        assertTrue(columnNames.contains("product$brand"), "显式引用的 product$brand 应存在");
        assertTrue(columnNames.contains("product$unitPrice"), "显式引用的 product$unitPrice 应存在");

        // 未显式引用的不应被自动添加
        assertFalse(columnNames.contains("product$productId"),
                "有显式属性引用时，未引用的 product$productId 不应被自动展开");

        log.info("显式属性引用场景下未自动展开其他属性验证通过");
    }

    @Test
    @Order(21)
    @DisplayName("显式引用嵌套维度时不递归展开 - FactSalesNestedDimQueryModel")
    void testNoAutoExpand_WhenNestedDimensionExplicitlyReferenced() {
        // FactSalesNestedDimQueryModel 中 product.category 和 product.category.group
        // 都被显式引用为独立 ref，所以 product 的展开不应递归到这些子维度
        JdbcQueryModel qm = getQueryModel("FactSalesNestedDimQueryModel");
        Set<String> columnNames = getColumnNames(qm);

        // 这些由各自的显式 ref 展开（DOT 格式）
        assertTrue(columnNames.contains("product.category$id"), "应包含 product.category$id");
        assertTrue(columnNames.contains("product.category$caption"), "应包含 product.category$caption");
        assertTrue(columnNames.contains("product.category.group$id"), "应包含 product.category.group$id");

        log.info("显式嵌套维度引用场景验证通过");
    }

    // ==================== 辅助方法 ====================

    private Set<String> getColumnNames(JdbcQueryModel qm) {
        List<DbQueryColumn> columns = qm.getJdbcQueryColumns();
        assertNotNull(columns, "查询列不应为空");
        Set<String> names = columns.stream()
                .map(DbQueryColumn::getName)
                .collect(Collectors.toSet());
        log.info("QM [{}] 共 {} 个查询列: {}", qm.getName(), names.size(),
                names.stream().sorted().collect(Collectors.toList()));
        return names;
    }

    private Map<String, String> getColumnFields(JdbcQueryModel qm) {
        List<DbQueryColumn> columns = qm.getJdbcQueryColumns();
        assertNotNull(columns, "查询列不应为空");
        Map<String, String> fields = columns.stream()
                .collect(Collectors.toMap(DbQueryColumn::getName, DbQueryColumn::getField));
        log.info("QM [{}] 字段映射: {}", qm.getName(), fields);
        return fields;
    }

    private void assertDistinctFields(Map<String, String> columnFields, String... columnNames) {
        Map<String, String> selected = List.of(columnNames).stream()
                .collect(Collectors.toMap(Function.identity(), name -> {
                    String field = columnFields.get(name);
                    assertNotNull(field, "缺少列: " + name);
                    return field;
                }));

        long distinctCount = selected.values().stream().distinct().count();
        assertEquals(selected.size(), distinctCount,
                "自动展开列的 field 不应重复，实际映射: " + selected);
    }
}
