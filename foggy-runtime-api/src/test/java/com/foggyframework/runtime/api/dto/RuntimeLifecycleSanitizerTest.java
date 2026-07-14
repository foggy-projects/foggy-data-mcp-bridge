package com.foggyframework.runtime.api.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RuntimeLifecycleSanitizerTest {

    private static final int EXPECTED_MAX_COMPOSITE_DEPTH = 5;

    @Test
    void deeplyNestedPureCollectionsStopAtUnifiedCompositeDepth() {
        Object hostileValue = "must-not-survive-depth-bound";
        for (int depth = 0; depth < 20_000; depth++) {
            hostileValue = List.of(hostileValue);
        }
        RuntimeDiagnostics diagnostics = new RuntimeDiagnostics(
                null, null, List.of(), Map.of("root", hostileValue));

        RuntimeDiagnostics sanitized = assertDoesNotThrow(
                () -> RuntimeLifecycleSanitizer.sanitizeDiagnostics(diagnostics));

        Object cursor = sanitized.attributes().get("root");
        for (int depth = 1; depth < EXPECTED_MAX_COMPOSITE_DEPTH; depth++) {
            assertThat(cursor)
                    .as("collection at composite depth %s", depth)
                    .isInstanceOf(List.class);
            List<?> level = (List<?>) cursor;
            assertThat(level)
                    .as("collection before depth cutoff %s", depth)
                    .hasSize(1);
            cursor = level.get(0);
        }
        assertThat(cursor)
                .as("collection at the composite depth cutoff")
                .isEqualTo(List.of());
    }

    @Test
    void mapsAndCollectionsShareOneCompositeDepthBudget() {
        Map<String, Object> attributes = Map.of(
                "root", List.of(Map.of(
                        "next", List.of(Map.of(
                                "next", List.of("must-be-truncated"))))));

        RuntimeDiagnostics sanitized = RuntimeLifecycleSanitizer.sanitizeDiagnostics(
                new RuntimeDiagnostics(null, null, List.of(), attributes));

        List<?> levelOne = (List<?>) sanitized.attributes().get("root");
        Map<?, ?> levelTwo = (Map<?, ?>) levelOne.get(0);
        List<?> levelThree = (List<?>) levelTwo.get("next");
        Map<?, ?> levelFour = (Map<?, ?>) levelThree.get(0);
        assertThat(levelFour.get("next"))
                .isInstanceOf(List.class)
                .isEqualTo(List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void safeNestedNullAndPrimitiveValuesRemainCompatible() {
        LinkedHashMap<String, Object> validation = new LinkedHashMap<>();
        validation.put("valid", true);
        validation.put("failedCount", 0);
        validation.put("ratio", 1.5d);
        validation.put("error", null);
        List<Object> values = new ArrayList<>();
        values.add(null);
        values.add(false);
        values.add(7L);
        values.add("safe-value");
        validation.put("values", values);

        RuntimeDiagnostics sanitized = RuntimeLifecycleSanitizer.sanitizeDiagnostics(
                new RuntimeDiagnostics(
                        null,
                        null,
                        List.of(),
                        Map.of("validation", validation)));

        Map<String, Object> result = (Map<String, Object>)
                sanitized.attributes().get("validation");
        assertAll(
                () -> assertThat(result.get("valid")).isEqualTo(true),
                () -> assertThat(result.get("failedCount")).isEqualTo(0),
                () -> assertThat(result.get("ratio")).isEqualTo(1.5d),
                () -> assertThat(result).containsEntry("error", null),
                () -> assertThat((List<Object>) result.get("values"))
                        .containsExactly(null, false, 7L, "safe-value")
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsAndCollectionsRetainTheirExistingWidthBounds() {
        LinkedHashMap<String, Object> wideMap = new LinkedHashMap<>();
        IntStream.range(0, 101)
                .forEach(index -> wideMap.put("key-%03d".formatted(index), index));
        List<Integer> wideCollection = IntStream.range(0, 101).boxed().toList();

        RuntimeDiagnostics sanitized = RuntimeLifecycleSanitizer.sanitizeDiagnostics(
                new RuntimeDiagnostics(
                        null,
                        null,
                        List.of(),
                        Map.of("map", wideMap, "collection", wideCollection)));

        assertAll(
                () -> assertThat((Map<String, Object>)
                        sanitized.attributes().get("map"))
                        .hasSize(100)
                        .doesNotContainKey("key-100"),
                () -> assertThat((List<Object>)
                        sanitized.attributes().get("collection"))
                        .isEqualTo(IntStream.range(0, 100).boxed().toList())
        );
    }
}
