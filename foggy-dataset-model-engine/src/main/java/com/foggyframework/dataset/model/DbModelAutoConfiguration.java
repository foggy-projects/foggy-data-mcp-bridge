package com.foggyframework.dataset.model;


import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.SystemBundlesContextImpl;
import com.foggyframework.dataset.DataSetAutoConfiguration;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.config.SemanticProperties;
import com.foggyframework.dataset.model.candidate.CandidateQueryFactory;
import com.foggyframework.dataset.model.candidate.DefaultCandidateQueryFactory;
import com.foggyframework.dataset.model.backend.JdbcEngineBackendProvider;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.engine.pivot.LocalPivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.model.engine.pivot.PivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.model.engine.pivot.PivotOuterCacheModelIdentityProvider;
import com.foggyframework.dataset.model.engine.pivot.RuntimeBundlePivotOuterCacheModelIdentityProvider;
import com.foggyframework.dataset.model.engine.formula.*;
import com.foggyframework.dataset.model.engine.query_model.DbModelFileChangeHandler;
import com.foggyframework.dataset.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionPort;
import com.foggyframework.dataset.model.semantic.port.PivotOuterCacheEvictionPort;
import com.foggyframework.dataset.model.semantic.permission.AuthorizationSignatureService;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionService;
import com.foggyframework.dataset.model.semantic.service.DefaultComposeExecutionPort;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.impl.loader.JdbcTableModelLoaderImpl;
import com.foggyframework.dataset.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.lifecycle.port.CommittedSourceRevisionGuard;
import com.foggyframework.dataset.model.lifecycle.port.StaleSourceRevisionException;
import com.foggyframework.dataset.model.plugins.result_set_filter.DataSetResultFilterManager;
import com.foggyframework.dataset.model.plugins.result_set_filter.DataSetResultStep;
import com.foggyframework.dataset.model.plugins.result_set_filter.DefaultDataSetResultFilterManagerImpl;
import com.foggyframework.dataset.model.plugins.result_set_filter.SemanticMoneyStep;
import com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionStep;
import com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionStepExecutor;
import com.foggyframework.dataset.model.service.impl.JdbcServiceImpl;
import com.foggyframework.dataset.model.spi.DbModelLoadProcessor;
import com.foggyframework.dataset.model.spi.QueryModelBuilder;
import com.foggyframework.dataset.model.spi.TableModelLoader;
import com.foggyframework.dataset.model.spi.TableModelLoaderManager;
import com.foggyframework.dataset.model.validation.DefaultDetachedModelValidationFactory;
import com.foggyframework.dataset.model.validation.DetachedModelValidationFactory;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

@AutoConfiguration(
        after = DataSetAutoConfiguration.class,
        afterName = {
                "com.foggyframework.bundle.FoggyBundleConfiguration",
                "com.foggyframework.fsscript.FoggyFscriptAutoConfiguration"
        })
@Import({
        com.foggyframework.dataset.model.config.QmValidationOnStartup.class,
        com.foggyframework.dataset.model.controller.DimensionDataStoreController.class,
        com.foggyframework.dataset.model.controller.FoggyDatasetExceptionHandler.class,
        com.foggyframework.dataset.model.controller.QueryModelDataStoreController.class,
        com.foggyframework.dataset.model.controller.SemanticController.class,
        com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelBuilder.class,
        com.foggyframework.dataset.model.event.BundleLifecycleListener.class,
        com.foggyframework.dataset.model.interceptor.SqlLoggingInterceptor.class,
        com.foggyframework.dataset.model.plugins.query_execution.L2CacheStep.class,
        com.foggyframework.dataset.model.plugins.query_execution.PhysicalColumnPermissionStep.class,
        com.foggyframework.dataset.model.plugins.query_execution.PreAggRewriteStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.AggregateMemberFilterRewriteStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.AuthorizationSignatureStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.AutoGroupByStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.FieldAccessPermissionStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.InlineExpressionPreprocessStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.L1CacheStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.ModelFieldPermissionResolveStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.ModelPermissionEnforcementStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.QueryRequestValidationStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.SchemaAwareFieldValidationStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.SubtotalStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.SyntheticMemberExternalPatchStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.SyntheticMemberInternalPatchStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.SyntheticMemberQueryBuilderStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.SystemSliceMergeStep.class,
        com.foggyframework.dataset.model.plugins.result_set_filter.TimeWindowInterceptor.class,
        com.foggyframework.dataset.model.semantic.controller.NativeDatasetController.class,
        com.foggyframework.dataset.model.semantic.controller.PivotOuterCacheAdminController.class,
        com.foggyframework.dataset.model.semantic.controller.SemanticServiceV3TestController.class,
        com.foggyframework.dataset.model.semantic.member.SyntheticMemberQueryModelFactory.class,
        com.foggyframework.dataset.model.semantic.permission.FieldPermissionResolver.class,
        com.foggyframework.dataset.model.semantic.service.NativeComposeQueryService.class,
        com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService.class,
        com.foggyframework.dataset.model.semantic.service.impl.DictionaryDiscoveryServiceImpl.class,
        com.foggyframework.dataset.model.semantic.service.impl.DimensionMemberLoaderImpl.class,
        com.foggyframework.dataset.model.semantic.service.impl.SemanticQueryServiceV3Impl.class,
        com.foggyframework.dataset.model.semantic.service.impl.SemanticServiceV3Impl.class,
        com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper.class,
        com.foggyframework.dataset.model.service.impl.DbModelDictServiceImpl.class,
        com.foggyframework.dataset.model.service.impl.QueryFacadeImpl.class
})
@Slf4j
public class DbModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthorizationSignatureService authorizationSignatureService() {
        return new AuthorizationSignatureService();
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelPermissionService modelPermissionService() {
        return new ModelPermissionService();
    }

    @Bean
    public JdbcTableModelLoaderImpl jdbcTableModelLoader(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader) {
        return new JdbcTableModelLoaderImpl(systemBundlesContext, fileFsscriptLoader );
    }
    @Bean
    @ConditionalOnMissingBean(CatalogSnapshotStore.class)
    public CatalogSnapshotStore catalogSnapshotStore(
            ObjectProvider<CommittedSourceRevisionRegistry> sourceRegistryProvider
    ) {
        CommittedSourceRevisionRegistry sourceRegistry =
                sourceRegistryProvider.getIfAvailable();
        if (sourceRegistry == null) {
            return new CatalogSnapshotStore();
        }
        CommittedSourceRevisionGuard guard = new CommittedSourceRevisionGuard() {
            @Override
            public SourceRevision currentSourceRevision(String namespace) {
                return new SourceRevision(sourceRegistry.currentRevision(namespace));
            }

            @Override
            public <T> T publishIfCurrent(
                    String namespace,
                    SourceRevision expected,
                    java.util.function.Supplier<T> publication
            ) {
                try {
                    return sourceRegistry.publishIfCurrent(
                            namespace, expected.value(), publication);
                } catch (CommittedSourceRevisionRegistry
                        .CommittedSourceRevisionChangedException stale) {
                    throw new StaleSourceRevisionException(
                            namespace,
                            expected,
                            currentSourceRevision(namespace));
                }
            }
        };
        return new CatalogSnapshotStore(guard);
    }

    @Bean
    public TableModelLoaderManagerImpl tableModelLoaderManager(SystemBundlesContext systemBundlesContext,
                                                               FileFsscriptLoader fileFsscriptLoader,
                                                               List<DbModelLoadProcessor> processors,
                                                               List<TableModelLoader> loaders,
                                                               @org.springframework.beans.factory.annotation.Autowired(required = false) com.foggyframework.dataset.model.spi.NamedDataSourceResolver namedDataSourceResolver,
                                                               DatasetProperties datasetProperties,
                                                               CatalogSnapshotStore catalogSnapshotStore) {
        return new TableModelLoaderManagerImpl(systemBundlesContext, fileFsscriptLoader, processors, loaders,
                namedDataSourceResolver, datasetProperties, catalogSnapshotStore);
    }
    @Bean
    public QueryModelLoaderImpl jdbcQueryModelLoader(TableModelLoaderManager tableModelLoaderManager,
                                                     SystemBundlesContext systemBundlesContext,
                                                     FileFsscriptLoader fileFsscriptLoader,
                                                     List<QueryModelBuilder> queryModelBuilders,
                                                     CatalogSnapshotStore catalogSnapshotStore) {
        return new QueryModelLoaderImpl(tableModelLoaderManager, systemBundlesContext, fileFsscriptLoader,
                queryModelBuilders, catalogSnapshotStore);
    }

    @Bean
    @ConditionalOnMissingBean(DetachedModelValidationFactory.class)
    public DetachedModelValidationFactory detachedModelValidationFactory(
            SystemBundlesContext systemBundlesContext,
            TableModelLoaderManager tableModelLoaderManager,
            QueryModelLoaderImpl queryModelLoader
    ) {
        return new DefaultDetachedModelValidationFactory(
                systemBundlesContext, tableModelLoaderManager, queryModelLoader);
    }

    @Bean
    @ConditionalOnMissingBean(CandidateQueryFactory.class)
    public CandidateQueryFactory candidateQueryFactory(
            SystemBundlesContext systemBundlesContext,
            DetachedModelValidationFactory detachedModelValidationFactory,
            SemanticQueryServiceV3 semanticQueryServiceV3,
            ObjectProvider<CommittedSourceRevisionRegistry> sourceRegistryProvider
    ) {
        CommittedSourceRevisionRegistry sourceRegistry =
                sourceRegistryProvider.getIfAvailable();
        if (sourceRegistry == null
                && systemBundlesContext instanceof SystemBundlesContextImpl context) {
            sourceRegistry = context.getSourceRevisionRegistry();
        }
        if (sourceRegistry == null) {
            throw new IllegalStateException(
                    "Candidate query requires CommittedSourceRevisionRegistry");
        }
        return new DefaultCandidateQueryFactory(
                systemBundlesContext,
                detachedModelValidationFactory,
                semanticQueryServiceV3,
                sourceRegistry
        );
    }

    @Bean
    public CatalogRefreshCoordinator catalogRefreshCoordinator(
            CatalogSnapshotStore catalogSnapshotStore,
            TableModelLoaderManagerImpl tableModelLoaderManager,
            QueryModelLoaderImpl queryModelLoader
    ) {
        return new CatalogRefreshCoordinator(
                catalogSnapshotStore, tableModelLoaderManager, queryModelLoader);
    }

    @Bean
    @ConditionalOnMissingBean(name = "jdbcQueryBackendProvider")
    public JdbcEngineBackendProvider jdbcQueryBackendProvider(
            QueryFacade queryFacade,
            QueryModelLoaderImpl queryModelLoader,
            CatalogRefreshCoordinator catalogRefreshCoordinator
    ) {
        return new JdbcEngineBackendProvider(
                queryFacade, queryModelLoader, catalogRefreshCoordinator);
    }

    @Bean
    public DbModelFileChangeHandler jdbcModelFileChangeHandler(
            QueryModelLoaderImpl jdbcQueryModelLoader,
            TableModelLoaderManagerImpl jdbcModelLoader,
            CatalogRefreshCoordinator catalogRefreshCoordinator
    ) {
        return new DbModelFileChangeHandler(
                jdbcQueryModelLoader, jdbcModelLoader, catalogRefreshCoordinator);
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
    public QueryExecutionStepExecutor queryExecutionStepExecutor(List<QueryExecutionStep> steps) {
        return new QueryExecutionStepExecutor(steps);
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
    @ConditionalOnProperty(
            name = "foggy.dataset.datasource.allow-global-fallback-for-namespace",
            havingValue = "true")
    public SmartInitializingSingleton globalNamespaceFallbackRiskDiagnostic(Environment environment) {
        return () -> log.warn(
                "FOGGY-SEC-932-001: global datasource fallback for a non-empty namespace is enabled; "
                        + "this compatibility mode can broaden production data access. activeProfiles={}",
                String.join(",", environment.getActiveProfiles()));
    }

    @Bean
    @ConditionalOnMissingBean(PivotOuterCacheModelIdentityProvider.class)
    public PivotOuterCacheModelIdentityProvider pivotOuterCacheModelIdentityProvider(
            SystemBundlesContext systemBundlesContext) {
        return new RuntimeBundlePivotOuterCacheModelIdentityProvider(systemBundlesContext);
    }

    @Bean
    @ConditionalOnMissingBean(PivotOuterCacheInvalidationBroadcaster.class)
    public PivotOuterCacheInvalidationBroadcaster pivotOuterCacheInvalidationBroadcaster(
            ObjectProvider<PivotOuterCacheEvictionPort> evictionPortProvider) {
        return new LocalPivotOuterCacheInvalidationBroadcaster(evictionPortProvider);
    }

    @Bean
    @ConditionalOnMissingBean(ComposeExecutionPort.class)
    public ComposeExecutionPort composeExecutionPort(
            SemanticQueryServiceV3 semanticQueryServiceV3,
            ObjectProvider<AuthorityResolver> authorityResolvers,
            @Value("${foggy.compose.dialect:mysql}") String defaultDialect) {
        return new DefaultComposeExecutionPort(
                semanticQueryServiceV3, authorityResolvers, defaultDialect);
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
