package com.foggyframework.dataset.model.engine.pivot.algo;

import com.foggyframework.dataset.model.engine.pivot.PivotResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 父子层级树构建器 (Hierarchy Tree Builder)
 *
 * <p>将扁平聚合结果集 + 邻接表骨架（nodeId → parentId）转换为
 * 真正的父子嵌套树。</p>
 *
 * <p>算法：
 * <ol>
 *   <li>将邻接表索引为 Map&lt;nodeId, parentId&gt;</li>
 *   <li>将结果集按 nodeId 索引</li>
 *   <li>找出根节点（parentId == null 或父不在结果集中）</li>
 *   <li>递归构建 TreeNode 树</li>
 *   <li>孤儿节点作为额外根节点输出</li>
 * </ol>
 * </p>
 *
 * <p>每个节点输出自身的直接聚合值，不做递归卷起（避免 Non-Additive 冲突）。</p>
 */
public class HierarchyTreeBuilder {

    private static final Logger logger = LoggerFactory.getLogger(HierarchyTreeBuilder.class);

    /**
     * 邻接表骨架：nodeId → parentId
     */
    public static class Skeleton {
        private final Map<Object, Object> adjacency; // nodeId → parentId

        public Skeleton(Map<Object, Object> adjacency) {
            this.adjacency = adjacency != null ? adjacency : Collections.emptyMap();
        }

        public Object getParentId(Object nodeId) {
            return adjacency.get(nodeId);
        }

        public boolean isEmpty() {
            return adjacency.isEmpty();
        }

        public int size() {
            return adjacency.size();
        }

        /**
         * 从辅助查询结果构建骨架
         *
         * @param rows       辅助查询返回的行（SELECT DISTINCT id, parentId）
         * @param idField    id 字段名, e.g. "team$id"
         * @param parentField parentId 字段名, e.g. "team$parentId"
         * @return 骨架
         */
        public static Skeleton fromRows(List<Map<String, Object>> rows,
                                         String idField, String parentField) {
            Map<Object, Object> adj = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                Object nodeId = row.get(idField);
                Object parentId = row.get(parentField);
                if (nodeId != null) {
                    adj.put(nodeId, parentId);
                }
            }
            return new Skeleton(adj);
        }
    }

    /**
     * 递归构建所需的不可变上下文（避免 buildNode 参数膨胀）
     */
    private static class BuildContext {
        final Map<Object, List<Map<String, Object>>> rowsByNodeId;
        final Map<Object, List<Object>> childrenIndex;
        final List<String> displayFields;
        final List<String> colFields;
        final List<String> metrics;
        final String idField;

        BuildContext(Map<Object, List<Map<String, Object>>> rowsByNodeId,
                     Map<Object, List<Object>> childrenIndex,
                     List<String> displayFields,
                     List<String> colFields,
                     List<String> metrics,
                     String idField) {
            this.rowsByNodeId = rowsByNodeId;
            this.childrenIndex = childrenIndex;
            this.displayFields = displayFields;
            this.colFields = colFields;
            this.metrics = metrics;
            this.idField = idField;
        }
    }

    /**
     * 构建父子层级树
     *
     * @param resultSet    Phase 2 加工后的扁平结果集
     * @param skeleton     邻接表骨架
     * @param idField      节点 ID 字段名, e.g. "team$id"
     * @param displayFields 展示字段列表（用户声明的 rowFields，含 caption 等）
     * @param colFields    列轴字段
     * @param metrics      度量字段
     * @return 树节点列表（根节点层）
     */
    public static List<PivotResult.TreeNode> build(
            List<Map<String, Object>> resultSet,
            Skeleton skeleton,
            String idField,
            List<String> displayFields,
            List<String> colFields,
            List<String> metrics) {

        if (resultSet.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 按 nodeId 索引结果集（同一 nodeId 可能有多行——不同日期列）
        Map<Object, List<Map<String, Object>>> rowsByNodeId = new LinkedHashMap<>();
        for (Map<String, Object> row : resultSet) {
            Object nodeId = row.get(idField);
            if (nodeId == null) continue;
            rowsByNodeId.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(row);
        }

        // 2. 构建子节点索引：parentId → List<nodeId>
        Map<Object, List<Object>> childrenIndex = new LinkedHashMap<>();
        Set<Object> allNodeIds = rowsByNodeId.keySet();

        for (Object nodeId : allNodeIds) {
            Object parentId = skeleton.getParentId(nodeId);
            if (parentId != null && allNodeIds.contains(parentId) && !parentId.equals(nodeId)) {
                childrenIndex.computeIfAbsent(parentId, k -> new ArrayList<>()).add(nodeId);
            }
        }

        // 3. 找出根节点（没有父节点，或父不在结果集中，或父是自己）
        List<Object> rootIds = new ArrayList<>();
        for (Object nodeId : allNodeIds) {
            Object parentId = skeleton.getParentId(nodeId);
            if (parentId == null || !allNodeIds.contains(parentId) || parentId.equals(nodeId)) {
                rootIds.add(nodeId);
            }
        }

        logger.debug("[HierarchyTreeBuilder] {} nodes, {} roots, skeleton size={}",
                allNodeIds.size(), rootIds.size(), skeleton.size());

        // 4. 递归构建树
        //    每棵子树独立 pathVisited，防止循环引用，但不阻止 DAG 共享子节点
        BuildContext ctx = new BuildContext(rowsByNodeId, childrenIndex,
                displayFields, colFields, metrics, idField);

        List<PivotResult.TreeNode> roots = new ArrayList<>();
        for (Object rootId : rootIds) {
            Set<Object> pathVisited = new HashSet<>();
            PivotResult.TreeNode node = buildNode(rootId, ctx, pathVisited);
            if (node != null) {
                roots.add(node);
            }
        }

        return roots;
    }

    /**
     * 递归构建单个节点
     *
     * @param nodeId       当前节点 ID
     * @param ctx          不可变构建上下文
     * @param pathVisited  当前 DFS 路径上已访问的节点（检测循环引用）
     */
    private static PivotResult.TreeNode buildNode(
            Object nodeId,
            BuildContext ctx,
            Set<Object> pathVisited) {

        // 循环引用检测：仅检测当前 DFS 路径上的环
        if (!pathVisited.add(nodeId)) {
            logger.warn("[HierarchyTreeBuilder] Circular reference detected at node: {}", nodeId);
            return null;
        }

        List<Map<String, Object>> nodeRows = ctx.rowsByNodeId.get(nodeId);
        if (nodeRows == null || nodeRows.isEmpty()) {
            return null;
        }

        PivotResult.TreeNode treeNode = new PivotResult.TreeNode();

        // 节点坐标
        Map<String, Object> nodeMap = new LinkedHashMap<>();
        Map<String, Object> firstRow = nodeRows.get(0);
        nodeMap.put(ctx.idField, nodeId);
        for (String field : ctx.displayFields) {
            if (!field.equals(ctx.idField)) {
                nodeMap.put(field, firstRow.get(field));
            }
        }
        treeNode.setNode(nodeMap);

        // 构建 cells
        Map<String, Object> cells = new LinkedHashMap<>();
        for (Map<String, Object> row : nodeRows) {
            String cellKey = PivotAlgoUtils.buildCellKey(row, ctx.colFields);
            for (String metric : ctx.metrics) {
                String fullKey = cellKey.isEmpty() ? metric : cellKey + "|" + metric;
                cells.put(fullKey, row.get(metric));
            }
        }
        treeNode.setCells(cells);

        // 递归构建子节点
        List<Object> childIds = ctx.childrenIndex.get(nodeId);
        if (childIds != null && !childIds.isEmpty()) {
            List<PivotResult.TreeNode> children = new ArrayList<>();
            for (Object childId : childIds) {
                PivotResult.TreeNode child = buildNode(childId, ctx, pathVisited);
                if (child != null) {
                    children.add(child);
                }
            }
            if (!children.isEmpty()) {
                treeNode.setChildren(children);
            }
        }

        // 回溯：离开当前路径，允许其他根的 DFS 重新访问此节点
        pathVisited.remove(nodeId);

        return treeNode;
    }
}
