package com.foggyframework.bundle.lifecycle;

import com.foggyframework.bundle.SystemBundlesContextImpl;
import com.foggyframework.bundle.event.BundleAddedEvent;
import com.foggyframework.bundle.event.BundleRemovedEvent;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** Production-green target for committed bundle source events. */
class BundleSourceCommitOrderingTest {

    private static final String NAMESPACE = "sales";

    @TempDir
    Path tempDir;

    @Test
    void bundleAddedEventMustObserveCommittedRegistryAndCarryCommittedRevision()
            throws Exception {
        String bundleName = "committed-add-bundle";
        Path bundleRoot = Files.createDirectory(tempDir.resolve(bundleName));
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        SystemBundlesContextImpl bundlesContext = freshContext(applicationContext);
        AtomicReference<BundleAddedEvent> observed = new AtomicReference<>();
        AtomicBoolean committedAtEvent = new AtomicBoolean();

        doAnswer(invocation -> {
            Object event = invocation.getArgument(0);
            if (event instanceof BundleAddedEvent added) {
                observed.set(added);
                committedAtEvent.set(
                        bundlesContext.containBundle(added.getBundleName())
                                && bundlesContext.getBundleDefinitionByName(added.getBundleName()) != null
                                && bundlesContext.listExternalBundles().stream()
                                .anyMatch(definition -> added.getBundleName().equals(definition.getName()))
                );
            }
            return null;
        }).when(applicationContext).publishEvent(any(BundleAddedEvent.class));

        assertThat(bundlesContext.addExternalBundle(
                bundleName, NAMESPACE, bundleRoot.toString(), false)).isTrue();

        assertThat(observed.get()).isNotNull();
        assertThat(committedAtEvent)
                .as("add listeners must observe the committed source registry")
                .isTrue();
        assertCommittedRevision(observed.get());
    }

    @Test
    void bundleRemovedEventMustObserveCommittedRegistryAndCarryCommittedRevision()
            throws Exception {
        String bundleName = "committed-remove-bundle";
        Path bundleRoot = Files.createDirectory(tempDir.resolve(bundleName));
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        SystemBundlesContextImpl bundlesContext = freshContext(applicationContext);
        AtomicReference<BundleRemovedEvent> observed = new AtomicReference<>();
        AtomicBoolean committedAtEvent = new AtomicBoolean();

        assertThat(bundlesContext.addExternalBundle(
                bundleName, NAMESPACE, bundleRoot.toString(), false)).isTrue();

        doAnswer(invocation -> {
            Object event = invocation.getArgument(0);
            if (event instanceof BundleRemovedEvent removed) {
                observed.set(removed);
                committedAtEvent.set(
                        !bundlesContext.containBundle(removed.getBundleName())
                                && bundlesContext.getBundleDefinitionByName(removed.getBundleName()) == null
                                && bundlesContext.listExternalBundles().stream()
                                .noneMatch(definition -> removed.getBundleName().equals(definition.getName()))
                );
            }
            return null;
        }).when(applicationContext).publishEvent(any(BundleRemovedEvent.class));

        assertThat(bundlesContext.removeBundle(bundleName)).isTrue();

        assertThat(observed.get()).isNotNull();
        assertThat(committedAtEvent)
                .as("remove listeners must observe the committed source registry")
                .isTrue();
        assertCommittedRevision(observed.get());
    }

    @Test
    void bundleRemoveMustClearItsSourceCachesBeforePublishingTheEvent() {
        String bundleName = "cache-order-bundle";
        Path bundleRoot = tempDir.resolve(bundleName);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        SystemBundlesContextImpl bundlesContext = freshContext(applicationContext);
        TrackingExternalFileBundle bundle = new TrackingExternalFileBundle(bundlesContext);
        ExternalBundleDefinition definition = new ExternalBundleDefinition(
                bundleName, NAMESPACE, bundleRoot.toString(), false);
        bundle.setName(bundleName);
        bundle.setBasePath(bundleRoot.toString());
        bundle.setRootPath(bundleRoot.toString());
        bundle.setBundleDefinition(definition);
        bundle.getName2Path().put("stale.tm", bundleRoot.resolve("stale.tm").toString());
        bundlesContext.setBundleList(new ArrayList<>(List.of(bundle)));
        bundlesContext.setName2BundleDefinition(new HashMap<>(Map.of(bundleName, definition)));

        AtomicBoolean cacheClearedAtEvent = new AtomicBoolean();
        doAnswer(invocation -> {
            Object event = invocation.getArgument(0);
            if (event instanceof BundleRemovedEvent) {
                cacheClearedAtEvent.set(bundle.cacheCleared.get()
                        && bundle.getName2Path().isEmpty());
            }
            return null;
        }).when(applicationContext).publishEvent(any(BundleRemovedEvent.class));

        assertThat(bundlesContext.removeBundle(bundleName)).isTrue();
        assertThat(cacheClearedAtEvent)
                .as("bundle cache/script/watch indexes must be committed before event publication")
                .isTrue();
    }

    private static void assertCommittedRevision(Object event) {
        Method accessor;
        try {
            accessor = event.getClass().getMethod("getCommittedSourceRevision");
        } catch (NoSuchMethodException e) {
            throw new AssertionError(
                    "Committed bundle events must expose getCommittedSourceRevision()",
                    e
            );
        }

        Object revision;
        try {
            revision = accessor.invoke(event);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError("Unable to read committed bundle SourceRevision", e);
        }
        assertThat(revision).isNotNull();
        assertThat(revision.toString()).isNotBlank();
    }

    private static SystemBundlesContextImpl freshContext(
            ApplicationContext applicationContext
    ) {
        SystemBundlesContextImpl context = new SystemBundlesContextImpl(new ArrayList<>());
        context.setAppCtx(applicationContext);
        context.setBundleList(new ArrayList<>());
        context.setName2BundleDefinition(new HashMap<>());
        return context;
    }

    private static final class TrackingExternalFileBundle extends ExternalFileBundle {

        private final AtomicBoolean cacheCleared = new AtomicBoolean();

        private TrackingExternalFileBundle(SystemBundlesContextImpl context) {
            super(context);
        }

        @Override
        public void clearCache() {
            super.clearCache();
            cacheCleared.set(true);
        }
    }
}
