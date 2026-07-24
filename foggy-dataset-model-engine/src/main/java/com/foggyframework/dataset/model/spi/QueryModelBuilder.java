package com.foggyframework.dataset.model.spi;

import com.foggyframework.dataset.model.def.query.DbQueryModelDef;
import com.foggyframework.dataset.model.engine.query_model.QueryModelSupport;
import com.foggyframework.fsscript.parser.spi.Fsscript;

import java.util.List;

public interface QueryModelBuilder {
//    QueryModelSupport build(DbQueryModelDef queryModelDef, Fsscript fsscript, List<TableModel> jdbcModelDxList);
    QueryModelSupport build(DbQueryModelDef queryModelDef, Fsscript fsscript);
}
