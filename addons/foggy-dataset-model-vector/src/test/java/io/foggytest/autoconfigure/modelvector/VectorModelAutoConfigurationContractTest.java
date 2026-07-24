package io.foggytest.autoconfigure.modelvector;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.impl.vector.TmVectorModelLoaderImpl;
import com.foggyframework.dataset.model.vector.VectorModelAutoConfiguration;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VectorModelAutoConfigurationContractTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VectorModelAutoConfiguration.class))
            .withBean(SystemBundlesContext.class, () -> mock(SystemBundlesContext.class))
            .withBean(FileFsscriptLoader.class, () -> mock(FileFsscriptLoader.class));

    @Test
    void loaderIsNotDiscoveredAsAnUnconditionalService() {
        assertThat(TmVectorModelLoaderImpl.class.getAnnotation(Service.class)).isNull();
    }

    @Test
    void bootThreeImportsRegistersTheModelVectorAutoConfiguration() throws IOException {
        assertThat(autoConfigurationImports())
                .contains("com.foggyframework.dataset.model.vector.VectorModelAutoConfiguration");
    }

    @Test
    void disabledConfigurationCreatesNoLoader() {
        contextRunner
                .withPropertyValues("foggy.vector.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TmVectorModelLoaderImpl.class);
                });
    }

    @Test
    void missingMilvusClassesSafelySkipAutoConfiguration() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("io.milvus"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TmVectorModelLoaderImpl.class);
                });
    }

    @Test
    void missingWebFluxClassesSafelySkipAutoConfiguration() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.web.reactive"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TmVectorModelLoaderImpl.class);
                });
    }

    @Test
    void completeConditionsCreateExactlyOneLoaderWithoutConnectingToMilvus() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TmVectorModelLoaderImpl.class);
        });
    }

    @Test
    void userLoaderBacksOffDefaultLoader() {
        TmVectorModelLoaderImpl customLoader = mock(TmVectorModelLoaderImpl.class);

        contextRunner
                .withBean(TmVectorModelLoaderImpl.class, () -> customLoader)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TmVectorModelLoaderImpl.class);
                    assertThat(context.getBean(TmVectorModelLoaderImpl.class)).isSameAs(customLoader);
                });
    }

    private static String autoConfigurationImports() throws IOException {
        var resources = TmVectorModelLoaderImpl.class.getClassLoader().getResources(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        StringBuilder content = new StringBuilder();
        for (URL resource : Collections.list(resources)) {
            content.append(new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8));
            content.append('\n');
        }
        return content.toString();
    }
}
