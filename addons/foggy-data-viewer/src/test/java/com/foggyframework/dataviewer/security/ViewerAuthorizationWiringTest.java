package com.foggyframework.dataviewer.security;

import com.foggyframework.dataviewer.config.*;
import com.foggyframework.dataviewer.plugins.LargeResultTruncationStep;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ViewerAuthorizationWiringTest {
    @TempDir Path directory;
    @Configuration(proxyBeanMethods=false)
    static class Engine {
        // Faithful dependency: QueryFacade -> engine result-filter manager -> viewer truncation step.
        @Bean QueryFacade queryFacade(LargeResultTruncationStep step) { return mock(QueryFacade.class); }
        @Bean ViewerIdentityProvider identityProvider() { return mock(ViewerIdentityProvider.class); }
        @Bean DatasetProperties datasetProperties() { return new DatasetProperties(); }
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
    }
    @Test void resultStepAndGrantServiceDoNotCreateAQueryFacadeDependencyCycle() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataViewerAutoConfiguration.class,ViewerAuthorizationAutoConfiguration.class))
                .withUserConfiguration(Engine.class)
                .withPropertyValues("foggy.data-viewer.link-authorization.enabled=true","foggy.data-viewer.cache.sqlite-path="+directory.resolve("viewer.sqlite"))
                .run(context->{ assertThat(context).hasNotFailed(); assertThat(context).hasSingleBean(ViewerGrantService.class); assertThat(context).hasSingleBean(SqliteViewerGrantStore.class); });
    }
}
