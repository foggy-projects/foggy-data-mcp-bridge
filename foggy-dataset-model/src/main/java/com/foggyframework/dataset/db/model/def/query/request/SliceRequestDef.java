package com.foggyframework.dataset.db.model.def.query.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class SliceRequestDef extends CondRequestDef {

    public SliceRequestDef(String field, String type, Object value) {
        super(field, type, value);
    }

    /**
     * 创建 OR 条件组
     */
    public static SliceRequestDef or(List<CondRequestDef> conditions) {
        SliceRequestDef def = new SliceRequestDef();
        def.setOr(conditions);
        return def;
    }

    /**
     * 创建 AND 条件组
     */
    public static SliceRequestDef and(List<CondRequestDef> conditions) {
        SliceRequestDef def = new SliceRequestDef();
        def.setAnd(conditions);
        return def;
    }
}
