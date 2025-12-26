package com.foggyframework.dataset.jdbc.model.engine.formula;

import com.foggyframework.dataset.jdbc.model.common.query.CondType;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.jdbc.model.spi.DbColumn;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BitInExpressionFormula extends SqlFormulaSupport implements SqlFormula {

    public BitInExpressionFormula(ApplicationContext appCtx) {
        super(appCtx);
    }

    @Override
    public String[] getNameList() {
        return new String[]{CondType.BIT_IN.getCode()};
    }


    @Override
    protected Object buildAndAddListSqlToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn jdbcColumn, String alias, List<Object> values, int link) {
        if (values.isEmpty()) {
            return null;
        }

        long flag = 0;
        for (Object value : values) {
            if (value instanceof Number) {
                flag |= ((Number) value).longValue();
            }
        }

        String sb = "  & " + flag + " = " + flag;

        listCond.listLink(jdbcColumn.buildSqlFragment(appCtx,alias, sb), Collections.EMPTY_LIST, link);
        return null;
    }

    @Override
    protected Object buildAndAddEmptyToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, int link) {

        return null;
    }

    @Override
    protected Object buildAndAddObjectToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn sqlColumn, String alias, Object value, int link) {


        return buildAndAddListSqlToJdbcCond(listCond, type, sqlColumn, alias, Arrays.asList(value), link);
    }
}
