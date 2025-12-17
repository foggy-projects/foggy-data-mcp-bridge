//package com.foggyframework.dataset.model.support;
//
//
//import com.foggyframework.core.ex.R;
//import com.foggyframework.core.trans.ObjectTransFormatter;
//import com.foggyframework.core.utils.StringUtils;
//import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
//import com.foggyframework.core.utils.beanhelper.BeanProperty;
//import com.foggyframework.core.utils.beanhelper.RequestBeanInjecter;
//import com.foggyframework.dataset.db.dialect.FDialect;
//import com.foggyframework.dataset.db.table.DsSqlTable;
//import com.foggyframework.dataset.db.table.SqlTable;
//import com.foggyframework.dataset.db.table.SqlTableSupport;
//import com.foggyframework.dataset.utils.DbUtils;
//import org.springframework.dao.DataAccessException;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.jdbc.core.ResultSetExtractor;
//import org.springframework.jdbc.core.RowMapper;
//
//import javax.sql.DataSource;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//public final class DataSourceDbUtils {
//
//    public JdbcTemplate template;
//
////    public final DataSource dataSource;
//
//    Map<Class, SqlTableSupport> name2Table = new HashMap<>();
//    public final FDialect dialect;
//
//    public DataSourceDbUtils(JdbcTemplate template) {
//        DataSource dataSource = template.getDataSource();
//
//        dialect = DbUtils.getDialect(dataSource);
//    }
//
//    public DataSource getDataSource() {
//        return template.getDataSource();
//    }
//
//    public DsSqlTable getDsSqlTable(String name, boolean errorIfNotFound) {
//        SqlTable st = (SqlTable) DbUtils.getTableByName(getDataSource(), name);
//        if (st == null && errorIfNotFound) {
//            throw R.throwB("表" + name + "不存在");
//        }
//        DsSqlTable dsSqlTable = new DsSqlTable(st, getDataSource());
//        return dsSqlTable;
//    }
//
////    public long getCount(Class beanClass) {
////
////        DataAccessObjectManager mgr = BeanUtils.getGlobalBean(null, DataAccessObjectManager.class);
////        DataAccessObject dao = mgr.getDataAccessObject(beanClass);
////
////        String sql = "select count(*) from " + dao.getSqlTableName() + " order by " + dao.getSqlIdColumn();
////
////        return dao.getTotalCount();
////    }
//
////    public long getCount(Class entityClass, String sql, Object[] args) {
////
////        DataAccessObjectManager mgr = BeanUtils.getGlobalBean(null, DataAccessObjectManager.class);
////        DataAccessObject dao = mgr.getDataAccessObject(entityClass);
////
////        Long l = dao.getDataSourceDbUtils().queryForObject(Long.class, sql, args);
////        return l == null ? 0 : l;
////    }
//
////    public DataSource getDs() {
////        return ds;
////    }
//
////    public <T> T getObject(Class<T> entityClass, Object key) {
////        SqlTableSupport st = getTableByEntityClass(entityClass);
//////		if(st==null) {
//////			System.err.println(8923984);
//////		}
////        return st.getObject(template, key);
////    }
//
////    public final SqlTableSupport getTableByEntityClass(Class entityClass) {
////
////        SqlTableSupport st = name2Table.get(entityClass);
////        if (st != null) {
////            return st;
////        }
////
////        synchronized (entityClass) {
////            st = DbUtils.getTableByEntityClass(getTableByEntityClass(), entityClass);
////            name2Table.put(entityClass, st);
////
////            return st;
////        }
////
////    }
//
//    public JdbcTemplate getTemplate() {
//        return template;
//    }
//
//
//
//    public List<Map> queryAutoFixName(String sql, Object... args) {
//
////        template.query()
//
//        List<Map> result = new ArrayList<>();
//        template.query(sql, (ext) -> {
//            int c = ext.getMetaData().getColumnCount();
//            Map<String, String> nameMap = new HashMap<>();
//
//            for (int i = 1; i <= c; i++) {
//                String name = ext.getMetaData().getColumnLabel(i);
//                nameMap.put(name, name);
//                if (name.indexOf("_") > 0) {
//                    nameMap.put(name, StringUtils.to(name));
//                }
//            }
//
//            while (ext.next()) {
//                try {
//                    Map map = new HashMap();
//
//                    for (Map.Entry<String, String> en : nameMap.entrySet()) {
//                        map.put(en.getValue(), ext.getObject(en.getKey()));
//                    }
//                    result.add(map);
//                } catch (Throwable e) {
//                    throw R.throwB(e.getMessage(), null, e);
//                }
//            }
//
//            return result;
//        }, args);
//        return result;
//    }
//
//    public <T> List<T> query(Class<T> entityClass, String sql, Object... args) {
////        if (entityClass == AutoFixNameClass.class) {
////            //自动把 "get_time" 转"getTime"
////            List list = queryAutoFixName(sql, args);
////            return list;
////        }
//        if (entityClass == null) {
//            List list = template.queryForList(sql, args);
//            return list;
//        }
//        SqlTableSupport st = getTableByEntityClass(entityClass);
//        if (st == null) {
//
//            return template.query(sql, (ext) -> {
//                int c = ext.getMetaData().getColumnCount();
//                BeanInfoHelper info = BeanInfoHelper.getClassHelper(entityClass);
//                Map<String, BeanProperty> columnName2BeanProperty = new HashMap<>();
//
//                for (int i = 1; i <= c; i++) {
//                    String name = ext.getMetaData().getColumnLabel(i);
//                    BeanProperty beanProperty = info.getBeanPropertyIngoreCase(name);
//                    if (beanProperty == null) {
//                        //去掉"_"找
//                        beanProperty = info.getBeanPropertyIngoreCase(name.replaceAll("_", ""));
//                    }
//                    if (beanProperty != null) {
//                        columnName2BeanProperty.put(name, beanProperty);
//                    }
//                }
//                List<T> result = new ArrayList<>();
//                while (ext.next()) {
//                    try {
//                        T v = entityClass.newInstance();
//
//                        for (Map.Entry<String, BeanProperty> en : columnName2BeanProperty.entrySet()) {
//                            en.getValue().setBeanValue(v, ext.getObject(en.getKey()));
//                        }
//                        result.add(v);
//                    } catch (Throwable e) {
//                        throw R.throwB(e.getMessage(), null, e);
//                    }
//                }
//
//                return result;
//            }, args);
//
////            throw new FoggyRuntimeException("未能找到实体对应的表结构 " + entityClass);
//        }
//        List<T> ll = st.queryList(template, sql, args);
//        return ll;
//    }
//
//    /**
//     * 为什么不用JdbcTemplate的queryForObject,因为那个在SQL返回空时会报异常
//     *
//     * @param clazz 通常为Double,Integer等
//     * @param sql
//     * @param args
//     * @return
//     */
//    public <T> T queryForObject(Class<T> clazz, String sql, Object... args) {
//
//        final ObjectTransFormatter<T> formatter = (ObjectTransFormatter<T>) RequestBeanInjecter.getInstance()
//                .getObjectTransFormatter(clazz);
//        if (formatter == null) {
////            throw new FoggyRuntimeException("unsupported ObjectTransFormatter :" + clazz);
//        }
//        return template.query(sql, args, new ResultSetExtractor<T>() {
//
//            @Override
//            public T extractData(ResultSet rs) throws SQLException, DataAccessException {
//                // TODO Auto-generated method stub
//                if (rs.next()) {
//                    Object obj = rs.getObject(1);
//                    return formatter.format(obj);
//                }
//                return null;
//            }
//        });
//
//    }
//
//    /**
//     * @param <T>   通常为Double,Integer等
//     * @param clazz
//     * @param sql
//     * @param args
//     * @return
//     */
//    public <T> List<T> queryObjectList(Class<T> clazz, String sql, Object... args) {
//
//        final ObjectTransFormatter<T> formatter = (ObjectTransFormatter<T>) RequestBeanInjecter.getInstance()
//                .getObjectTransFormatter(clazz);
//
//        if (formatter == null) {
//            throw new FoggyRuntimeException("unsupported ObjectTransFormatter :" + clazz);
//        }
//
//        return template.query(sql, new RowMapper<T>() {
//
//            @Override
//            public T mapRow(ResultSet rs, int arg1) throws SQLException {
//                Object obj = rs.getObject(1);
//                return formatter.format(obj);
//            }
//        }, args);
//
//    }
//
//    public List queryList(Class beanClass, int start, int limit, String sql, Object... args) {
//
////		DataAccessObjectManager mgr = BeanUtils.getGlobalBean(null, DataAccessObjectManager.class);
////		DataAccessObject dao = mgr.getDataAccessObject(beanClass);
//        String sqll = sql + " limit " + start + "," + limit;
//
////		return query(beanClass, sql);
//        return query(beanClass, sqll, args);
//    }
//
//    public List queryList(int start, int limit, String sql, Object... args) {
//
////		DataAccessObjectManager mgr = BeanUtils.getGlobalBean(null, DataAccessObjectManager.class);
////		DataAccessObject dao = mgr.getDataAccessObject(beanClass);
//        String sqll = sql + " limit " + start + "," + limit;
//
////		return query(beanClass, sql);
//        return queryMapList(sqll, args);
//    }
//
//    /**
//     * 呃，通常用于找出某个表的某一列
//     *
//     * @param entityClass
//     * @param icls
//     * @param sql
//     * @return
//     */
//    public <D, T> List<D> queryList(Class<T> entityClass, Class<D> icls, String sql, Object... args) {
//
//        return getTableByEntityClass(entityClass).queryList(getTemplate(), icls, sql, args);
//    }
//
//    public <T> List<T> queryList(Class<T> cls, String sql, String mainTeamId, int type) {
//
//        return getTemplate().queryForList(sql, cls, mainTeamId, type);
//    }
//
//    public <T> T queryObject(Class<T> entityClass, String sql, Object... args) {
//        T ll = getTableByEntityClass(entityClass).queryFirst(template, sql, args);
//        return ll;
//    }
//
//    public void refresh() {
//        name2Table.clear();
//    }
//
//    public List<Map<String, Object>> queryMapList(String sql, Object[] args) {
//        List<Map<String, Object>> ll = template.queryForList(sql, args);
//        return ll;
//    }
//
//}
