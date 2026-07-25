package com.foggyframework.bundle.lifecycle;

import com.foggyframework.bundle.SystemBundlesContextImpl;
import com.foggyframework.bundle.event.BundleAddedEvent;
import com.foggyframework.bundle.event.BundleRemovedEvent;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.fsscript.lifecycle.CommittedSourceRevisionRegistry;
import com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void replacementMustPublishOnlyTheCommittedNewBundle() throws Exception {
        String bundleName = "committed-replace-bundle";
        Path oldRoot = Files.createDirectory(tempDir.resolve("replace-old"));
        Path newRoot = Files.createDirectory(tempDir.resolve("replace-new"));
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        SystemBundlesContextImpl bundlesContext = freshContext(applicationContext);
        AtomicReference<BundleAddedEvent> replacementEvent =
                new AtomicReference<>();

        assertThat(bundlesContext.addExternalBundle(
                bundleName, NAMESPACE, oldRoot.toString(), false)).isTrue();
        doAnswer(invocation -> {
            BundleAddedEvent event = invocation.getArgument(0);
            replacementEvent.set(event);
            assertThat(bundlesContext.getBundleList())
                    .filteredOn(bundle -> bundleName.equals(bundle.getName()))
                    .singleElement()
                    .extracting(bundle -> bundle.getRootPath())
                    .isEqualTo(newRoot.toString());
            assertThat(bundlesContext.listExternalBundles())
                    .filteredOn(definition -> bundleName.equals(
                            definition.getName()))
                    .hasSize(1);
            return null;
        }).when(applicationContext).publishEvent(any(BundleAddedEvent.class));

        assertThat(bundlesContext.replaceExternalBundle(
                bundleName, NAMESPACE, newRoot.toString(), false)).isTrue();

        assertThat(replacementEvent.get()).isNotNull();
        assertThat(replacementEvent.get().getAddedBundle().getRootPath())
                .isEqualTo(newRoot.toString());
        assertCommittedRevision(replacementEvent.get());
    }

    @Test
    void watcherFailureDuringReplaceMustKeepOnlyTheOldBundle()
            throws Exception {
        String bundleName = "watch-failed-replace-bundle";
        Path oldRoot = Files.createDirectory(tempDir.resolve("watch-old"));
        Path newRoot = Files.createDirectory(tempDir.resolve("watch-new"));
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        FsscriptFileChangeHandler changeHandler =
                mock(FsscriptFileChangeHandler.class);
        when(applicationContext.getBean(FsscriptFileChangeHandler.class))
                .thenReturn(changeHandler);
        when(changeHandler.watchExternalBundle(
                oldRoot.toString(), NAMESPACE)).thenReturn(true);
        when(changeHandler.watchExternalBundle(
                newRoot.toString(), NAMESPACE)).thenReturn(false);
        CommittedSourceRevisionRegistry revisionRegistry =
                new CommittedSourceRevisionRegistry();
        SystemBundlesContextImpl bundlesContext =
                freshContext(applicationContext, revisionRegistry);

        assertThat(bundlesContext.addExternalBundle(
                bundleName, NAMESPACE, oldRoot.toString(), true)).isTrue();
        String revisionBefore = revisionRegistry.currentRevision(NAMESPACE);

        assertThat(bundlesContext.replaceExternalBundle(
                bundleName, NAMESPACE, newRoot.toString(), true)).isFalse();
        assertThat(revisionRegistry.currentRevision(NAMESPACE))
                .as("failed replacement must not advance the committed source revision")
                .isEqualTo(revisionBefore);
        assertThat(bundlesContext.getBundleList())
                .filteredOn(bundle -> bundleName.equals(bundle.getName()))
                .singleElement()
                .extracting(bundle -> bundle.getRootPath())
                .isEqualTo(oldRoot.toString());
        assertThat(bundlesContext.listExternalBundles())
                .filteredOn(definition -> bundleName.equals(
                        definition.getName()))
                .singleElement()
                .extracting(definition -> ((ExternalBundleDefinition) definition)
                        .getPath())
                .isEqualTo(oldRoot.toString());
        assertThat(bundlesContext.getBundleDefinitionByName(bundleName))
                .isInstanceOfSatisfying(
                        ExternalBundleDefinition.class,
                        definition -> assertThat(definition.isWatch()).isTrue());
        verify(changeHandler, times(2))
                .watchExternalBundle(oldRoot.toString(), NAMESPACE);
        verify(changeHandler)
                .unwatchExternalBundle(oldRoot.toString(), NAMESPACE);
    }

    @Test
    void listenerFailureAfterReplaceMustNotRollBackCommittedSource()
            throws Exception {
        String bundleName = "listener-failed-replace-bundle";
        Path oldRoot = Files.createDirectory(tempDir.resolve("listener-old"));
        Path newRoot = Files.createDirectory(tempDir.resolve("listener-new"));
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        SystemBundlesContextImpl bundlesContext = freshContext(applicationContext);
        AtomicBoolean failListener = new AtomicBoolean();

        doAnswer(invocation -> {
            if (failListener.get()) {
                throw new IllegalStateException("controlled listener failure");
            }
            return null;
        }).when(applicationContext).publishEvent(any(BundleAddedEvent.class));

        assertThat(bundlesContext.addExternalBundle(
                bundleName, NAMESPACE, oldRoot.toString(), false)).isTrue();
        failListener.set(true);

        assertThat(bundlesContext.replaceExternalBundle(
                bundleName, NAMESPACE, newRoot.toString(), false)).isTrue();
        assertThat(bundlesContext.getBundleList())
                .filteredOn(bundle -> bundleName.equals(bundle.getName()))
                .singleElement()
                .extracting(bundle -> bundle.getRootPath())
                .isEqualTo(newRoot.toString());
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
        return freshContext(
                applicationContext,
                new CommittedSourceRevisionRegistry());
    }

    private static SystemBundlesContextImpl freshContext(
            ApplicationContext applicationContext,
            CommittedSourceRevisionRegistry revisionRegistry
    ) {
        SystemBundlesContextImpl context =
                new SystemBundlesContextImpl(new ArrayList<>(), revisionRegistry);
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
