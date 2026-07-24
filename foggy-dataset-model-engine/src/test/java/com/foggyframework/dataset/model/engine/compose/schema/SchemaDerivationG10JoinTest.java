package com.foggyframework.dataset.model.engine.compose.schema;

import com.foggyframework.dataset.model.engine.compose.ComposeFeatureFlags;
import com.foggyframework.dataset.model.engine.compose.plan.Dsl;
import com.foggyframework.dataset.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.model.engine.compose.plan.PlanId;
import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G10 PR2 · {@link SchemaDerivation#derive} on {@link JoinPlan} flag-gated
 * behaviour. Mirrors the legacy {@code SchemaDerivationTest$JoinSchema}
 * coverage but pins {@link ComposeFeatureFlags#g10Enabled()} explicitly so
 * each regime is verified in isolation.
 */
@DisplayName("G10 PR2 · SchemaDerivation.deriveJoin flag-gated")
class SchemaDerivationG10JoinTest {

    @AfterEach
    void clearOverride() {
        ComposeFeatureFlags.overrideG10Enabled(null);
    }

    private static QueryPlan baseOf(String model, List<String> columns) {
        return Dsl.from(Dsl.FromOptions.builder()
                .model(model).columns(columns).build());
    }

    private static JoinPlan partnerJoin() {
        QueryPlan left = baseOf("OrderQM",
                List.of("orderId", "name", "amount"));
        QueryPlan right = baseOf("CustomerQM",
                List.of("customerId", "name", "rating"));
        return left.join(right, "left",
                List.of(JoinOn.of("orderId", "=", "customerId")));
    }

    // ------------------------------------------------------------------
    // Legacy (flag=false) — JOIN_OUTPUT_COLUMN_CONFLICT 仍抛
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("flag=false：保留 M4 抛 JOIN_OUTPUT_COLUMN_CONFLICT")
    class FlagOffLegacy {

        @Test
        @DisplayName("两侧同名 → JOIN_OUTPUT_COLUMN_CONFLICT")
        void overlapStillThrows() {
            ComposeFeatureFlags.overrideG10Enabled(false);
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> SchemaDerivation.derive(partnerJoin()));
            assertEquals(ComposeSchemaErrorCodes.JOIN_OUTPUT_COLUMN_CONFLICT,
                    ex.code());
            assertEquals("name", ex.offendingField());
        }

        @Test
        @DisplayName("无重名 join 输出抹除 sourceModel（M4 行为）")
        void nonOverlapStripsSourceModel() {
            ComposeFeatureFlags.overrideG10Enabled(false);
            QueryPlan left = baseOf("SalesQM",
                    List.of("partnerId", "totalSales"));
            QueryPlan right = baseOf("LeadsQM",
                    List.of("partnerKey", "leadCount"));
            JoinPlan join = left.join(right, "left",
                    List.of(JoinOn.of("partnerId", "=", "partnerKey")));
            OutputSchema schema = SchemaDerivation.derive(join);
            for (ColumnSpec c : schema.columns()) {
                assertNull(c.sourceModel(),
                        "flag=false 时 join 应抹除 sourceModel，列=" + c.name());
                assertNull(c.planProvenance(),
                        "flag=false 时不应写 planProvenance，列=" + c.name());
                assertFalse(c.isAmbiguous(),
                        "flag=false 时不应写 isAmbiguous，列=" + c.name());
            }
        }
    }

    // ------------------------------------------------------------------
    // G10 (flag=true) — 同名列被标 isAmbiguous + 携带 planProvenance
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("flag=true：歧义列共存 + planProvenance 保留")
    class FlagOnG10 {

        @Test
        @DisplayName("两侧同名不抛 + 标 isAmbiguous=true")
        void overlapMarkedAmbiguousNotThrown() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            JoinPlan join = partnerJoin();
            OutputSchema schema = assertDoesNotThrow(
                    () -> SchemaDerivation.derive(join));
            // 5 columns: orderId / name / amount / customerId / name / rating
            // (no, actually orderId/name/amount + customerId/name/rating = 6)
            assertEquals(6, schema.size());
            assertTrue(schema.isAmbiguous("name"),
                    "重名列 'name' 必须被标为 ambiguous");
            assertFalse(schema.isAmbiguous("orderId"),
                    "唯一列 'orderId' 不应歧义");
            assertFalse(schema.isAmbiguous("customerId"),
                    "唯一列 'customerId' 不应歧义");

            List<ColumnSpec> bothNames = schema.getAll("name");
            assertEquals(2, bothNames.size());
            for (ColumnSpec c : bothNames) {
                assertTrue(c.isAmbiguous(), "每个歧义列必须自带 isAmbiguous=true");
                assertNotNull(c.planProvenance(),
                        "每个歧义列必须自带 planProvenance");
            }
        }

        @Test
        @DisplayName("planProvenance 区分两侧来源")
        void planProvenanceDistinguishesSides() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            JoinPlan join = partnerJoin();
            OutputSchema schema = SchemaDerivation.derive(join);
            List<ColumnSpec> ambiguousCols = schema.getAll("name");
            PlanId leftSidePid = ambiguousCols.get(0).planProvenance();
            PlanId rightSidePid = ambiguousCols.get(1).planProvenance();
            assertNotNull(leftSidePid);
            assertNotNull(rightSidePid);
            assertNotEquals(leftSidePid, rightSidePid,
                    "两侧 planProvenance 必须不同（不同 plan 引用）");
            assertSame(join.left(), leftSidePid.resolve(),
                    "左侧 PlanId 必须 resolve 回 join.left()");
            assertSame(join.right(), rightSidePid.resolve(),
                    "右侧 PlanId 必须 resolve 回 join.right()");
        }

        @Test
        @DisplayName("唯一列也保留 planProvenance + sourceModel（PR3 路由依据）")
        void uniqueColumnsAlsoCarryProvenance() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            JoinPlan join = partnerJoin();
            OutputSchema schema = SchemaDerivation.derive(join);
            ColumnSpec orderId = schema.get("orderId");
            assertNotNull(orderId.planProvenance(),
                    "唯一列也必须保留 planProvenance");
            assertEquals("OrderQM", orderId.sourceModel(),
                    "G10 路径必须保留 sourceModel 而非清除");
            assertSame(join.left(), orderId.planProvenance().resolve());
            assertFalse(orderId.isAmbiguous());

            ColumnSpec customerId = schema.get("customerId");
            assertSame(join.right(), customerId.planProvenance().resolve());
            assertEquals("CustomerQM", customerId.sourceModel());
        }

        @Test
        @DisplayName("无重名 join：plan provenance 仍写入；不抛")
        void nonOverlapAlsoSetsProvenance() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan left = baseOf("SalesQM",
                    List.of("partnerId", "totalSales"));
            QueryPlan right = baseOf("LeadsQM",
                    List.of("partnerKey", "leadCount"));
            JoinPlan join = left.join(right, "left",
                    List.of(JoinOn.of("partnerId", "=", "partnerKey")));
            OutputSchema schema = SchemaDerivation.derive(join);
            assertEquals(4, schema.size());
            for (ColumnSpec c : schema.columns()) {
                assertNotNull(c.planProvenance(),
                        "G10 路径所有列均带 planProvenance: " + c.name());
                assertFalse(c.isAmbiguous(),
                        "无重名时不应有任何列被标歧义: " + c.name());
                assertNotNull(c.sourceModel(),
                        "G10 路径保留 sourceModel: " + c.name());
            }
        }

        @Test
        @DisplayName("on[*].left/right 校验在 G10 路径下仍生效")
        void joinOnValidationsStillWork() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            QueryPlan left = baseOf("A", List.of("x"));
            QueryPlan right = baseOf("B", List.of("y"));

            JoinPlan badLeft = left.join(right, "left",
                    List.of(JoinOn.of("missing", "=", "y")));
            assertEquals(ComposeSchemaErrorCodes.JOIN_ON_LEFT_UNKNOWN_FIELD,
                    assertThrows(ComposeSchemaException.class,
                            () -> SchemaDerivation.derive(badLeft)).code());

            JoinPlan badRight = left.join(right, "left",
                    List.of(JoinOn.of("x", "=", "missing")));
            assertEquals(ComposeSchemaErrorCodes.JOIN_ON_RIGHT_UNKNOWN_FIELD,
                    assertThrows(ComposeSchemaException.class,
                            () -> SchemaDerivation.derive(badRight)).code());
        }

        @Test
        @DisplayName("OutputSchema.get(name) 在歧义列上抛 OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP（端到端）")
        void downstreamGetOnAmbiguousFails() {
            ComposeFeatureFlags.overrideG10Enabled(true);
            OutputSchema schema = SchemaDerivation.derive(partnerJoin());
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> schema.get("name"));
            assertEquals(ComposeSchemaErrorCodes.OUTPUT_SCHEMA_AMBIGUOUS_LOOKUP,
                    ex.code());
        }
    }
}
