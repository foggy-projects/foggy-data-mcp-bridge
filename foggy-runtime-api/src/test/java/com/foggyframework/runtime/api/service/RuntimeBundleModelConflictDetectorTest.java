package com.foggyframework.runtime.api.service;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.bundle.BundleDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeBundleModelConflictDetectorTest {

    @Test
    void detectsSameKindCanonicalNamesInSameNamespace(@TempDir Path candidate)
            throws IOException {
        Files.writeString(candidate.resolve("OrderModel.tm"), "ignored");
        Files.writeString(candidate.resolve("OrderQuery.qm"), "ignored");
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        Bundle existing = bundle(
                "existing",
                "sales",
                resource("OrderModel.tm"),
                resource("OrderQuery.qm")
        );
        when(context.getBundleList()).thenReturn(List.of(existing));

        List<RuntimeBundleModelConflictDetector.ModelNameConflict> conflicts =
                new RuntimeBundleModelConflictDetector(context).findConflicts(
                        "candidate", " sales ", candidate.toString(), null);

        assertThat(conflicts)
                .extracting(conflict -> conflict.type() + ":" + conflict.modelName()
                        + ":" + conflict.existingBundleNames())
                .containsExactly(
                        "QM:OrderQuery:[existing]",
                        "TM:OrderModel:[existing]"
                );
    }

    @Test
    void ignoresOtherNamespacesAndTheBundleBeingReplaced(@TempDir Path candidate)
            throws IOException {
        Files.writeString(candidate.resolve("OrderModel.tm"), "ignored");
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        Bundle replaced = bundle("same-name", "sales", resource("OrderModel.tm"));
        Bundle otherNamespace =
                bundle("other-namespace", "support", resource("OrderModel.tm"));
        when(context.getBundleList()).thenReturn(List.of(
                replaced,
                otherNamespace
        ));

        List<RuntimeBundleModelConflictDetector.ModelNameConflict> conflicts =
                new RuntimeBundleModelConflictDetector(context).findConflicts(
                        "same-name", "sales", candidate.toString(), "same-name");

        assertThat(conflicts).isEmpty();
    }

    @Test
    void reportsDuplicateCanonicalNamesInsideCandidate(@TempDir Path candidate)
            throws IOException {
        Files.createDirectories(candidate.resolve("a"));
        Files.createDirectories(candidate.resolve("b"));
        Files.writeString(candidate.resolve("a").resolve("OrderQuery.qm"), "ignored");
        Files.writeString(candidate.resolve("b").resolve("OrderQuery.qm"), "ignored");
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.getBundleList()).thenReturn(List.of());

        List<RuntimeBundleModelConflictDetector.ModelNameConflict> conflicts =
                new RuntimeBundleModelConflictDetector(context).findConflicts(
                        "candidate", "sales", candidate.toString(), null);

        assertThat(conflicts).containsExactly(
                new RuntimeBundleModelConflictDetector.ModelNameConflict(
                        "QM", "OrderQuery", List.of(), 2)
        );
    }

    private static Bundle bundle(
            String name,
            String namespace,
            BundleResource... resources
    ) {
        BundleDefinition definition = mock(BundleDefinition.class);
        when(definition.getNamespace()).thenReturn(namespace);
        Bundle bundle = mock(Bundle.class);
        when(bundle.getName()).thenReturn(name);
        when(bundle.getDefinition()).thenReturn(definition);
        when(bundle.findBundleResources("**/*.tm")).thenReturn(filter(resources, ".tm"));
        when(bundle.findBundleResources("**/*.qm")).thenReturn(filter(resources, ".qm"));
        return bundle;
    }

    private static BundleResource resource(String filename) {
        return new BundleResource(
                mock(Bundle.class),
                new FileSystemResource(Path.of(filename))
        );
    }

    private static BundleResource[] filter(BundleResource[] resources, String suffix) {
        return java.util.Arrays.stream(resources)
                .filter(resource -> resource.getResource().getFilename().endsWith(suffix))
                .toArray(BundleResource[]::new);
    }
}
