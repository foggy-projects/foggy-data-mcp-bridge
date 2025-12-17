package com.foggyframework.dataset.fsscript;

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
public class LoadSqlTable implements FunDef {

    @Override
    public Object execute(ExpEvaluator ee, Exp[] args) {
        DataSource dataSource = (DataSource) args[0].evalResult(ee);
        String tableName = (String) args[1].evalResult(ee);
        Assert.notNull(dataSource, "数据源不得为空!" + args[0]);
        Assert.notNull(tableName, "表名不得为空！" + args[1]);
//            FDialect.g
        SqlTable sqlTable = DbUtils.getDialect(dataSource).getTableByName(dataSource, tableName,true);
        return sqlTable;
    }

    @Override
    public String getName() {
        return "loadSqlTable";
    }
}
