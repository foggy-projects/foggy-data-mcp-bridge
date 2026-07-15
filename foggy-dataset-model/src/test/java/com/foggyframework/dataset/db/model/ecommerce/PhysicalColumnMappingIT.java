package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.dataset.db.model.spi.PhysicalColumnMapping;
import com.foggyframework.dataset.db.model.spi.PhysicalColumnRef;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 物理列映射缓存集成测试
 * <p>
 * 验证 QM 加载时自动构建的 QM 字段名 ↔ 物理 table.column 双向映射。
 * 使用真实 ecommerce QM 模型。
 */
@DisplayName("物理列映射缓存集成测试")
class PhysicalColumnMappingIT extends EcommerceTestSupport {

    private static final String SALES_QM = "FactSalesQueryModel";

    private PhysicalColumnMapping getMapping(String qmName) {
        QueryModel qm = getQueryModel(qmName);
        assertNotNull(qm, "QM 模型应存在: " + qmName);
        PhysicalColumnMapping mapping = qm.getPhysicalColumnMapping();
        assertNotNull(mapping, "物理列映射缓存应已构建");
        return mapping;
    }

    /** 断言正向映射：QM 字段 → 物理 table.column */
    private void assertMapsTo(PhysicalColumnMapping mapping, String qmField, String table, String column) {
        List<PhysicalColumnRef> refs = mapping.getPhysicalColumns(qmField);
        assertFalse(refs.isEmpty(), qmField + " 应有物理列映射");
        assertTrue(refs.stream().anyMatch(r -> table.equals(r.table()) && column.equals(r.column())),
                qmField + " 应映射到 " + table + "." + column + ", 实际: " + refs);
    }

    /** 断言反向映射：物理 table.column → QM 字段名 */
    private void assertReverseMapsTo(PhysicalColumnMapping mapping, String table, String column, String expectedQmField) {
        List<String> qmNames = mapping.getQmFieldNames(table, column);
        assertTrue(qmNames.contains(expectedQmField),
                table + "." + column + " 应反向映射到 " + expectedQmField + ", 实际: " + qmNames);
    }

    // ==================== 基本结构 ====================

    @Nested
    @DisplayName("映射基本结构")
    class BasicStructure {

        @Test
        @DisplayName("映射缓存在 QM 加载后自动可用")
        void mappingAvailableAfterLoad() {
            PhysicalColumnMapping mapping = getMapping(SALES_QM);
            assertFalse(mapping.getAllQmFieldNames().isEmpty(), "应包含 QM 字段");
            assertFalse(mapping.getAllPhysicalTables().isEmpty(), "应包含物理表");
        }

        @Test
        @DisplayName("物理表包含事实表 fact_sales")
        void containsFactTable() {
            PhysicalColumnMapping mapping = getMapping(SALES_QM);
            assertTrue(mapping.getAllPhysicalTables().contains("fact_sales"),
                    "物理表应包含 fact_sales");
        }
    }

    // ==================== 度量映射 ====================

    @Nested
    @DisplayName("度量字段映射")
    class MeasureMapping {

        @Test
        @DisplayName("度量 salesAmount → fact_sales.sales_amount")
        void measureMapsToFactColumn() {
            assertMapsTo(getMapping(SALES_QM), "salesAmount", "fact_sales", "sales_amount");
        }

        @Test
        @DisplayName("反向查找：fact_sales.sales_amount → salesAmount")
        void reverseLookupsalesAmount() {
            assertReverseMapsTo(getMapping(SALES_QM), "fact_sales", "sales_amount", "salesAmount");
        }
    }

    // ==================== 维度映射 ====================

    @Nested
    @DisplayName("维度字段映射")
    class DimensionMapping {

        @Test
        @DisplayName("维度 $id 映射到事实表 FK")
        void dimensionIdMapsToFk() {
            PhysicalColumnMapping mapping = getMapping(SALES_QM);
            List<PhysicalColumnRef> refs = mapping.getPhysicalColumns("store$id");
            assertFalse(refs.isEmpty(), "store$id 应有物理列映射");
            // 至少有事实表的 FK 映射
            assertTrue(refs.stream().anyMatch(r -> "fact_sales".equals(r.table())),
                    "store$id 应包含 fact_sales 上的 FK 映射, 实际: " + refs);
        }

        @Test
        @DisplayName("维度 $caption 映射到维度表列")
        void dimensionCaptionMapsToDimTable() {
            PhysicalColumnMapping mapping = getMapping(SALES_QM);
            List<PhysicalColumnRef> refs = mapping.getPhysicalColumns("store$caption");
            assertFalse(refs.isEmpty(), "store$caption 应有物理列映射");
        }
    }

    // ==================== deniedColumns 转换 ====================

    @Nested
    @DisplayName("维度属性映射")
    class DimensionPropertyMapping {

        @Test
        @DisplayName("维度属性 customer$customerType → dim_customer.customer_type")
        void dimensionPropertyMapsToPhysicalColumn() {
            assertMapsTo(getMapping(SALES_QM), "customer$customerType", "dim_customer", "customer_type");
        }

        @Test
        @DisplayName("反向查找 dim_customer.customer_type → customer$customerType")
        void reverseLookupDimCustomerColumn() {
            assertReverseMapsTo(getMapping(SALES_QM), "dim_customer", "customer_type", "customer$customerType");
        }

        @Test
        @DisplayName("deniedColumns dim_customer.customer_type 转换包含 customer$customerType")
        void deniedDimColumnConvertsToQmField() {
            Set<String> denied = getMapping(SALES_QM).toDeniedQmFields(List.of(
                    new DeniedPhysicalColumn(null, "dim_customer", "customer_type")
            ));
            assertTrue(denied.contains("customer$customerType"),
                    "denied dim_customer.customer_type 应含 customer$customerType，实际: " + denied);
        }

        @Test
        @DisplayName("物理表集合包含维度表")
        void physicalTablesContainsDimTables() {
            Set<String> tables = getMapping(SALES_QM).getAllPhysicalTables();
            assertAll(
                    () -> assertTrue(tables.contains("dim_customer"), "应包含 dim_customer"),
                    () -> assertTrue(tables.contains("dim_product"), "应包含 dim_product"),
                    () -> assertTrue(tables.contains("dim_store"), "应包含 dim_store")
            );
        }

        @Test
        @DisplayName("维度 $id 同时映射到事实表 FK 和维度表 PK")
        void dimensionIdMapsToBothFkAndPk() {
            PhysicalColumnMapping mapping = getMapping(SALES_QM);
            assertAll(
                    () -> assertMapsTo(mapping, "customer$id", "fact_sales", "customer_key"),
                    () -> assertMapsTo(mapping, "customer$id", "dim_customer", "customer_key")
            );
        }

        @Test
        @DisplayName("维度 $caption 映射到维度表")
        void dimensionCaptionMapsToDimTable() {
            assertMapsTo(getMapping(SALES_QM), "customer$caption", "dim_customer", "customer_name");
        }
    }

    @Nested
    @DisplayName("deniedColumns → denied QM 字段转换")
    class DeniedConversion {

        @Test
        @DisplayName("物理列黑名单转换为 denied QM 字段集合")
        void toDeniedQmFields_convertsCorrectly() {
            PhysicalColumnMapping mapping = getMapping(SALES_QM);

            List<DeniedPhysicalColumn> denied = List.of(
                    new DeniedPhysicalColumn(null, "fact_sales", "sales_amount")
            );

            Set<String> deniedQmFields = mapping.toDeniedQmFields(denied);
            assertTrue(deniedQmFields.contains("salesAmount"),
                    "denied fact_sales.sales_amount 应转换为 QM 字段 salesAmount, 实际: " + deniedQmFields);
        }

        @Test
        @DisplayName("空 deniedColumns 返回空集合")
        void toDeniedQmFields_emptyInput() {
            PhysicalColumnMapping mapping = getMapping(SALES_QM);
            assertEquals(Set.of(), mapping.toDeniedQmFields(List.of()));
            assertEquals(Set.of(), mapping.toDeniedQmFields(null));
        }

        @Test
        @DisplayName("不匹配的物理列返回空集合")
        void toDeniedQmFields_noMatch() {
            PhysicalColumnMapping mapping = getMapping(SALES_QM);
            List<DeniedPhysicalColumn> denied = List.of(
                    new DeniedPhysicalColumn(null, "nonexistent_table", "col")
            );
            assertTrue(mapping.toDeniedQmFields(denied).isEmpty());
        }

        @Test
        @DisplayName("不存在的 QM 字段返回空列表")
        void getPhysicalColumns_unknownField() {
            PhysicalColumnMapping mapping = getMapping(SALES_QM);
            assertTrue(mapping.getPhysicalColumns("nonexistent_field").isEmpty());
        }
    }

    // ==================== 嵌套维度映射 ====================

    @Nested
    @DisplayName("嵌套维度物理列映射（雪花模型 3 层）")
    class NestedDimensionMapping {

        private static final String NESTED_QM = "FactSalesNestedDimQueryModel";

        @Test
        @DisplayName("一级维度属性映射到自身维度表（product → dim_product_nested）")
        void level1PropertyMapsToOwnTable() {
            assertMapsTo(getMapping(NESTED_QM), "product$brand", "dim_product_nested", "brand");
        }

        @Test
        @DisplayName("二级嵌套维度 FK 映射到父维度表（category FK → dim_product_nested）")
        void level2FkMapsToParentDimTable() {
            // category FK (category_key) 应在父维度表 dim_product_nested 上
            List<String> fkFields = getMapping(NESTED_QM).getQmFieldNames("dim_product_nested", "category_key");
            assertFalse(fkFields.isEmpty(),
                    "dim_product_nested.category_key 应有 QM 字段映射（二级维度 FK）, 实际: " + fkFields);
        }

        @Test
        @DisplayName("二级嵌套维度属性映射到自身维度表（category → dim_category_nested）")
        void level2PropertyMapsToOwnTable() {
            List<String> fields = getMapping(NESTED_QM).getQmFieldNames("dim_category_nested", "category_level");
            assertFalse(fields.isEmpty(),
                    "dim_category_nested.category_level 应有 QM 字段映射, 实际: " + fields);
        }

        @Test
        @DisplayName("三级嵌套维度 FK 映射到父维度表（group FK → dim_category_nested）")
        void level3FkMapsToParentDimTable() {
            List<String> fkFields = getMapping(NESTED_QM).getQmFieldNames("dim_category_nested", "group_key");
            assertFalse(fkFields.isEmpty(),
                    "dim_category_nested.group_key 应有 QM 字段映射（三级维度 FK）, 实际: " + fkFields);
        }

        @Test
        @DisplayName("三级嵌套维度属性映射到自身维度表（group → dim_category_group）")
        void level3PropertyMapsToOwnTable() {
            List<String> fields = getMapping(NESTED_QM).getQmFieldNames("dim_category_group", "group_type");
            assertFalse(fields.isEmpty(),
                    "dim_category_group.group_type 应有 QM 字段映射, 实际: " + fields);
        }

        @Test
        @DisplayName("嵌套维度的物理表集合包含全部维度表")
        void allDimTablesPresent() {
            Set<String> tables = getMapping(NESTED_QM).getAllPhysicalTables();
            assertAll(
                    () -> assertTrue(tables.contains("fact_sales_nested"), "应包含事实表"),
                    () -> assertTrue(tables.contains("dim_product_nested"), "应包含一级维度表"),
                    () -> assertTrue(tables.contains("dim_category_nested"), "应包含二级维度表"),
                    () -> assertTrue(tables.contains("dim_category_group"), "应包含三级维度表")
            );
        }

        @Test
        @DisplayName("嵌套维度的 deniedColumns 反向映射正确")
        void nestedDimDeniedColumnsReverseMapping() {
            Set<String> denied = getMapping(NESTED_QM).toDeniedQmFields(List.of(
                    new DeniedPhysicalColumn(null, "dim_category_group", "group_type")
            ));
            assertFalse(denied.isEmpty(),
                    "denied dim_category_group.group_type 应有 QM 字段映射, 实际: " + denied);
        }
    }

    // ==================== 父子维度（闭包表）映射 ====================

    @Nested
    @DisplayName("父子维度物理列映射（闭包表）")
    class ParentChildDimensionMapping {

        private static final String TEAM_QM = "FactTeamSalesQueryModel";

        @Test
        @DisplayName("父子维度属性映射到维度表（team → dim_team）")
        void hierarchyDimPropertyMapsToOwnTable() {
            assertMapsTo(getMapping(TEAM_QM), "team$managerName", "dim_team", "manager_name");
        }

        @Test
        @DisplayName("父子维度 $id 包含维度表 PK 映射")
        void hierarchyDimIdMapsToDimTable() {
            assertMapsTo(getMapping(TEAM_QM), "team$id", "dim_team", "team_id");
        }

        @Test
        @DisplayName("父子维度 deniedColumns 反向映射正确")
        void hierarchyDimDeniedColumnsWorks() {
            Set<String> denied = getMapping(TEAM_QM).toDeniedQmFields(List.of(
                    new DeniedPhysicalColumn(null, "dim_team", "manager_name")
            ));
            assertTrue(denied.contains("team$managerName"),
                    "denied dim_team.manager_name 应含 team$managerName, 实际: " + denied);
        }
    }
}
