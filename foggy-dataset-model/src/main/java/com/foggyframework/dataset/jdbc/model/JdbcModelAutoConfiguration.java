package com.foggyframework.dataset.jdbc.model;


import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.jdbc.model.config.SemanticProperties;
import com.foggyframework.dataset.jdbc.model.engine.formula.*;
import com.foggyframework.dataset.jdbc.model.engine.query_model.JdbcModelFileChangeHandler;
import com.foggyframework.dataset.jdbc.model.engine.query_model.JdbcQueryModelLoaderImpl;
import com.foggyframework.dataset.jdbc.model.impl.loader.JdbcTableModelLoaderImpl;
import com.foggyframework.dataset.jdbc.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.DataSetResultFilterManager;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.DataSetResultStep;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.DefaultDataSetResultFilterManagerImpl;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.SemanticMoneyStep;
import com.foggyframework.dataset.jdbc.model.service.impl.JdbcServiceImpl;
import com.foggyframework.dataset.jdbc.model.spi.JdbcModelLoadProcessor;
import com.foggyframework.dataset.jdbc.model.spi.QueryModelBuilder;
import com.foggyframework.dataset.jdbc.model.spi.TableModelLoader;
import com.foggyframework.dataset.jdbc.model.spi.TableModelLoaderManager;
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
@ComponentScan("com.foggyframework.dataset.jdbc.model")
public class JdbcModelAutoConfiguration {

    @Bean
    public JdbcTableModelLoaderImpl jdbcTableModelLoader(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader) {
        return new JdbcTableModelLoaderImpl(systemBundlesContext, fileFsscriptLoader );
    }
    @Bean
    public TableModelLoaderManagerImpl tableModelLoaderManager(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader, List<JdbcModelLoadProcessor> processors, List<TableModelLoader> loaders) {
        return new TableModelLoaderManagerImpl(systemBundlesContext, fileFsscriptLoader, processors,loaders);
    }
    @Bean
    public JdbcQueryModelLoaderImpl jdbcQueryModelLoader(TableModelLoaderManager tableModelLoaderManager,
                                                         SystemBundlesContext systemBundlesContext,
                                                         FileFsscriptLoader fileFsscriptLoader,
                                                         List<QueryModelBuilder> queryModelBuilders) {
        return new JdbcQueryModelLoaderImpl(tableModelLoaderManager,  systemBundlesContext, fileFsscriptLoader,queryModelBuilders);
    }

    @Bean
    public JdbcModelFileChangeHandler jdbcModelFileChangeHandler(JdbcQueryModelLoaderImpl jdbcQueryModelLoader, TableModelLoaderManagerImpl jdbcModelLoader) {
        return new JdbcModelFileChangeHandler(jdbcQueryModelLoader, jdbcModelLoader);
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
    @ConfigurationProperties(prefix = "semantic")
    public SemanticProperties semanticProperties() {
        return new SemanticProperties();
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
