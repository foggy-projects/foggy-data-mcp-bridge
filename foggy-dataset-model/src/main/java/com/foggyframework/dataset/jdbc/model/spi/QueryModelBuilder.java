package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.dataset.jdbc.model.def.query.JdbcQueryModelDef;
import com.foggyframework.dataset.jdbc.model.engine.query_model.QueryModelSupport;
import com.foggyframework.fsscript.parser.spi.Fsscript;

import java.util.List;

public interface QueryModelBuilder {
    QueryModelSupport build(JdbcQueryModelDef queryModelDef, Fsscript fsscript, List<TableModel> jdbcModelDxList);
}
