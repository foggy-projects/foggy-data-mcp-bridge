package com.foggyframework.dataset.db.model.impl.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("AggregateRelationDiagnosticContractTest")
class AggregateRelationDiagnosticContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("pins aggregate relation diagnostic record and json keys")
    void shouldPinDiagnosticRecordAndJsonKeys() {
        List<String> componentNames = Arrays.stream(AggregateRelationDiagnostic.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        List<String> expectedKeys = List.of("decision", "reasonCode", "field", "op", "target", "expression");
        assertEquals(expectedKeys, componentNames);

        AggregateRelationDiagnostic diagnostic = AggregateRelationDiagnostic.pushed(
                "salesAmount", ">", "having", "sum(agg_src.sales_amount) > ?");
        Map<String, Object> json = objectMapper.convertValue(diagnostic, new TypeReference<>() {
        });

        assertEquals(new LinkedHashSet<>(expectedKeys), json.keySet());
        assertEquals("pushed", json.get("decision"));
        assertNull(json.get("reasonCode"));
        assertEquals("salesAmount", json.get("field"));
        assertEquals(">", json.get("op"));
        assertEquals("having", json.get("target"));
        assertEquals("sum(agg_src.sales_amount) > ?", json.get("expression"));
    }

    @Test
    @DisplayName("pins aggregate relation diagnostic factory semantics")
    void shouldPinDiagnosticFactorySemantics() {
        AggregateRelationDiagnostic pushed = AggregateRelationDiagnostic.pushed(
                "salesAmount", ">", "where", "agg_src.sales_amount > ?");
        assertEquals("pushed", pushed.decision());
        assertNull(pushed.reasonCode());
        assertEquals("salesAmount", pushed.field());
        assertEquals(">", pushed.op());
        assertEquals("where", pushed.target());
        assertEquals("agg_src.sales_amount > ?", pushed.expression());

        AggregateRelationDiagnostic retained = AggregateRelationDiagnostic.retained(
                "salesAmount", "or", AggregateRelationQueryObject.REASON_OR_CONDITION_OUTER_ONLY);
        assertEquals("retained", retained.decision());
        assertEquals(AggregateRelationQueryObject.REASON_OR_CONDITION_OUTER_ONLY, retained.reasonCode());
        assertEquals("salesAmount", retained.field());
        assertEquals("or", retained.op());
        assertEquals("outer", retained.target());
        assertNull(retained.expression());

        AggregateRelationDiagnostic projectionRetained = AggregateRelationDiagnostic.projectionRetained(
                AggregateRelationQueryObject.REASON_RAW_SQL_CONDITION_PROJECTION_PRUNING_DISABLED);
        assertEquals("retained", projectionRetained.decision());
        assertEquals(AggregateRelationQueryObject.REASON_RAW_SQL_CONDITION_PROJECTION_PRUNING_DISABLED,
                projectionRetained.reasonCode());
        assertNull(projectionRetained.field());
        assertEquals("projection", projectionRetained.op());
        assertEquals("projection", projectionRetained.target());
        assertNull(projectionRetained.expression());

        AggregateRelationDiagnostic refused = AggregateRelationDiagnostic.refused(
                "salesAmount", "between", AggregateRelationQueryObject.REASON_INVALID_RANGE_VALUE);
        assertEquals("refused", refused.decision());
        assertEquals(AggregateRelationQueryObject.REASON_INVALID_RANGE_VALUE, refused.reasonCode());
        assertEquals("salesAmount", refused.field());
        assertEquals("between", refused.op());
        assertNull(refused.target());
        assertNull(refused.expression());
    }

    @Test
    @DisplayName("pins aggregate relation diagnostic reason codes")
    void shouldPinDiagnosticReasonCodes() {
        List<String> reasonCodes = List.of(
                AggregateRelationQueryObject.REASON_OR_CONDITION_OUTER_ONLY,
                AggregateRelationQueryObject.REASON_NULL_CHECK_OUTER_ONLY,
                AggregateRelationQueryObject.REASON_UNSUPPORTED_OPERATOR,
                AggregateRelationQueryObject.REASON_EMPTY_IN_VALUES,
                AggregateRelationQueryObject.REASON_INVALID_RANGE_VALUE,
                AggregateRelationQueryObject.REASON_NULL_VALUE_UNSUPPORTED,
                AggregateRelationQueryObject.REASON_NO_AGGREGATE_EXPRESSION,
                AggregateRelationQueryObject.REASON_NO_JOIN_KEY_MAPPING,
                AggregateRelationQueryObject.REASON_RAW_SQL_CONDITION_PROJECTION_PRUNING_DISABLED);

        assertEquals(List.of(
                "OR_CONDITION_OUTER_ONLY",
                "NULL_CHECK_OUTER_ONLY",
                "UNSUPPORTED_OPERATOR",
                "EMPTY_IN_VALUES",
                "INVALID_RANGE_VALUE",
                "NULL_VALUE_UNSUPPORTED",
                "NO_AGGREGATE_EXPRESSION",
                "NO_JOIN_KEY_MAPPING",
                "RAW_SQL_CONDITION_PROJECTION_PRUNING_DISABLED"), reasonCodes);
        assertEquals(reasonCodes.size(), new LinkedHashSet<>(reasonCodes).size(),
                "aggregate relation diagnostic reason codes must be unique");
    }
}
