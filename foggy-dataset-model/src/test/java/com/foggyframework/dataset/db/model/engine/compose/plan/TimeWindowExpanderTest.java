package com.foggyframework.dataset.db.model.engine.compose.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TimeWindowExpander}.
 *
 * @since 8.3.0.beta
 */
@DisplayName("TimeWindowExpander")
class TimeWindowExpanderTest {

    // ---- Fixtures ----

    private static BaseModelPlan salesPlan() {
        return BaseModelPlan.builder()
                .model("SaleOrderQM")
                .columns(List.of("salesDate$id", "partnerId", "salesAmount", "costAmount"))
                .build();
    }

    // ==================================================================
    // Rolling expansion
    // ==================================================================

    @Nested
    @DisplayName("Rolling expansion")
    class RollingExpansion {

        @Test
        @DisplayName("rolling_7d produces 7-row window with correct frame")
        void rolling7d() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "day", "rolling_7d", "[)",
                    List.of("-30D", "now"),
                    List.of("salesAmount"), "avg");

            TimeWindowExpander.ExpansionResult result = TimeWindowExpander.expandRolling(
                    tw, salesPlan(), List.of("partnerId"), Set.of("salesAmount", "costAmount"));

            assertNotNull(result);
            assertEquals(1, result.additionalColumns().size());

            ProjectedColumn col = result.additionalColumns().get(0);
            assertNotNull(col);
            assertTrue(col.alias().contains("rolling_7"));

            // Verify the expression contains the frame
            String expr = col.toColumnExpr();
            assertTrue(expr.contains("AVG(salesAmount)"),
                    "Should use AVG aggregator. Got: " + expr);
            assertTrue(expr.contains("PARTITION BY partnerId"),
                    "Should partition by groupBy fields. Got: " + expr);
            assertTrue(expr.contains("ORDER BY salesDate$id ASC"),
                    "Should order by time field. Got: " + expr);
            assertTrue(expr.contains("ROWS BETWEEN 6 PRECEDING AND CURRENT ROW"),
                    "Should have rolling frame. Got: " + expr);

            assertEquals("salesDate$id", result.orderByField());
            assertEquals(List.of("partnerId"), result.partitionByFields());
            assertNotNull(result.description());
        }

        @Test
        @DisplayName("rolling_30d with null targetMetrics expands all measures")
        void rolling30dAllMeasures() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "day", "rolling_30d", "[)",
                    List.of("-60D", "now"),
                    null, null);  // no targetMetrics, no aggregator

            TimeWindowExpander.ExpansionResult result = TimeWindowExpander.expandRolling(
                    tw, salesPlan(), List.of(), Set.of("salesAmount", "costAmount"));

            // Should produce 2 columns (one per measure)
            assertEquals(2, result.additionalColumns().size());

            // Each should default to SUM
            for (ProjectedColumn col : result.additionalColumns()) {
                String expr = col.toColumnExpr();
                assertTrue(expr.contains("SUM("),
                        "Should default to SUM aggregator. Got: " + expr);
                assertTrue(expr.contains("ROWS BETWEEN 29 PRECEDING AND CURRENT ROW"),
                        "Should have 30-row frame. Got: " + expr);
            }
        }

        @Test
        @DisplayName("rolling with no partition-by fields")
        void rollingNoPartition() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "day", "rolling_7d", "[)",
                    List.of("-30D", "now"),
                    List.of("salesAmount"), "sum");

            TimeWindowExpander.ExpansionResult result = TimeWindowExpander.expandRolling(
                    tw, salesPlan(), List.of(), Set.of("salesAmount"));

            ProjectedColumn col = result.additionalColumns().get(0);
            String expr = col.toColumnExpr();
            // Should NOT contain PARTITION BY when no partition fields
            assertFalse(expr.contains("PARTITION BY"),
                    "Should not have PARTITION BY with empty groupBy. Got: " + expr);
            assertTrue(expr.contains("ORDER BY salesDate$id ASC"),
                    "Should still have ORDER BY. Got: " + expr);
        }
    }

    // ==================================================================
    // Cumulative expansion
    // ==================================================================

    @Nested
    @DisplayName("Cumulative expansion")
    class CumulativeExpansion {

        @Test
        @DisplayName("ytd produces cumulative window")
        void ytd() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "ytd", "[)",
                    List.of("2024-01-01", "now"),
                    List.of("salesAmount"), null);

            TimeWindowExpander.ExpansionResult result = TimeWindowExpander.expandCumulative(
                    tw, salesPlan(), List.of("salesDate$year"), Set.of("salesAmount"));

            assertEquals(1, result.additionalColumns().size());

            ProjectedColumn col = result.additionalColumns().get(0);
            assertTrue(col.alias().contains("ytd"), "Alias should contain 'ytd'. Got: " + col.alias());

            String expr = col.toColumnExpr();
            assertTrue(expr.contains("ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"),
                    "Should have cumulative frame. Got: " + expr);
            assertTrue(expr.contains("PARTITION BY salesDate$year"),
                    "Should partition by year for YTD. Got: " + expr);
        }

        @Test
        @DisplayName("mtd produces cumulative window")
        void mtd() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "day", "mtd", "[)",
                    List.of("2024-01-01", "now"),
                    List.of("salesAmount"), null);

            TimeWindowExpander.ExpansionResult result = TimeWindowExpander.expandCumulative(
                    tw, salesPlan(), List.of("salesDate$month"), Set.of("salesAmount"));

            ProjectedColumn col = result.additionalColumns().get(0);
            assertTrue(col.alias().contains("mtd"), "Alias should contain 'mtd'. Got: " + col.alias());

            String expr = col.toColumnExpr();
            assertTrue(expr.contains("ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"),
                    "Should have cumulative frame. Got: " + expr);
        }
    }

    // ==================================================================
    // Comparative expansion (yoy/mom/wow)
    // ==================================================================

    @Nested
    @DisplayName("Comparative expansion")
    class ComparativeExpansion {

        @Test
        @DisplayName("yoy + month produces correct grain key and shift field")
        void yoyMonth() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"),
                    List.of("salesAmount"), null);

            TimeWindowExpander.ComparativeExpansionResult result =
                    TimeWindowExpander.expandComparative(tw, Set.of("salesAmount", "costAmount"), List.of());

            assertEquals(-1, result.periodOffset());
            assertEquals(RelativeDateParser.OffsetUnit.YEAR, result.offsetUnit());
            assertEquals("salesDate$month", result.grainKeyField(),
                    "YoY + month grain → grain key is salesDate$month");
            assertEquals("salesDate$year", result.shiftField(),
                    "YoY → shift field is salesDate$year");

            assertEquals(1, result.projectedColumns().size());
            TimeWindowExpander.ComparativeColumn col = result.projectedColumns().get(0);
            assertEquals("salesAmount", col.currentAlias());
            assertEquals("salesAmount__prior", col.priorAlias());
            assertEquals("salesAmount__diff", col.diffAlias());
            assertEquals("salesAmount__ratio", col.ratioAlias());
        }

        @Test
        @DisplayName("yoy + quarter produces salesDate$quarter grain key")
        void yoyQuarter() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "quarter", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"),
                    null, null);

            TimeWindowExpander.ComparativeExpansionResult result =
                    TimeWindowExpander.expandComparative(tw, Set.of("salesAmount", "costAmount"), List.of());

            assertEquals("salesDate$quarter", result.grainKeyField());
            assertEquals("salesDate$year", result.shiftField());
            // null targetMetrics → both measures
            assertEquals(2, result.metrics().size());
        }

        @Test
        @DisplayName("mom produces month shift with day grain key")
        void mom() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "mom", "[)",
                    List.of("2024-01-01", "2025-01-01"),
                    List.of("salesAmount"), null);

            TimeWindowExpander.ComparativeExpansionResult result =
                    TimeWindowExpander.expandComparative(tw, Set.of("salesAmount"), List.of());

            assertEquals(-1, result.periodOffset());
            assertEquals(RelativeDateParser.OffsetUnit.MONTH, result.offsetUnit());
            assertEquals("salesDate$id", result.grainKeyField(),
                    "MoM → grain key uses date field itself (day grain)");
            assertEquals("salesDate$month", result.shiftField());
        }

        @Test
        @DisplayName("wow produces week shift with dayOfWeek grain key")
        void wow() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "week", "wow", "[)",
                    List.of("-2W", "now"),
                    List.of("salesAmount"), null);

            TimeWindowExpander.ComparativeExpansionResult result =
                    TimeWindowExpander.expandComparative(tw, Set.of("salesAmount"), List.of());

            assertEquals(-1, result.periodOffset());
            assertEquals(RelativeDateParser.OffsetUnit.WEEK, result.offsetUnit());
            assertEquals("salesDate$dayOfWeek", result.grainKeyField());
            assertEquals("salesDate$week", result.shiftField());
        }

        @Test
        @DisplayName("invalid comparison throws")
        void invalidComparison() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "day", "rolling_7d", "[)",
                    List.of("-30D", "now"),
                    List.of("salesAmount"), null);

            assertThrows(IllegalArgumentException.class,
                    () -> TimeWindowExpander.expandComparative(tw, Set.of("salesAmount"), List.of()));
        }

        @Test
        @DisplayName("yoy with dimensions includes dims in join ON and projections")
        void yoyWithDimensions() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"),
                    List.of("salesAmount"), null);

            List<String> dims = List.of("product$category", "store$city");
            TimeWindowExpander.ComparativeExpansionResult result =
                    TimeWindowExpander.expandComparative(tw, Set.of("salesAmount"), dims);

            // dimensionFields should be stored in result
            assertEquals(dims, result.dimensionFields(),
                    "Should carry dimension fields through");

            // Build plan and verify join includes dimensions
            BaseModelPlan base = salesPlan();
            QueryPlan plan = TimeWindowExpander.buildComparativePlan(base, result, tw);
            assertNotNull(plan, "Comparative plan should not be null");

            // The plan should be a DerivedQueryPlan wrapping a JoinPlan
            assertInstanceOf(DerivedQueryPlan.class, plan);
            DerivedQueryPlan outerDerived = (DerivedQueryPlan) plan;
            assertInstanceOf(JoinPlan.class, outerDerived.source());

            JoinPlan joinPlan = (JoinPlan) outerDerived.source();
            // ON conditions: 2 dims + 1 shifted period key + 1 grain key = 4 conditions
            assertEquals(4, joinPlan.on().size(),
                    "JOIN ON should have 4 conditions (2 dims + shifted period key + grain key)");
            assertEquals("product$category", joinPlan.on().get(0).left());
            assertEquals("store$city", joinPlan.on().get(1).left());
            assertEquals("salesDate$year", joinPlan.on().get(2).left());
            assertEquals("salesDate$month", joinPlan.on().get(3).left());

            // Outer projection should include dimension fields
            List<Object> cols = outerDerived.columns();
            assertTrue(cols.contains("product$category"),
                    "Outer projection should include product$category");
            assertTrue(cols.contains("store$city"),
                    "Outer projection should include store$city");
        }
    }
}
