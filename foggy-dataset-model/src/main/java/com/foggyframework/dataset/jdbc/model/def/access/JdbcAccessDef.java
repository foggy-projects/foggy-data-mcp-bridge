package com.foggyframework.dataset.jdbc.model.def.access;

import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JdbcAccessDef {
    String dimension;

    String property;

    FsscriptFunction dimensionDataSql;

    FsscriptFunction queryBuilder;

}
