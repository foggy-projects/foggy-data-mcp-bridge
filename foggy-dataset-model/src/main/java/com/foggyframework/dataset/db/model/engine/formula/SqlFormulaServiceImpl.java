package com.foggyframework.dataset.db.model.engine.formula;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.spi.DbColumn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlFormulaServiceImpl implements SqlFormulaService {
    //    List<SqlFormula> sqlFormulas;
    Map<String, SqlFormula> name2SqlFormula = new HashMap<>();

    public SqlFormulaServiceImpl(List<SqlFormula> sqlFormulas) {
        addAll(sqlFormulas);
    }
//
//    JdbcSqlFormulaCalVisitor visitor = new JdbcSqlFormulaCalVisitor();
    public void addAll(List<SqlFormula> sqlFormulas) {
        for (SqlFormula sqlFormula : sqlFormulas) {
            add(sqlFormula, true);
        }
    }

    private void add(SqlFormula sqlFormula, boolean errorIfExist) {
        if (errorIfExist) {
            for (String s : sqlFormula.getNameList()) {
                if (name2SqlFormula.containsKey(s)) {
                    throw RX.throwAUserTip(DatasetMessages.formulaDuplicate(s));
                }
            }
        }
        for (String s : sqlFormula.getNameList()) {
            name2SqlFormula.put(s,sqlFormula);
        }

    }


@Override
    public void buildAndAddToJdbcCond(JdbcQuery.JdbcListCond listCond, String type, DbColumn jdbcColumn, String alias, Object value, int link){
        SqlFormula sqlFormula = name2SqlFormula.get(type);
        if(sqlFormula==null){
            throw RX.throwAUserTip(DatasetMessages.formulaNotfound(type));
        }
        sqlFormula.buildAndAddToJdbcCond(listCond,type,jdbcColumn,alias,value,link);

//        sqlFormula.
    }

    @Override
    public boolean supports(String operator) {
        return operator != null && name2SqlFormula.containsKey(operator);
    }
//    public JdbcQuery.JdbcCond buildC


}
