package com.foggyframework.dataset.jdbc.model.engine.formula;

import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.jdbc.model.spi.JdbcColumn;

public interface SqlFormulaService {
    void buildAndAddToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, JdbcColumn sqlColumn, String alias, Object value, int link);
}
