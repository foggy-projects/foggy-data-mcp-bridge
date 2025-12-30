package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.db.model.def.query.DbQueryModelDef;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelSupport;
import com.foggyframework.fsscript.parser.spi.Fsscript;

import java.util.List;

public interface QueryModelBuilder {
//    QueryModelSupport build(DbQueryModelDef queryModelDef, Fsscript fsscript, List<TableModel> jdbcModelDxList);
    QueryModelSupport build(DbQueryModelDef queryModelDef, Fsscript fsscript);
}
