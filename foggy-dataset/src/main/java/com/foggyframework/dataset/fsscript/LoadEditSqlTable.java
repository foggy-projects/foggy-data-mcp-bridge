package com.foggyframework.dataset.fsscript;

import com.foggyframework.dataset.db.table.EditSqlTable;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.utils.DbUtils;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import org.springframework.util.Assert;

import javax.sql.DataSource;

/**
 *
 */
public class LoadEditSqlTable implements FunDef {

    @Override
    public Object execute(ExpEvaluator ee, Exp[] args) {
        DataSource dataSource = (DataSource) args[0].evalResult(ee);
        Object table =args[1].evalResult(ee);
        Assert.notNull(dataSource, "数据源不得为空!" + args[0]);
        Assert.notNull(table, "表不得为空！" + args[1]);
//            FDialect.g
        if( table instanceof SqlTable){
           return new EditSqlTable((SqlTable) table, dataSource);
        }
        SqlTable sqlTable = DbUtils.getDialect(dataSource).getTableByName(dataSource, (String) table,true);
        return new EditSqlTable(sqlTable, dataSource);

    }

    @Override
    public String getName() {
        return "loadEditSqlTable";
    }
}
