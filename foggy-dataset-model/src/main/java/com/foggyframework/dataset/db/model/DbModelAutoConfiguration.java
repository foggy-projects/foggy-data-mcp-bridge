package com.foggyframework.dataset.db.model;


import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.config.SemanticProperties;
import com.foggyframework.dataset.db.model.engine.formula.*;
import com.foggyframework.dataset.db.model.engine.query_model.DbModelFileChangeHandler;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.db.model.impl.loader.JdbcTableModelLoaderImpl;
import com.foggyframework.dataset.db.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultFilterManager;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultStep;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DefaultDataSetResultFilterManagerImpl;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.SemanticMoneyStep;
import com.foggyframework.dataset.db.model.service.impl.JdbcServiceImpl;
import com.foggyframework.dataset.db.model.spi.DbModelLoadProcessor;
import com.foggyframework.dataset.db.model.spi.QueryModelBuilder;
import com.foggyframework.dataset.db.model.spi.TableModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ComponentScan("com.foggyframework.dataset.db.model")
public class DbModelAutoConfiguration {

    @Bean
    public JdbcTableModelLoaderImpl jdbcTableModelLoader(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader) {
        return new JdbcTableModelLoaderImpl(systemBundlesContext, fileFsscriptLoader );
    }
    @Bean
    public TableModelLoaderManagerImpl tableModelLoaderManager(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader, List<DbModelLoadProcessor> processors, List<TableModelLoader> loaders) {
        return new TableModelLoaderManagerImpl(systemBundlesContext, fileFsscriptLoader, processors,loaders);
    }
    @Bean
    public QueryModelLoaderImpl jdbcQueryModelLoader(TableModelLoaderManager tableModelLoaderManager,
                                                     SystemBundlesContext systemBundlesContext,
                                                     FileFsscriptLoader fileFsscriptLoader,
                                                     List<QueryModelBuilder> queryModelBuilders) {
        return new QueryModelLoaderImpl(tableModelLoaderManager,  systemBundlesContext, fileFsscriptLoader,queryModelBuilders);
    }

    @Bean
    public DbModelFileChangeHandler jdbcModelFileChangeHandler(QueryModelLoaderImpl jdbcQueryModelLoader, TableModelLoaderManagerImpl jdbcModelLoader) {
        return new DbModelFileChangeHandler(jdbcQueryModelLoader, jdbcModelLoader);
    }

    @Bean
    @ConditionalOnMissingBean(DataSetResultFilterManager.class)
    public DefaultDataSetResultFilterManagerImpl defaultDataSetResultFilterManager(List<DataSetResultStep> steps) {
        return new DefaultDataSetResultFilterManagerImpl(steps);
    }

    @Bean
    public SemanticMoneyStep semanticMoneyStep() {
        return new SemanticMoneyStep();
    }

    @Bean
    @ConfigurationProperties(prefix = "foggy.semantic")
    public SemanticProperties semanticProperties() {
        return new SemanticProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "foggy.dataset")
    public DatasetProperties datasetProperties() {
        return new DatasetProperties();
    }

    @Bean
    public SqlFormulaServiceImpl sqlFormulaService(List<SqlFormula> sqlFormulas, ApplicationContext appCtx) {

        List<SqlFormula> all = new ArrayList<>(sqlFormulas);

        all.add(new RangeExpressionFormula(appCtx));
        all.add(new ComparisonSqlFormula(appCtx));
        all.add(new EqSqlFormula(appCtx));
        all.add(new NotEqSqlFormula(appCtx));
        all.add(new ForceEqSqlFormula(appCtx));
        all.add(new LikeExpressionFormula(appCtx));
        all.add(new NotInExpressionFormula(appCtx));
        all.add(new NotLikeExpressionFormula(appCtx));

        all.add(new InExpressionFormula(appCtx));
        all.add(new IsNullSqlFormula(appCtx));
        all.add(new IsNotNullSqlFormula(appCtx));
        all.add(new BitInExpressionFormula(appCtx));
        all.add(new IsNullAndEmptySqlFormula(appCtx));
        all.add(new IsNotNullAndEmptySqlFormula(appCtx));

        return new SqlFormulaServiceImpl(all);
    }

    @Bean
    public JdbcServiceImpl jdbcService() {
        return new JdbcServiceImpl();
    }

}
