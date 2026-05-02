package com.foggyframework.dataset.db.model.engine.compose.plan;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M2 Dsl.from(...) 入口 — model/source 互斥 + shape 校验。
 * 跨仓对齐 Python test_from_entry.py。
 */
@DisplayName("M2 Dsl.from(...)")
class FromEntryTest {

    @Nested
    @DisplayName("model/source 互斥")
    class ModelSourceMutualExclusion {

        @Test
        @DisplayName("仅 model 构造 BaseModelPlan")
        void modelOnlyBuildsBaseModelPlan() {
            QueryPlan p = Dsl.from(Dsl.FromOptions.builder()
                    .model("SaleOrderQM")
                    .columns(List.of("id", "name"))
                    .build());
            assertInstanceOf(BaseModelPlan.class, p);
            BaseModelPlan b = (BaseModelPlan) p;
            assertEquals("SaleOrderQM", b.model());
            assertEquals(List.of("id", "name"), b.columns());
        }

        @Test
        @DisplayName("仅 source 构造 DerivedQueryPlan")
        void sourceOnlyBuildsDerivedQueryPlan() {
            QueryPlan base = Dsl.from(Dsl.FromOptions.builder()
                    .model("X").columns(List.of("id")).build());
            QueryPlan derived = Dsl.from(Dsl.FromOptions.builder()
                    .source(base).columns(List.of("id")).build());
            assertInstanceOf(DerivedQueryPlan.class, derived);
            assertSame(base, ((DerivedQueryPlan) derived).source());
        }

        @Test
        @DisplayName("同时 model 和 source 被拒绝")
        void bothModelAndSourceRejected() {
            QueryPlan base = Dsl.from(Dsl.FromOptions.builder()
                    .model("X").columns(List.of("id")).build());
            assertThrows(IllegalArgumentException.class,
                    () -> Dsl.from(Dsl.FromOptions.builder()
                            .model("Y").source(base).columns(List.of("id")).build()));
        }

        @Test
        @DisplayName("两者都不提供被拒绝")
        void neitherRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> Dsl.from(Dsl.FromOptions.builder()
                            .columns(List.of("id")).build()));
        }
    }

    @Nested
    @DisplayName("shape 校验")
    class ShapeValidation {

        @Test
        @DisplayName("opts 不能是 null")
        void optsNotNull() {
            assertThrows(IllegalArgumentException.class, () -> Dsl.from(null));
        }

        @Test
        @DisplayName("columns 必填")
        void columnsRequired() {
            assertThrows(IllegalArgumentException.class,
                    () -> Dsl.from(Dsl.FromOptions.builder().model("X").build()));
        }

        @Test
        @DisplayName("columns 不能是 null")
        void columnsMustNotBeNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> Dsl.from(Dsl.FromOptions.builder()
                            .model("X").columns(null).build()));
        }

        @Test
        @DisplayName("columns 不能是空列表")
        void columnsMustBeNonEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> Dsl.from(Dsl.FromOptions.builder()
                            .model("X").columns(List.of()).build()));
        }

        @Test
        @DisplayName("model 必须是非空字符串")
        void modelMustBeNonEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> Dsl.from(Dsl.FromOptions.builder()
                            .model("").columns(List.of("id")).build()));
        }

        @Test
        @DisplayName("pagination 非负")
        void paginationNegativeRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> Dsl.from(Dsl.FromOptions.builder()
                            .model("X").columns(List.of("id")).limit(-1).build()));
            assertThrows(IllegalArgumentException.class,
                    () -> Dsl.from(Dsl.FromOptions.builder()
                            .model("X").columns(List.of("id")).start(-1).build()));
        }
    }

    @Nested
    @DisplayName("可选字段透传")
    class OptionalFieldsPropagation {

        @Test
        @DisplayName("slice/groupBy/orderBy 全部透传")
        void sliceGroupByOrderByPropagated() {
            CalculatedFieldDef cf = new CalculatedFieldDef("grossAmount", "amount * 1.2");
            BaseModelPlan p = (BaseModelPlan) Dsl.from(Dsl.FromOptions.builder()
                    .model("SaleOrderQM")
                    .columns(List.of("id", "amount"))
                    .slice(List.of(Map.of("field", "state", "op", "=", "value", "paid")))
                    .groupBy(List.of("id"))
                    .orderBy(List.of("-amount"))
                    .calculatedFields(List.of(cf))
                    .limit(50)
                    .start(0)
                    .distinct(true)
                    .build());
            assertEquals(List.of(Map.of("field", "state", "op", "=", "value", "paid")),
                    p.slice());
            assertEquals(List.of("id"), p.groupBy());
            assertEquals(List.of("-amount"), p.orderBy());
            assertEquals(List.of(cf), p.calculatedFields());
            assertEquals(50, p.limit());
            assertEquals(0, p.start());
            assertTrue(p.distinct());
        }

        @Test
        @DisplayName("默认值：slice/groupBy/orderBy 为空，limit/start 为 null，distinct=false")
        void defaultsWhenOptionalOmitted() {
            BaseModelPlan p = (BaseModelPlan) Dsl.from(Dsl.FromOptions.builder()
                    .model("X").columns(List.of("id")).build());
            assertTrue(p.slice().isEmpty());
            assertTrue(p.groupBy().isEmpty());
            assertTrue(p.orderBy().isEmpty());
            assertNull(p.limit());
            assertNull(p.start());
            assertFalse(p.distinct());
        }
    }

    @Nested
    @DisplayName("内核等价：from(source=p, ...) == p.query(...)")
    class KernelEquivalence {

        @Test
        @DisplayName("两种写法生成结构相等的 DerivedQueryPlan")
        void fromSourceEqualsPlanQuery() {
            QueryPlan base = Dsl.from(Dsl.FromOptions.builder()
                    .model("X").columns(List.of("id", "name")).build());
            QueryPlan viaFrom = Dsl.from(Dsl.FromOptions.builder()
                    .source(base).columns(List.of("id")).limit(5).build());
            QueryPlan viaQuery = base.query(QueryOptions.builder()
                    .columns(List.of("id")).limit(5).build());
            assertEquals(viaFrom, viaQuery);
        }
    }
}
