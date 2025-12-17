package com.foggyframework.dataset.jdbc.model;


import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.jdbc.model.config.SemanticProperties;
import com.foggyframework.dataset.jdbc.model.engine.formula.*;
import com.foggyframework.dataset.jdbc.model.engine.query_model.JdbcModelFileChangeHandler;
import com.foggyframework.dataset.jdbc.model.engine.query_model.JdbcQueryModelLoaderImpl;
import com.foggyframework.dataset.jdbc.model.impl.loader.JdbcModelLoaderImpl;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.DataSetResultFilterManager;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.DataSetResultStep;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.DefaultDataSetResultFilterManagerImpl;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.SemanticMoneyStep;
import com.foggyframework.dataset.jdbc.model.service.impl.JdbcServiceImpl;
import com.foggyframework.dataset.jdbc.model.spi.JdbcModelLoadProcessor;
import com.foggyframework.dataset.jdbc.model.spi.JdbcModelLoader;
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
    public JdbcModelLoaderImpl jdbcModelLoader(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader, List<JdbcModelLoadProcessor> processors) {
        return new JdbcModelLoaderImpl(systemBundlesContext, fileFsscriptLoader, processors);
    }

    @Bean
    public JdbcQueryModelLoaderImpl jdbcQueryModelLoader(JdbcModelLoader jdbcModelLoader,
                                                         SqlFormulaService sqlFormulaService,
                                                         SystemBundlesContext systemBundlesContext,
                                                         FileFsscriptLoader fileFsscriptLoader) {
        return new JdbcQueryModelLoaderImpl(jdbcModelLoader, sqlFormulaService, systemBundlesContext, fileFsscriptLoader);
    }

    @Bean
    public JdbcModelFileChangeHandler jdbcModelFileChangeHandler(JdbcQueryModelLoaderImpl jdbcQueryModelLoader, JdbcModelLoaderImpl jdbcModelLoader) {
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
