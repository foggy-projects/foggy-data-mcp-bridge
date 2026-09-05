package com.foggyframework.dataset.model.semantic.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.semantic.domain.QueryInputWarning;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Query DSL unknown-property inspection")
class SemanticQueryPropertyInspectorTest {

    private final SemanticQueryPropertyInspector inspector = new SemanticQueryPropertyInspector();

    @Test
    @DisplayName("warn mode reports unsupported groupBy grain without echoing its value")
    void warnShouldReportUnsupportedGroupByGrain() {
        Map<String, Object> payload = Map.of(
                "groupBy", List.of(Map.of(
                        "field", "orderDate",
                        "grain", "sensitive-raw-value")));

        List<QueryInputWarning> warnings = inspector.inspect(payload, UnknownQueryPropertyPolicy.WARN);

        assertEquals(1, warnings.size());
        QueryInputWarning warning = warnings.get(0);
        assertEquals(SemanticQueryPropertyInspector.IGNORED_CODE, warning.code());
        assertEquals("$.groupBy[0].grain", warning.path());
        assertTrue(warning.suggestedNextAction().contains("model-defined time grain"));
        assertFalse(warning.safeToAutoRepair());
        assertEquals("grain", warning.details().get("property"));
        assertFalse(warning.toString().contains("sensitive-raw-value"));
    }

    @Test
    @DisplayName("multiple nested properties are reported in deterministic traversal order")
    void warnShouldCollectMultipleNestedPropertiesDeterministically() {
        Map<String, Object> firstGroup = new LinkedHashMap<>();
        firstGroup.put("grain", "month");
        firstGroup.put("field", "orderDate");
        firstGroup.put("bogus", true);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderBy", List.of(Map.of("field", "amount", "directionTypo", "asc")));
        payload.put("groupBy", List.of(firstGroup));
        payload.put("rootTypo", 1);

        List<QueryInputWarning> warnings = inspector.inspect(payload, UnknownQueryPropertyPolicy.WARN);

        assertEquals(List.of(
                        "$.rootTypo",
                        "$.groupBy[0].bogus",
                        "$.groupBy[0].grain",
                        "$.orderBy[0].directionTypo"),
                warnings.stream().map(QueryInputWarning::path).toList());
    }

    @Test
    @DisplayName("strict mode rejects all ordinary unknown properties before mapping")
    void strictShouldRejectAllUnknownProperties() {
        Map<String, Object> payload = Map.of(
                "groupBy", List.of(Map.of("field", "orderDate", "grain", "month")),
                "orderBy", List.of(Map.of("field", "amount", "descending", true)));

        QueryInputValidationException failure = assertThrows(
                QueryInputValidationException.class,
                () -> inspector.inspect(payload, UnknownQueryPropertyPolicy.STRICT));

        assertEquals(SemanticQueryPropertyInspector.STRICT_CODE, failure.getCode());
        assertEquals(List.of("$.groupBy[0].grain", "$.orderBy[0].descending"),
                failure.getViolations().stream().map(QueryInputWarning::path).toList());
    }

    @Test
    @DisplayName("repeated misspellings at different locations retain distinct actionable paths")
    void repeatedUnknownPropertyShouldRetainEachLocation() {
        List<QueryInputWarning> warnings = inspector.inspect(
                Map.of("groupBy", List.of(
                        Map.of("field", "createdMonth", "grain", "month"),
                        Map.of("field", "paidMonth", "grain", "month"))),
                UnknownQueryPropertyPolicy.WARN);

        assertEquals(List.of("$.groupBy[0].grain", "$.groupBy[1].grain"),
                warnings.stream().map(QueryInputWarning::path).toList());
    }

    @Test
    @DisplayName("raw JSON duplicate detector retains every nested duplicate path")
    void rawJsonDuplicateDetectorShouldRetainEveryPath() {
        List<DuplicateQueryProperty> duplicates = new QueryJsonDuplicateDetector(new ObjectMapper())
                .detect("""
                        {
                          "groupBy": [{"field":"createdAt", "field":"paidAt"}],
                          "limit": 10,
                          "limit": 20
                        }
                        """);

        assertEquals(List.of("$.groupBy[0].field", "$.limit"),
                duplicates.stream().map(DuplicateQueryProperty::path).toList());
        assertEquals(List.of(2, 2),
                duplicates.stream().map(DuplicateQueryProperty::occurrences).toList());
    }

    @Test
    @DisplayName("strict mode rejects all supplied duplicate paths")
    void strictShouldRejectAllDuplicateProperties() {
        List<DuplicateQueryProperty> duplicates = List.of(
                new DuplicateQueryProperty("$.groupBy[0].field", "field", 2),
                new DuplicateQueryProperty("$.limit", "limit", 3));

        QueryInputValidationException failure = assertThrows(
                QueryInputValidationException.class,
                () -> inspector.inspect(
                        Map.of("groupBy", List.of(Map.of("field", "paidAt")), "limit", 20),
                        UnknownQueryPropertyPolicy.STRICT,
                        duplicates));

        assertEquals(SemanticQueryPropertyInspector.DUPLICATE_CODE, failure.getCode());
        assertEquals(List.of("$.groupBy[0].field", "$.limit"),
                failure.getViolations().stream().map(QueryInputWarning::path).toList());
        assertEquals(3, failure.getViolations().get(1).details().get("occurrences"));
    }

    @Test
    @DisplayName("ignore mode drops ordinary diagnostics but still rejects protected properties")
    void ignoreShouldStillFailClosedForProtectedProperties() {
        assertTrue(inspector.inspect(
                Map.of("ordinaryTypo", true), UnknownQueryPropertyPolicy.IGNORE).isEmpty());

        QueryInputValidationException failure = assertThrows(
                QueryInputValidationException.class,
                () -> inspector.inspect(
                        Map.of("groupBy", List.of(Map.of("field", "region", "rute", "bypass"))),
                        UnknownQueryPropertyPolicy.IGNORE));

        assertEquals(SemanticQueryPropertyInspector.PROTECTED_CODE, failure.getCode());
        assertEquals("$.groupBy[0].rute", failure.getViolations().get(0).path());
    }

    @Test
    @DisplayName("dynamic slice shorthand is not mistaken for an unknown property")
    void sliceShorthandShouldRemainValid() {
        Map<String, Object> payload = Map.of(
                "slice", List.of(
                        Map.of("status", "PAID"),
                        Map.of("$or", List.of(
                                Map.of("region", "east"),
                                Map.of("field", "amount", "op", ">", "value", 0)))));

        assertTrue(inspector.inspect(payload, UnknownQueryPropertyPolicy.WARN).isEmpty());
    }

    @Test
    @DisplayName("pivot axis slice alias is inspected recursively")
    void pivotAxisSliceAliasShouldBeInspectedRecursively() {
        Map<String, Object> payload = Map.of(
                "pivot", Map.of(
                        "rows", List.of(Map.of(
                                "field", "region",
                                "slice", List.of(Map.of(
                                        "field", "status",
                                        "op", "=",
                                        "value", "PAID",
                                        "operatorTypo", "ignored")))),
                        "metrics", List.of("amount")));

        List<QueryInputWarning> warnings = inspector.inspect(
                payload, UnknownQueryPropertyPolicy.WARN);

        assertEquals(List.of("$.pivot.rows[0].slice[0].operatorTypo"),
                warnings.stream().map(QueryInputWarning::path).toList());
    }
}
