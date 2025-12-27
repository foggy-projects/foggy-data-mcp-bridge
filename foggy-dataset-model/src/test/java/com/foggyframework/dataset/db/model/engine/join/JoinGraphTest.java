package com.foggyframework.dataset.db.model.engine.join;

import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.model.spi.support.SimpleQueryObject;
import jakarta.persistence.criteria.JoinType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JoinGraph 单元测试
 */
@DisplayName("JoinGraph 依赖图测试")
class JoinGraphTest {

    // 模拟的 QueryObject
    QueryObject fact;      // 主表
    QueryObject product;   // 产品表
    QueryObject category;  // 分类表
    QueryObject brand;     // 品牌表
    QueryObject supplier;  // 供应商表

    @BeforeEach
    void setUp() {
        // 创建模拟的 QueryObject
        fact = SimpleQueryObject.of("t_fact", "m1", null);
        product = SimpleQueryObject.of("t_product", "d1", null);
        category = SimpleQueryObject.of("t_category", "d2", null);
        brand = SimpleQueryObject.of("t_brand", "d3", null);
        supplier = SimpleQueryObject.of("t_supplier", "d4", null);
    }

    @Nested
    @DisplayName("基础功能测试")
    class BasicTests {

        @Test
        @DisplayName("创建空图")
        void testCreateEmptyGraph() {
            JoinGraph graph = new JoinGraph(fact);

            assertEquals(1, graph.getNodeCount());
            assertEquals(0, graph.getEdgeCount());
            assertEquals(fact, graph.getRoot());
        }

        @Test
        @DisplayName("添加单条边")
        void testAddSingleEdge() {
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");

            assertEquals(2, graph.getNodeCount());
            assertEquals(1, graph.getEdgeCount());

            List<JoinEdge> edges = graph.getEdgesFrom(fact);
            assertEquals(1, edges.size());
            assertEquals(product, edges.get(0).getTo());
            assertEquals("product_id", edges.get(0).getForeignKey());
        }

        @Test
        @DisplayName("添加多条边")
        void testAddMultipleEdges() {
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id")
                 .addEdge(fact, brand, "brand_id")
                 .addEdge(product, category, "category_id");

            assertEquals(4, graph.getNodeCount());
            assertEquals(3, graph.getEdgeCount());
        }

        @Test
        @DisplayName("重复添加边应被忽略")
        void testDuplicateEdgeIgnored() {
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");
            graph.addEdge(fact, product, "product_id"); // 重复

            assertEquals(1, graph.getEdgeCount());
        }
    }

    @Nested
    @DisplayName("路径查找测试")
    class PathFindingTests {

        @Test
        @DisplayName("查找直接关联的表")
        void testFindDirectPath() {
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");

            List<JoinEdge> path = graph.getPath(Set.of(product));

            assertEquals(1, path.size());
            assertEquals(fact, path.get(0).getFrom());
            assertEquals(product, path.get(0).getTo());
        }

        @Test
        @DisplayName("查找嵌套关联的表（二级）")
        void testFindNestedPath() {
            // fact -> product -> category
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");
            graph.addEdge(product, category, "category_id");

            List<JoinEdge> path = graph.getPath(Set.of(category));

            assertEquals(2, path.size());
            // 拓扑排序：先 fact->product，再 product->category
            assertEquals(fact, path.get(0).getFrom());
            assertEquals(product, path.get(0).getTo());
            assertEquals(product, path.get(1).getFrom());
            assertEquals(category, path.get(1).getTo());
        }

        @Test
        @DisplayName("查找多个目标表的路径")
        void testFindMultipleTargetPath() {
            // fact -> product -> category
            // fact -> brand
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");
            graph.addEdge(product, category, "category_id");
            graph.addEdge(fact, brand, "brand_id");

            List<JoinEdge> path = graph.getPath(Set.of(category, brand));

            assertEquals(3, path.size());
            // 应该包含所有需要的边
            assertTrue(path.stream().anyMatch(e -> e.getTo().equals(product)));
            assertTrue(path.stream().anyMatch(e -> e.getTo().equals(category)));
            assertTrue(path.stream().anyMatch(e -> e.getTo().equals(brand)));
        }

        @Test
        @DisplayName("查找三级嵌套路径")
        void testFindThreeLevelPath() {
            // fact -> product -> category -> supplier
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");
            graph.addEdge(product, category, "category_id");
            graph.addEdge(category, supplier, "supplier_id");

            List<JoinEdge> path = graph.getPath(Set.of(supplier));

            assertEquals(3, path.size());
            // 验证拓扑顺序
            assertEquals(product, path.get(0).getTo());
            assertEquals(category, path.get(1).getTo());
            assertEquals(supplier, path.get(2).getTo());
        }

        @Test
        @DisplayName("查询主表自身返回空路径")
        void testFindRootReturnsEmptyPath() {
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");

            List<JoinEdge> path = graph.getPath(Set.of(fact));

            assertTrue(path.isEmpty());
        }

        @Test
        @DisplayName("查询空集合返回空路径")
        void testFindEmptySetReturnsEmptyPath() {
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");

            List<JoinEdge> path = graph.getPath(Set.of());

            assertTrue(path.isEmpty());
        }

        @Test
        @DisplayName("查询不可达的表应抛出异常")
        void testFindUnreachableThrowsException() {
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");
            // supplier 没有路径

            assertThrows(Exception.class, () -> graph.getPath(Set.of(supplier)));
        }
    }

    @Nested
    @DisplayName("路径缓存测试")
    class PathCacheTests {

        @Test
        @DisplayName("相同目标的路径应被缓存")
        void testPathIsCached() {
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");
            graph.addEdge(product, category, "category_id");

            List<JoinEdge> path1 = graph.getPath(Set.of(category));
            List<JoinEdge> path2 = graph.getPath(Set.of(category));

            assertSame(path1, path2, "相同目标应返回缓存的结果");
        }

        @Test
        @DisplayName("添加新边后缓存应被清除")
        void testCacheClearedAfterAddEdge() {
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");

            List<JoinEdge> path1 = graph.getPath(Set.of(product));

            graph.addEdge(fact, brand, "brand_id");

            List<JoinEdge> path2 = graph.getPath(Set.of(product));

            assertNotSame(path1, path2, "添加边后应重新计算路径");
        }
    }

    @Nested
    @DisplayName("循环检测测试")
    class CycleDetectionTests {

        @Test
        @DisplayName("无循环的图验证通过")
        void testValidGraphPasses() {
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");
            graph.addEdge(product, category, "category_id");

            assertDoesNotThrow(graph::validate);
        }

        @Test
        @DisplayName("有循环的图验证失败")
        void testCyclicGraphFails() {
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");
            graph.addEdge(product, category, "category_id");
            graph.addEdge(category, fact, "fact_id"); // 形成循环

            assertThrows(Exception.class, graph::validate);
        }
    }

    @Nested
    @DisplayName("拓扑排序测试")
    class TopologicalSortTests {

        @Test
        @DisplayName("复杂图的拓扑排序正确")
        void testComplexTopologicalSort() {
            // 构建复杂图:
            //      product
            //     /       \
            // fact         category
            //     \       /
            //      brand
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");
            graph.addEdge(fact, brand, "brand_id");
            graph.addEdge(product, category, "category_id");
            graph.addEdge(brand, category, "category_id"); // 两条路径到 category

            List<JoinEdge> path = graph.getPath(Set.of(category));

            // 应该有 3 条边（fact->product, fact->brand, product->category 或 brand->category）
            // 但由于 BFS，只会选择一条最短路径
            assertTrue(path.size() >= 2);

            // 验证 category 出现在最后
            assertEquals(category, path.get(path.size() - 1).getTo());
        }

        @Test
        @DisplayName("钻石依赖的拓扑排序正确")
        void testDiamondDependency() {
            // 钻石依赖:
            //     product
            //    /       \
            // fact       supplier
            //    \       /
            //     brand
            //       |
            //    supplier (通过 brand)
            JoinGraph graph = new JoinGraph(fact);
            graph.addEdge(fact, product, "product_id");
            graph.addEdge(fact, brand, "brand_id");
            graph.addEdge(product, supplier, "supplier_id");

            List<JoinEdge> path = graph.getPath(Set.of(supplier));

            // 验证顺序正确
            assertEquals(2, path.size());
            assertEquals(product, path.get(0).getTo());
            assertEquals(supplier, path.get(1).getTo());
        }
    }

    @Nested
    @DisplayName("JoinEdge 测试")
    class JoinEdgeTests {

        @Test
        @DisplayName("JoinEdge 默认为 LEFT JOIN")
        void testDefaultJoinType() {
            JoinEdge edge = JoinEdge.builder()
                    .from(fact)
                    .to(product)
                    .foreignKey("product_id")
                    .build();

            assertEquals(JoinType.LEFT, edge.getJoinType());
            assertEquals(" left join ", edge.getJoinTypeString());
        }

        @Test
        @DisplayName("JoinEdge 支持 INNER JOIN")
        void testInnerJoinType() {
            JoinEdge edge = JoinEdge.builder()
                    .from(fact)
                    .to(product)
                    .foreignKey("product_id")
                    .joinType(JoinType.INNER)
                    .build();

            assertEquals(JoinType.INNER, edge.getJoinType());
            assertEquals(" inner join ", edge.getJoinTypeString());
        }

        @Test
        @DisplayName("JoinEdge 的 edgeKey 唯一标识边")
        void testEdgeKey() {
            JoinEdge edge = JoinEdge.builder()
                    .from(fact)
                    .to(product)
                    .foreignKey("product_id")
                    .build();

            assertEquals("m1->d1", edge.getEdgeKey());
        }
    }
}
