package com.foggyframework.dataset.fsscript;

import com.foggyframework.dataset.db.data.dll.SqlTableRowEditor;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.utils.DbUtils;

import javax.sql.DataSource;
import java.sql.SQLException;

public class DataSetFsscriptUtils {

    public SqlTableRowEditor newSqlTableRowEditor(Object table) {
        if (table instanceof SqlTable) {
            return new SqlTableRowEditor((SqlTable) table);
        }
        throw new UnsupportedOperationException("仅支持参数为SqlTable");
    }

    public SqlTableRowEditor newSqlTableRowEditor(DataSource dataSource, Object table) throws SQLException {
        if (table instanceof SqlTable) {
            return new SqlTableRowEditor((SqlTable) table,dataSource);
        } else if (table instanceof String) {
            SqlTable sqlTable = DbUtils.getDialect(dataSource).getTableByName(dataSource, (String) table, true);
            return new SqlTableRowEditor(sqlTable,dataSource);
        } else if (table != null) {
            SqlTable sqlTable = SyncSqlTable.syncSqlTable(dataSource, table);
            return new SqlTableRowEditor(sqlTable,dataSource);
        } else {
            return null;
        }
    }


}
