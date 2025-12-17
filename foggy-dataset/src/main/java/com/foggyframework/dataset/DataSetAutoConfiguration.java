package com.foggyframework.dataset;

import com.foggyframework.dataset.fsscript.*;
import com.foggyframework.dataset.fun.*;
import com.foggyframework.dataset.utils.DataSourceFactory;
import com.foggyframework.dataset.utils.DataSourceFactoryImpl;
import com.foggyframework.dataset.utils.DataSourceQueryUtils;
import com.foggyframework.fsscript.exp.FunTable;
import com.foggyframework.fsscript.parser.FunDef;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class DataSetAutoConfiguration implements InitializingBean {

    @Resource
    FunTable funTable;

    @Override
    public void afterPropertiesSet() throws Exception {
        List<FunDef> regfuns = new ArrayList<>();
        regfuns.add(new SyncSqlTable());
        regfuns.add(new BuildEditSqlTable());
        regfuns.add(new SqlExp());
        regfuns.add(new AutoSqlExp());

        regfuns.add(new LoadEditSqlTable());
        regfuns.add(new LoadSqlTable());

        regfuns.add(new ToLikeStr());
        regfuns.add(new ToLikeStrL());
        regfuns.add(new ToLikeStrR());
        regfuns.add(new SqlInExp());
        funTable.addAll(regfuns);
    }

    @Bean
    public DataSetFsscriptUtils dataSetFsscriptUtils() {
        return new DataSetFsscriptUtils();
    }

    @Bean
    public DataSourceQueryUtils dataSourceQueryUtils() {
        return new DataSourceQueryUtils();
    }

    @Bean
    public DatasetJdbcUtils datasetJdbcUtils(DataSourceQueryUtils utils, ApplicationContext appCtx) {
        return new DatasetJdbcUtils(utils, appCtx);
    }

    /**
     * 数据源工厂 Bean
     *
     * <p>用于在 fsscript 中动态创建数据源：
     * <pre>
     * import bean dataSourceFactory;
     * export const myDs = dataSourceFactory.create({url: '...', username: '...', password: '...'});
     * </pre>
     */
    @Bean
    public DataSourceFactory dataSourceFactory() {
        return new DataSourceFactoryImpl();
    }
}
