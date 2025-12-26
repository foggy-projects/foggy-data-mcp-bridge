package com.foggyframework.dataset.jdbc.model.engine.formula;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.jdbc.model.spi.DbColumn;
import org.springframework.context.ApplicationContext;

import java.util.List;

public class NotLikeExpressionFormula extends SqlFormulaSupport implements SqlFormula {
    public NotLikeExpressionFormula(ApplicationContext appCtx) {
        super(appCtx);
    }

    @Override
    public String[] getNameList() {
        return new String[]{"not like", "not left_like", "not right_like"};
    }


    @Override
    protected Object buildAndAddListSqlToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn jdbcColumn, String alias, List<Object> values, int link) {
        throwOnlySupportListError();
        return null;
    }

    @Override
    protected Object buildAndAddEmptyToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, int link) {

        return null;
    }

    @Override
    protected Object buildAndAddObjectToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, int link) {

        if (StringUtils.equals(type, "not like")) {
            listCond.link(sqlColumn.buildSqlFragment(appCtx,alias, " not like ? "), "%" + value + "%", link);
        } else if (StringUtils.equals(type, "not left_like")) {
            listCond.link(sqlColumn.buildSqlFragment(appCtx,alias, "not like ? "), "%" + value, link);
        } else if (StringUtils.equals(type, "not right_like")) {
            listCond.link(sqlColumn.buildSqlFragment(appCtx,alias, "not like ? "), value + "%", link);
        }

        return null;
    }
}
