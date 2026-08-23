package com.foggyframework.analytics.runtime.api;

import com.foggyframework.analytics.definition.core.AnalyticsBundleDependencyStateResolver;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStore;
import com.foggyframework.analytics.definition.core.AnalyticsDefinitionResolver;
import com.foggyframework.analytics.definition.core.FileSystemAnalyticsBundleStore;
import com.foggyframework.analytics.definition.core.StoreBackedAnalyticsDefinitionResolver;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.runtime.api.config.FoggyAnalyticsRuntimeApiProperties;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsBundlesController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsCapabilitiesController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsRenderController;
import com.foggyframework.analytics.runtime.api.controller.AnalyticsRuntimeApiExceptionHandler;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeApiResponseFactory;
import com.foggyframework.analytics.runtime.api.service.AnalyticsRuntimeHttpResponseMapper;
import com.foggyframework.analytics.runtime.core.function.AnalyticsBundleFunctionOperations;
import com.foggyframework.analytics.runtime.core.function.AnalyticsFunctionFailureMapper;
import com.foggyframework.analytics.runtime.core.function.AnalyticsFunctionRenderOperations;
import com.foggyframework.analytics.runtime.core.function.CoreAnalyticsFunctionRenderOperations;
import com.foggyframework.analytics.runtime.core.function.DefaultAnalyticsFunctionEndpoint;
import com.foggyframework.analytics.runtime.core.render.AnalyticsRenderService;
import com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAuthority;
import com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsBundleDependencyStateResolver;
import com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsQueryExecutor;
import com.foggyframework.analytics.runtime.foggy.FoggyCatalogStableModelRevisionReadPort;
import com.foggyframework.analytics.runtime.foggy.FoggyQueryAuthorityResolver;
import com.foggyframework.analytics.runtime.foggy.FoggySemanticRequestContextResolver;
import com.foggyframework.analytics.runtime.foggy.FoggyStableModelRevisionReadPort;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.semantic.port.SemanticModelCatalogReadPort;
import com.foggyframework.dataset.model.semantic.port.SemanticQueryExecutionPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.ObjectProvider;

@AutoConfiguration(afterName = "com.foggyframework.dataset.model.DbModelAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "foggy.analytics.runtime-api",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(FoggyAnalyticsRuntimeApiProperties.class)
@Import({
        AnalyticsCapabilitiesController.class,
        AnalyticsBundlesController.class,
        AnalyticsRenderController.class,
        AnalyticsRuntimeApiExceptionHandler.class
})
public class AnalyticsRuntimeApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AnalyticsRuntimeApiResponseFactory analyticsRuntimeApiResponseFactory(
            FoggyAnalyticsRuntimeApiProperties properties) {
        return new AnalyticsRuntimeApiResponseFactory(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    AnalyticsFunctionFailureMapper analyticsFunctionFailureMapper() {
        return new AnalyticsFunctionFailureMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    AnalyticsRuntimeHttpResponseMapper analyticsRuntimeHttpResponseMapper() {
        return new AnalyticsRuntimeHttpResponseMapper();
    }

    @Bean
    @ConditionalOnMissingBean(FoggyStableModelRevisionReadPort.class)
    FoggyStableModelRevisionReadPort foggyStableModelRevisionReadPort(
            CatalogSnapshotStore catalogSnapshotStore) {
        return new FoggyCatalogStableModelRevisionReadPort(
                catalogSnapshotStore);
    }

    @Bean
    @ConditionalOnMissingBean(AnalyticsBundleDependencyStateResolver.class)
    AnalyticsBundleDependencyStateResolver analyticsBundleDependencyStateResolver(
            SemanticModelCatalogReadPort catalogReadPort,
            FoggyStableModelRevisionReadPort revisionReadPort) {
        return new FoggyAnalyticsBundleDependencyStateResolver(
                catalogReadPort,
                revisionReadPort);
    }

    @Bean
    @ConditionalOnMissingBean(AnalyticsBundleStore.class)
    AnalyticsBundleStore analyticsBundleStore(
            FoggyAnalyticsRuntimeApiProperties properties,
            AnalyticsBundleDependencyStateResolver dependencyStateResolver) {
        return new FileSystemAnalyticsBundleStore(
                properties.registrations(),
                dependencyStateResolver);
    }

    @Bean
    @ConditionalOnMissingBean(AnalyticsDefinitionResolver.class)
    AnalyticsDefinitionResolver analyticsDefinitionResolver(
            AnalyticsBundleStore bundleStore) {
        return new StoreBackedAnalyticsDefinitionResolver(bundleStore);
    }

    @Bean
    @ConditionalOnMissingBean
    AnalyticsBundleFunctionOperations analyticsBundleFunctionOperations(
            FoggyAnalyticsRuntimeApiProperties properties,
            AnalyticsBundleStore bundleStore,
            AnalyticsDefinitionResolver definitionResolver) {
        return new AnalyticsBundleFunctionOperations(
                properties.registrations(),
                bundleStore,
                definitionResolver);
    }

    @Bean
    @ConditionalOnMissingBean(AnalyticsFunctionRenderOperations.class)
    @ConditionalOnBean({
            FoggySemanticRequestContextResolver.class,
            SemanticModelCatalogReadPort.class,
            SemanticQueryExecutionPort.class
    })
    AnalyticsFunctionRenderOperations analyticsFunctionRenderOperations(
            FoggyAnalyticsRuntimeApiProperties properties,
            AnalyticsDefinitionResolver definitionResolver,
            SemanticModelCatalogReadPort catalogReadPort,
            FoggyStableModelRevisionReadPort revisionReadPort,
            SemanticQueryExecutionPort queryExecutionPort,
            FoggySemanticRequestContextResolver semanticContextResolver) {
        AnalyticsRenderService<FoggyAnalyticsAuthority> renderService =
                new AnalyticsRenderService<>(
                        definitionResolver,
                        new FoggyQueryAuthorityResolver(
                                catalogReadPort,
                                revisionReadPort,
                                semanticContextResolver),
                        new FoggyAnalyticsQueryExecutor(queryExecutionPort),
                        properties.getMaxRows());
        return new CoreAnalyticsFunctionRenderOperations<>(renderService);
    }

    @Bean
    @ConditionalOnMissingBean(AnalyticsFunctionEndpoint.class)
    AnalyticsFunctionEndpoint analyticsFunctionEndpoint(
            FoggyAnalyticsRuntimeApiProperties properties,
            AnalyticsBundleFunctionOperations bundleOperations,
            ObjectProvider<AnalyticsFunctionRenderOperations> renderOperations,
            AnalyticsRuntimeApiResponseFactory responses,
            AnalyticsFunctionFailureMapper failures) {
        return new DefaultAnalyticsFunctionEndpoint(
                properties.isEnabled(),
                properties.getSecurityMode(),
                properties.getMaxRows(),
                bundleOperations,
                renderOperations::getIfAvailable,
                responses.functionResponses(),
                failures);
    }
}
