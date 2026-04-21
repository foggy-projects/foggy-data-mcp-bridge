package com.foggyframework.dataset.db.model.engine.compose.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M2 DerivedQueryPlan 不变量 — 跨仓对齐 Python test_derived_query_plan.py。
 */
@DisplayName("M2 DerivedQueryPlan")
class DerivedQueryPlanTest {

    private BaseModelPlan base() {
        return BaseModelPlan.builder()
                .model("SaleOrderQM")
                .columns(List.of("id", "name"))
                .build();
    }

    @Nested
    @DisplayName("构造")
    class Construction {

        @Test
        @DisplayName("最小构造")
        void minimalValidConstruction() {
            BaseModelPlan src = base();
            DerivedQueryPlan d = DerivedQueryPlan.builder()
                    .source(src).columns(List.of("id")).build();
            assertSame(src, d.source());
            assertEquals(List.of("id"), d.columns());
        }

        @Test
        @DisplayName("source 必须是 QueryPlan")
        void sourceMustBeQueryPlan() {
            assertThrows(IllegalArgumentException.class,
                    () -> DerivedQueryPlan.builder()
                            .source(null).columns(List.of("id")).build());
        }

        @Test
        @DisplayName("columns 必填且非空")
        void columnsRequiredNonEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> DerivedQueryPlan.builder()
                            .source(base()).columns(List.of()).build());
        }

        @Test
        @DisplayName("limit 非负")
        void paginationValidated() {
            assertThrows(IllegalArgumentException.class,
                    () -> DerivedQueryPlan.builder()
                            .source(base()).columns(List.of("id")).limit(-1).build());
        }
    }

    @Nested
    @DisplayName("不可变性")
    class Immutability {
        @Test
        @DisplayName("columns 返回不可变副本")
        void columnsUnmodifiable() {
            DerivedQueryPlan d = DerivedQueryPlan.builder()
                    .source(base()).columns(List.of("id")).build();
            assertThrows(UnsupportedOperationException.class,
                    () -> d.columns().add("x"));
        }
    }

    @Nested
    @DisplayName("base_model_plans 树遍历")
    class TreeWalk {
        @Test
        @DisplayName("委派给 source")
        void baseModelPlansProxiesToSource() {
            BaseModelPlan src = base();
            DerivedQueryPlan d = DerivedQueryPlan.builder()
                    .source(src).columns(List.of("id")).build();
            assertEquals(List.of(src), d.baseModelPlans());
        }
    }

    @Nested
    @DisplayName("plan.query(...) 链式糖")
    class ChainQuerySugar {

        @Test
        @DisplayName("plan.query(...) 返回 DerivedQueryPlan")
        void planQueryReturnsDerived() {
            BaseModelPlan src = base();
            DerivedQueryPlan d = src.query(QueryOptions.of(List.of("id")));
            assertInstanceOf(DerivedQueryPlan.class, d);
            assertSame(src, d.source());
            assertEquals(List.of("id"), d.columns());
        }

        @Test
        @DisplayName("plan.query(...) 透传可选字段")
        void planQueryPropagatesOptionalFields() {
            BaseModelPlan src = base();
            DerivedQueryPlan d = src.query(QueryOptions.builder()
                    .columns(List.of("id", "name"))
                    .slice(List.of(Map.of("field", "id", "op", "=", "value", 1)))
                    .groupBy(List.of("id"))
                    .orderBy(List.of("id"))
                    .limit(10)
                    .start(0)
                    .distinct(true)
                    .build());
            assertEquals(List.of("id", "name"), d.columns());
            assertEquals(List.of(Map.of("field", "id", "op", "=", "value", 1)), d.slice());
            assertEquals(List.of("id"), d.groupBy());
            assertEquals(List.of("id"), d.orderBy());
            assertEquals(10, d.limit());
            assertEquals(0, d.start());
            assertTrue(d.distinct());
        }
    }

    @Nested
    @DisplayName("execute / toSql 占位")
    class ExecuteToSqlDeferred {
        @Test
        @DisplayName("execute 抛 UnsupportedInM2Exception")
        void executeRaises() {
            DerivedQueryPlan d = DerivedQueryPlan.builder()
                    .source(base()).columns(List.of("id")).build();
            assertThrows(UnsupportedInM2Exception.class, d::execute);
        }

        @Test
        @DisplayName("toSql 抛 UnsupportedInM2Exception")
        void toSqlRaises() {
            DerivedQueryPlan d = DerivedQueryPlan.builder()
                    .source(base()).columns(List.of("id")).build();
            assertThrows(UnsupportedInM2Exception.class, d::toSql);
        }
    }

    @Nested
    @DisplayName("QueryPlan 多态")
    class IsInstance {
        @Test
        @DisplayName("DerivedQueryPlan 是 QueryPlan 的子类")
        void derivedIsQueryPlan() {
            DerivedQueryPlan d = DerivedQueryPlan.builder()
                    .source(base()).columns(List.of("id")).build();
            assertTrue(d instanceof QueryPlan);
        }
    }
}
