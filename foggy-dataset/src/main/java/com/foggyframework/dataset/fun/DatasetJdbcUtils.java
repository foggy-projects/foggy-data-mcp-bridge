package com.foggyframework.dataset.fun;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.JsonUtils;
import com.foggyframework.dataset.db.table.QuerySqlTable;
import com.foggyframework.dataset.utils.DataSourceQueryUtils;
import com.foggyframework.dataset.utils.DatasetTemplate;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class DatasetJdbcUtils {

    DataSourceQueryUtils utils;

    ApplicationContext appCtx;

    @Resource
    Environment environment;

    Map<String, DataSource> name2DataSource = new HashMap<>();

    public DatasetJdbcUtils(DataSourceQueryUtils utils, ApplicationContext appCtx) {
        this.utils = utils;
        this.appCtx = appCtx;
    }

    public List<Map<String, Object>> queryForMapList(DataSource dataSource, String sql, Object[] args) {
        DatasetTemplate datasetTemplate = DataSourceQueryUtils.getDatasetTemplate(dataSource);

        List<Map<String, Object>> ll = datasetTemplate.getTemplate().queryForList(sql, args);
        return ll;
    }

    public List<Map<String, Object>> queryForMapList(DataSource dataSource, String sql, List args) {
        DatasetTemplate datasetTemplate = DataSourceQueryUtils.getDatasetTemplate(dataSource);

        List<Map<String, Object>> ll = datasetTemplate.getTemplate().queryForList(sql, args.toArray());
        return ll;
    }

    public Map<String, Object> queryForMap(DataSource dataSource, String sql, Object[] args) {
        DatasetTemplate datasetTemplate = DataSourceQueryUtils.getDatasetTemplate(dataSource);

        Map<String, Object> ll = datasetTemplate.getTemplate().queryForMap(sql, args);
        return ll;
    }

    public Map<String, Object> queryForMap(DataSource dataSource, String sql, List args) {
        DatasetTemplate datasetTemplate = DataSourceQueryUtils.getDatasetTemplate(dataSource);

        Map<String, Object> ll = datasetTemplate.getTemplate().queryForMap(sql, args.toArray());
        return ll;
    }

    public DataSource getOrCreateDataSource(GetOrCreateDataSourceForm form) {
        Assert.notNull(form.getBeanName(), "beanName不得为空!");
        String beanName = form.getBeanName();
        DataSource ds = name2DataSource.get(beanName);

        if (ds == null) {
            synchronized (this) {
                ds = name2DataSource.get(beanName);
                if (ds != null) {
                    return ds;
                }

                //获取数据源
                if (appCtx.containsBean(beanName)) {
                    Object bean = appCtx.getBean(beanName);
                    if (bean instanceof DataSource) {
                        ds = (DataSource) bean;
                    } else {
                        throw RX.throwB("期望传入的bean是DataSource类型，但不是" + beanName + "," + bean);
                    }
                    name2DataSource.put(beanName, ds);
                    if (log.isDebugEnabled()) {
                        log.debug("通过beanName找到数据源: " + beanName);
                    }
                    return ds;
                }
                String jdbcUrl;
                String username;
                String password;
                String driverClassName;

                if (!StringUtils.isEmpty(form.getConfigPrefix())) {
                    jdbcUrl = environment.getProperty(form.getConfigPrefix() + ".jdbcUrl");
                    if (StringUtils.isEmpty(jdbcUrl)) {
                        jdbcUrl = environment.getProperty(form.getConfigPrefix() + ".jdbc-url");
                    }
                    if (StringUtils.isEmpty(jdbcUrl)) {
                        jdbcUrl = environment.getRequiredProperty(form.getConfigPrefix() + ".url");
                    }

                    username = environment.getProperty(form.getConfigPrefix() + ".username");
                    password = environment.getProperty(form.getConfigPrefix() + ".password");
                    driverClassName = environment.getProperty(form.getConfigPrefix() + ".driverClassName");
                    if (StringUtils.isEmpty(driverClassName)) {
                        driverClassName = environment.getRequiredProperty(form.getConfigPrefix() + ".driver-class-name");
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("通过ConfigPrefix创建数据源: " + beanName + "，" + form.getConfigPrefix());
                    }
//                    Assert.notNull(form.getBeanName(), "beanName不得为空!");
                } else {
                    jdbcUrl = form.getJdbcUrl();
                    if (StringUtils.isEmpty(jdbcUrl)) {
                        jdbcUrl = form.getUrl();
                    }
                    username = form.getUsername();
                    password = form.getPassword();
                    driverClassName = form.getDriverClassName();
                    if (log.isDebugEnabled()) {
                        log.debug("通过配置: " + beanName + "，" + JsonUtils.toJson(form));
                    }
                    Assert.notNull(driverClassName, "driverClassName不得为空!");
                    Assert.notNull(jdbcUrl, "jdbcUrl不得为空!");
                }
                //创建数据源
                ds = DataSourceBuilder.create().driverClassName(driverClassName).password(password).username(username).url(jdbcUrl).build();

                name2DataSource.put(beanName, ds);

            }
        }
        return ds;
    }

    public Map<String, Object> getObject(DataSource dataSource, String tableName, Object id) {
        DatasetTemplate datasetTemplate = DataSourceQueryUtils.getDatasetTemplate(dataSource);
        return datasetTemplate.getSqlTableUsingCache(tableName, true).getObject(datasetTemplate.template, id);
    }

    public QuerySqlTable newQuerySqlTable(DataSource dataSource, String tableName) {
        return DataSourceQueryUtils.getDatasetTemplate(dataSource).getQuerySqlTable(tableName, true);
    }

    public QuerySqlTable newQuerySqlTable(JdbcTemplate jdbcTemplate, String tableName) {
        return DataSourceQueryUtils.getDatasetTemplate(jdbcTemplate.getDataSource()).getQuerySqlTable(tableName, true);
    }

    public QuerySqlTable getQuerySqlTable(DataSource dataSource, String tableName) {
        return DataSourceQueryUtils.getDatasetTemplate(dataSource).getQuerySqlTable(tableName, true);
    }

    public QuerySqlTable getQuerySqlTable(JdbcTemplate jdbcTemplate, String tableName) {
        return DataSourceQueryUtils.getDatasetTemplate(jdbcTemplate.getDataSource()).getQuerySqlTable(tableName, true);
    }
//    public Map<String,Object> queryById(JdbcTemplate jdbcTemplate,Object id){
//        if(id==null){
//            return null;
//        }
//
//        return null;
////        jdbcTemplate.
//    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetOrCreateDataSourceForm {

        @ApiModelProperty(value = "先使用beanName向spring查找，如果没有则创建")
        String beanName;

        @ApiModelProperty(value = "例如foggy.third.datasource，会去配置中找该配置来创建数据源")
        String configPrefix;
        String url;
        String jdbcUrl;
        String username;
        String password;
        String driverClassName;
    }
}
