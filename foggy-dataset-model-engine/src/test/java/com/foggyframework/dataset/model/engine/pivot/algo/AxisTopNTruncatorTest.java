package com.foggyframework.dataset.model.engine.pivot.algo;

import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;
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

    @Test
    @Order(7)
    @DisplayName("三层：仅最深层 TopN → 分区键为全部前置字段")
    void testThreeLevelDeepestOnlyTopN() {
        // 只有最深层 store 设置 limit，region 和 city 不设
        // 验证分区键 = [region, city]
        List<Map<String, Object>> data = List.of(
                makeRegionCityStoreRow("华东", "上海", "Store_A", 500),
                makeRegionCityStoreRow("华东", "上海", "Store_B", 300),
                makeRegionCityStoreRow("华东", "上海", "Store_C", 100),
                makeRegionCityStoreRow("华东", "杭州", "Store_D", 400),
                makeRegionCityStoreRow("华东", "杭州", "Store_E", 200),
                makeRegionCityStoreRow("华东", "杭州", "Store_F", 50)
        );

        AxisField regionField = new AxisField();
        regionField.setField("region");

        AxisField cityField = new AxisField();
        cityField.setField("city");

        AxisField storeField = new AxisField();
        storeField.setField("store");
        storeField.setOrderBy(List.of("-salesAmount"));
        storeField.setLimit(2); // 每个 region+city 取 Top 2 store

        List<Map<String, Object>> result = AxisTopNTruncator.apply(
                data, List.of(regionField, cityField, storeField));

        // 上海: Store_A(500), Store_B(300)；杭州: Store_D(400), Store_E(200)
        assertEquals(4, result.size());

        List<String> shStores = result.stream()
                .filter(r -> "上海".equals(r.get("city")))
                .map(r -> (String) r.get("store"))
                .collect(Collectors.toList());
        assertEquals(List.of("Store_A", "Store_B"), shStores);

        List<String> hzStores = result.stream()
                .filter(r -> "杭州".equals(r.get("city")))
                .map(r -> (String) r.get("store"))
                .collect(Collectors.toList());
        assertEquals(List.of("Store_D", "Store_E"), hzStores);
    }

    @Test
    @Order(8)
    @DisplayName("空值稳定性排序：null 度量被视为最小")
    void testStableSortWithNulls() {
        Map<String, Object> r1 = makeRow("上海", 500);
        Map<String, Object> r2 = makeRow("北京", null);
        Map<String, Object> r3 = makeRow("广州", 200);

        List<Map<String, Object>> data = List.of(r1, r2, r3);

        AxisField cityField = new AxisField();
        cityField.setField("city");
        cityField.setOrderBy(List.of("-salesAmount")); // 降序
        cityField.setLimit(2);

        List<Map<String, Object>> result = AxisTopNTruncator.apply(data, List.of(cityField));

        assertEquals(2, result.size());
        // null 被认为是最小，所以降序时，最大的是上海(500)，其次广州(200)，北京(null)被淘汰
        assertEquals("上海", result.get(0).get("city"));
        assertEquals("广州", result.get(1).get("city"));
    }

    @Test
    @Order(9)
    @DisplayName("两层：子级 start/offset 在每个父级分区内分页")
    void testTwoLevelPartitionedOffsetLimit() {
        List<Map<String, Object>> data = List.of(
                makeRegionCityRow("华东", "上海", 500),
                makeRegionCityRow("华东", "杭州", 300),
                makeRegionCityRow("华东", "南京", 200),
                makeRegionCityRow("华北", "北京", 400),
                makeRegionCityRow("华北", "天津", 100),
                makeRegionCityRow("华北", "石家庄", 50)
        );

        AxisField regionField = new AxisField();
        regionField.setField("region");

        AxisField cityField = new AxisField();
        cityField.setField("city");
        cityField.setOrderBy(List.of("-salesAmount"));
        cityField.setStart(1);
        cityField.setLimit(1);

        List<Map<String, Object>> result = AxisTopNTruncator.apply(
                data, List.of(regionField, cityField));

        assertEquals(2, result.size());
        assertEquals(List.of("杭州"), result.stream()
                .filter(r -> "华东".equals(r.get("region")))
                .map(r -> (String) r.get("city"))
                .collect(Collectors.toList()));
        assertEquals(List.of("天津"), result.stream()
                .filter(r -> "华北".equals(r.get("region")))
                .map(r -> (String) r.get("city"))
                .collect(Collectors.toList()));
    }

    @Test
    @Order(10)
    @DisplayName("级联 TopN 边界：中间层 limit 按明细行排序而非聚合后排名")
    void testCascadedTopNLimitation() {
        // 场景: region -> city(limit=1) -> store
        // 华东: 上海(Store_A=400, Store_B=300, 聚合=700), 杭州(Store_C=600, 聚合=600)
        // 如果按城市聚合值排序，应保留上海；但当前实现按明细行排序，
        // Store_C(600) 是最大单行，因此杭州会胜出。此测试文档化这个已知边界。
        List<Map<String, Object>> data = List.of(
                makeRegionCityStoreRow("华东", "上海", "Store_A", 400),
                makeRegionCityStoreRow("华东", "上海", "Store_B", 300),
                makeRegionCityStoreRow("华东", "杭州", "Store_C", 600)
        );

        AxisField regionField = new AxisField();
        regionField.setField("region");

        AxisField cityField = new AxisField();
        cityField.setField("city");
        cityField.setOrderBy(List.of("-salesAmount"));
        cityField.setLimit(1); // 每个 region 取 Top 1 city

        AxisField storeField = new AxisField();
        storeField.setField("store");

        List<Map<String, Object>> result = AxisTopNTruncator.apply(
                data, List.of(regionField, cityField, storeField));

        // 当前实现：按明细行的 salesAmount 排序 city 分区
        // Store_C(600) 是最大行 -> 杭州排第一 -> 保留杭州的 1 行
        // 注意：若需要"按城市聚合值排名"，需要先做中间聚合再截断，这属于未支持的级联 Generate
        assertEquals(1, result.size());
        assertEquals("杭州", result.get(0).get("city"),
                "当前实现按明细行排序截断，非聚合后排名。此为已知的级联 TopN 限制。");
    }

    // ========== 辅助方法 ==========

    private Map<String, Object> makeRow(String city, Integer sales) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("city", city);
        row.put("salesAmount", sales);
        return row;
    }

    private Map<String, Object> makeRegionCityRow(String region, String city, Integer sales) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("region", region);
        row.put("city", city);
        row.put("salesAmount", sales);
        return row;
    }

    private Map<String, Object> makeRegionCityStoreRow(String region, String city, String store, int sales) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("region", region);
        row.put("city", city);
        row.put("store", store);
        row.put("salesAmount", sales);
        return row;
    }
}
