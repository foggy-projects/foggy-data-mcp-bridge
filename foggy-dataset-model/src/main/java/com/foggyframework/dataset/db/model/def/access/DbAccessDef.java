package com.foggyframework.dataset.db.model.def.access;

import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DbAccessDef {
    String dimension;

    String property;

    FsscriptFunction dimensionDataSql;

    FsscriptFunction queryBuilder;

}
