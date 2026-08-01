package io.foggytest.runtimeapi;

import com.foggyframework.runtime.api.RuntimeApiAutoConfiguration;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.controller.RuntimeArtifactLifecycleController;
import com.foggyframework.runtime.api.controller.RuntimeAuthoringReleasesController;
import com.foggyframework.runtime.api.controller.RuntimeCapabilitiesController;
import com.foggyframework.runtime.api.service.RuntimeArtifactLifecycleInventoryService;
import com.foggyframework.runtime.api.service.RuntimeAuthoringPublicationLock;
import com.foggyframework.runtime.api.service.RuntimeAuthoringReleasePackageService;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspacePublicationService;
import com.foggyframework.runtime.api.service.RuntimePublishedBundleArtifactStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeApiOutsidePackageAutoConfigurationContractTest {

    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
    private static final String AUTO_CONFIGURATION =
            "com.foggyframework.runtime.api.RuntimeApiAutoConfiguration";

    @Test
    void registersRuntimeApiForApplicationsOutsideTheFoggyPackageTree() throws IOException {
        assertThat(getClass().getPackageName()).doesNotStartWith("com.foggyframework");
        assertThat(RuntimeApiAutoConfiguration.class.getAnnotation(AutoConfiguration.class)).isNotNull();

        Import importedTypes = RuntimeApiAutoConfiguration.class.getAnnotation(Import.class);
        assertThat(importedTypes).isNotNull();
        assertThat(List.of(importedTypes.value()))
                .contains(
                        FoggyRuntimeApiProperties.class,
                        RuntimeCapabilitiesController.class,
                        RuntimeAuthoringPublicationLock.class,
                        RuntimePublishedBundleArtifactStore.class,
                        RuntimeAuthoringWorkspacePublicationService.class,
                        RuntimeAuthoringReleasePackageService.class,
                        RuntimeArtifactLifecycleInventoryService.class,
                        RuntimeAuthoringReleasesController.class,
                        RuntimeArtifactLifecycleController.class);

        int registrations = 0;
        var resources = getClass().getClassLoader().getResources(IMPORTS_RESOURCE);
        for (URL resource : Collections.list(resources)) {
            try (var stream = resource.openStream()) {
                registrations += (int) new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                        .lines()
                        .map(String::trim)
                        .filter(AUTO_CONFIGURATION::equals)
                        .count();
            }
        }
        assertThat(registrations).isEqualTo(1);
    }
}
