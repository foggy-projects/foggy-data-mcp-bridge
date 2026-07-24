package com.foggyframework.dataset.model.engine.formula;

import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.spi.DbColumn;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * 比较运算符公式，支持 >=, >, <=, < 四种比较操作
 */
public class ComparisonSqlFormula extends SqlFormulaSupport implements SqlFormula {

    public ComparisonSqlFormula(ApplicationContext appCtx) {
        super(appCtx);
    }

    @Override
    public String[] getNameList() {
        return new String[]{">=", ">", "<=", "<"};
    }

    @Override
    protected Object buildAndAddListSqlToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, List<Object> values, String link) {
        throwOnlySupportObjectError();
        return null;
    }

    @Override
    protected Object buildAndAddEmptyToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, String link) {
        return null;
    }

    @Override
    protected Object buildAndAddObjectToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn jdbcColumn, String alias, Object value, String link) {
        listCond.link(jdbcColumn.buildSqlFragment(appCtx, alias, type + "?"), value, link);
        return null;
    }
}
