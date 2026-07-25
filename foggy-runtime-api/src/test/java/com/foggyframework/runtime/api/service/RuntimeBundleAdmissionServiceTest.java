package com.foggyframework.runtime.api.service;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.runtime.api.dto.ModelValidateRequest;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeBundleAdmissionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void sameNamespaceModelNameConflictMustFailClosed() throws Exception {
        Path liveRoot = Files.createDirectories(tempDir.resolve("live"));
        Path candidateRoot = Files.createDirectories(
                tempDir.resolve("candidate"));
        Files.writeString(liveRoot.resolve("SharedQuery.qm"), "live");
        Files.writeString(candidateRoot.resolve("SharedQuery.qm"), "candidate");
        SystemBundlesContext context = contextWith(
                bundle(contextStub(), "stable-bundle", "business", liveRoot));
        RuntimeBundleAdmissionService service =
                new RuntimeBundleAdmissionService(
                        context, mock(RuntimeModelOperations.class));

        assertThatThrownBy(() -> service.validate(
                "plugin-x", "business", candidateRoot.toString(), null))
                .isInstanceOf(RuntimeBundleAdmissionException.class)
                .extracting("code")
                .isEqualTo("BUNDLE_MODEL_CONFLICT");
    }

    @Test
    void sameModelNameInDifferentNamespaceIsAllowed() throws Exception {
        Path liveRoot = Files.createDirectories(tempDir.resolve("live"));
        Path candidateRoot = Files.createDirectories(
                tempDir.resolve("candidate"));
        Files.writeString(liveRoot.resolve("SharedQuery.qm"), "live");
        Files.writeString(candidateRoot.resolve("SharedQuery.qm"), "candidate");
        SystemBundlesContext context = contextWith(
                bundle(contextStub(), "stable-bundle", "business", liveRoot));
        RuntimeBundleAdmissionService service =
                new RuntimeBundleAdmissionService(
                        context, mock(RuntimeModelOperations.class));

        assertThatCode(() -> service.validate(
                "plugin-x", "plugin", candidateRoot.toString(), null))
                .doesNotThrowAnyException();
    }

    @Test
    void replaceExcludesItsOwnPreviousModelsFromConflictCheck()
            throws Exception {
        Path liveRoot = Files.createDirectories(tempDir.resolve("live"));
        Path candidateRoot = Files.createDirectories(
                tempDir.resolve("candidate"));
        Files.writeString(liveRoot.resolve("OwnedQuery.qm"), "live");
        Files.writeString(candidateRoot.resolve("OwnedQuery.qm"), "candidate");
        SystemBundlesContext context = contextWith(
                bundle(contextStub(), "plugin-x", "business", liveRoot));
        RuntimeBundleAdmissionService service =
                new RuntimeBundleAdmissionService(
                        context, mock(RuntimeModelOperations.class));

        assertThatCode(() -> service.validate(
                "plugin-x", "business", candidateRoot.toString(), "plugin-x"))
                .doesNotThrowAnyException();
    }

    @Test
    void detachedSyntaxValidationFailureHasStableCode() throws Exception {
        Path candidateRoot = Files.createDirectories(
                tempDir.resolve("candidate"));
        RuntimeModelOperations operations = mock(RuntimeModelOperations.class);
        doThrow(new RuntimeModelOperationException(
                "MODEL_VALIDATION_FAILED",
                "models.validate",
                "bad syntax",
                null,
                null,
                false,
                RuntimeDiagnostics.empty()
        )).when(operations).validateModels(
                any(ModelValidateRequest.class), eq("business"));
        RuntimeBundleAdmissionService service =
                new RuntimeBundleAdmissionService(
                        contextWith(), operations);

        assertThatThrownBy(() -> service.validate(
                "plugin-x", "business", candidateRoot.toString(), null))
                .isInstanceOf(RuntimeBundleAdmissionException.class)
                .extracting("code")
                .isEqualTo("BUNDLE_VALIDATION_FAILED");
    }

    private static SystemBundlesContext contextWith(Bundle... bundles) {
        SystemBundlesContext context = mock(SystemBundlesContext.class);
        when(context.getBundleList()).thenReturn(List.of(bundles));
        return context;
    }

    private static SystemBundlesContext contextStub() {
        return mock(SystemBundlesContext.class);
    }

    private static ExternalFileBundle bundle(
            SystemBundlesContext context,
            String name,
            String namespace,
            Path root
    ) {
        ExternalBundleDefinition definition = new ExternalBundleDefinition(
                name, namespace, root.toString(), false);
        ExternalFileBundle bundle = new ExternalFileBundle(context);
        bundle.setName(name);
        bundle.setBundleDefinition(definition);
        bundle.setBasePath(root.toString());
        bundle.setRootPath(root.toString());
        return bundle;
    }
}
