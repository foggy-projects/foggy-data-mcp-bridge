package com.foggyframework.dataset.db.data.dll;


import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j

public class InsertBuilder extends RowEditBuilder {

    public List<IdxSqlColumn> columns;

    public InsertBuilder(SqlTable sqlTable) {
        super(sqlTable);
    }

    /**
     * @param idx       start 1
     * @param sqlColumn 列
     */
    public void addColumn(int idx, SqlColumn sqlColumn) {
        if (columns == null) {
            columns = new ArrayList<>();
        }
        columns.add(new IdxSqlColumn(sqlColumn, idx));
    }

    public String genSql() {
        StringBuilder root = new StringBuilder();

        genSql(root);
        return root.toString();
    }

    public void genSql(StringBuilder root) {

        StringBuilder insertSql = new StringBuilder("insert into " + sqlTable.getName() + " (");
        if (columns == null) {
            throw RX.throwB("生成insert sql失败，没有columns。sql:" + insertSql);
        }
        int s = columns.size();
        StringBuilder values = new StringBuilder(" values (");
        for (IdxSqlColumn idxSqlColumn : columns) {
            SqlColumn sc = idxSqlColumn.sqlColumn;
            insertSql.append(sc.getName());
            values.append("?");
            if (s != 1) {
                insertSql.append(",");
                values.append(",");
            }
            s--;
        }
        insertSql.append(")").append(values).append(")");
        if (log.isDebugEnabled()) {
            log.debug("gen insertSql :" + insertSql);
        }
        root.append(insertSql);

    }

}
