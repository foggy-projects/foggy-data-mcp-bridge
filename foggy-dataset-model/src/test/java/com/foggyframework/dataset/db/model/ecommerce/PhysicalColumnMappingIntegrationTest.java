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
class PhysicalColumnMappingIntegrationTest extends EcommerceTestSupport {

    private static final String SALES_QM = "FactSalesQueryModel";

    private PhysicalColumnMapping getMapping(String qmName) {
        QueryModel qm = getQueryModel(qmName);
        assertNotNull(qm, "QM 模型应存在: " + qmName);
        PhysicalColumnMapping mapping = qm.getPhysicalColumnMapping();
        assertNotNull(mapping, "物理列映射缓存应已构建");
        return mapping;
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
            PhysicalColumnMapping mapping = getMapping(SALES_QM);
            List<PhysicalColumnRef> refs = mapping.getPhysicalColumns("salesAmount");
            assertFalse(refs.isEmpty(), "salesAmount 应有物理列映射");
            assertTrue(refs.stream().anyMatch(r ->
                            "fact_sales".equals(r.table()) && "sales_amount".equals(r.column())),
                    "salesAmount 应映射到 fact_sales.sales_amount, 实际: " + refs);
        }

        @Test
        @DisplayName("反向查找：fact_sales.sales_amount → salesAmount")
        void reverseLookupsalesAmount() {
            PhysicalColumnMapping mapping = getMapping(SALES_QM);
            List<String> qmNames = mapping.getQmFieldNames("fact_sales", "sales_amount");
            assertTrue(qmNames.contains("salesAmount"),
                    "fact_sales.sales_amount 应反向映射到 salesAmount, 实际: " + qmNames);
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
}
