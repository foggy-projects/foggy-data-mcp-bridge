package com.foggyframework.dataset.db.model.engine.compose.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G5 Phase 2 (F5) · {@link ColumnObjectNormalizer} plan-qualified column
 * normalization contract.
 *
 * <p>Counterpart to the F4 coverage in {@code F4ColumnObjectIntegrationTest}.
 * F5 normalize differs from F4 in that the output is NOT a string — it is a
 * {@link com.foggyframework.dataset.db.model.engine.compose.plan.expr.PlanExpression}
 * compound mirroring the chained-API output shape ({@code sales.amount.sum().as("t")}
 * → {@link ProjectedColumn} wrapping {@link AggregateColumn} wrapping
 * {@link PlanColumnRef}).</p>
 *
 * <p><b>Intentional split-from-F4-tests</b>: F4 coverage exists at
 * {@code F4ColumnObjectIntegrationTest} (real-SQL integration); F5 unit
 * coverage lives here so the normalize-only behaviour can be verified
 * without spinning up the SQL engine. F5 real-SQL coverage lands in
 * {@code F5ColumnObjectIntegrationTest} (PR-J2).</p>
 */
@DisplayName("G5 F5 · ColumnObjectNormalizer plan-qualified")
class ColumnObjectNormalizerF5Test {

    private static QueryPlan stubPlan(String model) {
        return BaseModelPlan.builder().model(model).columns(List.of("id")).build();
    }

    private static Map<String, Object> map(String... kvs) {
        // Use LinkedHashMap so the {plan, field, agg, as} key-iteration order is
        // deterministic for error-message stability.
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put(kvs[i], kvs[i + 1]);
        }
        return m;
    }

    @Nested
    @DisplayName("§3.1 happy path · {plan, field} → PlanColumnRef compound")
    class HappyPath {

        @Test
        @DisplayName("F5 minimal {plan, field} → PlanColumnRef")
        void minimalF5ReturnsPlanColumnRef() {
            QueryPlan sales = stubPlan("FactSalesQueryModel");
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("plan", sales);
            col.put("field", "amount");

            Object out = ColumnObjectNormalizer.normalize(col, 0);

            assertInstanceOf(PlanColumnRef.class, out);
            PlanColumnRef ref = (PlanColumnRef) out;
            assertSame(sales, ref.plan(), "plan reference is identity-preserved");
            assertEquals("amount", ref.name());
        }

        @Test
        @DisplayName("F5 {plan, field, agg} → AggregateColumn(PlanColumnRef)")
        void f5WithAggReturnsAggregateColumn() {
            QueryPlan sales = stubPlan("FactSalesQueryModel");
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("plan", sales);
            col.put("field", "amount");
            col.put("agg", "sum");

            Object out = ColumnObjectNormalizer.normalize(col, 0);

            assertInstanceOf(AggregateColumn.class, out);
            AggregateColumn agg = (AggregateColumn) out;
            assertEquals("SUM", agg.func(), "agg uppercased for SQL convention");
            assertSame(sales, agg.ref().plan());
            assertEquals("amount", agg.ref().name());
        }

        @Test
        @DisplayName("F5 {plan, field, as} → ProjectedColumn(PlanColumnRef)")
        void f5WithAsReturnsProjectedColumn() {
            QueryPlan sales = stubPlan("FactSalesQueryModel");
            Map<String, Object> col = map("plan", "PLACEHOLDER", "field", "name", "as", "salesName");
            col.put("plan", sales);

            Object out = ColumnObjectNormalizer.normalize(col, 0);

            assertInstanceOf(ProjectedColumn.class, out);
            ProjectedColumn proj = (ProjectedColumn) out;
            assertEquals("salesName", proj.alias());
            assertInstanceOf(PlanColumnRef.class, proj.expr());
            PlanColumnRef inner = (PlanColumnRef) proj.expr();
            assertSame(sales, inner.plan());
            assertEquals("name", inner.name());
        }

        @Test
        @DisplayName("F5 {plan, field, agg, as} → ProjectedColumn(AggregateColumn(PlanColumnRef))")
        void f5WithAggAndAsReturnsProjectedAggregate() {
            QueryPlan sales = stubPlan("FactSalesQueryModel");
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("plan", sales);
            col.put("field", "amount");
            col.put("agg", "sum");
            col.put("as", "totalSales");

            Object out = ColumnObjectNormalizer.normalize(col, 0);

            assertInstanceOf(ProjectedColumn.class, out);
            ProjectedColumn proj = (ProjectedColumn) out;
            assertEquals("totalSales", proj.alias());
            assertInstanceOf(AggregateColumn.class, proj.expr());
            AggregateColumn agg = (AggregateColumn) proj.expr();
            assertEquals("SUM", agg.func());
            assertSame(sales, agg.ref().plan());
            assertEquals("amount", agg.ref().name());
        }

        @Test
        @DisplayName("F5 count_distinct lowers to COUNT_DISTINCT(field) — engine handles SQL lowering downstream")
        void f5CountDistinctUppercased() {
            QueryPlan sales = stubPlan("FactSalesQueryModel");
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("plan", sales);
            col.put("field", "customerId");
            col.put("agg", "count_distinct");

            Object out = ColumnObjectNormalizer.normalize(col, 0);
            AggregateColumn agg = (AggregateColumn) out;
            assertEquals("COUNT_DISTINCT", agg.func(),
                    "agg is uppercased; AllowedFunctions/SqlFunctionExp lowers to COUNT(DISTINCT ...) downstream");
        }

        @Test
        @DisplayName("F5 group_concat uppercased and allowed by aggregation whitelist")
        void f5GroupConcatUppercased() {
            QueryPlan sales = stubPlan("FactSalesQueryModel");
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("plan", sales);
            col.put("field", "paymentMethod");
            col.put("agg", "group_concat");

            Object out = ColumnObjectNormalizer.normalize(col, 0);
            AggregateColumn agg = (AggregateColumn) out;
            assertEquals("GROUP_CONCAT", agg.func());
            assertEquals("paymentMethod", agg.ref().name());
        }

        @Test
        @DisplayName("F4 group_concat map normalizes to GROUP_CONCAT(field) AS alias")
        void f4GroupConcatAllowed() {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("field", "paymentMethod");
            col.put("agg", "group_concat");
            col.put("as", "paymentMethodList");

            Object out = ColumnObjectNormalizer.normalize(col, 0);

            assertEquals("GROUP_CONCAT(paymentMethod) AS paymentMethodList", out);
        }

        @Test
        @DisplayName("F5 in mixed array · F1 string + F4 map + F5 map all coexist")
        void f5MixedArray() {
            QueryPlan sales = stubPlan("FactSalesQueryModel");
            Map<String, Object> f4 = new LinkedHashMap<>();
            f4.put("field", "orderDate");
            f4.put("as", "od");
            Map<String, Object> f5 = new LinkedHashMap<>();
            f5.put("plan", sales);
            f5.put("field", "amount");
            f5.put("agg", "sum");
            f5.put("as", "total");

            List<Object> normalized = ColumnObjectNormalizer.normalizeColumns(
                    List.of("product$id", f4, f5));

            assertEquals(3, normalized.size());
            assertEquals("product$id", normalized.get(0), "F1 passthrough");
            assertEquals("orderDate AS od", normalized.get(1), "F4 → string");
            assertInstanceOf(ProjectedColumn.class, normalized.get(2),
                    "F5 → ProjectedColumn(AggregateColumn) compound");
        }
    }

    @Nested
    @DisplayName("§3.2 validation · F5 type / key / agg / as")
    class Validation {

        @Test
        @DisplayName("plan key is not a QueryPlan instance → COLUMN_PLAN_TYPE_INVALID")
        void planNotQueryPlanType() {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("plan", "FactSalesQueryModel");  // string instead of QueryPlan
            col.put("field", "amount");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ColumnObjectNormalizer.normalize(col, 7));
            assertTrue(ex.getMessage().startsWith("COLUMN_PLAN_TYPE_INVALID:"),
                    "error code prefix; got: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("columns[7]"),
                    "index in error message");
        }

        @Test
        @DisplayName("F5 with unknown key → COLUMN_FIELD_INVALID_KEY (with F5 whitelist enumerated)")
        void f5UnknownKey() {
            QueryPlan sales = stubPlan("FactSalesQueryModel");
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("plan", sales);
            col.put("field", "amount");
            col.put("foo", "bar");  // unknown key

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ColumnObjectNormalizer.normalize(col, 0));
            assertTrue(ex.getMessage().startsWith("COLUMN_FIELD_INVALID_KEY:"));
            // F5 whitelist must mention plan / field / agg / as
            assertTrue(ex.getMessage().contains("plan"), "F5 whitelist mentions plan");
        }

        @Test
        @DisplayName("F5 missing field → COLUMN_FIELD_REQUIRED (same code as F4)")
        void f5MissingField() {
            QueryPlan sales = stubPlan("FactSalesQueryModel");
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("plan", sales);
            // no field

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ColumnObjectNormalizer.normalize(col, 0));
            assertTrue(ex.getMessage().startsWith("COLUMN_FIELD_REQUIRED:"));
        }

        @Test
        @DisplayName("F5 invalid agg → COLUMN_AGG_NOT_SUPPORTED (same whitelist as F4)")
        void f5InvalidAgg() {
            QueryPlan sales = stubPlan("FactSalesQueryModel");
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("plan", sales);
            col.put("field", "amount");
            col.put("agg", "median");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ColumnObjectNormalizer.normalize(col, 0));
            assertTrue(ex.getMessage().startsWith("COLUMN_AGG_NOT_SUPPORTED:"));
        }

        @Test
        @DisplayName("F5 non-string as → COLUMN_AS_TYPE_INVALID")
        void f5InvalidAs() {
            QueryPlan sales = stubPlan("FactSalesQueryModel");
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("plan", sales);
            col.put("field", "amount");
            col.put("as", 42);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ColumnObjectNormalizer.normalize(col, 0));
            assertTrue(ex.getMessage().startsWith("COLUMN_AS_TYPE_INVALID:"));
        }
    }

    @Nested
    @DisplayName("normalizeColumnsToStrings F5 rejection (G5 spec §10.3 item 5)")
    class LegacyStringPathRejection {

        @Test
        @DisplayName("F5 Map in input → COLUMN_PLAN_TYPE_INVALID (no silent toString fallback)")
        void f5MapRejectedByLegacyStringPath() {
            QueryPlan sales = stubPlan("FactSalesQueryModel");
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("plan", sales);
            col.put("field", "amount");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ColumnObjectNormalizer.normalizeColumnsToStrings(List.of(col)));
            assertTrue(ex.getMessage().startsWith("COLUMN_PLAN_TYPE_INVALID:"),
                    "must reject; silent toString would corrupt SQL. Got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Chained-API PlanColumnRef → COLUMN_PLAN_TYPE_INVALID (no silent FieldRef(name) string)")
        void chainedPlanColumnRefRejected() {
            QueryPlan sales = stubPlan("FactSalesQueryModel");
            PlanColumnRef chained = new PlanColumnRef(sales, "amount");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ColumnObjectNormalizer.normalizeColumnsToStrings(List.of(chained)));
            assertTrue(ex.getMessage().startsWith("COLUMN_PLAN_TYPE_INVALID:"));
        }

        @Test
        @DisplayName("F4 Map + F1/F2/F3 strings work normally (only F5 rejected)")
        void f4AndStringsStillWork() {
            Map<String, Object> f4 = new LinkedHashMap<>();
            f4.put("field", "amount");
            f4.put("agg", "sum");
            f4.put("as", "total");

            List<String> result = ColumnObjectNormalizer.normalizeColumnsToStrings(
                    List.of("product$id", "name AS customer", f4));

            assertEquals(3, result.size());
            assertEquals("product$id", result.get(0));
            assertEquals("name AS customer", result.get(1));
            assertEquals("SUM(amount) AS total", result.get(2));
        }
    }
}
