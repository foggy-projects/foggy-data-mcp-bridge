package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinType;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.UnionPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PlanHash} · canonical / planHash / planDepth / MAX_PLAN_DEPTH 边界。
 */
class PlanHashTest {

    // ------------------------------------------------------------------
    // canonical()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("canonical(String) 原样返回")
    void canonicalString() {
        assertEquals("abc", PlanHash.canonical("abc"));
    }

    @Test
    @DisplayName("canonical(Integer) 原样返回")
    void canonicalInteger() {
        assertEquals(42, PlanHash.canonical(42));
    }

    @Test
    @DisplayName("canonical(null) 原样返回 null")
    void canonicalNull() {
        assertEquals(null, PlanHash.canonical(null));
    }

    @Test
    @DisplayName("canonical(List) 保持顺序，元素递归规整")
    void canonicalListPreservesOrder() {
        List<?> canonical = (List<?>) PlanHash.canonical(List.of("a", "b", "c"));
        assertEquals(List.of("a", "b", "c"), canonical);
    }

    @Test
    @DisplayName("canonical(Map) 按 key 排序 → [[k, v], ...]")
    void canonicalMapSortsKeys() {
        Map<String, Object> unsorted = new LinkedHashMap<>();
        unsorted.put("b", 2);
        unsorted.put("a", 1);
        Object sorted = PlanHash.canonical(unsorted);

        Map<String, Object> expectedInOrder = new LinkedHashMap<>();
        expectedInOrder.put("a", 1);
        expectedInOrder.put("b", 2);
        Object expected = PlanHash.canonical(expectedInOrder);

        assertEquals(expected, sorted, "map canonicalisation must be key-order independent");
    }

    @Test
    @DisplayName("canonical(nested map) 递归规整 → {a: [b: 1]} 与 {a: [b: 1]} 相等")
    void canonicalNestedMapEquality() {
        Map<String, Object> outer1 = Map.of("a", Map.of("b", 1));
        Map<String, Object> outer2 = Map.of("a", Map.of("b", 1));
        assertEquals(PlanHash.canonical(outer1), PlanHash.canonical(outer2));
    }

    @Test
    @DisplayName("canonical(List<Map>) 内层 Map 递归")
    void canonicalListOfMaps() {
        List<Map<String, Object>> list = List.of(Map.of("b", 2, "a", 1));
        List<Map<String, Object>> listReordered = List.of(Map.of("a", 1, "b", 2));
        assertEquals(PlanHash.canonical(list), PlanHash.canonical(listReordered));
    }

    @Test
    @DisplayName("canonical 对同一 List 返回的结果相等")
    void canonicalIsDeterministic() {
        List<String> list = List.of("x", "y");
        assertEquals(PlanHash.canonical(list), PlanHash.canonical(list));
    }

    // ------------------------------------------------------------------
    // planHash()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("BaseModelPlan → discriminator 'base' 位于 tuple 首位")
    void planHashBaseDiscriminatorPosition() {
        BaseModelPlan p = BaseModelPlan.builder()
                .model("M")
                .columns(List.of("id"))
                .build();
        List<Object> hash = PlanHash.planHash(p);
        assertEquals("base", hash.get(0));
        assertEquals("M", hash.get(1));
    }

    @Test
    @DisplayName("DerivedQueryPlan → discriminator 'derived'")
    void planHashDerivedDiscriminator() {
        BaseModelPlan base = BaseModelPlan.builder()
                .model("M").columns(List.of("id")).build();
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(base).columns(List.of("id")).build();
        assertEquals("derived", PlanHash.planHash(derived).get(0));
    }

    @Test
    @DisplayName("UnionPlan → discriminator 'union'")
    void planHashUnionDiscriminator() {
        BaseModelPlan base = BaseModelPlan.builder().model("M").columns(List.of("id")).build();
        UnionPlan u = base.union(base);
        assertEquals("union", PlanHash.planHash(u).get(0));
    }

    @Test
    @DisplayName("JoinPlan → discriminator 'join' + type token 位于第 2 位")
    void planHashJoinDiscriminator() {
        BaseModelPlan base = BaseModelPlan.builder().model("M").columns(List.of("id")).build();
        JoinPlan j = base.join(base, JoinType.LEFT, List.of(JoinOn.of("id", "=", "id")));
        List<Object> hash = PlanHash.planHash(j);
        assertEquals("join", hash.get(0));
        assertEquals("left", hash.get(1));
    }

    @Test
    @DisplayName("结构性等价 BaseModelPlan 的 planHash 相等")
    void structuralEquivalencePlansHashEqual() {
        BaseModelPlan a = BaseModelPlan.builder().model("M").columns(List.of("id")).build();
        BaseModelPlan b = BaseModelPlan.builder().model("M").columns(List.of("id")).build();
        assertEquals(PlanHash.planHash(a), PlanHash.planHash(b));
    }

    @Test
    @DisplayName("不同 model 的 BaseModelPlan planHash 不等")
    void differentModelDifferentHash() {
        BaseModelPlan a = BaseModelPlan.builder().model("A").columns(List.of("id")).build();
        BaseModelPlan b = BaseModelPlan.builder().model("B").columns(List.of("id")).build();
        assertNotEquals(PlanHash.planHash(a), PlanHash.planHash(b));
    }

    @Test
    @DisplayName("columns 顺序差异反映到 hash")
    void differentColumnOrderDifferentHash() {
        BaseModelPlan a = BaseModelPlan.builder().model("M").columns(List.of("a", "b")).build();
        BaseModelPlan b = BaseModelPlan.builder().model("M").columns(List.of("b", "a")).build();
        assertNotEquals(PlanHash.planHash(a), PlanHash.planHash(b));
    }

    @Test
    @DisplayName("distinct 标志反映到 hash")
    void distinctFlagAffectsHash() {
        BaseModelPlan a = BaseModelPlan.builder().model("M").columns(List.of("id")).distinct(false).build();
        BaseModelPlan b = BaseModelPlan.builder().model("M").columns(List.of("id")).distinct(true).build();
        assertNotEquals(PlanHash.planHash(a), PlanHash.planHash(b));
    }

    @Test
    @DisplayName("两个 model 相同但 slice 值不同的 BaseModelPlan hash 不等")
    void differentSliceDifferentHash() {
        BaseModelPlan a = BaseModelPlan.builder().model("M").columns(List.of("id"))
                .slice(List.of(Map.of("field", "x", "op", "=", "value", 1))).build();
        BaseModelPlan b = BaseModelPlan.builder().model("M").columns(List.of("id"))
                .slice(List.of(Map.of("field", "x", "op", "=", "value", 2))).build();
        assertNotEquals(PlanHash.planHash(a), PlanHash.planHash(b));
    }

    @Test
    @DisplayName("UnionPlan(all=true) vs all=false hash 不等")
    void unionAllFlagAffectsHash() {
        BaseModelPlan base = BaseModelPlan.builder().model("M").columns(List.of("id")).build();
        UnionPlan u1 = base.union(base, false);
        UnionPlan u2 = base.union(base, true);
        assertNotEquals(PlanHash.planHash(u1), PlanHash.planHash(u2));
    }

    @Test
    @DisplayName("JoinPlan 不同 on 条件 hash 不等")
    void joinDifferentOnDifferentHash() {
        BaseModelPlan l = BaseModelPlan.builder().model("L").columns(List.of("id")).build();
        BaseModelPlan r = BaseModelPlan.builder().model("R").columns(List.of("id")).build();
        JoinPlan j1 = l.join(r, JoinType.INNER, List.of(JoinOn.of("a", "=", "b")));
        JoinPlan j2 = l.join(r, JoinType.INNER, List.of(JoinOn.of("a", "=", "c")));
        assertNotEquals(PlanHash.planHash(j1), PlanHash.planHash(j2));
    }

    @Test
    @DisplayName("JoinPlan 不同 type hash 不等")
    void joinDifferentTypeDifferentHash() {
        BaseModelPlan l = BaseModelPlan.builder().model("L").columns(List.of("id")).build();
        BaseModelPlan r = BaseModelPlan.builder().model("R").columns(List.of("id")).build();
        JoinPlan inner = l.join(r, JoinType.INNER, List.of(JoinOn.of("id", "=", "id")));
        JoinPlan left = l.join(r, JoinType.LEFT, List.of(JoinOn.of("id", "=", "id")));
        assertNotEquals(PlanHash.planHash(inner), PlanHash.planHash(left));
    }

    @Test
    @DisplayName("递归嵌套 DerivedQueryPlan 的 hash 稳定")
    void derivedChainHashIsStable() {
        BaseModelPlan base = BaseModelPlan.builder().model("M").columns(List.of("id")).build();
        DerivedQueryPlan d1 = DerivedQueryPlan.builder().source(base).columns(List.of("id")).build();
        DerivedQueryPlan d2 = DerivedQueryPlan.builder().source(d1).columns(List.of("id")).build();

        // 构造等价副本
        BaseModelPlan base2 = BaseModelPlan.builder().model("M").columns(List.of("id")).build();
        DerivedQueryPlan e1 = DerivedQueryPlan.builder().source(base2).columns(List.of("id")).build();
        DerivedQueryPlan e2 = DerivedQueryPlan.builder().source(e1).columns(List.of("id")).build();

        assertEquals(PlanHash.planHash(d2), PlanHash.planHash(e2));
    }

    @Test
    @DisplayName("planHash(null) 抛 IllegalArgumentException")
    void planHashNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> PlanHash.planHash(null));
    }

    @Test
    @DisplayName("planHash 结果可作为 HashMap key")
    void planHashUsableAsMapKey() {
        BaseModelPlan a = BaseModelPlan.builder().model("M").columns(List.of("id")).build();
        BaseModelPlan b = BaseModelPlan.builder().model("M").columns(List.of("id")).build();
        Map<List<Object>, String> map = new HashMap<>();
        map.put(PlanHash.planHash(a), "first");
        assertEquals("first", map.get(PlanHash.planHash(b)));
    }

    // ------------------------------------------------------------------
    // planDepth()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("planDepth(BaseModelPlan) == 1")
    void baseDepth() {
        assertEquals(1, PlanHash.planDepth(
                BaseModelPlan.builder().model("M").columns(List.of("id")).build()));
    }

    @Test
    @DisplayName("planDepth(DerivedQueryPlan over base) == 2")
    void derivedOverBaseDepth() {
        BaseModelPlan base = BaseModelPlan.builder().model("M").columns(List.of("id")).build();
        DerivedQueryPlan d = DerivedQueryPlan.builder().source(base).columns(List.of("id")).build();
        assertEquals(2, PlanHash.planDepth(d));
    }

    @Test
    @DisplayName("planDepth(UnionPlan of two bases) == 2")
    void unionOfBasesDepth() {
        BaseModelPlan a = BaseModelPlan.builder().model("M").columns(List.of("id")).build();
        BaseModelPlan b = BaseModelPlan.builder().model("N").columns(List.of("id")).build();
        assertEquals(2, PlanHash.planDepth(a.union(b)));
    }

    @Test
    @DisplayName("planDepth(DerivedQueryPlan over Union) == 3")
    void derivedOverUnionDepth() {
        BaseModelPlan a = BaseModelPlan.builder().model("M").columns(List.of("id")).build();
        BaseModelPlan b = BaseModelPlan.builder().model("N").columns(List.of("id")).build();
        UnionPlan u = a.union(b);
        DerivedQueryPlan d = DerivedQueryPlan.builder().source(u).columns(List.of("id")).build();
        assertEquals(3, PlanHash.planDepth(d));
    }

    @Test
    @DisplayName("planDepth 深度 32 不抛")
    void depth32Allowed() {
        QueryPlan root = BaseModelPlan.builder().model("M").columns(List.of("id")).build();
        for (int i = 1; i < PlanHash.MAX_PLAN_DEPTH; i++) {
            root = DerivedQueryPlan.builder().source(root).columns(List.of("id")).build();
        }
        assertEquals(PlanHash.MAX_PLAN_DEPTH, PlanHash.planDepth(root));
    }

    @Test
    @DisplayName("planDepth(null) 抛 IllegalArgumentException")
    void planDepthNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> PlanHash.planDepth(null));
    }

    // ------------------------------------------------------------------
    // MAX_PLAN_DEPTH 常量
    // ------------------------------------------------------------------

    @Test
    @DisplayName("MAX_PLAN_DEPTH 字面量等于 32")
    void maxPlanDepthIs32() {
        assertEquals(32, PlanHash.MAX_PLAN_DEPTH);
    }

    @Test
    @DisplayName("NULL_SENTINEL 同一实例（用于作为 List.of 中 null 的替身）")
    void nullSentinelIsStable() {
        // canonical(List with null) 内部用 NULL_SENTINEL 替换；两次 canonical 返回值含相同 sentinel
        List<Object> listWithNull = new java.util.ArrayList<>();
        listWithNull.add(null);
        List<?> c1 = (List<?>) PlanHash.canonical(listWithNull);
        List<?> c2 = (List<?>) PlanHash.canonical(listWithNull);
        assertEquals(c1, c2);
        assertSame(c1.get(0), c2.get(0));
    }

}
