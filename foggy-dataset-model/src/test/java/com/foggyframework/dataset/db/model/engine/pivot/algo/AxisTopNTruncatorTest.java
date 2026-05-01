package com.foggyframework.dataset.db.model.engine.pivot.algo;

import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AxisTopNTruncator 单元测试
 *
 * <p>验证隐式父级分区的 TopN 截断算法。</p>
 */
@DisplayName("AxisTopNTruncator 轴向截断测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AxisTopNTruncatorTest {

    @Test
    @Order(1)
    @DisplayName("单层 TopN → 全局 Top 2")
    void testSingleLevelTopN() {
        // 3个城市：上海=500, 北京=300, 广州=200
        List<Map<String, Object>> data = List.of(
                makeRow("上海", 500),
                makeRow("北京", 300),
                makeRow("广州", 200)
        );

        AxisField cityField = new AxisField();
        cityField.setField("city");
        cityField.setOrderBy(List.of("-salesAmount")); // 降序
        cityField.setLimit(2);

        List<Map<String, Object>> result = AxisTopNTruncator.apply(data, List.of(cityField));

        assertEquals(2, result.size());
        assertEquals("上海", result.get(0).get("city"));
        assertEquals("北京", result.get(1).get("city"));
    }

    @Test
    @Order(2)
    @DisplayName("两层：父级分区 + 子级 TopN")
    void testTwoLevelPartitionedTopN() {
        // 华东: 上海=500, 杭州=300, 南京=200
        // 华北: 北京=400, 天津=100
        List<Map<String, Object>> data = List.of(
                makeRegionCityRow("华东", "上海", 500),
                makeRegionCityRow("华东", "杭州", 300),
                makeRegionCityRow("华东", "南京", 200),
                makeRegionCityRow("华北", "北京", 400),
                makeRegionCityRow("华北", "天津", 100)
        );

        AxisField regionField = new AxisField();
        regionField.setField("region");
        // region 不设 limit

        AxisField cityField = new AxisField();
        cityField.setField("city");
        cityField.setOrderBy(List.of("-salesAmount"));
        cityField.setLimit(2); // 每个 region 取 Top 2

        List<Map<String, Object>> result = AxisTopNTruncator.apply(
                data, List.of(regionField, cityField));

        // 华东: 上海, 杭州（top 2）; 华北: 北京, 天津（全部只有2个）
        assertEquals(4, result.size());

        List<String> huadongCities = result.stream()
                .filter(r -> "华东".equals(r.get("region")))
                .map(r -> (String) r.get("city"))
                .collect(Collectors.toList());
        assertEquals(List.of("上海", "杭州"), huadongCities);

        // 南京应被淘汰
        assertTrue(result.stream().noneMatch(r -> "南京".equals(r.get("city"))));
    }

    @Test
    @Order(3)
    @DisplayName("无 limit 配置 → 不截断")
    void testNoLimit() {
        List<Map<String, Object>> data = List.of(
                makeRow("上海", 500),
                makeRow("北京", 300),
                makeRow("广州", 200)
        );

        AxisField cityField = new AxisField();
        cityField.setField("city");
        // 不设置 limit

        List<Map<String, Object>> result = AxisTopNTruncator.apply(data, List.of(cityField));

        assertEquals(3, result.size(), "无 limit 时应保留所有记录");
    }

    @Test
    @Order(4)
    @DisplayName("升序排序 → 取最小的 N 个")
    void testAscendingOrder() {
        List<Map<String, Object>> data = List.of(
                makeRow("上海", 500),
                makeRow("北京", 300),
                makeRow("广州", 200)
        );

        AxisField cityField = new AxisField();
        cityField.setField("city");
        cityField.setOrderBy(List.of("salesAmount")); // 升序（无前缀）
        cityField.setLimit(2);

        List<Map<String, Object>> result = AxisTopNTruncator.apply(data, List.of(cityField));

        assertEquals(2, result.size());
        // 升序取 top 2 = 广州(200), 北京(300)
        assertEquals("广州", result.get(0).get("city"));
        assertEquals("北京", result.get(1).get("city"));
    }

    @Test
    @Order(5)
    @DisplayName("limit 大于实际数据量 → 保留全部")
    void testLimitExceedsData() {
        List<Map<String, Object>> data = List.of(
                makeRow("上海", 500),
                makeRow("北京", 300)
        );

        AxisField cityField = new AxisField();
        cityField.setField("city");
        cityField.setOrderBy(List.of("-salesAmount"));
        cityField.setLimit(100);

        List<Map<String, Object>> result = AxisTopNTruncator.apply(data, List.of(cityField));

        assertEquals(2, result.size(), "limit 大于数据量时应保留全部");
    }

    @Test
    @Order(6)
    @DisplayName("空输入 → 原样返回")
    void testEmptyInput() {
        List<Map<String, Object>> result = AxisTopNTruncator.apply(
                Collections.emptyList(), List.of(new AxisField()));
        assertTrue(result.isEmpty());
    }

    // ========== 辅助方法 ==========

    private Map<String, Object> makeRow(String city, int sales) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("city", city);
        row.put("salesAmount", sales);
        return row;
    }

    private Map<String, Object> makeRegionCityRow(String region, String city, int sales) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("region", region);
        row.put("city", city);
        row.put("salesAmount", sales);
        return row;
    }
}
