package com.foggyframework.dataset.db.model.engine.compose.schema;

import com.foggyframework.dataset.db.model.engine.compose.plan.Dsl;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryOptions;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M4 · SchemaDerivation.derive per-plan-type behaviour + spec examples.
 *
 * <p>Mirrors Python {@code tests/compose/schema/test_schema_derivation.py}.</p>
 */
@DisplayName("M4 SchemaDerivation")
class SchemaDerivationTest {

    // Convenience: build a BaseModelPlan via Dsl.from(...).
    private static QueryPlan baseOf(String model, List<String> columns) {
        return Dsl.from(Dsl.FromOptions.builder()
                .model(model).columns(columns).build());
    }

    private static QueryPlan baseOf(String model, List<String> columns,
                                    List<String> groupBy, List<String> orderBy) {
        return Dsl.from(Dsl.FromOptions.builder()
                .model(model).columns(columns)
                .groupBy(groupBy).orderBy(orderBy).build());
    }

    // ------------------------------------------------------------------
    // BaseModelPlan
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("BaseModelPlan schema")
    class BaseModelSchema {

        @Test
        @DisplayName("简单列清单")
        void simpleColumnList() {
            QueryPlan plan = baseOf("SaleOrderQM", List.of("id", "name", "total"));
            OutputSchema schema = SchemaDerivation.derive(plan);
            assertEquals(List.of("id", "name", "total"), schema.names());
            for (ColumnSpec c : schema.columns()) {
                assertEquals("SaleOrderQM", c.sourceModel());
            }
        }

        @Test
        @DisplayName("alias 改写 output name，保留 expression")
        void aliasStripsExpressionInOutputName() {
            QueryPlan plan = baseOf("SaleOrderQM",
                    List.of("SUM(amount) AS total", "customer$id AS customerId"));
            OutputSchema schema = SchemaDerivation.derive(plan);
            assertEquals(List.of("total", "customerId"), schema.names());
            assertEquals("SUM(amount)", schema.get("total").expression());
            assertTrue(schema.get("total").hasExplicitAlias());
            assertEquals("customer$id", schema.get("customerId").expression());
        }

        @Test
        @DisplayName("混合 aliased + bare")
        void mixedAliasedAndBare() {
            QueryPlan plan = baseOf("X",
                    List.of("orderId", "SUM(amount) AS total", "COUNT(*) AS orderCount"));
            OutputSchema schema = SchemaDerivation.derive(plan);
            assertEquals(List.of("orderId", "total", "orderCount"), schema.names());
        }

        @Test
        @DisplayName("重复 output name 在 base 层即被拒绝")
        void duplicateOutputNamesRejectedAtBase() {
            QueryPlan plan = baseOf("X", List.of("a AS x", "b AS x"));
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> SchemaDerivation.derive(plan));
            assertEquals(ComposeSchemaErrorCodes.DUPLICATE_OUTPUT_COLUMN, ex.code());
            assertEquals("x", ex.offendingField());
        }

        @Test
        @DisplayName("derive(null) 被拒绝")
        void deriveNullRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> SchemaDerivation.derive(null));
        }
    }

    // ------------------------------------------------------------------
    // DerivedQueryPlan
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("DerivedQueryPlan schema")
    class DerivedQuerySchema {

        private QueryPlan base() {
            return baseOf("SaleOrderQM", List.of("orderId", "customer$id", "amount"));
        }

        @Test
        @DisplayName("从 source 传播列")
        void propagatesColumnsFromSource() {
            QueryPlan derived = base().query(
                    QueryOptions.builder().columns(List.of("orderId")).build());
            OutputSchema schema = SchemaDerivation.derive(derived);
            assertEquals(List.of("orderId"), schema.names());
        }

        @Test
        @DisplayName("引用 source 未暴露的字段 → DERIVED_QUERY_UNKNOWN_FIELD")
        void referencesUnknownFieldRejected() {
            QueryPlan derived = base().query(
                    QueryOptions.builder().columns(List.of("nonExistent")).build());
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> SchemaDerivation.derive(derived));
            assertEquals(ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD, ex.code());
            assertEquals("nonExistent", ex.offendingField());
            assertNotNull(ex.planPath());
            assertTrue(ex.planPath().contains("DerivedQueryPlan"));
        }

        @Test
        @DisplayName("aliased output 可作为下一层的 reference target")
        void aliasedOutputReusableByFurtherDerivation() {
            QueryPlan base = baseOf("X", List.of("amount", "rate"));
            QueryPlan level1 = base.query(
                    QueryOptions.builder().columns(List.of("amount * rate AS total")).build());
            QueryPlan level2 = level1.query(
                    QueryOptions.builder().columns(List.of("total")).build());
            OutputSchema schema = SchemaDerivation.derive(level2);
            assertEquals(List.of("total"), schema.names());
        }

        @Test
        @DisplayName("未 project 的 base 字段对下层不可见")
        void derivedWithoutProjectionHidesOriginalField() {
            QueryPlan base = baseOf("X", List.of("amount", "rate"));
            QueryPlan level1 = base.query(
                    QueryOptions.builder().columns(List.of("amount")).build());
            QueryPlan level2 = level1.query(
                    QueryOptions.builder().columns(List.of("rate")).build());
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> SchemaDerivation.derive(level2));
            assertEquals(ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD, ex.code());
            assertEquals("rate", ex.offendingField());
        }

        @Test
        @DisplayName("group_by 引用不存在字段 → DERIVED_QUERY_UNKNOWN_FIELD")
        void groupByReferencesValidated() {
            QueryPlan base = baseOf("X", List.of("id", "amount"));
            QueryPlan derived = base.query(QueryOptions.builder()
                    .columns(List.of("SUM(amount) AS total"))
                    .groupBy(List.of("missing"))
                    .build());
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> SchemaDerivation.derive(derived));
            assertEquals(ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD, ex.code());
            assertEquals("missing", ex.offendingField());
        }

        @Test
        @DisplayName("order_by desc 前缀 `-amount` 可被处理")
        void orderByDescPrefixHandled() {
            QueryPlan base = baseOf("X", List.of("id", "amount"));
            QueryPlan derived = base.query(QueryOptions.builder()
                    .columns(List.of("id", "amount"))
                    .orderBy(List.of("-amount"))
                    .build());
            OutputSchema schema = SchemaDerivation.derive(derived);
            assertEquals(List.of("id", "amount"), schema.names());
        }

        @Test
        @DisplayName("reserved tokens `SUM` / `COALESCE` / `NULL` 不触发 unknown-field")
        void reservedTokensInExpressionNotFlagged() {
            QueryPlan base = baseOf("X", List.of("amount", "discount"));
            QueryPlan derived = base.query(QueryOptions.builder()
                    .columns(List.of("COALESCE(discount, 0) AS d", "SUM(amount) AS total"))
                    .build());
            OutputSchema schema = SchemaDerivation.derive(derived);
            assertEquals(List.of("d", "total"), schema.names());
        }

        @Test
        @DisplayName("base 层 group_by 引用不存在字段 → DERIVED_QUERY_UNKNOWN_FIELD")
        void baseGroupByReferencesValidated() {
            QueryPlan plan = baseOf("X",
                    List.of("id", "amount"),
                    List.of("missingField"),
                    List.of());
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> SchemaDerivation.derive(plan));
            assertEquals(ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD, ex.code());
            assertEquals("missingField", ex.offendingField());
        }

        @Test
        @DisplayName("base 层 order_by 引用不存在字段 → DERIVED_QUERY_UNKNOWN_FIELD")
        void baseOrderByReferencesValidated() {
            QueryPlan plan = baseOf("X",
                    List.of("id", "amount"),
                    List.of(),
                    List.of("-missing"));
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> SchemaDerivation.derive(plan));
            assertEquals(ComposeSchemaErrorCodes.DERIVED_QUERY_UNKNOWN_FIELD, ex.code());
            assertEquals("missing", ex.offendingField());
        }
    }

    // ------------------------------------------------------------------
    // UnionPlan
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("UnionPlan schema")
    class UnionSchema {

        @Test
        @DisplayName("匹配列数成功派生")
        void matchingColumnsSucceeds() {
            QueryPlan a = baseOf("CurrentQM", List.of("salespersonId", "amount"));
            QueryPlan b = baseOf("ArchivedQM", List.of("salespersonId", "amount"));
            OutputSchema schema = SchemaDerivation.derive(a.union(b, true));
            assertEquals(List.of("salespersonId", "amount"), schema.names());
        }

        @Test
        @DisplayName("列数不一致 → UNION_COLUMN_COUNT_MISMATCH")
        void unionColumnCountMismatchRejected() {
            QueryPlan a = baseOf("A", List.of("x", "y"));
            QueryPlan b = baseOf("B", List.of("x", "y", "z"));
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> SchemaDerivation.derive(a.union(b)));
            assertEquals(ComposeSchemaErrorCodes.UNION_COLUMN_COUNT_MISMATCH, ex.code());
            assertNotNull(ex.planPath());
            assertTrue(ex.planPath().contains("UnionPlan"));
        }

        @Test
        @DisplayName("union 的输出名 follow 左侧")
        void unionOutputNamesComeFromLeft() {
            QueryPlan a = baseOf("A", List.of("salesperson", "amount"));
            QueryPlan b = baseOf("B", List.of("who", "how_much"));
            OutputSchema schema = SchemaDerivation.derive(a.union(b));
            assertEquals(List.of("salesperson", "amount"), schema.names());
        }

        @Test
        @DisplayName("union 两侧均为 derived")
        void unionOfDerivedPlans() {
            QueryPlan baseA = baseOf("A", List.of("id", "amount"));
            QueryPlan baseB = baseOf("B", List.of("id", "amount"));
            QueryPlan derivedA = baseA.query(QueryOptions.builder()
                    .columns(List.of("id", "amount AS amt")).build());
            QueryPlan derivedB = baseB.query(QueryOptions.builder()
                    .columns(List.of("id", "amount AS amt")).build());
            OutputSchema schema = SchemaDerivation.derive(derivedA.union(derivedB));
            assertEquals(List.of("id", "amt"), schema.names());
        }

        @Test
        @DisplayName("union 派生后的 source_model 被抹除")
        void unionErasesSourceModelAttribution() {
            QueryPlan a = baseOf("CurrentQM", List.of("id", "amount"));
            QueryPlan b = baseOf("ArchivedQM", List.of("id", "amount"));
            OutputSchema schema = SchemaDerivation.derive(a.union(b, true));
            for (ColumnSpec c : schema.columns()) {
                assertNull(c.sourceModel(),
                        "union 输出不应保留 source_model，但列 " + c.name() + " 有 sourceModel=" + c.sourceModel());
            }
        }
    }

    // ------------------------------------------------------------------
    // JoinPlan
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("JoinPlan schema")
    class JoinSchema {

        @Test
        @DisplayName("无重名时保留两侧列")
        void joinPreservesBothSidesNonOverlappingColumns() {
            QueryPlan left = baseOf("SalesQM", List.of("partnerId", "totalSales"));
            QueryPlan right = baseOf("LeadsQM", List.of("partnerKey", "leadCount"));
            JoinPlan join = left.join(right, "left",
                    List.of(JoinOn.of("partnerId", "=", "partnerKey")));
            OutputSchema schema = SchemaDerivation.derive(join);
            assertEquals(List.of("partnerId", "totalSales", "partnerKey", "leadCount"),
                    schema.names());
        }

        @Test
        @DisplayName("on[*].left 不在左侧 → JOIN_ON_LEFT_UNKNOWN_FIELD")
        void joinOnLeftFieldUnknownRejected() {
            QueryPlan left = baseOf("A", List.of("x"));
            QueryPlan right = baseOf("B", List.of("y"));
            JoinPlan join = left.join(right, "left",
                    List.of(JoinOn.of("missing", "=", "y")));
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> SchemaDerivation.derive(join));
            assertEquals(ComposeSchemaErrorCodes.JOIN_ON_LEFT_UNKNOWN_FIELD, ex.code());
            assertEquals("missing", ex.offendingField());
        }

        @Test
        @DisplayName("on[*].right 不在右侧 → JOIN_ON_RIGHT_UNKNOWN_FIELD")
        void joinOnRightFieldUnknownRejected() {
            QueryPlan left = baseOf("A", List.of("x"));
            QueryPlan right = baseOf("B", List.of("y"));
            JoinPlan join = left.join(right, "left",
                    List.of(JoinOn.of("x", "=", "missing")));
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> SchemaDerivation.derive(join));
            assertEquals(ComposeSchemaErrorCodes.JOIN_ON_RIGHT_UNKNOWN_FIELD, ex.code());
            assertEquals("missing", ex.offendingField());
        }

        @Test
        @DisplayName("两侧输出 name 冲突 → JOIN_OUTPUT_COLUMN_CONFLICT")
        void joinOutputColumnConflictRejected() {
            QueryPlan left = baseOf("A",
                    List.of("partnerId", "partnerName", "totalSales"));
            QueryPlan right = baseOf("B",
                    List.of("partnerKey", "partnerName", "leadCount"));
            JoinPlan join = left.join(right, "left",
                    List.of(JoinOn.of("partnerId", "=", "partnerKey")));
            ComposeSchemaException ex = assertThrows(ComposeSchemaException.class,
                    () -> SchemaDerivation.derive(join));
            assertEquals(ComposeSchemaErrorCodes.JOIN_OUTPUT_COLUMN_CONFLICT, ex.code());
            assertEquals("partnerName", ex.offendingField());
        }

        @Test
        @DisplayName("join 输出抹除 source_model")
        void joinErasesSourceModelAttribution() {
            QueryPlan left = baseOf("SalesQM", List.of("partnerId", "totalSales"));
            QueryPlan right = baseOf("LeadsQM", List.of("partnerKey", "leadCount"));
            JoinPlan join = left.join(right, "left",
                    List.of(JoinOn.of("partnerId", "=", "partnerKey")));
            OutputSchema schema = SchemaDerivation.derive(join);
            for (ColumnSpec c : schema.columns()) {
                assertNull(c.sourceModel(),
                        "join 输出不应保留 source_model，但列 " + c.name() + " 有 sourceModel=" + c.sourceModel());
            }
        }
    }

    // ------------------------------------------------------------------
    // Spec examples end-to-end
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Spec 典型示例 1 — 两段聚合")
    class SpecExampleTwoStageAggregation {

        @Test
        @DisplayName("两段聚合派生干净")
        void twoStageDerivation() {
            QueryPlan overdueByCustomer = Dsl.from(Dsl.FromOptions.builder()
                    .model("ReceivableLineQM")
                    .columns(List.of(
                            "salespersonId",
                            "salespersonName",
                            "customer$id AS customerId",
                            "SUM(residualAmount) AS customerOverdue"
                    ))
                    .groupBy(List.of("salespersonId", "salespersonName", "customerId"))
                    .build());

            OutputSchema s1 = SchemaDerivation.derive(overdueByCustomer);
            assertEquals(List.of("salespersonId", "salespersonName",
                    "customerId", "customerOverdue"), s1.names());

            QueryPlan secondStage = overdueByCustomer.query(QueryOptions.builder()
                    .columns(List.of(
                            "salespersonId",
                            "salespersonName",
                            "SUM(customerOverdue) AS arOverdue",
                            "COUNT(*) AS arOverdueCustomerCount"
                    ))
                    .groupBy(List.of("salespersonId", "salespersonName"))
                    .orderBy(List.of("-arOverdue"))
                    .build());
            OutputSchema s2 = SchemaDerivation.derive(secondStage);
            assertEquals(List.of("salespersonId", "salespersonName",
                    "arOverdue", "arOverdueCustomerCount"), s2.names());
        }
    }

    @Nested
    @DisplayName("Spec 典型示例 3 — join 后派生 + alias 消歧")
    class SpecExampleJoinThenFilter {

        @Test
        @DisplayName("join with alias disambiguation works")
        void joinWithAliasDisambiguationWorks() {
            QueryPlan sales = Dsl.from(Dsl.FromOptions.builder()
                    .model("SaleOrderQM")
                    .columns(List.of(
                            "partner$id AS partnerId",
                            "partner$caption AS salesPartnerName",
                            "SUM(amountTotal) AS totalSales"
                    ))
                    .groupBy(List.of("partnerId", "salesPartnerName"))
                    .build());
            QueryPlan leads = Dsl.from(Dsl.FromOptions.builder()
                    .model("CrmLeadQM")
                    .columns(List.of(
                            "partner$id AS leadPartnerId",
                            "partner$caption AS leadPartnerName",
                            "COUNT(*) AS leadCount"
                    ))
                    .groupBy(List.of("leadPartnerId", "leadPartnerName"))
                    .build());
            JoinPlan joined = sales.join(leads, "left",
                    List.<Object>of(Map.of("left", "partnerId",
                            "op", "=",
                            "right", "leadPartnerId")));
            OutputSchema schema = SchemaDerivation.derive(joined);
            assertEquals(List.of("partnerId", "salesPartnerName", "totalSales",
                    "leadPartnerId", "leadPartnerName", "leadCount"),
                    schema.names());
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers — bare-identifier scan + reserved tokens
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("保留 token 与 bare-identifier 扫描")
    class ReservedTokensAndScanner {

        @Test
        @DisplayName("28 个保留 token 与 Python 严格对齐（5 agg + 7 control + 2 coalesce + 5 cmp + 3 date + 2 logical + 3 bool + 1 DISTINCT）")
        void reservedTokensCount() {
            // Python _RESERVED_TOKENS contains exactly 28 entries — see
            // foggy.dataset_model.engine.compose.schema.derive._RESERVED_TOKENS.
            // The execution prompt says "27"; that is an off-by-one in the
            // prompt. Python is the source of truth.
            assertEquals(28, SchemaDerivation.RESERVED_TOKENS.size());
        }

        @Test
        @DisplayName("保留 token 集合硬列举")
        void reservedTokensContents() {
            List<String> expected = List.of(
                    "SUM", "COUNT", "AVG", "MIN", "MAX",
                    "IIF", "IF", "CASE", "WHEN", "THEN", "ELSE", "END",
                    "COALESCE", "NULLIF",
                    "IS_NULL", "IS_NOT_NULL", "BETWEEN", "IN", "NOT",
                    "DATE_DIFF", "DATE_ADD", "NOW",
                    "AND", "OR",
                    "TRUE", "FALSE", "NULL",
                    "DISTINCT"
            );
            assertEquals(28, expected.size());
            for (String token : expected) {
                assertTrue(SchemaDerivation.RESERVED_TOKENS.contains(token),
                        "缺少保留 token: " + token);
            }
        }

        @Test
        @DisplayName("isReservedToken 大小写不敏感")
        void isReservedTokenCaseInsensitive() {
            assertTrue(SchemaDerivation.isReservedToken("SUM"));
            assertTrue(SchemaDerivation.isReservedToken("sum"));
            assertTrue(SchemaDerivation.isReservedToken("Sum"));
            assertTrue(SchemaDerivation.isReservedToken("is_null"));
            assertFalse(SchemaDerivation.isReservedToken("orderId"));
        }

        @Test
        @DisplayName("extractBareIdentifiers 忽略字符串字面量")
        void extractBareIdentifiersSkipsStringLiterals() {
            // The identifier 'fakeField' appears ONLY inside a string literal
            // and must therefore NOT be returned.
            List<String> ids = SchemaDerivation.extractBareIdentifiers(
                    "SUM(amount) = 'fakeField'");
            assertTrue(ids.contains("SUM"));
            assertTrue(ids.contains("amount"));
            assertFalse(ids.contains("fakeField"),
                    "fakeField 在字符串字面量中，不应被扫描出来");
        }

        @Test
        @DisplayName("maskStringLiterals 对双引号同样生效")
        void maskStringLiteralsHandlesDoubleQuotes() {
            String masked = SchemaDerivation.maskStringLiterals("foo + \"bar\"");
            // Content inside quotes should be blanked out.
            assertFalse(masked.contains("bar"));
            // Identifier outside quotes survives.
            assertTrue(masked.contains("foo"));
        }
    }
}
