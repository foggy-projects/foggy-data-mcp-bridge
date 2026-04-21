package com.foggyframework.dataset.db.model.engine.compose.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M4 · ColumnSpec + OutputSchema invariants.
 *
 * <p>Mirrors Python {@code tests/compose/schema/test_output_schema.py}.</p>
 */
@DisplayName("M4 OutputSchema + ColumnSpec")
class OutputSchemaTest {

    @Nested
    @DisplayName("ColumnSpec 不变量")
    class ColumnSpecInvariants {

        @Test
        @DisplayName("最小构造")
        void minimalValidConstruction() {
            ColumnSpec c = ColumnSpec.of("orderId", "orderId");
            assertEquals("orderId", c.name());
            assertEquals("orderId", c.expression());
            assertNull(c.sourceModel());
            assertNull(c.dataType());
            assertFalse(c.hasExplicitAlias());
        }

        @Test
        @DisplayName("name 必填非空")
        void nameRequiredNonEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> ColumnSpec.builder().name("").expression("x").build());
            assertThrows(IllegalArgumentException.class,
                    () -> ColumnSpec.builder().name(null).expression("x").build());
        }

        @Test
        @DisplayName("expression 必填非空")
        void expressionRequiredNonEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> ColumnSpec.builder().name("x").expression("").build());
            assertThrows(IllegalArgumentException.class,
                    () -> ColumnSpec.builder().name("x").expression(null).build());
        }

        @Test
        @DisplayName("值相等 + hash 一致")
        void valueEquality() {
            ColumnSpec a = ColumnSpec.builder().name("x").expression("x").sourceModel("M").build();
            ColumnSpec b = ColumnSpec.builder().name("x").expression("x").sourceModel("M").build();
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }

    @Nested
    @DisplayName("OutputSchema 构造")
    class OutputSchemaConstruction {

        @Test
        @DisplayName("空 schema 合法")
        void emptySchemaIsLegal() {
            OutputSchema s = OutputSchema.empty();
            assertEquals(0, s.size());
            assertTrue(s.isEmpty());
            assertEquals(List.of(), s.names());
        }

        @Test
        @DisplayName("of(list) 构造正常")
        void ofList() {
            OutputSchema s = OutputSchema.of(List.of(
                    ColumnSpec.of("a", "a"),
                    ColumnSpec.of("b", "b")
            ));
            List<String> names = new ArrayList<>();
            for (ColumnSpec c : s.columns()) names.add(c.name());
            assertEquals(List.of("a", "b"), names);
        }

        @Test
        @DisplayName("重复 output name 被拒绝")
        void duplicateOutputNamesRejected() {
            assertThrows(IllegalArgumentException.class, () ->
                    OutputSchema.of(List.of(
                            ColumnSpec.of("x", "a"),
                            ColumnSpec.of("x", "b")
                    )));
        }

        @Test
        @DisplayName("null 元素被拒绝")
        void nullElementRejected() {
            List<ColumnSpec> withNull = Arrays.asList(ColumnSpec.of("x", "x"), null);
            assertThrows(IllegalArgumentException.class,
                    () -> OutputSchema.of(withNull));
        }
    }

    @Nested
    @DisplayName("OutputSchema 访问器")
    class Accessors {

        private OutputSchema three() {
            return OutputSchema.of(List.of(
                    ColumnSpec.of("id", "id"),
                    ColumnSpec.of("name", "name"),
                    ColumnSpec.builder().name("total")
                            .expression("SUM(amount)")
                            .hasExplicitAlias(true).build()
            ));
        }

        @Test
        @DisplayName("names() 返回有序列表")
        void namesOrdered() {
            assertEquals(List.of("id", "name", "total"), three().names());
        }

        @Test
        @DisplayName("nameSet() 包含所有 name")
        void nameSetContainsAll() {
            Set<String> set = three().nameSet();
            assertTrue(set.contains("id"));
            assertTrue(set.contains("name"));
            assertTrue(set.contains("total"));
            assertEquals(3, set.size());
        }

        @Test
        @DisplayName("contains / get 命中与未命中")
        void containsAndGet() {
            OutputSchema s = three();
            assertTrue(s.contains("id"));
            assertFalse(s.contains("missing"));
            assertEquals("SUM(amount)", s.get("total").expression());
            assertNull(s.get("missing"));
        }

        @Test
        @DisplayName("indexOf 命中与未命中")
        void indexOfHitsAndMisses() {
            OutputSchema s = three();
            assertEquals(0, s.indexOf("id"));
            assertEquals(2, s.indexOf("total"));
            assertThrows(NoSuchElementException.class, () -> s.indexOf("missing"));
        }

        @Test
        @DisplayName("columns() 返回不可变视图")
        void columnsUnmodifiable() {
            OutputSchema s = three();
            assertThrows(UnsupportedOperationException.class,
                    () -> s.columns().add(ColumnSpec.of("z", "z")));
        }
    }

    @Nested
    @DisplayName("不可变性 / 值语义")
    class Immutability {

        @Test
        @DisplayName("构造后 defensively copy：修改外部 list 不影响 schema")
        void defensiveCopyOnConstruct() {
            List<ColumnSpec> src = new ArrayList<>();
            src.add(ColumnSpec.of("x", "x"));
            OutputSchema s = OutputSchema.of(src);
            src.add(ColumnSpec.of("y", "y"));  // 外部修改
            assertEquals(1, s.size());
            assertFalse(s.contains("y"));
        }

        @Test
        @DisplayName("等值 + hash 一致")
        void valueEqualityAndHash() {
            OutputSchema a = OutputSchema.of(List.of(ColumnSpec.of("x", "x")));
            OutputSchema b = OutputSchema.of(List.of(ColumnSpec.of("x", "x")));
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("OutputSchema 只暴露 read-only 访问器，禁止暴露 mutator")
        void outputSchemaReadOnlySurface() {
            Set<String> forbidden = Set.of(
                    "setColumns", "addColumn", "remove", "add", "clear",
                    "removeIf", "put"
            );
            for (Method m : OutputSchema.class.getMethods()) {
                assertFalse(forbidden.contains(m.getName()),
                        () -> "OutputSchema 不得暴露 " + m.getName());
            }
        }
    }
}
