package com.foggyframework.dataset.db.model.semantic.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private void assertInvalidSliceElement(Object element, String expectedMessagePart) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("slice", List.of(element));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> mapper.toQueryRequest(payload));

        assertTrue(exception.getMessage().contains("QUERY_MODEL_SLICE_CONTRACT_INVALID"),
                exception.getMessage());
        assertTrue(exception.getMessage().contains(expectedMessagePart), exception.getMessage());
    }
}
