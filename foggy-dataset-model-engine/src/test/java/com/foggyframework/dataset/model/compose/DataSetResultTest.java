package com.foggyframework.dataset.model.compose;

import com.foggyframework.dataset.model.engine.compose.DataSetResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataSetResult 纯单元测试（无需数据库）
 *
 * <p>验证 filter、sort、compute、column 等内存操作，
 * 以及链式调用和空结果集边界情况。</p>
 *
 * @author foggy-dataset
 * @since 8.2.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("QM Compose - DataSetResult 单元测试")
class DataSetResultTest {

    /**
     * 构造测试数据集
     */
    private static DataSetResult createSampleDataSet() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(createRow("orderId", 1, "customer", "Alice", "amount", new BigDecimal("1500"), "status", "confirmed"));
        items.add(createRow("orderId", 2, "customer", "Bob", "amount", new BigDecimal("800"), "status", "pending"));
        items.add(createRow("orderId", 3, "customer", "Charlie", "amount", new BigDecimal("2200"), "status", "confirmed"));
        items.add(createRow("orderId", 4, "customer", "Alice", "amount", new BigDecimal("300"), "status", "cancelled"));
        items.add(createRow("orderId", 5, "customer", "Diana", "amount", new BigDecimal("1000"), "status", "confirmed"));
        return new DataSetResult(items, null);
    }

    private static Map<String, Object> createRow(Object... kvs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            row.put((String) kvs[i], kvs[i + 1]);
        }
        return row;
    }

    // ==========================================
    // 基本访问方法
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("toList() 返回不可修改列表")
    void testToListUnmodifiable() {
        DataSetResult ds = createSampleDataSet();
        List<Map<String, Object>> list = ds.toList();

        assertEquals(5, list.size());
        assertThrows(UnsupportedOperationException.class,
                () -> list.add(new HashMap<>()),
                "toList() 应返回不可修改列表");
    }

    @Test
    @Order(2)
    @DisplayName("first() 返回第一行")
    void testFirst() {
        DataSetResult ds = createSampleDataSet();
        Map<String, Object> first = ds.first();

        assertEquals(1, first.get("orderId"));
        assertEquals("Alice", first.get("customer"));
    }

    @Test
    @Order(3)
    @DisplayName("size() 和 isEmpty()")
    void testSizeAndIsEmpty() {
        DataSetResult ds = createSampleDataSet();
        assertEquals(5, ds.size());
        assertFalse(ds.isEmpty());
    }

    @Test
    @Order(4)
    @DisplayName("value() 返回首行指定列值")
    void testValue() {
        DataSetResult ds = createSampleDataSet();
        assertEquals(1, ds.value("orderId"));
        assertEquals("Alice", ds.value("customer"));
    }

    // ==========================================
    // column() 去重
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("column() 提取单列并去重")
    void testColumnDedup() {
        DataSetResult ds = createSampleDataSet();
        List<Object> customers = ds.column("customer");

        log.info("column('customer') = {}", customers);
        assertEquals(4, customers.size(), "5 行中有 2 个 Alice，去重后应为 4");
        assertEquals("Alice", customers.get(0));
        assertEquals("Bob", customers.get(1));
        assertEquals("Charlie", customers.get(2));
        assertEquals("Diana", customers.get(3));
    }

    @Test
    @Order(11)
    @DisplayName("column() 过滤 null 值")
    void testColumnFilterNull() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(createRow("id", 1, "name", "A"));
        items.add(createRow("id", 2, "name", null)); // null 应被过滤
        items.add(createRow("id", 3)); // 字段不存在 → get 返回 null
        DataSetResult ds = new DataSetResult(items, null);

        List<Object> names = ds.column("name");
        assertEquals(1, names.size(), "null 值应被过滤");
        assertEquals("A", names.get(0));
    }

    @Test
    @Order(12)
    @DisplayName("column() 保持原始顺序（LinkedHashSet）")
    void testColumnPreservesOrder() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(createRow("id", 3));
        items.add(createRow("id", 1));
        items.add(createRow("id", 2));
        items.add(createRow("id", 1)); // 重复
        DataSetResult ds = new DataSetResult(items, null);

        List<Object> ids = ds.column("id");
        assertEquals(Arrays.asList(3, 1, 2), ids, "应保持首次出现的顺序");
    }

    // ==========================================
    // filter() 内存过滤
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("filter() 数值比较")
    void testFilterNumericComparison() {
        DataSetResult ds = createSampleDataSet();
        DataSetResult filtered = ds.filter("amount > 1000");

        log.info("filter('amount > 1000'): {} rows", filtered.size());
        assertEquals(2, filtered.size(), "amount > 1000 的应该是 1500 和 2200");
        assertEquals(1, filtered.toList().get(0).get("orderId"));
        assertEquals(3, filtered.toList().get(1).get("orderId"));
    }

    @Test
    @Order(21)
    @DisplayName("filter() 字符串相等比较")
    void testFilterStringEquality() {
        DataSetResult ds = createSampleDataSet();
        DataSetResult filtered = ds.filter("status == 'confirmed'");

        assertEquals(3, filtered.size(), "confirmed 状态有 3 条");
    }

    @Test
    @Order(22)
    @DisplayName("filter() 不修改原始数据集")
    void testFilterImmutability() {
        DataSetResult ds = createSampleDataSet();
        int originalSize = ds.size();

        ds.filter("amount > 1000");

        assertEquals(originalSize, ds.size(), "原始数据集不应被修改");
    }

    @Test
    @Order(23)
    @DisplayName("filter() 空表达式抛异常")
    void testFilterEmptyExpression() {
        DataSetResult ds = createSampleDataSet();
        assertThrows(IllegalArgumentException.class, () -> ds.filter(""));
        assertThrows(IllegalArgumentException.class, () -> ds.filter(null));
    }

    // ==========================================
    // sort() 排序
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("sort() 升序排序")
    void testSortAscending() {
        DataSetResult ds = createSampleDataSet();
        DataSetResult sorted = ds.sort("amount");

        List<Map<String, Object>> rows = sorted.toList();
        log.info("sort('amount') first={}, last={}",
                rows.get(0).get("amount"), rows.get(rows.size() - 1).get("amount"));

        BigDecimal prev = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            BigDecimal current = (BigDecimal) row.get("amount");
            assertTrue(current.compareTo(prev) >= 0,
                    "升序排序时 " + current + " 应 >= " + prev);
            prev = current;
        }
    }

    @Test
    @Order(31)
    @DisplayName("sort('-field') 降序排序")
    void testSortDescending() {
        DataSetResult ds = createSampleDataSet();
        DataSetResult sorted = ds.sort("-amount");

        List<Map<String, Object>> rows = sorted.toList();
        log.info("sort('-amount') first={}, last={}",
                rows.get(0).get("amount"), rows.get(rows.size() - 1).get("amount"));

        assertEquals(new BigDecimal("2200"), rows.get(0).get("amount"), "最大值应排在首位");
        assertEquals(new BigDecimal("300"), rows.get(rows.size() - 1).get("amount"), "最小值应排在末位");
    }

    @Test
    @Order(32)
    @DisplayName("sort() 字符串字段排序")
    void testSortStringField() {
        DataSetResult ds = createSampleDataSet();
        DataSetResult sorted = ds.sort("customer");

        List<Map<String, Object>> rows = sorted.toList();
        // Alice (x2), Bob, Charlie, Diana → Alice 先出现
        assertEquals("Alice", rows.get(0).get("customer"));
    }

    @Test
    @Order(33)
    @DisplayName("sort() 不修改原始数据集")
    void testSortImmutability() {
        DataSetResult ds = createSampleDataSet();
        Object firstOrderId = ds.first().get("orderId");

        ds.sort("-amount");

        assertEquals(firstOrderId, ds.first().get("orderId"), "原始数据集不应被修改");
    }

    @Test
    @Order(34)
    @DisplayName("sort() null 值排在前面")
    void testSortWithNullValues() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(createRow("id", 1, "score", 80));
        items.add(createRow("id", 2)); // score 为 null
        items.add(createRow("id", 3, "score", 60));
        DataSetResult ds = new DataSetResult(items, null);

        DataSetResult sorted = ds.sort("score");
        // null 排最前（compareValues: null < any）
        assertNull(sorted.toList().get(0).get("score"), "null 应排在最前");
    }

    @Test
    @Order(35)
    @DisplayName("sort() 空/null 字段名抛异常")
    void testSortEmptyField() {
        DataSetResult ds = createSampleDataSet();
        assertThrows(IllegalArgumentException.class, () -> ds.sort(""));
        assertThrows(IllegalArgumentException.class, () -> ds.sort(null));
    }

    // ==========================================
    // compute() 计算列
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("compute() 添加计算列")
    void testComputeNewColumn() {
        DataSetResult ds = createSampleDataSet();
        DataSetResult computed = ds.compute("doubleAmount", "amount * 2");

        for (Map<String, Object> row : computed.toList()) {
            assertNotNull(row.get("doubleAmount"), "每行应有计算列 doubleAmount");
            log.debug("orderId={}, amount={}, doubleAmount={}",
                    row.get("orderId"), row.get("amount"), row.get("doubleAmount"));
        }
        // 第一行：amount=1500，doubleAmount=3000
        Object val = computed.first().get("doubleAmount");
        assertNotNull(val);
    }

    @Test
    @Order(41)
    @DisplayName("compute() 不修改原始数据集")
    void testComputeImmutability() {
        DataSetResult ds = createSampleDataSet();
        ds.compute("newCol", "amount + 1");

        assertNull(ds.first().get("newCol"), "原始数据集不应有新列");
    }

    @Test
    @Order(42)
    @DisplayName("compute() 空参数抛异常")
    void testComputeEmptyParams() {
        DataSetResult ds = createSampleDataSet();
        assertThrows(IllegalArgumentException.class, () -> ds.compute("", "1+1"));
        assertThrows(IllegalArgumentException.class, () -> ds.compute("x", ""));
        assertThrows(IllegalArgumentException.class, () -> ds.compute(null, "1+1"));
        assertThrows(IllegalArgumentException.class, () -> ds.compute("x", null));
    }

    // ==========================================
    // 链式调用
    // ==========================================

    @Test
    @Order(50)
    @DisplayName("链式调用：filter → sort → compute")
    void testChainedOperations() {
        DataSetResult ds = createSampleDataSet();

        DataSetResult result = ds
                .filter("amount > 500")            // 排除 amount=300
                .sort("-amount")                     // 降序：2200, 1500, 1000, 800
                .compute("rank", "orderId");         // 简单标记

        log.info("链式调用结果：{} 行", result.size());
        assertEquals(4, result.size(), "filter 应排除 amount=300 的行");

        // 验证排序：降序
        List<Map<String, Object>> rows = result.toList();
        assertEquals(new BigDecimal("2200"), rows.get(0).get("amount"), "降序第一行应是 2200");
        assertEquals(new BigDecimal("800"), rows.get(rows.size() - 1).get("amount"), "降序最后一行应是 800");

        // 验证 compute 列存在
        assertNotNull(rows.get(0).get("rank"), "应有 rank 列");
    }

    @Test
    @Order(51)
    @DisplayName("链式调用：sort → filter → column")
    void testChainedSortFilterColumn() {
        DataSetResult ds = createSampleDataSet();

        List<Object> result = ds
                .sort("-amount")
                .filter("status == 'confirmed'")
                .column("orderId");

        log.info("sort → filter → column 结果: {}", result);
        assertEquals(3, result.size(), "confirmed 有 3 条");
    }

    // ==========================================
    // 空结果集边界
    // ==========================================

    @Test
    @Order(60)
    @DisplayName("空结果集：基本方法")
    void testEmptyDataSet() {
        DataSetResult empty = new DataSetResult(Collections.emptyList(), null);

        assertEquals(0, empty.size());
        assertTrue(empty.isEmpty());
        assertTrue(empty.toList().isEmpty());
        assertEquals(Collections.emptyMap(), empty.first());
        assertNull(empty.value("anyField"));
        assertTrue(empty.column("anyField").isEmpty());
    }

    @Test
    @Order(61)
    @DisplayName("空结果集：filter 返回空")
    void testEmptyDataSetFilter() {
        DataSetResult empty = new DataSetResult(Collections.emptyList(), null);
        DataSetResult result = empty.filter("amount > 0");

        assertTrue(result.isEmpty());
    }

    @Test
    @Order(62)
    @DisplayName("空结果集：sort 返回空")
    void testEmptyDataSetSort() {
        DataSetResult empty = new DataSetResult(Collections.emptyList(), null);
        DataSetResult result = empty.sort("amount");

        assertTrue(result.isEmpty());
    }

    @Test
    @Order(63)
    @DisplayName("空结果集：compute 返回空")
    void testEmptyDataSetCompute() {
        DataSetResult empty = new DataSetResult(Collections.emptyList(), null);
        DataSetResult result = empty.compute("newCol", "1+1");

        assertTrue(result.isEmpty());
    }

    @Test
    @Order(64)
    @DisplayName("null items 构造应安全处理")
    void testNullItemsConstructor() {
        DataSetResult ds = new DataSetResult(null, null);

        assertEquals(0, ds.size());
        assertTrue(ds.isEmpty());
    }

    @Test
    @Order(65)
    @DisplayName("单行数据集：sort 应正常返回")
    void testSingleRowSort() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(createRow("id", 1, "name", "only"));
        DataSetResult ds = new DataSetResult(items, null);

        DataSetResult sorted = ds.sort("-id");
        assertEquals(1, sorted.size());
        assertEquals(1, sorted.first().get("id"));
    }

    @Test
    @Order(66)
    @DisplayName("filter 过滤全部行后返回空结果")
    void testFilterAllOut() {
        DataSetResult ds = createSampleDataSet();
        DataSetResult filtered = ds.filter("amount > 999999");

        assertTrue(filtered.isEmpty());
        assertEquals(0, filtered.size());
    }

    // ==========================================
    // withJoin 前置验证
    // ==========================================

    @Test
    @Order(70)
    @DisplayName("withJoin() 无 dslParams 应抛异常")
    void testWithJoinNoDslParams() {
        DataSetResult left = createSampleDataSet();
        DataSetResult right = createSampleDataSet();

        assertThrows(IllegalStateException.class,
                () -> left.withJoin(right, "LEFT", "orderId"),
                "无 dslParams 时应抛异常");
    }

    @Test
    @Order(71)
    @DisplayName("withJoin() 右侧无 dslParams 应抛异常")
    void testWithJoinRightNoDslParams() {
        DataSetResult left = createSampleDataSet();
        left.setDslParams(Map.of("model", "TestQM"));

        DataSetResult right = createSampleDataSet();
        // right 没有 dslParams

        assertThrows(IllegalStateException.class,
                () -> left.withJoin(right, "LEFT", "orderId"),
                "右侧无 dslParams 时应抛异常");
    }

    @Test
    @Order(72)
    @DisplayName("withJoin() 无 composeContext 应抛异常")
    void testWithJoinNoComposeContext() {
        DataSetResult left = createSampleDataSet();
        left.setDslParams(Map.of("model", "TestQM"));

        DataSetResult right = createSampleDataSet();
        right.setDslParams(Map.of("model", "TestQM2"));

        // left 有 dslParams 但没有 composeContext
        assertThrows(IllegalStateException.class,
                () -> left.withJoin(right, "LEFT", "orderId"),
                "无 composeContext 时应抛异常");
    }

    // ==========================================
    // joinInMemory -- 内存 Hash JOIN
    // ==========================================

    /**
     * 构造"客户"表数据集（与订单表通过 orderId 关联不上，通过 customer 关联）
     */
    private static DataSetResult createCustomerDataSet() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(createRow("customerId", 101, "customer", "Alice", "region", "East", "level", "VIP"));
        items.add(createRow("customerId", 102, "customer", "Bob", "region", "West", "level", "Normal"));
        items.add(createRow("customerId", 103, "customer", "Charlie", "region", "East", "level", "VIP"));
        // Diana 不在客户表中 → LEFT JOIN 时应保留，INNER JOIN 时应丢弃
        // Eve 在客户表中但不在订单表中 → 右侧多余行不出现
        items.add(createRow("customerId", 105, "customer", "Eve", "region", "North", "level", "Normal"));
        return new DataSetResult(items, null);
    }

    @Test
    @Order(100)
    @DisplayName("joinInMemory LEFT JOIN：基本合并")
    void testJoinInMemoryLeftBasic() {
        DataSetResult orders = createSampleDataSet();   // 5 行，customer: Alice(x2), Bob, Charlie, Diana
        DataSetResult customers = createCustomerDataSet(); // Alice, Bob, Charlie, Eve

        DataSetResult result = orders.joinInMemory(customers, "LEFT", "customer");
        log.info("LEFT JOIN result: {} rows", result.size());

        // 5 行（Diana 在客户表无匹配，但 LEFT JOIN 保留）
        assertEquals(5, result.size(), "LEFT JOIN 应保留所有左侧行");

        // 验证合并列：匹配行应有 region 列
        Map<String, Object> aliceRow = result.toList().get(0);
        assertEquals("Alice", aliceRow.get("customer"));
        assertEquals("East", aliceRow.get("region"), "匹配行应合并右侧列");
        assertEquals("VIP", aliceRow.get("level"), "匹配行应合并右侧列");

        // 验证 Diana 行（无匹配）
        Map<String, Object> dianaRow = result.toList().stream()
                .filter(r -> "Diana".equals(r.get("customer")))
                .findFirst().orElseThrow();
        assertNull(dianaRow.get("region"), "无匹配行右侧列应为 null");
    }

    @Test
    @Order(101)
    @DisplayName("joinInMemory INNER JOIN：仅保留匹配行")
    void testJoinInMemoryInnerBasic() {
        DataSetResult orders = createSampleDataSet();
        DataSetResult customers = createCustomerDataSet();

        DataSetResult result = orders.joinInMemory(customers, "INNER", "customer");
        log.info("INNER JOIN result: {} rows", result.size());

        // Diana 不在客户表 → INNER JOIN 丢弃，Alice(x2) + Bob + Charlie = 4
        assertEquals(4, result.size(), "INNER JOIN 应只保留匹配行");

        // Eve 在客户表但不在订单表 → 不应出现
        boolean hasEve = result.toList().stream()
                .anyMatch(r -> "Eve".equals(r.get("customer")));
        assertFalse(hasEve, "右侧独有行不应出现在结果中");
    }

    @Test
    @Order(102)
    @DisplayName("joinInMemory 1:N 展开：左侧多行匹配同一右侧行")
    void testJoinInMemoryOneToMany() {
        DataSetResult orders = createSampleDataSet();  // Alice 出现 2 次
        DataSetResult customers = createCustomerDataSet();

        DataSetResult result = orders.joinInMemory(customers, "LEFT", "customer");

        // Alice 在 orders 中有 2 行，在 customers 中有 1 行 → 2 行
        long aliceCount = result.toList().stream()
                .filter(r -> "Alice".equals(r.get("customer")))
                .count();
        assertEquals(2, aliceCount, "1:N → 左侧 2 行各匹配 1 行右侧");
    }

    @Test
    @Order(103)
    @DisplayName("joinInMemory N:1 展开：右侧多行匹配同一左侧行")
    void testJoinInMemoryManyToOne() {
        // 左表：1 行 region=East
        List<Map<String, Object>> leftItems = new ArrayList<>();
        leftItems.add(createRow("region", "East", "summary", "East Region"));
        DataSetResult left = new DataSetResult(leftItems, null);

        // 右表：2 行 region=East
        List<Map<String, Object>> rightItems = new ArrayList<>();
        rightItems.add(createRow("region", "East", "city", "Shanghai"));
        rightItems.add(createRow("region", "East", "city", "Beijing"));
        DataSetResult right = new DataSetResult(rightItems, null);

        DataSetResult result = left.joinInMemory(right, "LEFT", "region");

        assertEquals(2, result.size(), "1:N → 1 左行展开为 2 行");
        // 验证左侧列保留
        assertEquals("East Region", result.toList().get(0).get("summary"));
        assertEquals("East Region", result.toList().get(1).get("summary"));
        // 验证右侧列合并
        Set<Object> cities = new LinkedHashSet<>(result.column("city"));
        assertTrue(cities.contains("Shanghai"));
        assertTrue(cities.contains("Beijing"));
    }

    @Test
    @Order(104)
    @DisplayName("joinInMemory 左右不同 joinKey")
    void testJoinInMemoryDifferentKeys() {
        // 左表用 orderId
        List<Map<String, Object>> leftItems = new ArrayList<>();
        leftItems.add(createRow("orderId", 1, "product", "Widget"));
        leftItems.add(createRow("orderId", 2, "product", "Gadget"));
        DataSetResult left = new DataSetResult(leftItems, null);

        // 右表用 orderRef
        List<Map<String, Object>> rightItems = new ArrayList<>();
        rightItems.add(createRow("orderRef", 1, "shipDate", "2026-03-01"));
        rightItems.add(createRow("orderRef", 3, "shipDate", "2026-03-02")); // no match
        DataSetResult right = new DataSetResult(rightItems, null);

        DataSetResult result = left.joinInMemory(right, "LEFT", "orderId", "orderRef");

        assertEquals(2, result.size(), "LEFT JOIN 应保留全部左侧行");
        assertEquals("2026-03-01", result.toList().get(0).get("shipDate"), "orderId=1 应匹配");
        assertNull(result.toList().get(1).get("shipDate"), "orderId=2 无匹配");
    }

    @Test
    @Order(105)
    @DisplayName("joinInMemory 同名列左表优先")
    void testJoinInMemoryLeftColumnPrecedence() {
        List<Map<String, Object>> leftItems = new ArrayList<>();
        leftItems.add(createRow("id", 1, "name", "LeftName"));
        DataSetResult left = new DataSetResult(leftItems, null);

        List<Map<String, Object>> rightItems = new ArrayList<>();
        rightItems.add(createRow("id", 1, "name", "RightName", "extra", "X"));
        DataSetResult right = new DataSetResult(rightItems, null);

        DataSetResult result = left.joinInMemory(right, "LEFT", "id");

        assertEquals("LeftName", result.first().get("name"), "同名列应保留左表值");
        assertEquals("X", result.first().get("extra"), "右表独有列应合并");
    }

    @Test
    @Order(106)
    @DisplayName("joinInMemory 不修改原始数据集")
    void testJoinInMemoryImmutability() {
        DataSetResult orders = createSampleDataSet();
        DataSetResult customers = createCustomerDataSet();
        int originalOrdersSize = orders.size();
        int originalCustomersSize = customers.size();

        orders.joinInMemory(customers, "LEFT", "customer");

        assertEquals(originalOrdersSize, orders.size(), "左表不应被修改");
        assertEquals(originalCustomersSize, customers.size(), "右表不应被修改");
        // 原始行不应被污染
        assertNull(orders.first().get("region"), "原始左表行不应有右表列");
    }

    @Test
    @Order(107)
    @DisplayName("joinInMemory + filter/sort 链式调用")
    void testJoinInMemoryChained() {
        DataSetResult orders = createSampleDataSet();
        DataSetResult customers = createCustomerDataSet();

        DataSetResult result = orders
                .joinInMemory(customers, "INNER", "customer")
                .filter("level == 'VIP'")
                .sort("-amount");

        log.info("joinInMemory + filter + sort: {} rows", result.size());
        // Alice(x2) + Charlie 是 VIP，Bob 是 Normal → 3 行
        assertEquals(3, result.size());
        // 降序排列
        assertTrue(((Comparable) result.toList().get(0).get("amount"))
                .compareTo(result.toList().get(1).get("amount")) >= 0);
    }

    // ---- joinInMemory 边界情况 ----

    @Test
    @Order(110)
    @DisplayName("joinInMemory 左表空")
    void testJoinInMemoryEmptyLeft() {
        DataSetResult empty = new DataSetResult(Collections.emptyList(), null);
        DataSetResult right = createCustomerDataSet();

        DataSetResult resultLeft = empty.joinInMemory(right, "LEFT", "customer");
        DataSetResult resultInner = empty.joinInMemory(right, "INNER", "customer");

        assertTrue(resultLeft.isEmpty());
        assertTrue(resultInner.isEmpty());
    }

    @Test
    @Order(111)
    @DisplayName("joinInMemory 右表空 LEFT JOIN 保留左表")
    void testJoinInMemoryEmptyRightLeft() {
        DataSetResult orders = createSampleDataSet();
        DataSetResult empty = new DataSetResult(Collections.emptyList(), null);

        DataSetResult result = orders.joinInMemory(empty, "LEFT", "customer");

        assertEquals(orders.size(), result.size(), "LEFT JOIN 右表空时应保留全部左表行");
    }

    @Test
    @Order(112)
    @DisplayName("joinInMemory 右表空 INNER JOIN 返回空")
    void testJoinInMemoryEmptyRightInner() {
        DataSetResult orders = createSampleDataSet();
        DataSetResult empty = new DataSetResult(Collections.emptyList(), null);

        DataSetResult result = orders.joinInMemory(empty, "INNER", "customer");

        assertTrue(result.isEmpty(), "INNER JOIN 右表空时应返回空");
    }

    @Test
    @Order(113)
    @DisplayName("joinInMemory joinKey 全部为 null 时无匹配")
    void testJoinInMemoryNullKeys() {
        List<Map<String, Object>> leftItems = new ArrayList<>();
        leftItems.add(createRow("id", 1));
        leftItems.add(createRow("id", 2));
        DataSetResult left = new DataSetResult(leftItems, null);

        List<Map<String, Object>> rightItems = new ArrayList<>();
        rightItems.add(createRow("name", "X")); // 没有 id 字段 → key=null
        DataSetResult right = new DataSetResult(rightItems, null);

        DataSetResult resultLeft = left.joinInMemory(right, "LEFT", "id");
        assertEquals(2, resultLeft.size(), "LEFT JOIN：无匹配仍保留");

        DataSetResult resultInner = left.joinInMemory(right, "INNER", "id");
        assertTrue(resultInner.isEmpty(), "INNER JOIN：无匹配返回空");
    }

    @Test
    @Order(114)
    @DisplayName("joinInMemory 非法参数校验")
    void testJoinInMemoryInvalidArgs() {
        DataSetResult ds = createSampleDataSet();
        DataSetResult right = createCustomerDataSet();

        assertThrows(IllegalArgumentException.class,
                () -> ds.joinInMemory(right, "", "id"));
        assertThrows(IllegalArgumentException.class,
                () -> ds.joinInMemory(right, "LEFT", ""));
        assertThrows(IllegalArgumentException.class,
                () -> ds.joinInMemory(right, "FULL", "id"),
                "不支持的 joinType 应抛异常");
    }

    // ==========================================
    // toString
    // ==========================================

    @Test
    @Order(200)
    @DisplayName("toString() 包含行数")
    void testToString() {
        DataSetResult ds = createSampleDataSet();
        assertTrue(ds.toString().contains("5"), "toString 应包含行数");
    }
}
