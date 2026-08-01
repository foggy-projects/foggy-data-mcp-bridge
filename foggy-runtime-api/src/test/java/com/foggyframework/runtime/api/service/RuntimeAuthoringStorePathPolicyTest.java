package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeAuthoringStorePathPolicyTest {

    @TempDir
    Path tempDirectory;

    @Test
    void rejectsEqualAncestorDescendantAndSymlinkEquivalentSources()
            throws Exception {
        Path storeRoot = tempDirectory.resolve("authoring-store");
        RuntimeAuthoringStorePathPolicy policy = policy(storeRoot,
                mock(SystemBundlesContext.class));

        assertConflict(() -> policy.assertBundleSourceDisjoint(
                storeRoot.toString()));
        assertConflict(() -> policy.assertBundleSourceDisjoint(
                tempDirectory.toString()));
        assertConflict(() -> policy.assertBundleSourceDisjoint(
                storeRoot.resolve("bundle").toString()));
        assertThatCode(() -> policy.assertBundleSourceDisjoint(
                tempDirectory.resolve("disjoint").toString()))
                .doesNotThrowAnyException();

        Path real = Files.createDirectory(tempDirectory.resolve("real"));
        Path alias = tempDirectory.resolve("alias");
        Files.createSymbolicLink(alias, real);
        RuntimeAuthoringStorePathPolicy aliased = policy(
                alias.resolve("workspace"), mock(SystemBundlesContext.class));
        assertConflict(() -> aliased.assertBundleSourceDisjoint(
                real.toString()));
    }

    @Test
    void checksConfiguredAndRuntimeRegistrySourcesButIgnoresSpringResources() {
        Path storeRoot = tempDirectory.resolve("store");
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.listExternalBundles()).thenReturn(List.of(
                new ExternalBundleDefinition(
                        "configured", "sales", storeRoot.resolve("models").toString(),
                        false),
                new ExternalBundleDefinition(
                        "classpath", "sales", "classpath:/models", false)));
        RuntimeAuthoringStorePathPolicy policy = policy(storeRoot, context);

        assertConflict(() -> policy.assertStoreDisjoint(List.of()));

        when(context.listExternalBundles()).thenReturn(List.of());
        RuntimeBundleRegistryService.RuntimeBundleRecord record =
                new RuntimeBundleRegistryService.RuntimeBundleRecord(
                        "inactive", "sales", tempDirectory.toString(),
                        false, false, "now", "now");
        assertConflict(() -> policy.assertStoreDisjoint(List.of(record)));
        assertThatCode(() -> policy.assertStoreDisjoint(List.of(
                new RuntimeBundleRegistryService.RuntimeBundleRecord(
                        "resource", "sales", "jar:file:/models.jar!/foggy",
                        false, false, "now", "now"))))
                .doesNotThrowAnyException();
    }

    @Test
    void workspaceStoreRejectsConfiguredOverlapBeforeTouchingSource()
            throws Exception {
        Path source = Files.createDirectory(tempDirectory.resolve("live-source"));
        Path sentinel = Files.writeString(source.resolve("Order.tm"), "live");
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.listExternalBundles()).thenReturn(List.of(
                new ExternalBundleDefinition(
                        "live", "sales", source.toString(), false)));
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getAuthoringWorkspaces().setPath(source.toString());
        properties.getBundleRegistry().setPath(
                tempDirectory.resolve("runtime-bundles.json").toString());
        RuntimeAuthoringStorePathPolicy policy =
                new RuntimeAuthoringStorePathPolicy(properties, context);
        RuntimeBundleRegistryService registry =
                new RuntimeBundleRegistryService(
                        properties, context, new ObjectMapper());
        RuntimeAuthoringWorkspaceStore store =
                new RuntimeAuthoringWorkspaceStore(
                        properties, new ObjectMapper(), policy, registry);

        assertThatThrownBy(() -> store.list(null, null, true))
                .isInstanceOf(RuntimeAuthoringWorkspaceException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((RuntimeAuthoringWorkspaceException) error).code())
                        .isEqualTo("WORKSPACE_STORE_FAILURE"));
        org.assertj.core.api.Assertions.assertThat(sentinel).hasContent("live");
        org.assertj.core.api.Assertions.assertThat(
                source.resolve("workspaces.json")).doesNotExist();
    }

    private RuntimeAuthoringStorePathPolicy policy(
            Path storeRoot,
            SystemBundlesContext context
    ) {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getAuthoringWorkspaces().setPath(storeRoot.toString());
        return new RuntimeAuthoringStorePathPolicy(properties, context);
    }

    private static void assertConflict(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(
                        RuntimeAuthoringStorePathPolicy.PathConflictException.class)
                .hasMessage("Authoring workspace store and Bundle source paths must be disjoint.")
                .hasNoCause();
    }
}
