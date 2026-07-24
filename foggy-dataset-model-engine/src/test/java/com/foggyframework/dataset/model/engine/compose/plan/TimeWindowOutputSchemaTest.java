package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.dataset.model.engine.compose.relation.ReferencePolicy;
import com.foggyframework.dataset.model.engine.compose.relation.SemanticKind;
import com.foggyframework.dataset.model.engine.compose.schema.ColumnSpec;
import com.foggyframework.dataset.model.engine.compose.schema.OutputSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S7a POC · TimeWindowExpander.getOutputSchema() metadata tests.
 */
@DisplayName("TimeWindowOutputSchemaTest · S7a POC")
class TimeWindowOutputSchemaTest {

    private static TimeWindowDef twDef(String comparison, String grain) {
        return TimeWindowDef.fromMap(Map.of(
                "field", "salesDate$id",
                "comparison", comparison,
                "grain", grain != null ? grain : "day",
                "range", "[)",
                "value", List.of("2024-01-01", "2025-01-01"),
                "targetMetrics", List.of("salesAmount")));
    }

    @Test
    @DisplayName("yoy includes __prior, __diff, __ratio with correct metadata")
    void yoyOutputSchema() {
        TimeWindowDef tw = twDef("yoy", "month");
        OutputSchema schema = TimeWindowExpander.getOutputSchema(
                tw, List.of("storeName"), Set.of("salesAmount"));

        // Dimension
        ColumnSpec storeName = schema.get("storeName");
        assertNotNull(storeName);
        assertEquals(SemanticKind.BASE_FIELD, storeName.semanticKind());
        assertTrue(storeName.referencePolicy().contains(ReferencePolicy.GROUPABLE));

        // Original metric
        ColumnSpec salesAmount = schema.get("salesAmount");
        assertNotNull(salesAmount);
        assertEquals(SemanticKind.AGGREGATE_MEASURE, salesAmount.semanticKind());
        assertTrue(salesAmount.referencePolicy().contains(ReferencePolicy.READABLE));

        // __prior
        ColumnSpec prior = schema.get("salesAmount__prior");
        assertNotNull(prior);
        assertEquals(SemanticKind.TIME_WINDOW_DERIVED, prior.semanticKind());
        assertEquals("prior period salesAmount", prior.valueMeaning());
        assertTrue(prior.lineage().contains("salesAmount"));
        assertTrue(prior.referencePolicy().contains(ReferencePolicy.ORDERABLE));

        // __diff
        ColumnSpec diff = schema.get("salesAmount__diff");
        assertNotNull(diff);
        assertEquals(SemanticKind.TIME_WINDOW_DERIVED, diff.semanticKind());
        assertEquals("current minus prior salesAmount", diff.valueMeaning());

        // __ratio — NOT aggregatable
        ColumnSpec ratio = schema.get("salesAmount__ratio");
        assertNotNull(ratio);
        assertEquals(SemanticKind.TIME_WINDOW_DERIVED, ratio.semanticKind());
        assertEquals("current relative to prior salesAmount", ratio.valueMeaning());
        assertTrue(ratio.referencePolicy().contains(ReferencePolicy.READABLE));
        assertTrue(ratio.referencePolicy().contains(ReferencePolicy.ORDERABLE));
        assertFalse(ratio.referencePolicy().contains(ReferencePolicy.AGGREGATABLE),
                "__ratio must NOT include aggregatable");

        // Grain keys
        assertTrue(schema.contains("salesDate$year"), "yoy must include $year");
        assertTrue(schema.contains("salesDate$month"), "yoy+month must include $month");
    }

    @Test
    @DisplayName("rolling includes __rolling_* with correct metadata")
    void rollingOutputSchema() {
        TimeWindowDef tw = twDef("rolling_7d", "day");
        OutputSchema schema = TimeWindowExpander.getOutputSchema(
                tw, List.of("salesDate$id"), Set.of("salesAmount"));

        ColumnSpec rolling = schema.get("salesAmount__rolling_7d");
        assertNotNull(rolling, "should include salesAmount__rolling_7d");
        assertEquals(SemanticKind.TIME_WINDOW_DERIVED, rolling.semanticKind());
        assertTrue(rolling.valueMeaning().contains("rolling"));
        assertTrue(rolling.lineage().contains("salesAmount"));
    }

    @Test
    @DisplayName("cumulative ytd includes __ytd with correct metadata")
    void cumulativeYtdOutputSchema() {
        TimeWindowDef tw = twDef("ytd", "day");
        OutputSchema schema = TimeWindowExpander.getOutputSchema(
                tw, List.of("salesDate$id"), Set.of("salesAmount"));

        ColumnSpec ytd = schema.get("salesAmount__ytd");
        assertNotNull(ytd, "should include salesAmount__ytd");
        assertEquals(SemanticKind.TIME_WINDOW_DERIVED, ytd.semanticKind());
        assertTrue(ytd.valueMeaning().contains("cumulative"));
        assertTrue(ytd.lineage().contains("salesAmount"));
    }

    @Test
    @DisplayName("cumulative mtd includes __mtd with correct metadata")
    void cumulativeMtdOutputSchema() {
        TimeWindowDef tw = twDef("mtd", "day");
        OutputSchema schema = TimeWindowExpander.getOutputSchema(
                tw, List.of("salesDate$id"), Set.of("salesAmount"));

        ColumnSpec mtd = schema.get("salesAmount__mtd");
        assertNotNull(mtd, "should include salesAmount__mtd");
        assertEquals(SemanticKind.TIME_WINDOW_DERIVED, mtd.semanticKind());
    }

    @Test
    @DisplayName("multiple metrics produce multiple derived columns")
    void multipleMetrics() {
        TimeWindowDef tw = TimeWindowDef.fromMap(Map.of(
                "field", "salesDate$id",
                "comparison", "yoy",
                "grain", "month",
                "range", "[)",
                "value", List.of("2024-01-01", "2025-01-01"),
                "targetMetrics", List.of("salesAmount", "quantity")));
        OutputSchema schema = TimeWindowExpander.getOutputSchema(
                tw, List.of("storeName"), Set.of("salesAmount", "quantity"));

        assertTrue(schema.contains("salesAmount__ratio"));
        assertTrue(schema.contains("quantity__ratio"));
        assertTrue(schema.contains("salesAmount__prior"));
        assertTrue(schema.contains("quantity__prior"));
    }
}
