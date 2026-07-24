package com.foggyframework.dataset.model.semantic.member;

import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import lombok.Data;

import java.util.List;

/**
 * 外部系统对 synthetic member-QM 注入的轻量 patch。
 */
@Data
public class SyntheticMemberExternalPatch {

    public static final String EXT_DATA_KEY = "syntheticMemberPatch";

    private List<String> visibleColumns;

    private List<SliceRequestDef> forcedSlice;

    private List<OrderRequestDef> forcedOrderBy;

    public boolean isEmpty() {
        return (visibleColumns == null || visibleColumns.isEmpty())
                && (forcedSlice == null || forcedSlice.isEmpty())
                && (forcedOrderBy == null || forcedOrderBy.isEmpty());
    }
}
