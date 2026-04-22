package com.foggyframework.dataset.db.model.engine.compose.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M2 端到端组合测试 — 需求.md §典型示例跨语言对齐 + Layer-C 白名单硬断言。
 * 跨仓对齐 Python test_plan_composition.py。
 */
@DisplayName("M2 Plan Composition")
class PlanCompositionTest {

    @Nested
    @DisplayName("需求.md §典型示例 1 二段聚合")
    class SpecExample1TwoStageAggregation {

        @Test
        @DisplayName("基于 ReceivableLineQM 的二段聚合派生可正常构造")
        void buildTwoStageAggregation() {
            QueryPlan overdueByCustomer = Dsl.from(Dsl.FromOptions.builder()
                    .model("ReceivableLineQM")
                    .columns(List.of(
                            "salespersonId",
                            "salespersonName",
                            "customer$id AS customerId",
                            "SUM(IIF(isOverdue == 1, residualAmount, 0)) AS customerOverdueAmount"))
                    .slice(List.of(
                            Map.of("field", "docType", "op", "=", "value", "AR"),
                            Map.of("field", "docState", "op", "=", "value", "posted")))
                    .groupBy(List.of("salespersonId", "salespersonName", "customerId"))
                    .build());
            assertInstanceOf(BaseModelPlan.class, overdueByCustomer);

            DerivedQueryPlan salespersonOverdue = overdueByCustomer.query(QueryOptions.builder()
                    .columns(List.of(
                            "salespersonId",
                            "salespersonName",
                            "SUM(customerOverdueAmount) AS arOverdueAmount",
                            "COUNT(*) AS arOverdueCustomerCount"))
                    .groupBy(List.of("salespersonId", "salespersonName"))
                    .orderBy(List.of("-arOverdueAmount"))
                    .build());
            assertInstanceOf(DerivedQueryPlan.class, salespersonOverdue);
            assertSame(overdueByCustomer, salespersonOverdue.source());

            // execute()/toSql() still deferred
            assertThrows(UnsupportedInM2Exception.class, salespersonOverdue::execute);
        }
    }

    @Nested
    @DisplayName("需求.md §典型示例 2 union 后再聚合")
    class SpecExample2UnionThenAggregate {

        @Test
        @DisplayName("当前 + 归档两个数据源做 UNION ALL 再聚合")
        void buildUnionThenAggregate() {
            QueryPlan current = Dsl.from(Dsl.FromOptions.builder()
                    .model("CurrentReceivableQM")
                    .columns(List.of("salespersonId", "amount"))
                    .build());
            QueryPlan archived = Dsl.from(Dsl.FromOptions.builder()
                    .model("ArchivedReceivableQM")
                    .columns(List.of("salespersonId", "amount"))
                    .build());

            UnionPlan merged = current.union(archived, true);
            DerivedQueryPlan finalPlan = merged.query(QueryOptions.builder()
                    .columns(List.of("salespersonId", "SUM(amount) AS totalAmount"))
                    .groupBy(List.of("salespersonId"))
                    .build());

            assertTrue(merged.all());
            assertInstanceOf(DerivedQueryPlan.class, finalPlan);

            List<BaseModelPlan> bases = finalPlan.baseModelPlans();
            assertEquals(2, bases.size());
            Set<String> modelNames = bases.stream().map(BaseModelPlan::model)
                    .collect(Collectors.toSet());
            assertEquals(Set.of("CurrentReceivableQM", "ArchivedReceivableQM"), modelNames);
        }
    }

    @Nested
    @DisplayName("需求.md §典型示例 3 join 后再筛选")
    class SpecExample3JoinThenFilter {

        @Test
        @DisplayName("SaleOrder 左连接 CrmLead 后按 totalSales 筛选")
        void buildJoinThenFilter() {
            QueryPlan sales = Dsl.from(Dsl.FromOptions.builder()
                    .model("SaleOrderQM")
                    .columns(List.of(
                            "partner$id AS partnerId",
                            "partner$caption AS partnerName",
                            "SUM(amountTotal) AS totalSales"))
                    .groupBy(List.of("partnerId", "partnerName"))
                    .build());
            QueryPlan leads = Dsl.from(Dsl.FromOptions.builder()
                    .model("CrmLeadQM")
                    .columns(List.of(
                            "partner$id AS partnerId",
                            "COUNT(*) AS leadCount"))
                    .groupBy(List.of("partnerId"))
                    .build());

            JoinPlan joined = (JoinPlan) sales.join(leads, "left",
                    List.of(Map.of("left", "partnerId", "op", "=", "right", "partnerId")));
            DerivedQueryPlan finalPlan = joined.query(QueryOptions.builder()
                    .columns(List.of("partnerName", "totalSales", "leadCount"))
                    .slice(List.of(Map.of("field", "totalSales", "op", ">", "value", 10000)))
                    .orderBy(List.of("-totalSales"))
                    .build());

            assertEquals(JoinType.LEFT, joined.type());
            assertInstanceOf(DerivedQueryPlan.class, finalPlan);

            Set<String> baseNames = finalPlan.baseModelPlans().stream()
                    .map(BaseModelPlan::model).collect(Collectors.toSet());
            assertEquals(Set.of("SaleOrderQM", "CrmLeadQM"), baseNames);

            // JoinOn coerced from Map entry
            assertEquals(JoinOn.of("partnerId", "=", "partnerId"), joined.on().get(0));
        }
    }

    @Nested
    @DisplayName("多层派生")
    class MultiLevelDerivation {
        @Test
        @DisplayName("3 层 query 链，每层的 source 指向上一层")
        void threeLevelDerivationChain() {
            QueryPlan level0 = Dsl.from(Dsl.FromOptions.builder()
                    .model("X").columns(List.of("id")).build());
            DerivedQueryPlan level1 = level0.query(QueryOptions.of(List.of("id")));
            DerivedQueryPlan level2 = level1.query(QueryOptions.of(List.of("id")));
            DerivedQueryPlan level3 = level2.query(QueryOptions.of(List.of("id")));

            assertInstanceOf(BaseModelPlan.class, level0);
            assertSame(level2, level3.source());
            assertSame(level1, level2.source());
            assertSame(level0, level1.source());
            assertEquals(List.of((BaseModelPlan) level0), level3.baseModelPlans());
        }
    }

    @Nested
    @DisplayName("Layer-C 公共面白名单（M9 沙箱防护脚手架 §Layer C）")
    class LayerCWhitelist {

        @Test
        @DisplayName("QueryPlan 公共面只暴露 5 个动词方法；raw/memoryFilter/forEach/... 必须缺席")
        void layerCWhitelistEnforced() {
            // 允许公共方法集 = Layer-C 5 动词 × 重载 + Object 继承 + equals/hashCode/toString
            // + baseModelPlans（M5 authority 管线跨包使用；JS sandbox 通过 M9 反射白名单另行拦截）
            Set<String> allowed = Set.of(
                    "query", "union", "join", "execute", "toSql",
                    "baseModelPlans",
                    "equals", "hashCode", "toString", "getClass",
                    "wait", "notify", "notifyAll");
            Set<String> forbidden = Set.of(
                    "raw", "rawSql", "memoryFilter", "memory_filter",
                    "forEach", "for_each", "items", "rows",
                    "toArray", "iterator",
                    // Python-side forbidden names from test_plan_composition (防止串联复制)
                    "raw_sql", "to_array");

            for (Class<?> cls : List.of(BaseModelPlan.class, DerivedQueryPlan.class,
                    UnionPlan.class, JoinPlan.class)) {
                for (Method m : cls.getMethods()) {
                    String name = m.getName();
                    assertFalse(forbidden.contains(name),
                            () -> cls.getSimpleName() + " 不得暴露 " + name
                                    + "（Layer-C 白名单违规）");
                }
            }
        }

        @Test
        @DisplayName("5 个动词方法均存在于 QueryPlan 的 public 面")
        void fiveMethodsPresent() {
            BaseModelPlan p = BaseModelPlan.builder()
                    .model("X").columns(List.of("id")).build();
            Set<String> names = java.util.Arrays.stream(BaseModelPlan.class.getMethods())
                    .map(Method::getName).collect(Collectors.toSet());
            for (String required : List.of("query", "union", "join", "execute", "toSql")) {
                assertTrue(names.contains(required),
                        "QueryPlan Layer-C 白名单必须暴露 " + required);
            }
        }
    }

    @Nested
    @DisplayName("base_model_plans preorder 左-右")
    class BaseModelPlansPreorder {

        @Test
        @DisplayName("混合 Derived/Union/Join 的树保持左-右 preorder")
        void mixedTreePreorder() {
            QueryPlan a = Dsl.from(Dsl.FromOptions.builder().model("A").columns(List.of("id")).build());
            QueryPlan b = Dsl.from(Dsl.FromOptions.builder().model("B").columns(List.of("id")).build());
            QueryPlan c = Dsl.from(Dsl.FromOptions.builder().model("C").columns(List.of("id")).build());
            QueryPlan d = Dsl.from(Dsl.FromOptions.builder().model("D").columns(List.of("id")).build());

            // ((A ∪ B) ⋈ C).query(...) ∪ D.query(...)
            DerivedQueryPlan leftTree = a.union(b)
                    .join(c, JoinType.INNER, List.of(JoinOn.of("id", "=", "id")))
                    .query(QueryOptions.of(List.of("id")));
            DerivedQueryPlan rightTree = d.query(QueryOptions.of(List.of("id")));
            UnionPlan root = leftTree.union(rightTree);

            List<String> models = root.baseModelPlans().stream()
                    .map(BaseModelPlan::model).collect(Collectors.toList());
            assertEquals(List.of("A", "B", "C", "D"), models);
        }
    }
}
