package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SemanticRequestNormalizerTest {

    @Test
    @DisplayName("Normalizer converts flat semantic slice items to JDBC slice defs")
    void convertsFlatSliceItems() {
        SemanticQueryRequest.SliceItem item = slice("status", "=", "COMPLETED");

        List<SliceRequestDef> result = SemanticRequestNormalizer.toJdbcSlices(List.of(item));

        assertEquals(1, result.size());
        assertEquals("status", result.get(0).getField());
        assertEquals("=", result.get(0).getOp());
        assertEquals("COMPLETED", result.get(0).getValue());
        assertNull(result.get(0).getOr());
        assertNull(result.get(0).getAnd());
    }

    @Test
    @DisplayName("Normalizer recursively converts OR and AND semantic slice groups")
    void convertsLogicalSliceGroups() {
        SemanticQueryRequest.SliceItem orGroup = new SemanticQueryRequest.SliceItem();
        orGroup.setOr(List.of(
                slice("status", "=", "COMPLETED"),
                andGroup(slice("amount", ">", 100), slice("region", "=", "East"))));

        SliceRequestDef result = SemanticRequestNormalizer.toJdbcSlice(orGroup);

        assertNotNull(result.getOr());
        assertEquals(2, result.getOr().size());
        assertEquals("status", result.getOr().get(0).getField());
        assertNotNull(result.getOr().get(1).getAnd());
        assertEquals("amount", result.getOr().get(1).getAnd().get(0).getField());
        assertEquals("region", result.getOr().get(1).getAnd().get(1).getField());
    }

    private SemanticQueryRequest.SliceItem andGroup(SemanticQueryRequest.SliceItem... children) {
        SemanticQueryRequest.SliceItem group = new SemanticQueryRequest.SliceItem();
        group.setAnd(List.of(children));
        return group;
    }

    private SemanticQueryRequest.SliceItem slice(String field, String op, Object value) {
        SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
        item.setField(field);
        item.setOp(op);
        item.setValue(value);
        return item;
    }
}
