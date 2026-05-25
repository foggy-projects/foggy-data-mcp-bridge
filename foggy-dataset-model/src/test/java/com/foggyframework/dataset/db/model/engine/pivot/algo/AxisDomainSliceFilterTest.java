package com.foggyframework.dataset.db.model.engine.pivot.algo;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AxisDomainSliceFilter 轴成员域过滤测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AxisDomainSliceFilterTest {

    @Test
    @Order(1)
    @DisplayName("无 domainSlice 条件 → 原样返回")
    void testNoDomainSlice() {
        List<Map<String, Object>> data = buildSalesData();

        AxisField field = new AxisField();
        field.setField("region");

        List<Map<String, Object>> result = AxisDomainSliceFilter.apply(data, List.of(field));

        assertEquals(data.size(), result.size(), "无 domainSlice 时，结果集应原样返回");
    }

    @Test
    @Order(2)
    @DisplayName("简单 domainSlice 过滤与 Cell 状态保留 (Cell Preservation)")
    void testCellPreservation() {
        List<Map<String, Object>> data = buildSalesData();
        // 数据构成：
        // 华东: 手机 (100), 电脑 (200)
        // 华北: 手机 (100)
        // 华南: 电脑 (500)

        AxisField field = new AxisField();
        field.setField("region");

        // 仅保留产品为"手机"的产品分类所在区域的维度成员
        SemanticQueryRequest.SliceItem slice = new SemanticQueryRequest.SliceItem();
        slice.setField("product");
        slice.setOp("=");
        slice.setValue("手机");
        field.setDomainSlice(List.of(slice));

        List<Map<String, Object>> result = AxisDomainSliceFilter.apply(data, List.of(field));

        // 华东和华北包含"手机"，因此华东和华北是 survivingMembers。
        // 由于 Cell Preservation，华东下的"电脑 (200)"也必须完整保留下来！
        // 华南下只有"电脑"，不包含"手机"，因此华南成员被整行淘汰。
        // 最终保留的行：华东-手机(100), 华东-电脑(200), 华北-手机(100) -> 共 3 行
        assertEquals(3, result.size());
        
        boolean hasHuadongComputer = result.stream()
                .anyMatch(row -> "华东".equals(row.get("region")) && "电脑".equals(row.get("product")));
        assertTrue(hasHuadongComputer, "即使电脑不符合过滤条件，华东区域的电脑销售记录也必须被保留下来（Cell Preservation）");

        boolean hasHuanan = result.stream()
                .anyMatch(row -> "华南".equals(row.get("region")));
        assertFalse(hasHuanan, "华南没有符合过滤条件的手机，整个区域成员必须被淘汰");
    }

    @Test
    @Order(3)
    @DisplayName("Nesting logical OR condition")
    void testNestingOrCondition() {
        List<Map<String, Object>> data = buildSalesData();

        AxisField field = new AxisField();
        field.setField("region");

        // (product = '电脑' OR salesAmount >= 500)
        SemanticQueryRequest.SliceItem s1 = new SemanticQueryRequest.SliceItem();
        s1.setField("product");
        s1.setOp("=");
        s1.setValue("电脑");

        SemanticQueryRequest.SliceItem s2 = new SemanticQueryRequest.SliceItem();
        s2.setField("salesAmount");
        s2.setOp(">=");
        s2.setValue(500);

        SemanticQueryRequest.SliceItem orGroup = new SemanticQueryRequest.SliceItem();
        orGroup.setOr(List.of(s1, s2));

        field.setDomainSlice(List.of(orGroup));

        List<Map<String, Object>> result = AxisDomainSliceFilter.apply(data, List.of(field));

        // 电脑(华东，华南) 和 sales>=500(华南) 符合条件 -> 华东和华南幸存。
        // 华北产品是手机，sales 100 -> 华北淘汰。
        // Surviving regions: 华东, 华南.
        // 最终结果保留：华东-手机(100), 华东-电脑(200), 华南-电脑(500) -> 共 3 行
        assertEquals(3, result.size());
        assertTrue(result.stream().noneMatch(row -> "华北".equals(row.get("region"))));
    }

    private List<Map<String, Object>> buildSalesData() {
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(makeRow("华东", "手机", 100));
        data.add(makeRow("华东", "电脑", 200));
        data.add(makeRow("华北", "手机", 100));
        data.add(makeRow("华南", "电脑", 500));
        return data;
    }

    private Map<String, Object> makeRow(String region, String product, int sales) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("region", region);
        row.put("product", product);
        row.put("salesAmount", sales);
        return row;
    }
}
