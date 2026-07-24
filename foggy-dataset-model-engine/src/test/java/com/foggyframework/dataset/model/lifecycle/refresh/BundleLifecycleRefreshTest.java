package com.foggyframework.dataset.model.lifecycle.refresh;

import com.foggyframework.bundle.event.BundleAddedEvent;
import com.foggyframework.bundle.event.BundleRemovedEvent;
import com.foggyframework.dataset.model.engine.pivot.PivotOuterCacheInvalidationBroadcaster;
import com.foggyframework.dataset.model.event.BundleLifecycleListener;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogAdmissionState;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BundleLifecycleRefreshTest {

    private static final String NAMESPACE = "tenant-bundle";

    @Test
    void addAndRemoveMustUseTheSameNamespaceRefreshBoundary() {
        CatalogRefreshCoordinator coordinator = mock(CatalogRefreshCoordinator.class);
        PivotOuterCacheInvalidationBroadcaster pivot =
                mock(PivotOuterCacheInvalidationBroadcaster.class);
        BundleLifecycleListener listener = listener(
                coordinator, new CatalogSnapshotStore(), pivot);
        when(coordinator.refresh(any())).thenReturn(success());

        listener.onBundleAdded(new BundleAddedEvent(
                this, "bundle", NAMESPACE, null, "source:2", true));
        listener.onBundleRemoved(new BundleRemovedEvent(
                this, "bundle", NAMESPACE, null, "source:3", true));

        ArgumentCaptor<CatalogRefreshRequest> requests =
                ArgumentCaptor.forClass(CatalogRefreshRequest.class);
        verify(coordinator, times(2)).refresh(requests.capture());
        for (CatalogRefreshRequest request : requests.getAllValues()) {
            assertEquals(NAMESPACE, request.namespace());
            assertEquals(CatalogRefreshScope.NAMESPACE, request.scope());
            assertEquals(CatalogRefreshTrigger.BUNDLE, request.trigger());
        }
        verify(pivot, times(2)).evict(NAMESPACE, null);
    }

    @Test
    void failedRefreshMustNotEvictAsIfANewCatalogHadPublished() {
        CatalogRefreshCoordinator coordinator = mock(CatalogRefreshCoordinator.class);
        PivotOuterCacheInvalidationBroadcaster pivot =
                mock(PivotOuterCacheInvalidationBroadcaster.class);
        BundleLifecycleListener listener = listener(
                coordinator, new CatalogSnapshotStore(), pivot);
        when(coordinator.refresh(any())).thenThrow(
                new IllegalStateException("controlled refresh failure"));

        listener.onBundleRemoved(new BundleRemovedEvent(
                this, "bundle", NAMESPACE, null, "source:3", true));

        verify(pivot, never()).evict(any(), any());
    }

    @Test
    void unknownBundleScopeMustBlockKnownCatalogAdmissions() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        store.capture(NAMESPACE);
        CatalogRefreshCoordinator coordinator = mock(CatalogRefreshCoordinator.class);
        BundleLifecycleListener listener = listener(
                coordinator,
                store,
                mock(PivotOuterCacheInvalidationBroadcaster.class));

        listener.onBundleRemoved(new BundleRemovedEvent(
                this, "bundle", null, null, "source:unknown", false));

        assertEquals(CatalogAdmissionState.STALE_ADMISSION_BLOCKED,
                store.admissionState(NAMESPACE));
        verify(coordinator, never()).refresh(any());
    }

    private static BundleLifecycleListener listener(
            CatalogRefreshCoordinator coordinator,
            CatalogSnapshotStore store,
            PivotOuterCacheInvalidationBroadcaster pivot
    ) {
        BundleLifecycleListener listener = new BundleLifecycleListener();
        ReflectionTestUtils.setField(
                listener, "catalogRefreshCoordinator", coordinator);
        ReflectionTestUtils.setField(
                listener, "catalogSnapshotStore", store);
        ReflectionTestUtils.setField(
                listener, "pivotOuterCacheInvalidationBroadcaster", pivot);
        return listener;
    }

    private static CatalogRefreshResult success() {
        SourceRevision revision = new SourceRevision("source:2");
        CatalogIdentity identity = new CatalogIdentity(
                NAMESPACE, new CatalogGeneration("catalog:2"), revision);
        return new CatalogRefreshResult(
                NAMESPACE,
                CatalogRefreshScope.NAMESPACE,
                null,
                identity,
                revision,
                Set.of(),
                Set.of(),
                List.of(),
                1L,
                CatalogAdmissionState.ACTIVE,
                List.of());
    }
}
