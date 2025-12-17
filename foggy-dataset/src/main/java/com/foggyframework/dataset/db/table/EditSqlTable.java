package com.foggyframework.dataset.db.table;


import com.foggyframework.dataset.db.data.dll.SqlTableRowEditor;
import com.foggyframework.dataset.resultset.ListResultSet;
import com.foggyframework.dataset.resultset.Record;
import lombok.Getter;
import lombok.Setter;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;
@Getter
@Setter
public class EditSqlTable {
    SqlTableRowEditor editSqlTable;
    DataSource ds;
    boolean etlTime = false;

    public void setIdColumn(SqlColumn idColumn) {
        editSqlTable.setIdColumn(idColumn);
    }

    public void setIdColumnByName(String name) {
        editSqlTable.setIdColumnByName(name);

    }

    public boolean hasSqlColumn(String name) {
        return editSqlTable.getSqlTable().getSqlColumn(name, false) != null;
    }

    public EditSqlTable(SqlTable st, DataSource ds) {
        this.editSqlTable = new SqlTableRowEditor(st, ds);
        this.ds = ds;
    }

    public SqlTable getSqlTable() {
        return editSqlTable.getSqlTable();
    }

    public void insertRs(ListResultSet rs) throws SQLException {
        editSqlTable.insertRs(ds, rs, true);
    }

    public void insertRsRecord(Record record) throws SQLException {
        editSqlTable.insertRsRecord(ds, record, true);
    }

    private void fix(Map<String, Object> row, Date date) {
        row.put("etl_time", date);
    }

    private void fix(Map<String, Object> row) {
        if (etlTime) {
            fix(row, new Date());
        }
    }

    private void fix(List<Map<String, Object>> row) {
        if (etlTime) {
            Date d = new Date();
            for (Map<String, Object> m : row) {
                fix(m, d);
            }
        }
    }

    /**
     * 对于mysql来说，会采用 ON DUPLICATE KEY来解决
     *
     * @param rs
     * @throws SQLException
     */
    public void insertUpdateRs(ListResultSet rs, Map<String, Object> config) throws SQLException {
        editSqlTable.insertUpdateRs(ds, rs, config, true);
    }

    public void insertUpdateRowByMap(Map<String, Object> row) throws SQLException {
        fix(row);
        editSqlTable.insertUpdateMapData(ds, row, null, true);
    }

    public void insertUpdateRowByMap(Map<String, Object> row, Map<String, Object> configs) throws SQLException {
        fix(row);
        editSqlTable.insertUpdateMapData(ds, row, configs, true);
    }

    public void insertUpdateByListMap(List<Map<String, Object>> row) throws SQLException {
        fix(row);
        editSqlTable.insertUpdateByListMap(ds, row, null, false);
    }

    public void insertUpdateByListMap(List<Map<String, Object>> row, Map<String, Object> configs) throws SQLException {
        fix(row);
        editSqlTable.insertUpdateByListMap(ds, row, configs, false);
    }

    public void deleteByColumn(String columnName, Object value) throws SQLException {
        editSqlTable.deleteByColumn(ds, columnName, value);
    }

    public void deleteByRs(ListResultSet rs) throws SQLException {
        System.err.println("开始删除RS，共 " + rs.getRecords().size() + "条");
        while (rs.next()) {
            editSqlTable.deleteByRecord(ds, rs.getRecord());
        }

    }

    public void clear() throws SQLException {
        editSqlTable.clear(ds);
    }
}
