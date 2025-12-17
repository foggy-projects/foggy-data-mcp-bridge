package com.foggyframework.dataset.db;

import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;

import java.sql.SQLException;

public interface DbUpdater {

    int MODE_NORMAL = 1;

    int MODE_SKIP_ERROR = 2;

    void addDbObject(DbObject dbObject);

    void addCreateScript(DbObject dbObject);

    void addModifyScript(DbObject dbObject);

    void clear();

    void execute(int mode) throws SQLException;

    void addIndex(SqlTable st, SqlColumn column);

}
