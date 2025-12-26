package com.foggyframework.dataset.db.model.def.column;

import com.foggyframework.dataset.db.model.def.query.SelectColumnDef;
import lombok.Data;

import java.util.List;

@Data
public class DbColumnGroupDef {
    String caption;

    List<SelectColumnDef> items;
}
