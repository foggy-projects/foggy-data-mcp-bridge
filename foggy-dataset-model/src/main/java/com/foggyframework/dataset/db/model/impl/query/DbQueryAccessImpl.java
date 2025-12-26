package com.foggyframework.dataset.db.model.impl.query;

import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public  class DbQueryAccessImpl {

    String dimension;

    FsscriptFunction dimensionDataSql;

    FsscriptFunction queryBuilder;

}
