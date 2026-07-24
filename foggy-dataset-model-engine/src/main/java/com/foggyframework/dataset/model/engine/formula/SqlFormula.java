package com.foggyframework.dataset.model.engine.formula;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.spi.DbColumn;

public interface SqlFormula {
    String[] getNameList();

    Object buildAndAddToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, String link);

    default Object buildAndAddToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn,
                                         String alias, Object value, String link, FDialect dialect) {
        return buildAndAddToJdbcCond(listCond, type, sqlColumn, alias, value, link);
    }

}
