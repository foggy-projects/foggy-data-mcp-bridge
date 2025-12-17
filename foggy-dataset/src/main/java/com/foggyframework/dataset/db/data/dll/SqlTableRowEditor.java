package com.foggyframework.dataset.db.data.dll;


import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.resultset.ListResultSet;
import com.foggyframework.dataset.resultset.ListResultSetMetaData;
import com.foggyframework.dataset.resultset.Record;
import com.foggyframework.dataset.resultset.support.SqlUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Slf4j
public class SqlTableRowEditor {
    SqlTable sqlTable;

    DataSource dataSource;

    public SqlTableRowEditor(SqlTable st) {
        this.sqlTable = st;
    }

    public SqlTableRowEditor(SqlTable sqlTable, DataSource dataSource) {
        this.sqlTable = sqlTable;
        this.dataSource = dataSource;
    }
    //    OnDuplicateKeyBuilder onDuplicateKeyBuilder;

//    String onDuplicateInsertSql;

    public void setIdColumn(SqlColumn idColumn) {
        sqlTable.setIdColumn(idColumn);
    }

    public void setIdColumnByName(String name) {
        sqlTable.setIdColumnByName(name);
    }


    public int insertRs(DataSource ds, ListResultSet rs, boolean commitByRow) throws SQLException {
        return insertRs1(ds, rs, true);
    }

    public void insertRs(ListResultSet rs, boolean commitByRow) throws SQLException {
        insertRs(dataSource, rs, true);
    }

    public OnDuplicateKeyBuilderKey buildInsertOnDuplicateKey(Map<String, Object> columns, Map<String, Object> configs) {

        return buildInsertOnDuplicateKey1(columns.keySet(), configs);
    }

    public OnDuplicateKeyBuilderKey buildInsertOnDuplicateKey1(Collection<String> columns, Map<String, Object> configs) {
        InsertBuilder builder = new InsertBuilder(sqlTable);

        int i = 1;
        for (String c : columns) {

            SqlColumn sqlColumn = sqlTable.getSqlColumn(c, false);
            if (sqlColumn != null) {
                builder.addColumn(i, sqlColumn);
                i++;
            }

        }

        OnDuplicateKeyBuilder onDuplicateKeyBuilder = new OnDuplicateKeyBuilder(builder);

        String onDuplicateInsertSql = onDuplicateKeyBuilder.genByConfigs(configs);
        return new OnDuplicateKeyBuilderKey(onDuplicateKeyBuilder, onDuplicateInsertSql);
    }

    /**
     * 呃，这个，是为数据同步的一个东西，它用于生成类型如下更新语名
     * <p>
     * on duplicate key  update name= if(last_modified_date > values(last_modified_date), name , values(name))
     *
     * @param columns
     * @param configs
     * @return
     */
    public OnDuplicateKeyBuilderKey buildGtTimeOnDuplicateKey(Collection<String> columns, Map<String, Object> configs) {
        InsertBuilder builder = new InsertBuilder(sqlTable);
        Map<String, Object> updates = configs==null?null: (Map<String, Object>) configs.get("updates");
        if (configs == null) {
            configs = new HashMap<>();
            updates = new HashMap<>();
            configs.put("updates", updates);
        }

        if (updates == null) {
            updates = new HashMap<>();
            configs.put("updates", updates);
        }

        String version_column = (String) configs.get("versionColumn");
        if (StringUtils.isEmpty(version_column)) {
            version_column = "version";
        }
        int i = 1;
        for (String c : columns) {

            SqlColumn sqlColumn = sqlTable.getSqlColumn(c, false);
            if (sqlColumn != null) {
                builder.addColumn(i, sqlColumn);
                i++;
            } else {
                log.warn(String.format("传入了列%s,但表%s中不存在该列，忽略", c, sqlTable.getName()));
            }
            if (!updates.containsKey(c)) {
                //if(last_modified_date > values(last_modified_date), name , values(name))
                updates.put(c, String.format("if(%s > values(%s), %s , values(%s))", version_column, version_column, c, c));
            }
        }

        OnDuplicateKeyBuilder onDuplicateKeyBuilder = new OnDuplicateKeyBuilder(builder);

        String onDuplicateInsertSql = onDuplicateKeyBuilder.genByConfigs(configs);
        return new OnDuplicateKeyBuilderKey(onDuplicateKeyBuilder, onDuplicateInsertSql);
    }

    public void insertUpdateByListMap(OnDuplicateKeyBuilderKey onDuplicateInsertKey, List<Map<String, Object>> list, Map<String, Object> configs, boolean commitByRow) throws SQLException {
        insertUpdateByListMap(dataSource, onDuplicateInsertKey, list, configs, commitByRow);
    }

    private OnDuplicateKeyBuilderKey auto(Map<String, Object> mm, Map<String, Object> configs) {
        if (configs == null) {
            return buildInsertOnDuplicateKey(mm, configs);
        }
        Map<String, Object> updates = (Map<String, Object>) configs.get("updates");
        if (updates == null) {
            return buildInsertOnDuplicateKey(mm, configs);
        }
        String versionColumn = (String) updates.get("versionColumn");

        OnDuplicateKeyBuilderKey builderKey = null;
        if (StringUtils.isEmpty(versionColumn)) {
            builderKey = buildInsertOnDuplicateKey(mm, configs);
        } else {
            configs.put("versionColumn", versionColumn);
            builderKey = buildGtTimeOnDuplicateKey(mm.keySet(), configs);
        }
        return builderKey;
    }

    public void insertUpdateByListMap(DataSource ds, OnDuplicateKeyBuilderKey onDuplicateInsertKey, List<Map<String, Object>> list, Map<String, Object> configs, boolean commitByRow) throws SQLException {
        if (list.isEmpty()) {
            return;
        }
        long start = 0;
        if (log.isDebugEnabled()) {
            start = System.currentTimeMillis();
        }
        //这个，还是不能用缓存，因为每次都有可能不一样的字段，不一样的configs
        //如果考虑性能，请加大export var trunk = 1;让list每次多传点
//        if (onDuplicateInsertSql == null) {
//            synchronized (this) {
        if (onDuplicateInsertKey == null) {
            onDuplicateInsertKey = auto(list.get(0), configs);
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "total : " + list.size() + ",commitByRow:" + commitByRow + ",insertUpdateByListMap " + onDuplicateInsertKey.sql);
        }
        PreparedStatement ps = null;
        try (Connection conn = ds.getConnection()) {

            ps = conn.prepareStatement(onDuplicateInsertKey.sql);
            for (Map<String, Object> data : list) {


                int idx = 1;
                for (IdxSqlColumn c : onDuplicateInsertKey.builder.insertBuilder.columns) {
                    SqlUtils.doPsValue(data.get(c.sqlColumn.getName()), c.sqlColumn, ps, idx);
                    idx++;
                }

                ps.addBatch();

            }
            ps.executeBatch();

            if (!conn.getAutoCommit()) {
                conn.commit();
            }
        } finally {
            if (ps != null) {
                try {
                    ps.close();
                } catch (Throwable t) {

                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug(String.format("插入%s条，费时%s毫秒", list.size(), (System.currentTimeMillis() - start)));
        }


    }

    public int insertUpdateByListList(OnDuplicateKeyBuilderKey onDuplicateInsertKey, List<List> list, Map<String, Object> configs, boolean commitByRow) throws SQLException {
        return insertUpdateByListList(dataSource, onDuplicateInsertKey, list, configs, commitByRow);
    }

    public int insertUpdateByListList(DataSource ds, OnDuplicateKeyBuilderKey onDuplicateInsertKey, List<List> list, Map<String, Object> configs, boolean commitByRow) throws SQLException {
        if (list.isEmpty()) {
            return 0;
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "total : " + list.size() + ",insertUpdateByListMap " + onDuplicateInsertKey.sql);
        }
        int r = 0;
        PreparedStatement ps = null;
        try (Connection conn = ds.getConnection()) {

            ps = conn.prepareStatement(onDuplicateInsertKey.sql);
            for (List data : list) {

                int idx = 1;
                for (IdxSqlColumn c : onDuplicateInsertKey.builder.insertBuilder.columns) {
                    SqlUtils.doPsValue(data.get(idx - 1), c.sqlColumn, ps, idx);
                    idx++;
                }

                r = r + ps.executeUpdate();
                ps.addBatch();

            }
            ps.executeBatch();

            if (!conn.getAutoCommit()) {
                conn.commit();
            }
        } finally {
            close(ps);
        }
        return r;

    }

    public void insertUpdateByListMap(DataSource ds, List<Map<String, Object>> list, Map<String, Object> configs, boolean commitByRow) throws SQLException {
        insertUpdateByListMap(ds, null, list, configs, commitByRow);
    }

    public void insertUpdateMapData(DataSource ds, Map<String, Object> data, Map<String, Object> configs, boolean commitByRow) throws SQLException {
        insertUpdateMapData(ds, null, data, configs, commitByRow);
    }

    public void insertUpdateByListMap(List<Map<String, Object>> list, Map<String, Object> configs, boolean commitByRow) throws SQLException {
        insertUpdateByListMap(dataSource, null, list, configs, commitByRow);
    }

    public int insertUpdateMapData(Map<String, Object> data, Map<String, Object> configs, boolean commitByRow) throws SQLException {
        return insertUpdateMapData(dataSource, null, data, configs, commitByRow);
    }

    public int insertUpdateMapData(DataSource ds,
                                   OnDuplicateKeyBuilderKey onDuplicateInsertKey,
                                   Map<String, Object> data,
                                   Map<String, Object> configs,
                                   boolean commitByRow) throws SQLException {
        long start = System.currentTimeMillis();

        if (onDuplicateInsertKey == null) {
            onDuplicateInsertKey = auto(data, configs);
        }

        int num = 0;
        int r = 0;
        PreparedStatement ps = null;
        try (Connection conn = ds.getConnection()) {

            ps = conn.prepareStatement(onDuplicateInsertKey.sql);
            int idx = 1;
            for (IdxSqlColumn c : onDuplicateInsertKey.builder.insertBuilder.columns) {
                SqlUtils.doPsValue(data.get(c.sqlColumn.getName()), c.sqlColumn, ps, idx);
                idx++;
            }

            r = ps.executeUpdate();
            if (commitByRow) {
                if (!conn.getAutoCommit()) {
                    conn.commit();
                }

            }
            num++;
            if (log.isDebugEnabled()) {
                log.debug(
                        "total : " + num + ",insertUpdateRs " + onDuplicateInsertKey.sql + " \n cost: " + (System.currentTimeMillis() - start));
            }

        } finally {
            close(ps);
        }
        return r;
    }

    private void close(AutoCloseable close) {
        if (close != null) {
            try {
                close.close();
            } catch (Exception e) {
            }

        }
    }

    public int insertUpdateListData(OnDuplicateKeyBuilderKey onDuplicateInsertKey,
                                    List data,
                                    Map<String, Object> configs,
                                    boolean commitByRow) throws SQLException {
        return insertUpdateListData(dataSource, onDuplicateInsertKey, data, configs, commitByRow);
    }

    /**
     * @param ds
     * @param onDuplicateInsertKey 根据list插入数据的情况下，这个参数是必须传递的!
     * @param data
     * @param configs
     * @param commitByRow
     * @throws SQLException
     */
    public int insertUpdateListData(DataSource ds,
                                    OnDuplicateKeyBuilderKey onDuplicateInsertKey,
                                    List data,
                                    Map<String, Object> configs,
                                    boolean commitByRow) throws SQLException {
        long start = System.currentTimeMillis();

        int num = 0;
        int r = 0;
        PreparedStatement ps = null;
        try (Connection conn = ds.getConnection()) {


            ps = conn.prepareStatement(onDuplicateInsertKey.sql);
            int idx = 1;
            for (IdxSqlColumn c : onDuplicateInsertKey.builder.insertBuilder.columns) {
                SqlUtils.doPsValue(data.get(idx - 1), c.sqlColumn, ps, idx);
                idx++;
            }

            r = ps.executeUpdate();
            if (commitByRow) {
                if (!conn.getAutoCommit()) {
                    conn.commit();
                }

            }
            num++;
            if (log.isDebugEnabled()) {
                log.debug(
                        "total : " + num + ",insertUpdateRs " + onDuplicateInsertKey.sql + " \n cost: " + (System.currentTimeMillis() - start));
            }

        } finally {
            close(ps);
        }
        return r;
    }

    public int insertUpdateRs(ListResultSet rs, Map<String, Object> configs, boolean commitByRow) throws SQLException {
        return insertUpdateRs(rs, configs, commitByRow);
    }

    public int insertUpdateRs(DataSource ds, ListResultSet rs, Map<String, Object> configs, boolean commitByRow) throws SQLException {
        long start = System.currentTimeMillis();
        InsertBuilder builder = new InsertBuilder(sqlTable);
        int size = rs.getMetaData().getColumnCount();
        int r = 0;
        for (int i = 1; i <= size; i++) {
            String c = rs.getMetaData().getColumnName(i);

            SqlColumn sqlColumn = sqlTable.getSqlColumn(c, false);
            if (sqlColumn != null) {
                builder.addColumn(i, sqlColumn);
            }

        }

        OnDuplicateKeyBuilder onDuplicateKeyBuilder = new OnDuplicateKeyBuilder(builder);

        String insertSql = onDuplicateKeyBuilder.genByConfigs(configs);

        int num = 0;
        PreparedStatement ps = null;

        try (Connection conn = ds.getConnection()) {
            ps = conn.prepareStatement(insertSql);

            while (rs.next()) {
                int idx = 1;
                for (IdxSqlColumn c : builder.columns) {
                    SqlUtils.doPsValue(rs.getObject(c.idx), c.sqlColumn, ps, idx);
                    idx++;
                }
                ps.addBatch();
                num++;
            }

            int[] rr = ps.executeBatch();
            for (int i : rr) {
                r = r + i;
            }
            if (!conn.getAutoCommit()) {
                conn.commit();
            }

            System.out.println(
                    "total : " + num + ",insertUpdateRs " + insertSql + " \n cost: " + (System.currentTimeMillis() - start));
        } finally {
            close(ps);
        }
        return r;
    }

    private InsertBuilder build1(ListResultSetMetaData metaData) throws SQLException {
        InsertBuilder builder = new InsertBuilder(sqlTable);
        int size = metaData.getColumnCount();

        for (int i = 1; i <= size; i++) {
            String c = metaData.getColumnName(i);

            SqlColumn sqlColumn = sqlTable.getSqlColumn(c, false);
            if (sqlColumn != null) {
                builder.addColumn(i, sqlColumn);
            }

        }
        return builder;
    }

    public int insertRs1(ListResultSet rs, boolean commitByRow) throws SQLException {
        return insertRs1(dataSource, rs, commitByRow);
    }

    /**
     * 把rs中的值，插入到ds下的sqlTable，注意，如果rs中出现 sqlTable不存在的字段，则会忽略.
     * <p>
     * 如果rs中不包含主键，则会自动生成
     *
     * @param ds
     * @param rs
     * @throws SQLException
     */
    public int insertRs1(DataSource ds, ListResultSet rs, boolean commitByRow) throws SQLException {

        long start = System.currentTimeMillis();

        InsertBuilder builder = build1(rs.getMetaData());

        String insertSql = builder.genSql();
        int num = 0;
        int r = 0;
        PreparedStatement ps = null;
        try (Connection conn = ds.getConnection()) {

            ps = conn.prepareStatement(insertSql);

            while (rs.next()) {

                int idx = 1;
                for (IdxSqlColumn c : builder.columns) {
                    SqlUtils.doPsValue(rs.getObject(c.idx), c.sqlColumn, ps, idx);
                    idx++;
                }
                ps.addBatch();

                num++;
            }

            int[] rr = ps.executeBatch();
            for (int i : rr) {
                r = r + i;
            }

            if (!conn.getAutoCommit()) {
                conn.commit();
            }

            System.out.println(
                    "total : " + num + ",insert " + insertSql + " \n cost: " + (System.currentTimeMillis() - start));
        } finally {
            close(ps);
        }
        return r;
    }

    public int deleteByColumn(String columnName, Object value) throws SQLException {
        return deleteByColumn(dataSource, columnName, value);
    }
    public int deleteByColumnValueMap(Map<String,Object> maps) throws SQLException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : maps.entrySet()) {


        }
        throw new UnsupportedOperationException();
    }
    public int deleteByColumn(DataSource ds, String columnName, Object value) throws SQLException {
        String sql = "delete from " + this.sqlTable.getName() + " where " + columnName + "=?";
        long start = System.currentTimeMillis();
        int r = 0;
        PreparedStatement ps = null;
        try (Connection conn = ds.getConnection()) {


            ps = conn.prepareStatement(sql);
            ps.setObject(1, value);
            r = ps.executeUpdate();
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
            System.out.println(
                    "delete" + sql + " , args: " + value + " cost: " + (System.currentTimeMillis() - start));

        } finally {
            close(ps);
        }
        return r;
    }

    public void clear() throws SQLException {
        clear(dataSource);
    }

    public void clear(DataSource ds) throws SQLException {
        String sql = "delete from " + this.sqlTable.getName();
        PreparedStatement ps = null;
        try (Connection conn = ds.getConnection()) {


            ps = conn.prepareStatement(sql);
//			ps.setObject(1, value);
            ps.executeUpdate();
            if (!conn.getAutoCommit()) {
                conn.commit();
            }

        } finally {
            close(ps);
        }
    }

    public void deleteByRecord(Record record) throws SQLException {
        deleteByRecord(dataSource, record);
    }

    public void deleteByRecord(DataSource ds, Record record) throws SQLException {
        SqlColumn idColumn = sqlTable.getIdColumn();
        if (idColumn == null) {
            throw new RuntimeException("需要先定义ID列");
        }
        Object v = record.getObject(idColumn.getName());
        if (v == null) {
            throw new RuntimeException("ID列的值为空？？？");
        }
        deleteByColumn(ds, idColumn.getName(), v);
//		record.get
    }

    public void insertRsRecord(Record record, boolean commitByRow) throws SQLException {
        insertRsRecord(dataSource, record, commitByRow);
    }

    public void insertRsRecord(DataSource ds, Record record, boolean commitByRow) throws SQLException {
        InsertBuilder builder = build1(record.getMetaData());

        String insertSql = builder.genSql();

        PreparedStatement ps = null;
        try (Connection conn = ds.getConnection()) {


            ps = conn.prepareStatement(insertSql);
            int idx = 1;
            for (IdxSqlColumn c : builder.columns) {
                SqlUtils.doPsValue(record.getObject(c.idx), c.sqlColumn, ps, idx);
                idx++;
            }
            ps.executeUpdate();
            if (commitByRow) {
                if (!conn.getAutoCommit()) {
                    conn.commit();
                }

            }

        } finally {
            close(ps);
        }
    }

}
