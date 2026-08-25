package com.foggyframework.dataset.model.semantic.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SemanticQueryPayloadMapper slice contract")
class SemanticQueryPayloadMapperTest {

    private final SemanticQueryPayloadMapper mapper = new SemanticQueryPayloadMapper(new ObjectMapper());

    @Test
    @DisplayName("valid logical slice group should be preserved")
    void validLogicalSliceGroupShouldBePreserved() {
        Map<String, Object> payload = Map.of(
                "slice",
                List.of(Map.of("$or", List.of(
                        Map.of("field", "unsettledRevenue", "op", ">", "value", 0),
                        Map.of("field", "unsettledCost", "op", ">", "value", 0)
                )))
        );

        SemanticQueryRequest request = mapper.toQueryRequest(payload);

        assertNotNull(request.getSlice());
        assertEquals(1, request.getSlice().size());
        SemanticQueryRequest.SliceItem group = request.getSlice().get(0);
        assertNotNull(group.getOr());
        assertEquals(2, group.getOr().size());
        assertEquals("unsettledRevenue", group.getOr().get(0).getField());
        assertEquals("unsettledCost", group.getOr().get(1).getField());
    }

    @Test
    @DisplayName("slice boolean element should fail closed")
    void sliceBooleanElementShouldFailClosed() {
        assertInvalidSliceElement(Boolean.TRUE, "payload.slice[0]");
    }

    @Test
    @DisplayName("slice stringified json element should fail closed")
    void sliceStringifiedJsonElementShouldFailClosed() {
        assertInvalidSliceElement("{\"$or\":[{\"field\":\"amount\",\"op\":\">\",\"value\":0}]}",
                "got string");
    }

    @Test
    @DisplayName("slice escaped logic key should fail closed")
    void sliceEscapedLogicKeyShouldFailClosed() {
        assertInvalidSliceElement(Map.of("\"$or\"", List.of(
                Map.of("field", "amount", "op", ">", "value", 0)
        )), "invalid key");
    }

    @Test
    @DisplayName("slice logical group value must be object array")
    void sliceLogicalGroupValueShouldBeObjectArray() {
        assertInvalidSliceElement(Map.of("$or", "{\"field\":\"amount\",\"op\":\">\",\"value\":0}"),
                "payload.slice[0].$or");
    }

    @Test
    @DisplayName("pivot row and column string shorthand should map to axis fields")
    void pivotStringAxisShorthandShouldMapToAxisFields() {
        Map<String, Object> payload = Map.of(
                "pivot", Map.of(
                        "rows", List.of("orderStatus"),
                        "columns", List.of("orderDate$month"),
                        "metrics", List.of(
                                "payAmount",
                                Map.of(
                                        "name", "monthIndex",
                                        "type", "baselineRatio",
                                        "of", "payAmount",
                                        "axis", "columns",
                                        "baseline", "first")),
                        "outputFormat", "flat"
                )
        );

        SemanticQueryRequest request = mapper.toQueryRequest(payload);

        assertNotNull(request.getPivot());
        assertEquals("orderStatus", request.getPivot().getRows().get(0).getField());
        assertEquals("orderDate$month", request.getPivot().getColumns().get(0).getField());
        assertEquals("first", request.getPivot().getMetricItems().get(1).getBaseline());
        assertEquals("flat", request.getPivot().getOutputFormat());
    }

    @Test
    @DisplayName("all public shorthand and nested filter metadata should be preserved")
    void publicShorthandAndNestedFilterMetadataShouldBePreserved() {
        Map<String, Object> payload = Map.of(
                "groupBy", List.of("customer$id", Map.of("field", "customer$caption", "agg", "PK")),
                "orderBy", List.of("-payAmount", Map.of(
                        "field", "customer$caption",
                        "dir", "asc",
                        "nullLast", true)),
                "slice", List.of(
                        Map.of("status", "PAID"),
                        Map.of(
                                "field", "org$id",
                                "op", "descendantsOf",
                                "value", "root",
                                "maxDepth", 3),
                        Map.of("$expr", "payAmount > costAmount")));

        SemanticQueryRequest request = mapper.toQueryRequest(payload);

        assertEquals("customer$id", request.getGroupBy().get(0).getField());
        assertNull(request.getGroupBy().get(0).getAgg());
        assertEquals("PK", request.getGroupBy().get(1).getAgg());
        assertEquals("desc", request.getOrderBy().get(0).getDir());
        assertEquals(Boolean.TRUE, request.getOrderBy().get(1).getNullLast());
        assertEquals("status", request.getSlice().get(0).getField());
        assertEquals("=", request.getSlice().get(0).getOp());
        assertEquals(3, request.getSlice().get(1).getMaxDepth());
        assertEquals("payAmount > costAmount", request.getSlice().get(2).getExpr());
        assertEquals(3, SemanticRequestNormalizer.toJdbcSlices(request.getSlice())
                .get(1).getMaxDepth());
        assertEquals("payAmount > costAmount",
                SemanticRequestNormalizer.toJdbcSlices(request.getSlice()).get(2).getExpr());
    }

    private void assertInvalidSliceElement(Object element, String expectedMessagePart) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("slice", List.of(element));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> mapper.toQueryRequest(payload));

        assertTrue(exception.getMessage().contains("QUERY_MODEL_SLICE_CONTRACT_INVALID"),
                exception.getMessage());
        assertTrue(exception.getMessage().contains(expectedMessagePart), exception.getMessage());
    }
}
