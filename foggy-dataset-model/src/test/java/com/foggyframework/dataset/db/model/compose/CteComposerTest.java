package com.foggyframework.dataset.db.model.compose;

import com.foggyframework.dataset.db.model.engine.compose.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CteComposer 纯单元测试（无需数据库）
 *
 * <p>验证 CTE 和子查询两种模式的 SQL 拼接、参数合并、多表链式 JOIN 等核心逻辑。</p>
 *
 * @author foggy-dataset
 * @since 8.2.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("QM Compose - CteComposer 单元测试")
class CteComposerTest {

    // ==========================================
    // CTE 模式：二元组合
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("CTE 模式：二元 LEFT JOIN，selectColumns=null 使用 alias.*")
    void testCteBinaryLeftJoinSelectAll() {
        CteUnit left = new CteUnit("cte_0",
                "SELECT id, name FROM orders", Collections.emptyList(), null);
        CteUnit right = new CteUnit("cte_1",
                "SELECT id, email FROM customers", Collections.emptyList(), null);
        JoinSpec spec = JoinSpec.leftJoin("id", "id", null, null);

        ComposedSql result = CteComposer.compose(left, right, spec, true);
        String sql = result.getSql();
        log.info("CTE binary LEFT JOIN (select all):\n{}", sql);

        assertTrue(sql.contains("WITH cte_0 AS ("), "应包含 WITH cte_0 AS");
        assertTrue(sql.contains("cte_1 AS ("), "应包含 cte_1 AS");
        assertTrue(sql.contains("cte_0.*"), "selectColumns=null 时应使用 cte_0.*");
        assertTrue(sql.contains("cte_1.*"), "selectColumns=null 时应使用 cte_1.*");
        assertTrue(sql.contains("LEFT JOIN cte_1"), "应包含 LEFT JOIN");
        assertTrue(sql.contains("ON cte_0.id = cte_1.id"), "应包含 JOIN 条件");
        assertTrue(sql.contains("FROM cte_0"), "应从 cte_0 开始");
        assertTrue(result.getParams().isEmpty(), "无参数时 params 应为空");
    }

    @Test
    @Order(2)
    @DisplayName("CTE 模式：二元 INNER JOIN，指定 selectColumns")
    void testCteBinaryInnerJoinWithSelectColumns() {
        CteUnit left = new CteUnit("cte_0",
                "SELECT id, name, amount FROM orders",
                Collections.emptyList(),
                Arrays.asList("id", "name", "amount"));
        CteUnit right = new CteUnit("cte_1",
                "SELECT id, email FROM customers",
                Collections.emptyList(),
                Arrays.asList("email"));

        JoinSpec spec = JoinSpec.innerJoin("id", "id",
                Arrays.asList("id", "name", "amount"),
                Arrays.asList("email"));

        ComposedSql result = CteComposer.compose(left, right, spec, true);
        String sql = result.getSql();
        log.info("CTE binary INNER JOIN (select columns):\n{}", sql);

        assertTrue(sql.contains("INNER JOIN cte_1"), "应使用 INNER JOIN");
        assertTrue(sql.contains("cte_0.id"), "应选择 left 的 id 列");
        assertTrue(sql.contains("cte_0.name"), "应选择 left 的 name 列");
        assertTrue(sql.contains("cte_1.email"), "应选择 right 的 email 列");
        assertFalse(sql.contains("cte_0.*"), "指定 selectColumns 时不应使用 *");
    }

    @Test
    @Order(3)
    @DisplayName("CTE 模式：参数合并顺序（left 在前，right 在后）")
    void testCteParamsMergeOrder() {
        CteUnit left = new CteUnit("cte_0",
                "SELECT * FROM orders WHERE status = ?",
                Arrays.asList("confirmed"), null);
        CteUnit right = new CteUnit("cte_1",
                "SELECT * FROM customers WHERE region = ?",
                Arrays.asList("EAST"), null);
        JoinSpec spec = JoinSpec.leftJoin("customer_id", "id", null, null);

        ComposedSql result = CteComposer.compose(left, right, spec, true);

        assertEquals(2, result.getParams().size(), "应合并 2 个参数");
        assertEquals("confirmed", result.getParams().get(0), "第一个参数应来自 left");
        assertEquals("EAST", result.getParams().get(1), "第二个参数应来自 right");
    }

    // ==========================================
    // 子查询模式（MySQL 5.7 回退）
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("子查询模式：二元 LEFT JOIN，selectColumns=null 使用 alias.*")
    void testSubqueryBinaryLeftJoin() {
        CteUnit left = new CteUnit("t0",
                "SELECT id, name FROM orders", Collections.emptyList(), null);
        CteUnit right = new CteUnit("t1",
                "SELECT id, email FROM customers", Collections.emptyList(), null);
        JoinSpec spec = JoinSpec.leftJoin("id", "id", null, null);

        ComposedSql result = CteComposer.compose(left, right, spec, false);
        String sql = result.getSql();
        log.info("Subquery binary LEFT JOIN:\n{}", sql);

        assertFalse(sql.contains("WITH"), "子查询模式不应包含 WITH");
        assertTrue(sql.contains("FROM (SELECT id, name FROM orders) AS t0"), "应包含左侧子查询");
        assertTrue(sql.contains("LEFT JOIN (SELECT id, email FROM customers) AS t1"), "应包含右侧子查询");
        assertTrue(sql.contains("ON t0.id = t1.id"), "应包含 JOIN 条件");
    }

    @Test
    @Order(11)
    @DisplayName("子查询模式：参数合并顺序与 CTE 一致")
    void testSubqueryParamsMergeOrder() {
        CteUnit left = new CteUnit("t0",
                "SELECT * FROM orders WHERE status = ?",
                Arrays.asList("pending"), null);
        CteUnit right = new CteUnit("t1",
                "SELECT * FROM customers WHERE active = ?",
                Arrays.asList(true), null);
        JoinSpec spec = JoinSpec.leftJoin("cid", "id", null, null);

        ComposedSql result = CteComposer.compose(left, right, spec, false);

        assertEquals(2, result.getParams().size());
        assertEquals("pending", result.getParams().get(0));
        assertEquals(true, result.getParams().get(1));
    }

    // ==========================================
    // 多表链式 JOIN
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("CTE 模式：三表链式 JOIN")
    void testCteMultiThreeWayJoin() {
        CteUnit orders = new CteUnit("cte_0",
                "SELECT order_id, customer_id, amount FROM orders WHERE amount > ?",
                Arrays.asList(100), Arrays.asList("order_id", "customer_id", "amount"));
        CteUnit customers = new CteUnit("cte_1",
                "SELECT id, name, region_id FROM customers",
                Collections.emptyList(), Arrays.asList("id", "name", "region_id"));
        CteUnit regions = new CteUnit("cte_2",
                "SELECT id, region_name FROM regions",
                Collections.emptyList(), Arrays.asList("region_name"));

        List<JoinSpec> joinSpecs = Arrays.asList(
                JoinSpec.leftJoin("customer_id", "id", null, null),
                JoinSpec.leftJoin("region_id", "id", null, null)
        );

        ComposedSql result = CteComposer.compose(
                Arrays.asList(orders, customers, regions), joinSpecs, true);
        String sql = result.getSql();
        log.info("CTE 3-way chain JOIN:\n{}", sql);

        assertTrue(sql.contains("WITH cte_0 AS ("), "应包含 cte_0");
        assertTrue(sql.contains("cte_1 AS ("), "应包含 cte_1");
        assertTrue(sql.contains("cte_2 AS ("), "应包含 cte_2");
        assertTrue(sql.contains("FROM cte_0"), "应从 cte_0 开始");
        assertTrue(sql.contains("LEFT JOIN cte_1 ON cte_0.customer_id = cte_1.id"),
                "应包含第一个 JOIN");
        assertTrue(sql.contains("LEFT JOIN cte_2 ON cte_1.region_id = cte_2.id"),
                "应包含第二个 JOIN（链式）");

        assertEquals(1, result.getParams().size(), "只有 orders 有参数");
        assertEquals(100, result.getParams().get(0));
    }

    @Test
    @Order(21)
    @DisplayName("子查询模式：三表链式 JOIN")
    void testSubqueryMultiThreeWayJoin() {
        CteUnit t0 = new CteUnit("t0",
                "SELECT id, cid FROM orders", Collections.emptyList(), null);
        CteUnit t1 = new CteUnit("t1",
                "SELECT id, rid FROM customers", Collections.emptyList(), null);
        CteUnit t2 = new CteUnit("t2",
                "SELECT id, name FROM regions", Collections.emptyList(), null);

        List<JoinSpec> joinSpecs = Arrays.asList(
                JoinSpec.leftJoin("cid", "id", null, null),
                JoinSpec.innerJoin("rid", "id", null, null)
        );

        ComposedSql result = CteComposer.compose(
                Arrays.asList(t0, t1, t2), joinSpecs, false);
        String sql = result.getSql();
        log.info("Subquery 3-way chain JOIN:\n{}", sql);

        assertFalse(sql.contains("WITH"), "子查询模式不应包含 WITH");
        assertTrue(sql.contains("FROM (SELECT id, cid FROM orders) AS t0"), "应包含第一个子查询");
        assertTrue(sql.contains("LEFT JOIN (SELECT id, rid FROM customers) AS t1"), "第一个 JOIN");
        assertTrue(sql.contains("INNER JOIN (SELECT id, name FROM regions) AS t2"), "第二个 JOIN");
    }

    @Test
    @Order(22)
    @DisplayName("多表链式 JOIN：参数按 unit 顺序合并")
    void testMultiJoinParamsMergeOrder() {
        CteUnit t0 = new CteUnit("cte_0", "SELECT * FROM a WHERE x = ?",
                Arrays.asList("A"), null);
        CteUnit t1 = new CteUnit("cte_1", "SELECT * FROM b WHERE y = ?",
                Arrays.asList("B"), null);
        CteUnit t2 = new CteUnit("cte_2", "SELECT * FROM c WHERE z = ? AND w = ?",
                Arrays.asList("C", "D"), null);

        List<JoinSpec> joinSpecs = Arrays.asList(
                JoinSpec.leftJoin("id", "aid", null, null),
                JoinSpec.leftJoin("id", "bid", null, null)
        );

        ComposedSql result = CteComposer.compose(
                Arrays.asList(t0, t1, t2), joinSpecs, true);

        assertEquals(4, result.getParams().size(), "应合并所有参数");
        assertEquals("A", result.getParams().get(0));
        assertEquals("B", result.getParams().get(1));
        assertEquals("C", result.getParams().get(2));
        assertEquals("D", result.getParams().get(3));
    }

    // ==========================================
    // 边界验证
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("多表组合：units < 2 时应抛出异常")
    void testMultiComposeTooFewUnits() {
        CteUnit single = new CteUnit("cte_0", "SELECT 1", Collections.emptyList(), null);

        assertThrows(IllegalArgumentException.class,
                () -> CteComposer.compose(Collections.singletonList(single), Collections.emptyList(), true),
                "只有 1 个 unit 时应抛出异常");
    }

    @Test
    @Order(31)
    @DisplayName("多表组合：joinSpecs 数量不匹配时应抛出异常")
    void testMultiComposeJoinSpecsMismatch() {
        CteUnit t0 = new CteUnit("cte_0", "SELECT 1", Collections.emptyList(), null);
        CteUnit t1 = new CteUnit("cte_1", "SELECT 2", Collections.emptyList(), null);
        CteUnit t2 = new CteUnit("cte_2", "SELECT 3", Collections.emptyList(), null);

        // 3 units 需要 2 个 joinSpecs，只给 1 个
        assertThrows(IllegalArgumentException.class,
                () -> CteComposer.compose(
                        Arrays.asList(t0, t1, t2),
                        Collections.singletonList(JoinSpec.leftJoin("id", "id", null, null)),
                        true),
                "joinSpecs 数量不匹配时应抛出异常");
    }

    @Test
    @Order(32)
    @DisplayName("params 为 null 时不应抛异常")
    void testNullParams() {
        CteUnit left = new CteUnit("cte_0", "SELECT 1", null, null);
        CteUnit right = new CteUnit("cte_1", "SELECT 2", null, null);
        JoinSpec spec = JoinSpec.leftJoin("id", "id", null, null);

        ComposedSql result = CteComposer.compose(left, right, spec, true);

        assertNotNull(result.getSql());
        assertTrue(result.getParams().isEmpty(), "null params 应合并为空列表");
    }

    @Test
    @Order(33)
    @DisplayName("JoinSpec 工厂方法验证")
    void testJoinSpecFactoryMethods() {
        JoinSpec left = JoinSpec.leftJoin("a", "b",
                Arrays.asList("col1"), Arrays.asList("col2"));
        assertEquals("LEFT", left.getJoinType());
        assertEquals("a", left.getLeftKey());
        assertEquals("b", left.getRightKey());

        JoinSpec inner = JoinSpec.innerJoin("x", "y", null, null);
        assertEquals("INNER", inner.getJoinType());
        assertNull(inner.getLeftColumns());
        assertNull(inner.getRightColumns());
    }

    @Test
    @Order(34)
    @DisplayName("CTE 与子查询模式生成的 SQL 结构差异")
    void testCteVsSubqueryStructuralDifference() {
        CteUnit left = new CteUnit("t0", "SELECT 1 AS id", Collections.emptyList(), null);
        CteUnit right = new CteUnit("t1", "SELECT 1 AS id", Collections.emptyList(), null);
        JoinSpec spec = JoinSpec.leftJoin("id", "id", null, null);

        ComposedSql cteSql = CteComposer.compose(left, right, spec, true);
        ComposedSql subSql = CteComposer.compose(left, right, spec, false);

        assertTrue(cteSql.getSql().startsWith("WITH"), "CTE 模式应以 WITH 开头");
        assertTrue(subSql.getSql().startsWith("SELECT"), "子查询模式应以 SELECT 开头");
        assertFalse(subSql.getSql().contains("WITH"), "子查询模式不应包含 WITH");
    }
}
