package com.foggyframework.dataset.jdbc.model.engine.formula;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.jdbc.model.spi.DbColumn;
import org.springframework.context.ApplicationContext;

import java.util.List;

public class RangeExpressionFormula extends SqlFormulaSupport implements SqlFormula {
    public RangeExpressionFormula(ApplicationContext appCtx) {
        super(appCtx);
    }

    @Override
    public String[] getNameList() {
        return new String[]{"[]", "[)", "(]", "()"};
    }


    @Override
    protected Object buildAndAddListSqlToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn jdbcColumn, String alias, List<Object> values, int link) {

        int f = type.charAt(0);
        int e = type.charAt(1);
        Object startValue = values.get(0);
        Object endValue = values.get(1);
        if (StringUtils.isNotEmpty(startValue)) {
            switch (f) {
                case '[':
                    listCond.link(jdbcColumn.buildSqlFragment(appCtx,alias,">=?"), startValue,link);
                    break;
                case '(':
                    listCond.link(jdbcColumn.buildSqlFragment(appCtx,alias,">?"), startValue,link);
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }
        if (StringUtils.isNotEmpty(endValue)) {
            switch (e) {
                case ']':
                    listCond.link(jdbcColumn.buildSqlFragment(appCtx,alias,"<=?"), endValue,link);
                    break;
                case ')':
                    listCond.link(jdbcColumn.buildSqlFragment(appCtx,alias,"<?"), endValue,link);
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }
        return null;
    }

    @Override
    protected Object buildAndAddEmptyToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, int link) {
        return null;
    }

    @Override
    protected Object buildAndAddObjectToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, int link) {
        throwOnlySupportListError();
        return null;
    }
}
