package com.foggyframework.dataset.utils;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@AllArgsConstructor
@ToString
public class SqlTableBuilder {
    String name;
    //    SqlTable sqlTable;
    SqlColumnBuilder idColumnBuilder;
    List<SqlColumnBuilder> columnBuilders;

    public SqlTable buildSqlTable() {
        SqlTable sqlTable = new SqlTable(name);

        if (idColumnBuilder != null) {
            SqlColumn sqlColumn = buildSqlColumn(sqlTable, idColumnBuilder);
            sqlTable.setIdColumn(sqlColumn);
        }
        for (SqlColumnBuilder columnBuild : columnBuilders) {
            buildSqlColumn(sqlTable, columnBuild);
        }
        return sqlTable;
    }

    SqlColumn buildSqlColumn(SqlTable sqlTable, SqlColumnBuilder columnBuild) {
        SqlColumn column = new SqlColumn(columnBuild.name, columnBuild.name, columnBuild.type, columnBuild.length,columnBuild.defaultValue);
        sqlTable.addSqlColumn(column);
        return column;

    }

    public SqlColumnBuilder getColumnBuilderByName(String name) {
        return columnBuilders.stream().filter(e ->
                StringUtils.equals(e.getName(), name)
        ).findFirst().orElse(null);
    }
}
