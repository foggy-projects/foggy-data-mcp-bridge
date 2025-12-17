package com.foggyframework.dataset.jdbc.model.engine.formula;

import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.jdbc.model.spi.JdbcColumn;
import org.springframework.context.ApplicationContext;

import java.util.List;

public class NotEqSqlFormula extends SqlFormulaSupport implements SqlFormula {


    public NotEqSqlFormula(ApplicationContext appCtx) {
        super(appCtx);
    }

    @Override
    public String[] getNameList() {
        return new String[]{"<>","!="};
    }


    @Override
    protected Object buildAndAddListSqlToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, JdbcColumn sqlColumn, String alias, List<Object> values, int link) {
        throwOnlySupportObjectError();
        return null;
    }

    @Override
    protected Object buildAndAddEmptyToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, JdbcColumn sqlColumn, String alias, Object value, int link) {
        return null;
    }

    @Override
    protected Object buildAndAddObjectToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, JdbcColumn jdbcColumn, String alias, Object value, int link) {
        listCond.link(jdbcColumn.buildSqlFragment(appCtx,alias,"<>?"), value,link);
        return null;
    }
}
