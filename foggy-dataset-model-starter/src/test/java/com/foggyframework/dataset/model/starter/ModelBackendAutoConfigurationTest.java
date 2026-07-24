package com.foggyframework.dataset.model.starter;

import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.api.backend.BackendCapability;
import com.foggyframework.dataset.model.api.backend.BackendDescriptor;
import com.foggyframework.dataset.model.api.backend.BackendId;
import com.foggyframework.dataset.model.api.backend.QueryBackendProvider;
import com.foggyframework.dataset.model.core.backend.BackendProviderCatalog;
import com.foggyframework.dataset.model.core.backend.DuplicateBackendProviderException;
import com.foggyframework.dataset.model.core.backend.MissingBackendProviderException;
import com.foggyframework.dataset.model.jdbc.JdbcQueryBackendProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelBackendAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ModelBackendAutoConfiguration.class));

    @Test
    void createsJdbcProviderAndCatalogWhenFacadeExists() {
        contextRunner.withBean(QueryFacade.class, () -> request -> null)
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(QueryBackendProvider.class).size());
                    BackendProviderCatalog catalog = context.getBean(BackendProviderCatalog.class);
                    QueryBackendProvider provider = catalog.require(
                            JdbcQueryBackendProvider.JDBC,
                            BackendCapability.QUERY,
                            QueryBackendProvider.class);
                    assertSame(context.getBean(QueryBackendProvider.class), provider);
                });
    }

    @Test
    void missingFacadeDoesNotCreatePartialProvider() {
        contextRunner.run(context -> {
            assertEquals(0, context.getBeansOfType(QueryBackendProvider.class).size());
            BackendProviderCatalog catalog = context.getBean(BackendProviderCatalog.class);
            assertThrows(MissingBackendProviderException.class,
                    () -> catalog.require(JdbcQueryBackendProvider.JDBC));
        });
    }

    @Test
    void userProviderAndCatalogBackOffDefaultBeans() {
        contextRunner.withUserConfiguration(UserProviderConfiguration.class)
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(QueryBackendProvider.class).size());
                    assertSame(context.getBean("customProvider"),
                            context.getBean(QueryBackendProvider.class));
                    assertEquals(BackendId.of("custom"), context.getBean(BackendProviderCatalog.class)
                            .providers().get(0).descriptor().backendId());
                });

        BackendProviderCatalog userCatalog = BackendProviderCatalog.of(java.util.List.of());
        contextRunner.withBean(BackendProviderCatalog.class, () -> userCatalog)
                .run(context -> assertSame(userCatalog, context.getBean(BackendProviderCatalog.class)));
    }

    @Test
    void jdbcProviderCoexistsWithAnotherBackend() {
        contextRunner.withBean(QueryFacade.class, () -> request -> null)
                .withUserConfiguration(UserProviderConfiguration.class)
                .run(context -> {
                    assertEquals(2, context.getBeansOfType(QueryBackendProvider.class).size());
                    BackendProviderCatalog catalog = context.getBean(BackendProviderCatalog.class);
                    assertTrue(catalog.providers().stream()
                            .anyMatch(provider -> JdbcQueryBackendProvider.JDBC.equals(
                                    provider.descriptor().backendId())));
                    assertTrue(catalog.providers().stream()
                            .anyMatch(provider -> BackendId.of("custom").equals(
                                    provider.descriptor().backendId())));
                });
    }

    @Test
    void namedJdbcProviderBacksOffDefaultAdapter() {
        contextRunner.withBean(QueryFacade.class, () -> request -> null)
                .withUserConfiguration(JdbcOverrideConfiguration.class)
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(QueryBackendProvider.class).size());
                    assertSame(context.getBean("jdbcQueryBackendProvider"),
                            context.getBean(QueryBackendProvider.class));
                });
    }

    @Test
    void duplicateUserProvidersFailContextClosed() {
        contextRunner.withUserConfiguration(DuplicateProviderConfiguration.class)
                .run(context -> assertInstanceOf(DuplicateBackendProviderException.class,
                        rootCause(context.getStartupFailure())));
    }

    @Test
    void missingJdbcAdapterDisablesAutoConfiguration() {
        contextRunner.withClassLoader(new FilteredClassLoader(JdbcQueryBackendProvider.class))
                .withBean(QueryFacade.class, () -> request -> null)
                .run(context -> {
                    assertEquals(0, context.getBeansOfType(QueryBackendProvider.class).size());
                    assertEquals(0, context.getBeansOfType(BackendProviderCatalog.class).size());
                });
    }

    private Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @Configuration(proxyBeanMethods = false)
    static class UserProviderConfiguration {
        @Bean
        QueryBackendProvider customProvider() {
            return provider("custom");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateProviderConfiguration {
        @Bean
        QueryBackendProvider firstProvider() {
            return provider("duplicate");
        }

        @Bean
        QueryBackendProvider secondProvider() {
            return provider("duplicate");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbcOverrideConfiguration {
        @Bean
        QueryBackendProvider jdbcQueryBackendProvider() {
            return provider("jdbc");
        }
    }

    private static QueryBackendProvider provider(String id) {
        BackendDescriptor descriptor = new BackendDescriptor(
                BackendId.of(id), Set.of(BackendCapability.QUERY));
        QueryFacade facade = request -> null;
        return new QueryBackendProvider() {
            @Override
            public BackendDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public QueryFacade queryFacade() {
                return facade;
            }
        };
    }
}
