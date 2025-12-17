package com.foggyframework.dataset.fsscript;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.table.EditSqlTable;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 *
 */
public class BuildEditSqlTable implements FunDef {

    @Override
    public Object execute(ExpEvaluator ee, Exp[] args)  {
        DataSource dataSource = (DataSource) args[0].evalResult(ee);
        Object table = args[1].evalResult(ee);

        try {
            SqlTable sqlTable  = SyncSqlTable.syncSqlTable(dataSource, table);
            return new EditSqlTable(sqlTable,dataSource);
        } catch (SQLException e) {
            throw RX.throwB(e);
        }

    }

    @Override
    public String getName() {
        return "buildEditSqlTable";
    }
}
