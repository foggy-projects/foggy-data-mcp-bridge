package com.foggyframework.dataset.jdbc.model.engine.formula;

import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.jdbc.model.spi.JdbcColumn;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.List;

public class NotInExpressionFormula extends SqlFormulaSupport implements SqlFormula {
    public NotInExpressionFormula(ApplicationContext appCtx) {
        super(appCtx);
    }

    @Override
    public String[] getNameList() {
        return new String[]{"not in","nin"};
    }


    @Override
    protected Object buildAndAddListSqlToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, JdbcColumn jdbcColumn, String alias, List<Object> values, int link) {
        if(values.isEmpty()){
            return null;
        }

        StringBuilder sb = new StringBuilder(" not in (");
        int i = 0;
        for (Object obj : values) {
            if(i>0){
                sb.append(",");
            }
            sb.append("?");
            i++;
        }
        sb.append(")");
        listCond.listLink(jdbcColumn.buildSqlFragment(appCtx,alias,sb.toString()), values, link);
        return null;
    }

    @Override
    protected Object buildAndAddEmptyToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, JdbcColumn sqlColumn, String alias, Object value, int link) {

        return null;
    }

    @Override
    protected Object buildAndAddObjectToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, JdbcColumn sqlColumn, String alias, Object value, int link) {


        return buildAndAddListSqlToJdbcCond(listCond, type, sqlColumn, alias, Arrays.asList(value), link);
    }
}
