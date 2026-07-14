package com.foggyframework.dataset.mcp.spi.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.db.model.semantic.service.SemanticModelCatalogService.NamespaceCatalogView;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.mcp.config.McpProperties;
import com.foggyframework.dataset.mcp.service.ModelCatalogService;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Minimal Spring constructor-selection smoke for the shared catalog authority. */
class CatalogAuthoritySpringWiringTest {

    @Test
    @SuppressWarnings("unchecked")
    void springSelectsTrackedConstructorsAndInjectsOneAuthorityBean() {
        SemanticServiceV3 semanticService = mock(SemanticServiceV3.class);
        SemanticQueryServiceV3 queryService = mock(SemanticQueryServiceV3.class);
        SystemBundlesContext bundles = mock(SystemBundlesContext.class);
        QueryModelLoader loader = mock(QueryModelLoader.class);
        SemanticModelCatalogService authority = mock(SemanticModelCatalogService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        McpProperties properties = new McpProperties();
        properties.getSemantic().setUseAllModels(true);
        NamespaceCatalogView view = trackedView();

        when(authority.getAllModelNames("tenant-a"))
                .thenReturn(view.modelNames());
        when(authority.namespaceCatalogView("tenant-a"))
                .thenReturn(view);
        SemanticMetadataResponse metadata = new SemanticMetadataResponse();
        metadata.setData(Map.of("fields", Map.of(), "models", Map.of()));
        when(semanticService.getMetadata(any(), eq("json"), any()))
                .thenReturn(metadata);

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(SemanticServiceV3.class, () -> semanticService);
            context.registerBean(SemanticQueryServiceV3.class, () -> queryService);
            context.registerBean(SystemBundlesContext.class, () -> bundles);
            context.registerBean(QueryModelLoader.class, () -> loader);
            context.registerBean(SemanticModelCatalogService.class, () -> authority);
            context.registerBean(ObjectMapper.class, () -> objectMapper);
            context.registerBean(McpProperties.class, () -> properties);
            context.registerBean(SemanticServiceResolverImpl.class);
            context.registerBean(ModelCatalogService.class);
            context.refresh();

            SemanticServiceResolver resolver = context.getBean(
                    SemanticServiceResolver.class);
            ModelCatalogService catalogService = context.getBean(
                    ModelCatalogService.class);

            assertThat(resolver.getAllModelNames("tenant-a"))
                    .containsExactly("SpringModel");
            Map<String, Object> catalog = catalogService.buildCatalog(
                    Map.of("fieldLimit", 0), "tenant-a", null);
            assertThat((List<String>) catalog.get("models"))
                    .containsExactly("SpringModel");
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) catalog.get("items");
            assertThat(items).singleElement().satisfies(item -> {
                assertThat(item.get("shortAlias")).isEqualTo("SM");
                assertThat(item.get("caption"))
                        .isEqualTo("Spring model caption");
            });

            assertThat(context.getBean(SemanticModelCatalogService.class))
                    .isSameAs(authority);
            verify(authority).getAllModelNames("tenant-a");
            verify(authority, times(2)).namespaceCatalogView("tenant-a");
            verifyNoInteractions(loader, bundles);
        }
    }

    private static NamespaceCatalogView trackedView() {
        QueryModel model = mock(QueryModel.class);
        when(model.getName()).thenReturn("SpringModel");
        when(model.getShortAlias()).thenReturn("SM");
        when(model.getCaption()).thenReturn("Spring model caption");
        CatalogIdentity identity = new CatalogIdentity(
                "tenant-a",
                new CatalogGeneration("spring-g1"),
                new SourceRevision("spring-r1"));
        DatasourceBindingIdentity binding = new DatasourceBindingIdentity(
                "tenant-a:primary",
                "jdbc",
                new DatasourceBindingGeneration("spring-b1"));
        CatalogResolution<QueryModel> resolution = new CatalogResolution<>(
                "SpringModel",
                model,
                identity,
                Map.of(binding.bindingKey(), binding),
                true);
        return new NamespaceCatalogView(
                identity,
                List.of("SpringModel"),
                Map.of("SpringModel", "SM"),
                Map.of("SpringModel", model),
                Map.of("SpringModel", resolution));
    }
}
