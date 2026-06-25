package com.foggyframework.bundle.controller;

import com.foggyframework.bundle.SystemBundlesContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BundleManagementControllerConditionalTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(SystemBundlesContext.class, () -> mock(SystemBundlesContext.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldNotRegisterBundleManagementControllerByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(BundleManagementController.class));
    }

    @Test
    void shouldRegisterBundleManagementControllerWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("foggy.fsscript.bundle-management.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(BundleManagementController.class));
    }

    @Import(BundleManagementController.class)
    static class TestConfiguration {
    }
}
