package com.foggyframework.dataset.jdbc.model.engine.formula;

import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.jdbc.model.spi.JdbcColumn;
import org.springframework.context.ApplicationContext;

public interface SqlFormula {
    String[] getNameList();

    Object buildAndAddToJdbcCond( JdbcQuery.JdbcListCond listCond, String type, JdbcColumn sqlColumn, String alias, Object value, int link);


}
