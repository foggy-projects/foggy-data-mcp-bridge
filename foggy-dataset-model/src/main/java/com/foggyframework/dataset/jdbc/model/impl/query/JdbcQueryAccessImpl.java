package com.foggyframework.dataset.jdbc.model.impl.query;

import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public  class JdbcQueryAccessImpl {

    String dimension;

    FsscriptFunction dimensionDataSql;

    FsscriptFunction queryBuilder;

}
