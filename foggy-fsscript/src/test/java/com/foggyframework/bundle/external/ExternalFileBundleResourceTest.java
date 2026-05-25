package com.foggyframework.bundle.external;

import com.foggyframework.bundle.SystemBundlesContextImpl;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalFileBundleResourceTest {

    @Test
    void findResourcesSupportsClasspathLocation() {
        ExternalBundleDefinition definition = new ExternalBundleDefinition(
                "classpath-bundle",
                "test",
                "classpath:/external_bundle_test",
                false
        );
        ExternalFileBundle bundle = new ExternalFileBundle(new SystemBundlesContextImpl(new ArrayList<>()));
        bundle.setName(definition.getName());
        bundle.setBundleDefinition(definition);
        bundle.setBasePath(definition.getPath());
        bundle.setRootPath(definition.getPath());

        Resource[] resources = bundle.findResources("**/*.fsscript");

        assertEquals(4, resources.length);
    }
}
