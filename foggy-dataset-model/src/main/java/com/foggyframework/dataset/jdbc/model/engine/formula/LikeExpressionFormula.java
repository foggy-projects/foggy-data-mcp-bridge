package com.foggyframework.dataset.jdbc.model.engine.formula;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.jdbc.model.spi.JdbcColumn;
import org.springframework.context.ApplicationContext;

import java.util.List;

public class LikeExpressionFormula extends SqlFormulaSupport implements SqlFormula {
    public LikeExpressionFormula(ApplicationContext appCtx) {
        super(appCtx);
    }

    @Override
    public String[] getNameList() {
        return new String[]{"like", "left_like", "right_like"};
    }


    @Override
    protected Object buildAndAddListSqlToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, JdbcColumn jdbcColumn, String alias, List<Object> values, int link) {
        throwOnlySupportListError();
        return null;
    }

    @Override
    protected Object buildAndAddEmptyToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, JdbcColumn sqlColumn, String alias, Object value, int link) {

        return null;
    }

    @Override
    protected Object buildAndAddObjectToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, JdbcColumn sqlColumn, String alias, Object value, int link) {

        if (StringUtils.equals(type, "like")) {
            listCond.link(sqlColumn.buildSqlFragment(appCtx,alias, " like ? "), "%" + value + "%", link);
        } else if (StringUtils.equals(type, "left_like")) {
            listCond.link(sqlColumn.buildSqlFragment(appCtx,alias, " like ? "), "%" + value, link);
        } else if (StringUtils.equals(type, "right_like")) {
            listCond.link(sqlColumn.buildSqlFragment(appCtx,alias, " like ? "), value + "%", link);
        }

        return null;
    }
}
