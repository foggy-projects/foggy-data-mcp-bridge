package com.foggyframework.dataset.jdbc.model.engine.formula;

import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.jdbc.model.spi.DbColumn;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.List;

public class IsNullAndEmptySqlFormula extends SqlFormulaSupport implements SqlFormula {


    public IsNullAndEmptySqlFormula(ApplicationContext appCtx) {
        super(appCtx);
    }

    @Override
    public String[] getNameList() {
        return new String[]{"isNullAndEmpty"};
    }


    @Override
    protected Object buildAndAddListSqlToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, List<Object> values, int link) {
        xx(listCond, sqlColumn, alias, link);
        return null;
    }

    @Override
    protected Object buildAndAddEmptyToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, int link) {
        xx(listCond, sqlColumn, alias, link);
        return null;
    }

    @Override
    protected Object buildAndAddObjectToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn jdbcColumn, String alias, Object value, int link) {
        xx(listCond, jdbcColumn, alias, link);
        return null;
    }

    private void xx(JdbcQuery.JdbcListCond listCond, DbColumn jdbcColumn, String alias, int link) {
        listCond.listLink("(" + jdbcColumn.buildSqlFragment(appCtx,alias, "is null") +
                " or " + jdbcColumn.buildSqlFragment(appCtx,alias, "=''") + ")", Collections.EMPTY_LIST, link);
    }
}
