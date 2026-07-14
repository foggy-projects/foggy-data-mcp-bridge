package io.foggytest.autoconfigure.vector;

import com.foggyframework.dataset.vector.DataSetVectorAutoConfiguration;
import com.foggyframework.dataset.vector.funs.VectorFileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataSetVectorAutoConfigurationContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSetVectorAutoConfiguration.class))
            .withPropertyValues("spring.ai.vectorstore.enabled=true")
            .withBean(VectorStore.class, () -> mock(VectorStore.class))
            .withBean(FileFsscriptLoader.class, () -> mock(FileFsscriptLoader.class))
            .withBean(FsscriptFileChangeHandler.class, () -> mock(FsscriptFileChangeHandler.class));

    @Test
    void disabledConfigurationCreatesNoLoader() {
        contextRunner
                .withPropertyValues("foggy.vector.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(VectorFileFsscriptLoader.class);
                });
    }

    @Test
    void disabledSpringVectorStoreConfigurationCreatesNoLoader() {
        contextRunner
                .withPropertyValues("spring.ai.vectorstore.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(VectorFileFsscriptLoader.class);
                });
    }

    @Test
    void missingVectorStoreClassesSafelySkipAutoConfiguration() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.ai.vectorstore"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(VectorFileFsscriptLoader.class);
                });
    }

    @Test
    void missingCoreBeanBacksOffWithoutFailingTheContext() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSetVectorAutoConfiguration.class))
                .withPropertyValues("spring.ai.vectorstore.enabled=true")
                .withBean(FileFsscriptLoader.class, () -> mock(FileFsscriptLoader.class))
                .withBean(FsscriptFileChangeHandler.class, () -> mock(FsscriptFileChangeHandler.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(VectorFileFsscriptLoader.class);
                });
    }

    @Test
    void completeConditionsCreateExactlyOneLoaderWithoutConnectingToVectorStore() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(VectorFileFsscriptLoader.class);
        });
    }

    @Test
    void userLoaderBacksOffDefaultLoader() {
        VectorFileFsscriptLoader customLoader = mock(VectorFileFsscriptLoader.class);

        contextRunner
                .withBean(VectorFileFsscriptLoader.class, () -> customLoader)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(VectorFileFsscriptLoader.class);
                    assertThat(context.getBean(VectorFileFsscriptLoader.class)).isSameAs(customLoader);
                });
    }

    @Test
    void bootThreeImportsRegistersVectorAutoConfiguration() throws IOException {
        assertThat(autoConfigurationImports())
                .contains(DataSetVectorAutoConfiguration.class.getName());
    }

    private static String autoConfigurationImports() throws IOException {
        var resources = DataSetVectorAutoConfiguration.class.getClassLoader().getResources(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        StringBuilder content = new StringBuilder();
        for (URL resource : Collections.list(resources)) {
            content.append(new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8));
            content.append('\n');
        }
        return content.toString();
    }
}
