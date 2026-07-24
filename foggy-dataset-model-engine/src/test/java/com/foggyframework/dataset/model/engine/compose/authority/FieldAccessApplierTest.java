package com.foggyframework.dataset.model.engine.compose.authority;

import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.compose.schema.ColumnSpec;
import com.foggyframework.dataset.model.engine.compose.schema.OutputSchema;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M5 {@link FieldAccessApplier#apply} — OutputSchema filter by
 * {@link ModelBinding#fieldAccess()} whitelist.
 *
 * <p>Mirrors Python {@code tests/compose/authority/test_apply_field_access.py}.</p>
 */
@DisplayName("M5 FieldAccessApplier")
class FieldAccessApplierTest {

    private static OutputSchema schemaABC() {
        return OutputSchema.of(List.of(
                ColumnSpec.of("a", "a"),
                ColumnSpec.of("b", "b"),
                ColumnSpec.of("c", "c")
        ));
    }

    // ------------------------------------------------------------------
    // field_access = null → no-op
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("fieldAccess == null → no-op（同引用返回）")
    class FieldAccessNull {

        @Test
        @DisplayName("默认 binding 的 fieldAccess 为 null，返回原 schema（同引用）")
        void returnsInputUnchanged() {
            OutputSchema schema = schemaABC();
            ModelBinding binding = ModelBinding.builder().build();
            OutputSchema result = FieldAccessApplier.apply(schema, binding);
            assertSame(schema, result);
        }

        @Test
        @DisplayName("显式 fieldAccess(null) 等价于默认")
        void explicitNullSameAsDefault() {
            OutputSchema schema = schemaABC();
            ModelBinding binding = ModelBinding.builder().fieldAccess(null).build();
            OutputSchema result = FieldAccessApplier.apply(schema, binding);
            assertSame(schema, result);
        }
    }

    // ------------------------------------------------------------------
    // field_access = [] → empty schema
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("fieldAccess == []  → 空 schema")
    class FieldAccessEmpty {

        @Test
        @DisplayName("空 whitelist 剥离所有列")
        void emptyListStripsAllColumns() {
            OutputSchema schema = schemaABC();
            ModelBinding binding = ModelBinding.builder().fieldAccess(List.of()).build();
            OutputSchema result = FieldAccessApplier.apply(schema, binding);
            assertEquals(0, result.size());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空 list 与 null 语义不同：不是同引用")
        void emptyListIsNotIdentity() {
            OutputSchema schema = schemaABC();
            ModelBinding binding = ModelBinding.builder().fieldAccess(List.of()).build();
            OutputSchema result = FieldAccessApplier.apply(schema, binding);
            assertNotSame(schema, result);
        }
    }

    // ------------------------------------------------------------------
    // whitelist filtering
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("whitelist 过滤")
    class Whitelist {

        @Test
        @DisplayName("只保留 whitelist 中的列")
        void keepsOnlyMatchingColumns() {
            OutputSchema result = FieldAccessApplier.apply(schemaABC(),
                    ModelBinding.builder().fieldAccess(List.of("a", "c")).build());
            assertEquals(List.of("a", "c"), result.names());
        }

        @Test
        @DisplayName("输出保持 schema 原顺序（不跟随 whitelist 顺序）")
        void preservesOriginalOrder() {
            OutputSchema result = FieldAccessApplier.apply(schemaABC(),
                    ModelBinding.builder().fieldAccess(List.of("c", "a")).build());
            assertEquals(List.of("a", "c"), result.names());
        }

        @Test
        @DisplayName("whitelist 中 schema 里不存在的名字被静默忽略")
        void unknownNamesSilentlyIgnored() {
            OutputSchema result = FieldAccessApplier.apply(schemaABC(),
                    ModelBinding.builder().fieldAccess(List.of("a", "phantom")).build());
            assertEquals(List.of("a"), result.names());
        }

        @Test
        @DisplayName("whitelist 中的重复条目无害")
        void duplicateWhitelistEntriesHarmless() {
            OutputSchema result = FieldAccessApplier.apply(schemaABC(),
                    ModelBinding.builder().fieldAccess(List.of("a", "a", "b")).build());
            assertEquals(List.of("a", "b"), result.names());
        }

        @Test
        @DisplayName("ColumnSpec 的所有字段被完整保留（expression / sourceModel / hasExplicitAlias）")
        void columnSpecCarriedOverIntact() {
            OutputSchema schema = OutputSchema.of(List.of(
                    ColumnSpec.builder()
                            .name("total")
                            .expression("SUM(amount) AS total")
                            .sourceModel("SaleOrderQM")
                            .hasExplicitAlias(true)
                            .build()));
            ModelBinding binding = ModelBinding.builder().fieldAccess(List.of("total")).build();
            OutputSchema result = FieldAccessApplier.apply(schema, binding);
            assertEquals(1, result.size());
            ColumnSpec kept = result.get("total");
            assertNotNull(kept);
            assertEquals("SUM(amount) AS total", kept.expression());
            assertEquals("SaleOrderQM", kept.sourceModel());
            assertTrue(kept.hasExplicitAlias());
        }
    }

    // ------------------------------------------------------------------
    // doesn't interact with denied_columns / system_slice
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("不受 deniedColumns / systemSlice 影响")
    class IndependentOfOtherBindingFields {

        @Test
        @DisplayName("deniedColumns 在 M5 被忽略（留给 M6）")
        void deniedColumnsIgnored() {
            DeniedPhysicalColumn denied = new DeniedPhysicalColumn(null, "sale_order", "a_phys");
            ModelBinding binding = ModelBinding.builder()
                    .fieldAccess(List.of("a", "b"))
                    .deniedColumns(List.of(denied))
                    .build();
            OutputSchema result = FieldAccessApplier.apply(schemaABC(), binding);
            assertEquals(List.of("a", "b"), result.names());
        }

        @Test
        @DisplayName("systemSlice 在 M5 被忽略（留给 M6）")
        void systemSliceIgnored() {
            SliceRequestDef slice = new SliceRequestDef("user_id", "=", 1);
            ModelBinding binding = ModelBinding.builder()
                    .fieldAccess(List.of("b"))
                    .systemSlice(List.of(slice))
                    .build();
            OutputSchema result = FieldAccessApplier.apply(schemaABC(), binding);
            assertEquals(List.of("b"), result.names());
        }
    }

    // ------------------------------------------------------------------
    // fail-closed on bad inputs
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Fail-closed 输入校验")
    class BadInputs {

        @Test
        @DisplayName("schema == null 抛 IllegalArgumentException")
        void schemaNullRejected() {
            ModelBinding binding = ModelBinding.builder().fieldAccess(List.of("a")).build();
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> FieldAccessApplier.apply(null, binding));
            assertTrue(ex.getMessage().contains("schema"));
        }

        @Test
        @DisplayName("binding == null 抛 IllegalArgumentException")
        void bindingNullRejected() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> FieldAccessApplier.apply(schemaABC(), null));
            assertTrue(ex.getMessage().contains("binding"));
        }
    }

    // ------------------------------------------------------------------
    // empty input schema
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("空输入 schema")
    class EmptyInputSchema {

        @Test
        @DisplayName("空 schema + whitelist → 仍为空")
        void emptySchemaWithWhitelistStaysEmpty() {
            OutputSchema empty = OutputSchema.empty();
            OutputSchema result = FieldAccessApplier.apply(empty,
                    ModelBinding.builder().fieldAccess(List.of("a", "b")).build());
            assertEquals(0, result.size());
        }

        @Test
        @DisplayName("空 schema + null whitelist → 同引用返回")
        void emptySchemaWithNullAccessStaysEmpty() {
            OutputSchema empty = OutputSchema.empty();
            OutputSchema result = FieldAccessApplier.apply(empty,
                    ModelBinding.builder().build());
            assertSame(empty, result);
        }
    }
}
