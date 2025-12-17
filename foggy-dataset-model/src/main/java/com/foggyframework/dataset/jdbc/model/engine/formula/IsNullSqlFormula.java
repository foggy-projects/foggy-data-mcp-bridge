package com.foggyframework.dataset.jdbc.model.engine.formula;

import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.jdbc.model.spi.JdbcColumn;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.List;

public class IsNullSqlFormula extends SqlFormulaSupport implements SqlFormula {


    public IsNullSqlFormula(ApplicationContext appCtx) {
        super(appCtx);
    }

    @Override
    public String[] getNameList() {
        return new String[]{"isNull","is null"};
    }


    @Override
    protected Object buildAndAddListSqlToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, JdbcColumn sqlColumn, String alias, List<Object> values, int link) {
        listCond.listLink(sqlColumn.buildSqlFragment(appCtx,alias,"is null"), Collections.EMPTY_LIST,link);
        return null;
    }

    @Override
    protected Object buildAndAddEmptyToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, JdbcColumn sqlColumn, String alias, Object value, int link) {
        listCond.listLink(sqlColumn.buildSqlFragment(appCtx,alias,"is null"), Collections.EMPTY_LIST,link);
        return null;
    }

    @Override
    protected Object buildAndAddObjectToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, JdbcColumn jdbcColumn, String alias, Object value, int link) {
        listCond.listLink(jdbcColumn.buildSqlFragment(appCtx,alias,"is null"), Collections.EMPTY_LIST,link);
        return null;
    }
}
