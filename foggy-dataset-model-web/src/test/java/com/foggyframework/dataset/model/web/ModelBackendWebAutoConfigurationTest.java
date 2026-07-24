package com.foggyframework.dataset.model.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendDescriptor;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.api.backend.BackendProvider;
import com.foggyframework.dataset.model.api.backend.CacheInvalidationBackendProvider;
import com.foggyframework.dataset.model.api.backend.CacheInvalidationPort;
import com.foggyframework.dataset.model.api.backend.QueryBackendProvider;
import com.foggyframework.dataset.model.core.backend.BackendProviderCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

class ModelBackendWebAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ModelBackendWebAutoConfiguration.class))
            .withUserConfiguration(WebMvcConfiguration.class);

    @Test
    void endpointIsOptInAndRequiresCatalog() {
        contextRunner.withBean(BackendProviderCatalog.class, ModelBackendWebAutoConfigurationTest::catalog)
                .run(context -> assertThat(context)
                        .doesNotHaveBean(BackendProviderCatalogController.class));

        contextRunner.withPropertyValues("foggy.model.backends.web.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(BackendProviderCatalogController.class));
    }

    @Test
    void exposesDeterministicProviderSnapshotWhenEnabled() {
        contextRunner.withPropertyValues("foggy.model.backends.web.enabled=true")
                .withBean(BackendProviderCatalog.class, ModelBackendWebAutoConfigurationTest::catalog)
                .run(context -> {
                    assertThat(context).hasSingleBean(BackendProviderCatalogController.class);
                    String content = webAppContextSetup(context).build()
                            .perform(get("/foggy/model/backends"))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
                    List<BackendProviderView> response = new ObjectMapper().readValue(
                            content, new TypeReference<>() { });
                    assertThat(response).containsExactly(
                            new BackendProviderView("jdbc", List.of("QUERY")),
                            new BackendProviderView("mongo", List.of("CACHE_INVALIDATION", "QUERY")));
                });
    }

    @Test
    void userControllerBacksOffDefaultBean() {
        BackendProviderCatalog catalog = catalog();
        BackendProviderCatalogController userController =
                new BackendProviderCatalogController(catalog);
        contextRunner.withPropertyValues("foggy.model.backends.web.enabled=true")
                .withBean(BackendProviderCatalog.class, () -> catalog)
                .withBean(BackendProviderCatalogController.class, () -> userController)
                .run(context -> assertThat(context.getBean(BackendProviderCatalogController.class))
                        .isSameAs(userController));
    }

    @Test
    void missingCoreDependencyDisablesAutoConfiguration() {
        contextRunner.withPropertyValues("foggy.model.backends.web.enabled=true")
                .withClassLoader(new FilteredClassLoader(BackendProviderCatalog.class))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(BackendProviderCatalogController.class));
    }

    private static BackendProviderCatalog catalog() {
        return BackendProviderCatalog.of(List.of(
                provider("mongo", BackendCapability.QUERY, BackendCapability.CACHE_INVALIDATION),
                provider("jdbc", BackendCapability.QUERY)));
    }

    private static BackendProvider provider(String id, BackendCapability... capabilities) {
        BackendDescriptor descriptor = new BackendDescriptor(
                BackendId.of(id), Set.of(capabilities));
        return new TestBackendProvider(descriptor);
    }

    private static final class TestBackendProvider
            implements QueryBackendProvider, CacheInvalidationBackendProvider, CacheInvalidationPort {

        private final BackendDescriptor descriptor;
        private final QueryFacade queryFacade = request -> null;

        private TestBackendProvider(BackendDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public BackendDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public QueryFacade queryFacade() {
            return queryFacade;
        }

        @Override
        public CacheInvalidationPort cacheInvalidation() {
            return this;
        }

        @Override
        public void evict(String modelName) {
            // Snapshot fixture only.
        }

        @Override
        public void evictAll() {
            // Snapshot fixture only.
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class WebMvcConfiguration {
    }
}
