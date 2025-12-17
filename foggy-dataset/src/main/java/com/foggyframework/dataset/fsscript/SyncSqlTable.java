package com.foggyframework.dataset.fsscript;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.utils.JdbcTableUtils;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * 同步表结构，第一个参数是DataSource,第二个参数是表结构定义，
 * 如果第二个参数是String，则直接从数据获取表结构.
 * 返回SqlTable对象
 */
public class SyncSqlTable implements FunDef {

    @Override
    public Object execute(ExpEvaluator ee, Exp[] args)  {
        DataSource dataSource = (DataSource) args[0].evalResult(ee);
        Object table = args[1].evalResult(ee);

        try {
            return syncSqlTable(dataSource,table);
        } catch (SQLException e) {
            throw RX.throwB(e);
        }
    }

    public static SqlTable syncSqlTable(DataSource dataSource, Object table) throws SQLException {
        if (table instanceof SqlTable) {
//            return (SqlTable) table;
            //set not null
            for (SqlColumn sqlColumn : ((SqlTable) table).getSqlColumns()) {
                sqlColumn.setNullable(true);
            }

            return JdbcTableUtils.createOrUpdateSqlTable(dataSource, (SqlTable)table);
        }

        return JdbcTableUtils.createOrUpdateTable(dataSource, table);
    }

    @Override
    public String getName() {
        return "synSqlTable";
    }
}
