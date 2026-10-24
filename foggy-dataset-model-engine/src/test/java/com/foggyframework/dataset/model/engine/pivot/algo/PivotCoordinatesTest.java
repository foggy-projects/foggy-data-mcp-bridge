package com.foggyframework.dataset.model.engine.pivot.algo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class PivotCoordinatesTest {
    @Test
    void typedTupleIdentityDistinguishesNullLiteralNumbersAndDelimiters() {
        List<Object> values = Arrays.asList(null, "null", "", 1, "1", "a|b", "~n", "a;b:1", "中");
        Set<String> keys = new HashSet<>();
        for (Object a : values)
            for (Object b : values) {
                Map<String, Object> row = new HashMap<>();
                row.put("a", a);
                row.put("b", b);
                assertTrue(keys.add(PivotCoordinates.key(row, List.of("a", "b"))));
            }
    }

    @Test
    void ordinaryCellLabelsStayCompatibleAndSpecialComponentsAreUnique() {
        assertEquals("2024-01", PivotCoordinates.display("2024-01"));
        assertEquals("~n", PivotCoordinates.display(null));
        assertNotEquals(PivotCoordinates.display(null), PivotCoordinates.display("~n"));
        assertNotEquals(
                PivotCoordinates.display("a|b") + "|" + PivotCoordinates.display("c"),
                PivotCoordinates.display("a") + "|" + PivotCoordinates.display("b|c"));
        var cells = new HashMap<String, Object>();
        PivotCoordinates.putCell(cells, "x", null);
        assertThrows(IllegalArgumentException.class, () -> PivotCoordinates.putCell(cells, "x", 1));
    }

    @Test
    void subtotalCachePreservesTypedValuesAndLiteralBusinessMarkers() {
        var cache = new com.foggyframework.dataset.model.engine.pivot.rollup.RollupCache();
        var coords =
                List.of(
                        com.foggyframework.dataset.model.engine.pivot.rollup.RollupCoordinate
                                .grandTotal("r"),
                        com.foggyframework.dataset.model.engine.pivot.rollup.RollupCoordinate.of(
                                "c", "GRAND_TOTAL"));
        cache.put("c", coords, Map.of("average", 10));
        var plan =
                new com.foggyframework.dataset.model.engine.pivot.rollup.RollupMetricPlan(
                        "average",
                        com.foggyframework.dataset.model.engine.pivot.rollup.RollupStrategy
                                .AUX_REQUERY,
                        com.foggyframework.dataset.model.spi.DbAggregation.AVG);
        var options = new com.foggyframework.dataset.model.semantic.domain.pivot.PivotOptions();
        options.setGrandTotal(true);
        var result =
                SubtotalInjector.apply(
                        List.of(Map.of("r", "A", "c", "GRAND_TOTAL", "average", 10)),
                        List.of("r"),
                        List.of("c"),
                        List.of("average"),
                        options,
                        List.of(plan),
                        cache);
        assertEquals(10, result.get(1).get("average"));
        cache.put(
                "numeric",
                List.of(
                        com.foggyframework.dataset.model.engine.pivot.rollup.RollupCoordinate.of(
                                "id", (short) 1)),
                Map.of("value", 20));
        assertEquals(
                20,
                cache.getOrNull(
                        "numeric",
                        List.of(
                                com.foggyframework.dataset.model.engine.pivot.rollup
                                        .RollupCoordinate.of("id", 1)),
                        "value"));
        assertNull(
                cache.getOrNull(
                        "numeric",
                        List.of(
                                com.foggyframework.dataset.model.engine.pivot.rollup
                                        .RollupCoordinate.of("id", "1")),
                        "value"));
    }

    @Test
    void treeRowNullCannotMergeWithALiteralLegacyMarker() {
        Map<String, Object> empty = new HashMap<>();
        empty.put("r", null);
        empty.put("m", 10);
        var pivot = new com.foggyframework.dataset.model.semantic.domain.pivot.PivotRequest();
        pivot.setOutputFormat("tree");
        var result =
                ResultShaper.shape(
                        List.of(empty, Map.of("r", "__null__", "m", 20)),
                        pivot,
                        List.of("r"),
                        List.of(),
                        List.of("m"));
        assertEquals(2, result.getTreeData().size());
        assertEquals(
                10,
                result.getTreeData().stream()
                        .filter(n -> n.getNode().get("r") == null)
                        .findFirst()
                        .orElseThrow()
                        .getCells()
                        .get("m"));
        assertEquals(
                20,
                result.getTreeData().stream()
                        .filter(n -> "__null__".equals(n.getNode().get("r")))
                        .findFirst()
                        .orElseThrow()
                        .getCells()
                        .get("m"));
    }
}
