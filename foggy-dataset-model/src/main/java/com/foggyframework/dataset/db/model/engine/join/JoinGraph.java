package com.foggyframework.dataset.db.model.engine.join;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import jakarta.persistence.criteria.JoinType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * JOIN 依赖图
 * <p>
 * 使用图结构管理表之间的关联关系，提供：
 * <ul>
 *   <li>路径发现：给定目标表集合，找到从主表到这些表的最短路径</li>
 *   <li>拓扑排序：确保 JOIN 顺序正确（先 JOIN 的表在前）</li>
 *   <li>循环检测：防止循环依赖导致的无限循环</li>
 * </ul>
 * </p>
 *
 * <h3>设计优势</h3>
 * <pre>
 * 传统方式（运行时搜索）：
 *   join(product) -> 搜索 fact 有无 FK -> 搜索已有 join 有无 FK -> 搜索 preJoin -> 失败
 *   复杂度: O(n * m)，每次 join 都要遍历搜索
 *
 * 依赖图方式：
 *   模型加载时构建图 -> join(product) -> getPath({product}) -> [fact->product]
 *   复杂度: O(1) 查找 + O(n) 路径计算，路径可缓存
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 构建图
 * JoinGraph graph = new JoinGraph(factTable);
 * graph.addEdge(factTable, productTable, "product_id");
 * graph.addEdge(productTable, categoryTable, "category_id");
 *
 * // 查询路径
 * List&lt;JoinEdge&gt; path = graph.getPath(Set.of(categoryTable));
 * // 返回: [fact->product, product->category]
 * </pre>
 */
@Slf4j
public class JoinGraph {

    /**
     * 主表（图的根节点）
     */
    @Getter
    private final QueryObject root;

    /**
     * 邻接表：from -> [edges]
     * <p>记录从每个节点出发的所有边</p>
     */
    private final Map<String, List<JoinEdge>> adjacency = new LinkedHashMap<>();

    /**
     * 反向邻接表：to -> [edges]
     * <p>用于快速查找某个节点的入边（谁指向它）</p>
     */
    private final Map<String, List<JoinEdge>> reverseAdjacency = new LinkedHashMap<>();

    /**
     * 所有节点（按别名索引）
     */
    private final Map<String, QueryObject> nodes = new LinkedHashMap<>();

    /**
     * 路径缓存：target -> path
     * <p>缓存已计算的路径，避免重复计算</p>
     */
    private final Map<String, List<JoinEdge>> pathCache = new HashMap<>();

    public JoinGraph(QueryObject root) {
        RX.notNull(root, "主表不得为空");
        this.root = root;
        this.nodes.put(root.getAlias(), root);
    }

    /**
     * 添加边（使用外键）
     *
     * @param from       LEFT 表
     * @param to         RIGHT 表
     * @param foreignKey 外键字段名（在 from 表上）
     * @return this（支持链式调用）
     */
    public JoinGraph addEdge(QueryObject from, QueryObject to, String foreignKey) {
        return addEdge(from, to, foreignKey, null, JoinType.LEFT);
    }

    /**
     * 添加边（使用 OnBuilder）
     *
     * @param from      LEFT 表
     * @param to        RIGHT 表
     * @param onBuilder ON 条件构建器
     * @param joinType  JOIN 类型
     * @return this
     */
    public JoinGraph addEdge(QueryObject from, QueryObject to, FsscriptFunction onBuilder, JoinType joinType) {
        return addEdge(from, to, null, onBuilder, joinType);
    }

    /**
     * 添加边（完整参数）
     */
    public JoinGraph addEdge(QueryObject from, QueryObject to, String foreignKey,
                              FsscriptFunction onBuilder, JoinType joinType) {
        RX.notNull(from, "from 不得为空");
        RX.notNull(to, "to 不得为空");

        String fromAlias = from.getAlias();
        String toAlias = to.getAlias();

        // 注册节点
        nodes.putIfAbsent(fromAlias, from);
        nodes.putIfAbsent(toAlias, to);

        // 检查重复边
        List<JoinEdge> edges = adjacency.computeIfAbsent(fromAlias, k -> new ArrayList<>());
        for (JoinEdge edge : edges) {
            if (edge.getTo().getAlias().equals(toAlias)) {
                // 已存在相同的边，跳过
                log.debug("边已存在，跳过: {} -> {}", fromAlias, toAlias);
                return this;
            }
        }

        // 创建边
        JoinEdge edge = JoinEdge.builder()
                .from(from)
                .to(to)
                .foreignKey(foreignKey)
                .onBuilder(onBuilder)
                .joinType(joinType != null ? joinType : JoinType.LEFT)
                .build();

        edges.add(edge);

        // 反向邻接表
        reverseAdjacency.computeIfAbsent(toAlias, k -> new ArrayList<>()).add(edge);

        // 清除路径缓存（图结构变化）
        pathCache.clear();

        if (log.isDebugEnabled()) {
            log.debug("添加边: {}", edge);
        }

        return this;
    }

    /**
     * 获取到达目标表集合的 JOIN 路径
     * <p>
     * 使用 BFS 找到从主表到所有目标表的最短路径，然后拓扑排序。
     * </p>
     *
     * @param targets 目标表集合
     * @return JOIN 边列表（按拓扑顺序排列）
     */
    public List<JoinEdge> getPath(Set<QueryObject> targets) {
        if (targets == null || targets.isEmpty()) {
            return Collections.emptyList();
        }

        // 过滤掉主表本身
        Set<String> targetAliases = new LinkedHashSet<>();
        for (QueryObject target : targets) {
            if (!target.isRootEqual(root)) {
                targetAliases.add(target.getAlias());
            }
        }

        if (targetAliases.isEmpty()) {
            return Collections.emptyList();
        }

        // 生成缓存键
        String cacheKey = String.join(",", new TreeSet<>(targetAliases));
        List<JoinEdge> cached = pathCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // BFS 找到所有需要的边
        List<JoinEdge> result = findPathBFS(targetAliases);

        // 缓存结果
        pathCache.put(cacheKey, result);

        return result;
    }

    /**
     * 使用 BFS 找到到达目标节点的路径
     */
    private List<JoinEdge> findPathBFS(Set<String> targetAliases) {
        // 记录已访问节点的父边
        Map<String, JoinEdge> parentEdge = new HashMap<>();
        parentEdge.put(root.getAlias(), null);

        // BFS 队列
        Queue<String> queue = new LinkedList<>();
        queue.offer(root.getAlias());

        // 记录已找到的目标
        Set<String> foundTargets = new HashSet<>();

        while (!queue.isEmpty() && foundTargets.size() < targetAliases.size()) {
            String current = queue.poll();

            List<JoinEdge> edges = adjacency.get(current);
            if (edges == null) {
                continue;
            }

            for (JoinEdge edge : edges) {
                String toAlias = edge.getTo().getAlias();
                if (!parentEdge.containsKey(toAlias)) {
                    parentEdge.put(toAlias, edge);
                    queue.offer(toAlias);

                    if (targetAliases.contains(toAlias)) {
                        foundTargets.add(toAlias);
                    }
                }
            }
        }

        // 检查是否所有目标都可达
        for (String target : targetAliases) {
            if (!parentEdge.containsKey(target)) {
                throw RX.throwAUserTip("无法找到表 [" + target + "] 的关联路径");
            }
        }

        // 回溯收集所有需要的边
        Set<JoinEdge> neededEdges = new LinkedHashSet<>();
        for (String target : targetAliases) {
            String current = target;
            while (current != null && !current.equals(root.getAlias())) {
                JoinEdge edge = parentEdge.get(current);
                if (edge != null) {
                    neededEdges.add(edge);
                    current = edge.getFrom().getAlias();
                } else {
                    break;
                }
            }
        }

        // 拓扑排序
        return topologicalSort(neededEdges);
    }

    /**
     * 对边集合进行拓扑排序
     * <p>确保 JOIN 顺序正确：如果 A -> B -> C，则 A->B 必须在 B->C 之前</p>
     */
    private List<JoinEdge> topologicalSort(Set<JoinEdge> edges) {
        if (edges.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集涉及的节点
        Set<String> involvedNodes = new LinkedHashSet<>();
        involvedNodes.add(root.getAlias());
        for (JoinEdge edge : edges) {
            involvedNodes.add(edge.getFrom().getAlias());
            involvedNodes.add(edge.getTo().getAlias());
        }

        // 计算入度（只考虑涉及的边）
        Map<String, Integer> inDegree = new HashMap<>();
        for (String node : involvedNodes) {
            inDegree.put(node, 0);
        }
        for (JoinEdge edge : edges) {
            String to = edge.getTo().getAlias();
            inDegree.merge(to, 1, Integer::sum);
        }

        // Kahn's 算法
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<JoinEdge> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            String current = queue.poll();
            visited.add(current);

            // 找到从 current 出发的边（在 edges 集合中）
            for (JoinEdge edge : edges) {
                if (edge.getFrom().getAlias().equals(current)) {
                    result.add(edge);
                    String to = edge.getTo().getAlias();
                    int newDegree = inDegree.get(to) - 1;
                    inDegree.put(to, newDegree);
                    if (newDegree == 0) {
                        queue.offer(to);
                    }
                }
            }
        }

        // 检查是否有循环
        if (result.size() != edges.size()) {
            throw RX.throwAUserTip("检测到循环依赖，无法生成有效的 JOIN 顺序");
        }

        return result;
    }

    /**
     * 验证图的有效性
     * <p>检查是否存在循环依赖</p>
     *
     * @throws RuntimeException 如果存在循环依赖
     */
    public void validate() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String node : nodes.keySet()) {
            if (hasCycle(node, visited, recursionStack)) {
                throw RX.throwAUserTip("检测到循环依赖");
            }
        }
    }

    private boolean hasCycle(String node, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }

        visited.add(node);
        recursionStack.add(node);

        List<JoinEdge> edges = adjacency.get(node);
        if (edges != null) {
            for (JoinEdge edge : edges) {
                if (hasCycle(edge.getTo().getAlias(), visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(node);
        return false;
    }

    /**
     * 获取某个节点的所有出边
     */
    public List<JoinEdge> getEdgesFrom(QueryObject from) {
        List<JoinEdge> edges = adjacency.get(from.getAlias());
        return edges != null ? Collections.unmodifiableList(edges) : Collections.emptyList();
    }

    /**
     * 获取某个节点的所有入边
     */
    public List<JoinEdge> getEdgesTo(QueryObject to) {
        List<JoinEdge> edges = reverseAdjacency.get(to.getAlias());
        return edges != null ? Collections.unmodifiableList(edges) : Collections.emptyList();
    }

    /**
     * 获取所有节点
     */
    public Collection<QueryObject> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * 获取所有边
     */
    public List<JoinEdge> getAllEdges() {
        List<JoinEdge> result = new ArrayList<>();
        for (List<JoinEdge> edges : adjacency.values()) {
            result.addAll(edges);
        }
        return result;
    }

    /**
     * 获取节点数量
     */
    public int getNodeCount() {
        return nodes.size();
    }

    /**
     * 获取边数量
     */
    public int getEdgeCount() {
        return adjacency.values().stream().mapToInt(List::size).sum();
    }

    /**
     * 清除路径缓存
     * <p>当图结构变化时自动调用</p>
     */
    public void clearPathCache() {
        pathCache.clear();
    }

    /**
     * 创建图的浅拷贝
     * <p>用于多模型查询场景，避免修改原始图</p>
     *
     * @return 新的 JoinGraph 实例，包含相同的边
     */
    public JoinGraph copy() {
        JoinGraph copy = new JoinGraph(this.root);
        // 复制所有边
        for (List<JoinEdge> edges : adjacency.values()) {
            for (JoinEdge edge : edges) {
                copy.addEdge(edge.getFrom(), edge.getTo(), edge.getForeignKey(),
                        edge.getOnBuilder(), edge.getJoinType());
            }
        }
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("JoinGraph{root=").append(root.getAlias());
        sb.append(", nodes=").append(nodes.size());
        sb.append(", edges=").append(getEdgeCount());
        sb.append("}\n");

        for (Map.Entry<String, List<JoinEdge>> entry : adjacency.entrySet()) {
            for (JoinEdge edge : entry.getValue()) {
                sb.append("  ").append(edge).append("\n");
            }
        }

        return sb.toString();
    }
}
