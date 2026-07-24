package com.foggyframework.dataset.model.lifecycle.catalog;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.dataset.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.model.spi.TableModelLoaderManager;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryModelDiscoveryAuthorityTest {

    @Test
    void discoveryMustFailClosedOnDuplicateCanonicalResourceNames() {
        Bundle bundle = bundle("tenant-a",
                resource("OrderQueryModel.qm"), resource("OrderQueryModel.qm"));
        QueryModelLoaderImpl loader = loader(List.of(bundle));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> loader.discoverQueryModelNames("tenant-a"));

        assertEquals("duplicate query model resource in namespace: OrderQueryModel",
                failure.getMessage());
    }

    @Test
    void discoveryFailureMustPropagateWithoutPublishingPartialNames() {
        IllegalStateException marker = new IllegalStateException("controlled discovery failure");
        Bundle bundle = bundle("tenant-a");
        when(bundle.findBundleResources("**/*.qm")).thenThrow(marker);
        QueryModelLoaderImpl loader = loader(List.of(bundle));

        assertSame(marker, assertThrows(IllegalStateException.class,
                () -> loader.discoverQueryModelNames("tenant-a")));
    }

    @Test
    void discoveryMustRejectResourcesWithoutCanonicalFilename() {
        QueryModelLoaderImpl loader = loader(List.of(bundle("", resource(null))));

        assertThrows(IllegalStateException.class,
                () -> loader.discoverQueryModelNames(""));
    }

    private QueryModelLoaderImpl loader(List<Bundle> bundles) {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.getBundleList()).thenReturn(bundles);
        return new QueryModelLoaderImpl(
                mock(TableModelLoaderManager.class),
                context,
                mock(FileFsscriptLoader.class),
                List.of(),
                new CatalogSnapshotStore("discovery-authority-boot"));
    }

    private Bundle bundle(String namespace, BundleResource... resources) {
        BundleDefinition definition = mock(BundleDefinition.class);
        when(definition.getNamespace()).thenReturn(namespace);
        Bundle bundle = mock(Bundle.class);
        when(bundle.getDefinition()).thenReturn(definition);
        when(bundle.findBundleResources("**/*.qm")).thenReturn(resources);
        return bundle;
    }

    private BundleResource resource(String filename) {
        Resource resource = mock(Resource.class);
        when(resource.getFilename()).thenReturn(filename);
        BundleResource bundleResource = mock(BundleResource.class);
        when(bundleResource.getResource()).thenReturn(resource);
        return bundleResource;
    }
}
