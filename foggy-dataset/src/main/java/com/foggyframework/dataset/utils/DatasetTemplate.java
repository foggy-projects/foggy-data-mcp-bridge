package com.foggyframework.dataset.utils;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.core.utils.beanhelper.RequestBeanInjecter;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.table.EditSqlTable;
import com.foggyframework.dataset.db.table.QuerySqlTable;
import com.foggyframework.dataset.db.table.SqlTable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DatasetTemplate {

    public final JdbcTemplate template;

    public final DataSource dataSource;

    Map<String, SqlTable> name2SqlTable = new HashMap<>();

    public final FDialect dialect;
    private Object key = new Object();

    public DatasetTemplate(DataSource dataSource) {
        this.dataSource = dataSource;
        template = new JdbcTemplate(dataSource);
        dialect = DbUtils.getDialect(dataSource);
    }

    public DatasetTemplate(DataSource dataSource, JdbcTemplate template) {
        this.dataSource = dataSource;
        this.template = template;
        dialect = DbUtils.getDialect(dataSource);
    }

    public SqlTable getSqlTableUsingCache(String tableName, boolean errorIfNotFound) {
        SqlTable st = name2SqlTable.get(tableName);
        if (st == null) {
            synchronized (key) {
                st = name2SqlTable.get(tableName);
                if (st != null) {
                    return st;
                }
                st = DbUtils.getTableByName(dataSource, tableName);
                if (st == null) {
                    if (errorIfNotFound) {
                        throw RX.throwB(String.format("表%s不存在", tableName));
                    }
                    return null;
                }
                name2SqlTable.put(tableName, st);
            }
        }
        return st;
    }

    public EditSqlTable getDsSqlTable(String tableName, boolean errorIfNotFound) {
        SqlTable st = DbUtils.getTableByName(dataSource, tableName);
        if (st == null) {
            if (errorIfNotFound) {
                throw RX.throwB(String.format("表%s不存在", tableName));
            }
            return null;
        }
        EditSqlTable dsSqlTable = new EditSqlTable(st, dataSource);
        return dsSqlTable;
    }

    public QuerySqlTable getQuerySqlTable(String tableName, boolean errorIfNotFound) {
        SqlTable st = DbUtils.getTableByName(dataSource, tableName);
        if (st == null) {
            if (errorIfNotFound) {
                throw RX.throwB(String.format("表%s不存在", tableName));
            }
            return null;
        }

        return new QuerySqlTable(st, template);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public JdbcTemplate getTemplate() {
        return template;
    }

    /**
     * 为什么不用JdbcTemplate的queryForObject,因为那个在SQL返回空时会报异常
     *
     * @param clazz 通常为Double,Integer等
     * @param sql
     * @param args
     * @return
     */
    public <T> T queryForObject(Class<T> clazz, String sql, Object... args) {

        final ObjectTransFormatter<T> formatter = (ObjectTransFormatter<T>) RequestBeanInjecter.getInstance()
                .getObjectTransFormatter(clazz);
        if (formatter == null) {
            throw RX.throwB("unsupported ObjectTransFormatter :" + clazz);
        }
        return template.query(sql, args, new ResultSetExtractor<T>() {

            @Override
            public T extractData(ResultSet rs) throws SQLException, DataAccessException {
                // TODO Auto-generated method stub
                if (rs.next()) {
                    Object obj = rs.getObject(1);
                    return formatter.format(obj);
                }
                return null;
            }
        });

    }

    /**
     * @param <T>   通常为Double,Integer等
     * @param clazz
     * @param sql
     * @param args
     * @return
     */
    public <T> List<T> queryObjectList(Class<T> clazz, String sql, Object... args) {

        final ObjectTransFormatter<T> formatter = (ObjectTransFormatter<T>) RequestBeanInjecter.getInstance()
                .getObjectTransFormatter(clazz);

        if (formatter == null) {
            throw RX.throwB("unsupported ObjectTransFormatter :" + clazz);
        }

        return template.query(sql, new RowMapper<T>() {

            @Override
            public T mapRow(ResultSet rs, int arg1) throws SQLException {
                Object obj = rs.getObject(1);
                return formatter.format(obj);
            }
        }, args);

    }


    public List queryList(int start, int limit, String sql, Object... args) {

//		DataAccessObjectManager mgr = BeanUtils.getGlobalBean(null, DataAccessObjectManager.class);
//		DataAccessObject dao = mgr.getDataAccessObject(beanClass);
        String sqll = sql + " limit " + start + "," + limit;

//		return query(beanClass, sql);
        return queryMapList(sqll, args);
    }
    public List queryList1(int start, int limit, String sql, List<Object>  args) {

//		DataAccessObjectManager mgr = BeanUtils.getGlobalBean(null, DataAccessObjectManager.class);
//		DataAccessObject dao = mgr.getDataAccessObject(beanClass);
        String sqll = sql + " limit " + start + "," + limit;

//		return query(beanClass, sql);
        List<Map<String, Object>> ll = template.queryForList(sqll, args.toArray());
        return ll;
    }

    public <T> List<T> queryList(Class<T> cls, String sql, String mainTeamId, int type) {

        return getTemplate().queryForList(sql, cls, mainTeamId, type);
    }


    public void refresh() {
        name2SqlTable.clear();
    }

    public List<Map<String, Object>> queryMapList(String sql, Object[] args) {
        List<Map<String, Object>> ll = template.queryForList(sql, args);
        return ll;
    }
    public Map<String, Object> queryMapObject(String sql, Object[] args) {
        List<Map<String, Object>> ll = queryMapList(sql, args);
        return (ll==null||ll.isEmpty())?null:ll.get(0);
    }
    public Map<String, Object> queryMapObject1(String sql, List<?> args) {
        List<Map<String, Object>> ll = queryMapList(sql, args==null?new Object[0]:args.toArray());
        return (ll==null||ll.isEmpty())?null:ll.get(0);
    }

    public Long queryCount(String sql, Object[] args) {
        String countSql = "select count(*) from (" + sql + ") _xx";
        return queryForObject(Long.class, countSql, args);
    }
}
