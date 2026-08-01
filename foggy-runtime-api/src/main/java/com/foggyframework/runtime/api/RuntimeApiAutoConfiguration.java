package com.foggyframework.runtime.api;

import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.controller.RuntimeBundlesController;
import com.foggyframework.runtime.api.controller.RuntimeAuthoringWorkspacesController;
import com.foggyframework.runtime.api.controller.RuntimeAccessController;
import com.foggyframework.runtime.api.controller.RuntimeCapabilitiesController;
import com.foggyframework.runtime.api.controller.RuntimeComposeController;
import com.foggyframework.runtime.api.controller.RuntimeDatasourcesController;
import com.foggyframework.runtime.api.controller.RuntimeFsscriptController;
import com.foggyframework.runtime.api.controller.RuntimeModelsController;
import com.foggyframework.runtime.api.controller.RuntimeQueryController;
import com.foggyframework.runtime.api.controller.RuntimeResourcesController;
import com.foggyframework.runtime.api.controller.RuntimeTablesController;
import com.foggyframework.runtime.api.security.RuntimeApiAuthInterceptor;
import com.foggyframework.runtime.api.security.RuntimeApiSecurityConfiguration;
import com.foggyframework.runtime.api.service.HikariManagedDataSourcePoolFactory;
import com.foggyframework.runtime.api.service.ManagedDataSourcePoolManager;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeBundleModelConflictDetector;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceService;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceStore;
import com.foggyframework.runtime.api.service.RuntimeAuthoringStorePathPolicy;
import com.foggyframework.runtime.api.service.RuntimeBundleInventoryService;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService;
import com.foggyframework.runtime.api.service.RuntimeCandidateQueryService;
import com.foggyframework.runtime.api.service.RuntimeComposeContextFactory;
import com.foggyframework.runtime.api.service.RuntimeComposeDialectResolver;
import com.foggyframework.runtime.api.service.RuntimeComposeRunner;
import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService;
import com.foggyframework.runtime.api.service.RuntimeFsscriptCteBridge;
import com.foggyframework.runtime.api.service.RuntimeModelOperations;
import com.foggyframework.runtime.api.service.RuntimeNamedDataSourceResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

@AutoConfiguration(afterName = "com.foggyframework.dataset.model.DbModelAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
@Import({
        FoggyRuntimeApiProperties.class,
        RuntimeApiAuthInterceptor.class,
        RuntimeApiSecurityConfiguration.class,
        HikariManagedDataSourcePoolFactory.class,
        ManagedDataSourcePoolManager.class,
        RuntimeApiResponseFactory.class,
        RuntimeAuthoringStorePathPolicy.class,
        RuntimeAuthoringWorkspaceStore.class,
        RuntimeBundleInventoryService.class,
        RuntimeAuthoringWorkspaceService.class,
        RuntimeBundleModelConflictDetector.class,
        RuntimeBundleRegistryService.class,
        RuntimeCandidateQueryService.class,
        RuntimeComposeContextFactory.class,
        RuntimeComposeDialectResolver.class,
        RuntimeComposeRunner.class,
        RuntimeDatasourceRegistryService.class,
        RuntimeFsscriptCteBridge.class,
        RuntimeModelOperations.class,
        RuntimeNamedDataSourceResolver.class,
        RuntimeBundlesController.class,
        RuntimeAuthoringWorkspacesController.class,
        RuntimeAccessController.class,
        RuntimeCapabilitiesController.class,
        RuntimeComposeController.class,
        RuntimeDatasourcesController.class,
        RuntimeFsscriptController.class,
        RuntimeModelsController.class,
        RuntimeQueryController.class,
        RuntimeResourcesController.class,
        RuntimeTablesController.class
})
public class RuntimeApiAutoConfiguration {
}
