package com.foggyframework.dataset.model.engine.pivot.algo;

import com.foggyframework.dataset.model.engine.pivot.PivotResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HierarchyTreeBuilder 单元测试
 *
 * <p>使用与 dim_team 相同的层级结构：
 * <pre>
 *   总公司 (T001)
 *   ├── 技术部 (T002)
 *   │   ├── 研发组 (T003)
 *   │   │   ├── 前端小组 (T006)
 *   │   │   └── 后端小组 (T007)
 *   │   └── 测试组 (T004)
 *   └── 销售部 (T005)
 *       ├── 华东区 (T008)
 *       └── 华北区 (T009)
 * </pre>
 * </p>
 */
@DisplayName("HierarchyTreeBuilder 父子层级树构建")
class HierarchyTreeBuilderTest {

    private static final Logger log = LoggerFactory.getLogger(HierarchyTreeBuilderTest.class);

    // 邻接表骨架：nodeId → parentId
    private HierarchyTreeBuilder.Skeleton buildTeamSkeleton() {
        Map<Object, Object> adj = new LinkedHashMap<>();
        adj.put("T001", null);      // 总公司（根节点）
        adj.put("T002", "T001");    // 技术部
        adj.put("T003", "T002");    // 研发组
        adj.put("T004", "T002");    // 测试组
        adj.put("T005", "T001");    // 销售部
        adj.put("T006", "T003");    // 前端小组
        adj.put("T007", "T003");    // 后端小组
        adj.put("T008", "T005");    // 华东区
        adj.put("T009", "T005");    // 华北区
        return new HierarchyTreeBuilder.Skeleton(adj);
    }

    // 模拟结果集：team$id, team$caption, salesAmount
    private List<Map<String, Object>> buildTeamResultSet() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("T001", "总公司", 50000.0));
        rows.add(row("T002", "技术部", 30000.0));
        rows.add(row("T003", "研发组", 10000.0));
        rows.add(row("T004", "测试组", 8000.0));
        rows.add(row("T005", "销售部", 100000.0));
        rows.add(row("T006", "前端小组", 5000.0));
        rows.add(row("T007", "后端小组", 7000.0));
        rows.add(row("T008", "华东区", 45000.0));
        rows.add(row("T009", "华北区", 40000.0));
        return rows;
    }

    private Map<String, Object> row(String id, String caption, double salesAmount) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("team$id", id);
        r.put("team$caption", caption);
        r.put("salesAmount", salesAmount);
        return r;
    }

    @Test
    @DisplayName("基本树构建：根节点和子节点正确嵌套")
    void testBasicTreeStructure() {
        HierarchyTreeBuilder.Skeleton skeleton = buildTeamSkeleton();
        List<Map<String, Object>> resultSet = buildTeamResultSet();

        List<PivotResult.TreeNode> roots = HierarchyTreeBuilder.build(
                resultSet, skeleton, "team$id",
                List.of("team$caption"), List.of(), List.of("salesAmount"));

        // 应该只有一个根节点：总公司
        assertEquals(1, roots.size());
        PivotResult.TreeNode root = roots.get(0);
        assertEquals("T001", root.getNode().get("team$id"));
        assertEquals("总公司", root.getNode().get("team$caption"));

        // 根节点有 2 个子节点：技术部、销售部
        assertNotNull(root.getChildren());
        assertEquals(2, root.getChildren().size());

        log.info("根节点: {}", root.getNode());
        log.info("子节点数: {}", root.getChildren().size());
    }

    @Test
    @DisplayName("四层嵌套深度验证")
    void testFourLevelDepth() {
        HierarchyTreeBuilder.Skeleton skeleton = buildTeamSkeleton();
        List<Map<String, Object>> resultSet = buildTeamResultSet();

        List<PivotResult.TreeNode> roots = HierarchyTreeBuilder.build(
                resultSet, skeleton, "team$id",
                List.of("team$caption"), List.of(), List.of("salesAmount"));

        PivotResult.TreeNode root = roots.get(0);

        // Level 1: 总公司
        assertEquals("T001", root.getNode().get("team$id"));

        // Level 2: 技术部
        PivotResult.TreeNode techDept = findChild(root, "T002");
        assertNotNull(techDept, "应找到技术部");

        // Level 3: 研发组
        PivotResult.TreeNode devTeam = findChild(techDept, "T003");
        assertNotNull(devTeam, "应找到研发组");

        // Level 4: 前端小组、后端小组
        assertNotNull(devTeam.getChildren());
        assertEquals(2, devTeam.getChildren().size());

        PivotResult.TreeNode frontend = findChild(devTeam, "T006");
        assertNotNull(frontend, "应找到前端小组");
        assertNull(frontend.getChildren(), "前端小组是叶子节点");

        log.info("四层嵌套验证通过: T001 → T002 → T003 → T006/T007");
    }

    @Test
    @DisplayName("度量值正确挂载")
    void testMetricValues() {
        HierarchyTreeBuilder.Skeleton skeleton = buildTeamSkeleton();
        List<Map<String, Object>> resultSet = buildTeamResultSet();

        List<PivotResult.TreeNode> roots = HierarchyTreeBuilder.build(
                resultSet, skeleton, "team$id",
                List.of("team$caption"), List.of(), List.of("salesAmount"));

        PivotResult.TreeNode root = roots.get(0);
        // 总公司自身销售 50000
        assertEquals(50000.0, root.getCells().get("salesAmount"));

        PivotResult.TreeNode techDept = findChild(root, "T002");
        assertEquals(30000.0, techDept.getCells().get("salesAmount"));

        PivotResult.TreeNode frontend = findChild(findChild(techDept, "T003"), "T006");
        assertEquals(5000.0, frontend.getCells().get("salesAmount"));
    }

    @Test
    @DisplayName("部分节点缺失数据：仅部分团队有销售")
    void testPartialData() {
        HierarchyTreeBuilder.Skeleton skeleton = buildTeamSkeleton();

        // 只有 T001, T002, T005 有数据
        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(row("T001", "总公司", 50000.0));
        resultSet.add(row("T002", "技术部", 30000.0));
        resultSet.add(row("T005", "销售部", 100000.0));

        List<PivotResult.TreeNode> roots = HierarchyTreeBuilder.build(
                resultSet, skeleton, "team$id",
                List.of("team$caption"), List.of(), List.of("salesAmount"));

        assertEquals(1, roots.size());
        PivotResult.TreeNode root = roots.get(0);
        // 技术部是 root 的子节点（因为 T002 的 parent T001 在结果集中）
        PivotResult.TreeNode techDept = findChild(root, "T002");
        assertNotNull(techDept);
        // 技术部没有子节点（T003, T004 不在结果集中）
        assertNull(techDept.getChildren());

        log.info("部分数据验证通过: 只有 3 个有数据的节点正确建树");
    }

    @Test
    @DisplayName("孤儿节点处理：parent 不在结果集中")
    void testOrphanNodes() {
        HierarchyTreeBuilder.Skeleton skeleton = buildTeamSkeleton();

        // 只有 T003, T006, T007（没有 T001, T002 — T003 的 parent T002 不在结果集中）
        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(row("T003", "研发组", 10000.0));
        resultSet.add(row("T006", "前端小组", 5000.0));
        resultSet.add(row("T007", "后端小组", 7000.0));

        List<PivotResult.TreeNode> roots = HierarchyTreeBuilder.build(
                resultSet, skeleton, "team$id",
                List.of("team$caption"), List.of(), List.of("salesAmount"));

        // T003 应成为根节点（因为 T002 不在结果集中）
        assertEquals(1, roots.size());
        assertEquals("T003", roots.get(0).getNode().get("team$id"));
        assertEquals(2, roots.get(0).getChildren().size());

        log.info("孤儿节点验证通过: T003 成为根节点");
    }

    @Test
    @DisplayName("循环引用检测：纯环无根节点")
    void testCircularReference() {
        // 构造循环引用：A → B → C → A
        Map<Object, Object> adj = new LinkedHashMap<>();
        adj.put("A", "C");
        adj.put("B", "A");
        adj.put("C", "B");
        HierarchyTreeBuilder.Skeleton skeleton = new HierarchyTreeBuilder.Skeleton(adj);

        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(rowSimple("A", 100.0));
        resultSet.add(rowSimple("B", 200.0));
        resultSet.add(rowSimple("C", 300.0));

        // 不应死循环，应优雅处理
        List<PivotResult.TreeNode> roots = assertDoesNotThrow(() ->
                HierarchyTreeBuilder.build(resultSet, skeleton, "id",
                        List.of(), List.of(), List.of("value")));

        // A→C, B→A, C→B：所有父都在结果集中且不等于自身 → 无根节点 → 空列表
        assertEquals(0, roots.size(), "纯循环引用不应产生根节点");

        log.info("循环引用检测通过: {} 个根节点输出", roots.size());
    }

    @Test
    @DisplayName("DAG 拓扑：两个根共享子节点")
    void testDAGSharedChild() {
        // R1 和 R2 都是根节点，C 同时出现在 R1 和 R2 的子节点中
        // 由于严格树结构邻接表只记录一个 parent，我们模拟一种变体：
        //   R1(无parent), R2(无parent), C(parent=R1)
        // C 只挂在 R1 下，但确保 R2 也能正常输出（不被 visited 错误阻挡）
        Map<Object, Object> adj = new LinkedHashMap<>();
        adj.put("R1", null);
        adj.put("R2", null);
        adj.put("C", "R1");

        HierarchyTreeBuilder.Skeleton skeleton = new HierarchyTreeBuilder.Skeleton(adj);

        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(rowSimple("R1", 100.0));
        resultSet.add(rowSimple("R2", 200.0));
        resultSet.add(rowSimple("C", 300.0));

        List<PivotResult.TreeNode> roots = HierarchyTreeBuilder.build(
                resultSet, skeleton, "id",
                List.of(), List.of(), List.of("value"));

        // 应有 2 个根节点
        assertEquals(2, roots.size(), "DAG 应有两个根节点");

        // R1 应有子节点 C
        PivotResult.TreeNode r1 = roots.stream()
                .filter(n -> "R1".equals(n.getNode().get("id")))
                .findFirst().orElse(null);
        assertNotNull(r1);
        assertNotNull(r1.getChildren());
        assertEquals(1, r1.getChildren().size());
        assertEquals("C", r1.getChildren().get(0).getNode().get("id"));

        // R2 无子节点但不应被 visited 阻挡
        PivotResult.TreeNode r2 = roots.stream()
                .filter(n -> "R2".equals(n.getNode().get("id")))
                .findFirst().orElse(null);
        assertNotNull(r2, "R2 不应被共享 visited 阻挡");
        assertNull(r2.getChildren());

        log.info("DAG 验证通过: R1 children={}, R2 exists=true",
                r1.getChildren().size());
    }

    @Test
    @DisplayName("空结果集返回空列表")
    void testEmptyResultSet() {
        HierarchyTreeBuilder.Skeleton skeleton = buildTeamSkeleton();
        List<PivotResult.TreeNode> roots = HierarchyTreeBuilder.build(
                Collections.emptyList(), skeleton, "team$id",
                List.of("team$caption"), List.of(), List.of("salesAmount"));

        assertTrue(roots.isEmpty());
    }

    @Test
    @DisplayName("空骨架返回空列表")
    void testEmptySkeleton() {
        HierarchyTreeBuilder.Skeleton skeleton = new HierarchyTreeBuilder.Skeleton(null);
        assertTrue(skeleton.isEmpty());
        assertEquals(0, skeleton.size());
    }

    @Test
    @DisplayName("Skeleton.fromRows 正确构建")
    void testSkeletonFromRows() {
        List<Map<String, Object>> rows = List.of(
                Map.of("team$id", "T001"),
                Map.of("team$id", "T002", "team$parentId", "T001"),
                Map.of("team$id", "T003", "team$parentId", "T002")
        );

        HierarchyTreeBuilder.Skeleton skeleton =
                HierarchyTreeBuilder.Skeleton.fromRows(rows, "team$id", "team$parentId");

        assertEquals(3, skeleton.size());
        assertNull(skeleton.getParentId("T001"));
        assertEquals("T001", skeleton.getParentId("T002"));
        assertEquals("T002", skeleton.getParentId("T003"));
    }

    @Test
    @DisplayName("带列轴的树构建")
    void testTreeWithColumnAxis() {
        // 简化版：2 个节点 × 2 个日期
        Map<Object, Object> adj = new LinkedHashMap<>();
        adj.put("T001", null);
        adj.put("T002", "T001");
        HierarchyTreeBuilder.Skeleton skeleton = new HierarchyTreeBuilder.Skeleton(adj);

        List<Map<String, Object>> resultSet = new ArrayList<>();
        resultSet.add(rowWithCol("T001", "总公司", 1, 50000.0));
        resultSet.add(rowWithCol("T001", "总公司", 2, 60000.0));
        resultSet.add(rowWithCol("T002", "技术部", 1, 30000.0));
        resultSet.add(rowWithCol("T002", "技术部", 2, 35000.0));

        List<PivotResult.TreeNode> roots = HierarchyTreeBuilder.build(
                resultSet, skeleton, "team$id",
                List.of("team$caption"), List.of("month"), List.of("salesAmount"));

        assertEquals(1, roots.size());
        PivotResult.TreeNode root = roots.get(0);

        // cells 应有 2 个条目：1|salesAmount, 2|salesAmount
        assertEquals(2, root.getCells().size());
        assertEquals(50000.0, root.getCells().get("1|salesAmount"));
        assertEquals(60000.0, root.getCells().get("2|salesAmount"));

        log.info("带列轴验证通过: root cells={}", root.getCells());
    }

    // ========== 辅助方法 ==========

    private Map<String, Object> rowSimple(String id, double value) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("value", value);
        return r;
    }

    private Map<String, Object> rowWithCol(String id, String caption, int month, double amount) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("team$id", id);
        r.put("team$caption", caption);
        r.put("month", month);
        r.put("salesAmount", amount);
        return r;
    }

    private PivotResult.TreeNode findChild(PivotResult.TreeNode parent, String id) {
        if (parent.getChildren() == null) return null;
        return parent.getChildren().stream()
                .filter(c -> id.equals(c.getNode().get("team$id")))
                .findFirst()
                .orElse(null);
    }
}
