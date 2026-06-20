package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Normalizes semantic request fragments into the JDBC request model.
 */
public final class SemanticRequestNormalizer {

    private SemanticRequestNormalizer() {
    }

    public static List<SliceRequestDef> toJdbcSlices(List<SemanticQueryRequest.SliceItem> items) {
        if (items == null) {
            return null;
        }
        return items.stream()
                .map(SemanticRequestNormalizer::toJdbcSlice)
                .collect(Collectors.toList());
    }

    public static SliceRequestDef toJdbcSlice(SemanticQueryRequest.SliceItem item) {
        if (item == null) {
            return null;
        }
        if (item._isLogicalGroup()) {
            SliceRequestDef groupDef = new SliceRequestDef();
            List<CondRequestDef> children = new ArrayList<>();
            for (SemanticQueryRequest.SliceItem child : item._getGroupChildren()) {
                children.add(toJdbcSlice(child));
            }
            if (item._isOrGroup()) {
                groupDef.setOr(children);
            } else {
                groupDef.setAnd(children);
            }
            return groupDef;
        }

        SliceRequestDef slice = new SliceRequestDef();
        slice.setField(item.getField());
        slice.setOp(item.getOp());
        slice.setValue(item.getValue());
        return slice;
    }
}
