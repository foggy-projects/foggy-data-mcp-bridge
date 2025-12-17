package com.foggyframework.dataset.jdbc.model.def.query.request;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public class SliceRequestDef extends CondRequestDef {

    public SliceRequestDef(String field, String type, Object value, int link, List<CondRequestDef> children) {
        super(field, type, value, link, children);
    }

    public SliceRequestDef(String field, String type, Object value) {
        super(field, type, value);
    }
}
