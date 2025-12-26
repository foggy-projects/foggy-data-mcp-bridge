package com.foggyframework.dataset.db.model.engine.formula;

import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.spi.DbColumn;

public interface SqlFormula {
    String[] getNameList();

    Object buildAndAddToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, int link);


}
