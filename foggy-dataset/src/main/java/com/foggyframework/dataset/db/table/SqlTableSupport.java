package com.foggyframework.dataset.db.table;


import com.foggyframework.core.ex.RX;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.core.utils.JsonUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.core.utils.beanhelper.RequestBeanInjecter;
import com.foggyframework.dataset.db.SqlObject;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class SqlTableSupport extends SqlObject {

    protected Map<String, SqlColumn> name2SqlColumn = new HashMap<String, SqlColumn>();

    protected List<SqlColumn> sqlColumns;

    protected SqlColumn idColumn;
    static ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();

    public SqlColumn getSqlColumn(int i) {

        return sqlColumns == null ? null : sqlColumns.get(i);
    }

    public void addSqlColumn(SqlColumn e) {
        RX.notNull(e, "列定义不得为空");
        if (StringUtils.isEmpty(e.getName())) {
            throw RX.throwAUserTip(String.format("尝试加字段到表%s,但列%s缺少name定义", this.name, JsonUtils.toJson(e)));
        }

        if (sqlColumns == null) {
            sqlColumns = new ArrayList<SqlColumn>();
        }

        sqlColumns.add(e);

        name2SqlColumn.put(e.getName().toUpperCase(), e);
    }

    public SqlColumn getIdColumn() {
        return idColumn;
    }

    public void setIdColumn(SqlColumn idColumn) {
        this.idColumn = idColumn;
    }

    public void setIdColumnByName(String name) {
        this.idColumn = getSqlColumn(name, true);

    }

    Class entityClass;

    public Class getEntityClass() {
        return entityClass;
    }

    public void setEntityClass(Class entityClass) {
        this.entityClass = entityClass;
    }

    public Map<String, SqlColumn> getName2SqlColumn() {
        return name2SqlColumn;
    }

    public SqlTableSupport() {
        super();
    }

    public SqlTableSupport(String name, String caption) {
        super(name);
        this.caption = caption;
    }

    public SqlTableSupport(String name, String caption, List<SqlColumn> sqlColumns, SqlColumn idColumn) {
        super(name);
        this.caption = caption;
        this.sqlColumns = sqlColumns;
        this.idColumn = idColumn;
        if (sqlColumns != null) {
            for (SqlColumn c : sqlColumns) {
                name2SqlColumn.put(c.getName().toUpperCase(), c);
            }
        }
    }

    protected abstract String getSearchColumnSql();

    public SqlColumn getSqlColumn(String name, boolean errorIfNotFound) {
        SqlColumn c = name2SqlColumn.get(name.toUpperCase());
        if (c == null && errorIfNotFound) {
            throw new RuntimeException("sql column [" + name + "] not found int table [" + this.name + "]");
        } else {
            return c;
        }
    }

    public SqlColumn removeSqlColumn(String name) {
        SqlColumn sqlColumn = name2SqlColumn.remove(name.toUpperCase());
        return sqlColumn;
    }

    public SqlColumn createSqlColumn(String name, String typeName, int length) {
        int type = SqlColumn._getTypeName2JdbcType(typeName);
        SqlColumn sc = new SqlColumn(name, typeName, type, length);

        return sc;
    }

    public SqlColumn appendSqlColumn(String name, String typeName, int length) {
        SqlColumn sc = createSqlColumn(name, typeName, length);
        if (sqlColumns == null) {
            sqlColumns = new ArrayList<SqlColumn>();
        }
        addSqlColumn(sc);
        return sc;
    }

//    /**
//     * 呃，如果通过数据库表的列名没找到，尝试使用java field
//     *
//     * @param name
//     * @param errorIfNotFound
//     * @return
//     */
//    public SqlColumn getSqlColumn1(String name, boolean errorIfNotFound) {
//        SqlColumn c = name2SqlColumn.get(name.toUpperCase());
//        if (c == null) {
//            for (SqlColumn x : this.sqlColumns) {
//                if (StringUtils.equals(x.getJavaFieldName(), name)) {
//                    return x;
//                }
//            }
//        }
//
//        if (c == null && errorIfNotFound) {
//            throw new RuntimeException("sql column [" + name + "] not found int table [" + this.name + "]");
//        } else {
//            return c;
//        }
//    }

//    public SqlColumn getSqlColumnByJdbcName(String name, boolean errorIfNotFound) {
//
//        for (SqlColumn c : sqlColumns) {
//            if (c.beanProperty != null && (StringUtils.equalsIgnoreCase(c.beanProperty.getName(), name)
//                    || StringUtils.equalsIgnoreCase(c.getName(), name))) {
//                return c;
//            }
//        }
//        if (errorIfNotFound) {
//            throw new RuntimeException("sql column [" + name + "] not found int table [" + this.name + "]");
//        } else {
//            return null;
//        }
//    }
    // public Map<String, SqlColumn> getSqlColumnMaps() {
    // return name2SqlColumn;
    // }

    public List<SqlColumn> getSqlColumns() {
        return sqlColumns;
    }

    public void setSqlColumns(List<SqlColumn> columns) {
        this.sqlColumns = columns;
        if (sqlColumns != null) {
            for (SqlColumn c : sqlColumns) {
                name2SqlColumn.put(c.getName().toUpperCase(), c);
            }
        }
        // return sqlColumns==null?Collections.EMPTY_LIST:sqlColumns.values();
    }

    public <T> List<T> queryList(JdbcTemplate tpl, String sql, Object... args) {
        try {
            List<T> ll = null;
            if (args == null || args.length == 0) {
                ll = tpl.query(sql, new X<T>());
            } else {
                ll = tpl.query(sql, new X<T>(), args);
            }


            return ll;
        } catch (NullPointerException e) {
            throw e;
        }
    }

    String idSql = null;

    public <T> T getObject(JdbcTemplate tpl, Object key) {

        if (idSql == null) {
            idSql = "select * from " + name + " where " + this.idColumn.getName() + "=?";
        }
        if (entityClass == null) {

//
//            List<T> results = (List<T>) tpl.query(idSql, new Object[]{key}, new RowMapperResultSetExtractor<>(rowMapper, 1));
//            return results.isEmpty()?null:results.get(0);

            List t = tpl.query(idSql, rowMapper, key);
            return t.isEmpty() ? null : (T) t.get(0);
        }

        Object t = tpl.query(idSql, new X2(), key);

//		tpl.query(sql, rse, args).query(sql, rch, args);
        return (T) t;
    }

    public class X<T> implements RowMapper<T> {

        @Override
        public T mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                T bean = (T) entityClass.newInstance();
                for (SqlColumn sc : sqlColumns) {

//                    if (sc.beanProperty != null) {
//                        sc.beanProperty.setBeanValue(bean, rs.getObject(sc.getName()));
//                    }
                }
                return bean;
            } catch (Throwable t) {
                throw RX.throwB(t);
            }
        }

    }

    public class X2<T> implements ResultSetExtractor<T> {

        @Override
        public T extractData(ResultSet rs) throws SQLException, DataAccessException {
            if (rs.next()) {
                try {
                    T bean = (T) entityClass.newInstance();
                    for (SqlColumn sc : sqlColumns) {

//                        if (sc.beanProperty != null) {
//                            sc.beanProperty.setBeanValue(bean, rs.getObject(sc.getName()));
//                        }
                    }
                    return bean;
                } catch (Throwable t) {
                    throw RX.throwB(t);
                }
            }
            return null;
        }

    }

    public class X3<T> implements RowMapper<T> {
        ObjectTransFormatter<T> formatter;

        public X3(Class<T> icls) {
            formatter = (ObjectTransFormatter<T>) RequestBeanInjecter.getInstance().getObjectTransFormatter(icls);
            if (formatter == null) {
                throw RX.throwB("unsupported ObjectTransFormatter :" + icls);
            }
        }

        @Override
        public T mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                Object v = formatter.format(rs.getObject(1));
                return (T) v;
            } catch (Throwable t) {
                throw RX.throwB(t);
            }
        }

    }

    public <T> T queryFirst(JdbcTemplate tpl, String sql, Object... args) {

        sql = sql + " limit 1";
        return tpl.query(sql, new X2<T>(), args);
    }

    /**
     * 呃，通常用于找出某个表的某一列
     *
     * @param tpl
     * @param icls
     * @param sql
     * @param args
     */
    public <D> List<D> queryList(JdbcTemplate tpl, Class<D> icls, String sql, Object[] args) {
        List<D> ll = tpl.query(sql, new X3<D>(icls), args);

        return ll;
    }

}
