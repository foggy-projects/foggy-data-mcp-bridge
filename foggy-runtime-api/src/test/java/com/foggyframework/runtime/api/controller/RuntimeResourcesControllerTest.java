package com.foggyframework.runtime.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.ResourceSaveFile;
import com.foggyframework.runtime.api.dto.ResourceSaveRequest;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeAuthoringPublicationLock;
import com.foggyframework.runtime.api.service.RuntimeBundleRegistryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RuntimeResourcesControllerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void immutablePublicationRejectsSaveBeforeFilesystemMutation()
            throws Exception {
        Path root = Files.createDirectories(tempDirectory.resolve("published"));
        Path resource = Files.writeString(root.resolve("Order.tm"), "published");
        Fixture fixture = fixture("immutable");
        var base = fixture.registry().newRecord(
                "orders", "sales", root.toString(), false, true);
        fixture.registry().save(base.withPublication(
                root.toString(), "sha256:" + "a".repeat(64)));

        var response = fixture.controller().saveResources(
                request("orders", "changed"), "sales");

        assertThat(response.success()).isFalse();
        assertThat(response.error().code()).isEqualTo("RESOURCE_BUNDLE_IMMUTABLE");
        assertThat(response.error().phase()).isEqualTo("resources.save");
        assertThat(resource).hasContent("published");
    }

    @Test
    void mutableRuntimeManagedBundleRetainsExistingSaveBehavior()
            throws Exception {
        Path root = Files.createDirectories(tempDirectory.resolve("mutable"));
        Path resource = Files.writeString(root.resolve("Order.tm"), "base");
        Fixture fixture = fixture("mutable-registry");
        fixture.registry().save(fixture.registry().newRecord(
                "orders", "sales", root.toString(), false, true));

        var response = fixture.controller().saveResources(
                request("orders", "changed"), "sales");

        assertThat(response.success()).isTrue();
        assertThat(response.data().savedCount()).isEqualTo(1);
        assertThat(resource).hasContent("changed");
    }

    @Test
    void resourceSaveUsesSharedPublicationLock() throws Exception {
        Path root = Files.createDirectories(tempDirectory.resolve("locked"));
        Path resource = Files.writeString(root.resolve("Order.tm"), "base");
        Fixture fixture = fixture("locked-registry");
        fixture.registry().save(fixture.registry().newRecord(
                "orders", "sales", root.toString(), false, true));
        CountDownLatch started = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try (RuntimeAuthoringPublicationLock.Guard ignored =
                     fixture.publicationLock().acquire()) {
            var future = executor.submit(() -> {
                started.countDown();
                return fixture.controller().saveResources(
                        request("orders", "changed"), "sales");
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(future.isDone()).isFalse();
        } finally {
            executor.shutdown();
        }
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(resource).hasContent("changed");
    }

    private Fixture fixture(String name) {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getBundleRegistry().setPath(
                tempDirectory.resolve(name + ".json").toString());
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        RuntimeBundleRegistryService registry =
                new RuntimeBundleRegistryService(
                        properties, context, new ObjectMapper());
        RuntimeAuthoringPublicationLock publicationLock =
                new RuntimeAuthoringPublicationLock();
        RuntimeResourcesController controller = new RuntimeResourcesController(
                new RuntimeApiResponseFactory(properties), context, registry,
                publicationLock);
        return new Fixture(controller, registry, publicationLock);
    }

    private static ResourceSaveRequest request(String bundle, String content) {
        return new ResourceSaveRequest(
                "sales", bundle,
                List.of(new ResourceSaveFile("Order.tm", content, null)),
                false, false);
    }

    private record Fixture(
            RuntimeResourcesController controller,
            RuntimeBundleRegistryService registry,
            RuntimeAuthoringPublicationLock publicationLock
    ) {
    }
}
