package com.foggyframework.dataset.mcp.validation;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.dataset.db.model.validation.DetachedModelValidationFactory;
import com.foggyframework.dataset.db.model.validation.DetachedModelValidationSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticLayerValidationService unit tests")
class SemanticLayerValidationServiceTest {

    @Mock
    private DetachedModelValidationFactory validationFactory;

    @Mock
    private DetachedModelValidationSession validationSession;

    @Mock
    private Bundle bundle;

    @InjectMocks
    private SemanticLayerValidationService validationService;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("empty path is rejected before opening a validation session")
    void validate_nullPath_shouldReturnError() {
        ValidationRequest request = ValidationRequest.builder()
                .path(null)
                .namespace("test")
                .build();

        ValidationResult result = validationService.validate(request);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).getMessage().contains("路径参数不能为空"));
        verifyNoInteractions(validationFactory);
    }

    @Test
    @DisplayName("detached TM validation succeeds without live loader access")
    void validate_normalFlow_shouldSucceed() throws Exception {
        BundleResource tmResource = resource("TestModel.tm");
        when(validationFactory.open(
                "external-validation-test", "test", tempDir.toString()))
                .thenReturn(validationSession);
        when(validationSession.sourceBundle()).thenReturn(bundle);
        when(bundle.findBundleResources("**/*.tm"))
                .thenReturn(new BundleResource[]{tmResource});
        when(bundle.findBundleResources("**/*.qm"))
                .thenReturn(new BundleResource[0]);

        ValidationResult result = validationService.validate(ValidationRequest.builder()
                .path(tempDir.toString())
                .namespace("test")
                .build());

        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalFiles());
        assertEquals(1, result.getValidFiles());
        assertEquals(0, result.getInvalidFiles());
        verify(validationSession).validateTableModel(tmResource, "test");
        verify(validationSession).close();
    }

    @Test
    @DisplayName("detached QM failures are returned with optional stack trace")
    void validate_invalidQm_shouldReturnValidationError() throws Exception {
        BundleResource qmResource = resource("Broken.qm");
        when(validationFactory.open(
                "external-validation-test", "test", tempDir.toString()))
                .thenReturn(validationSession);
        when(validationSession.sourceBundle()).thenReturn(bundle);
        when(bundle.findBundleResources("**/*.tm"))
                .thenReturn(new BundleResource[0]);
        when(bundle.findBundleResources("**/*.qm"))
                .thenReturn(new BundleResource[]{qmResource});
        doThrow(new IllegalStateException("invalid query model"))
                .when(validationSession).validateQueryModel(qmResource);

        ValidationResult result = validationService.validate(ValidationRequest.builder()
                .path(tempDir.toString())
                .namespace("test")
                .includeStackTrace(true)
                .build());

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalFiles());
        assertEquals(0, result.getValidFiles());
        assertEquals(1, result.getInvalidFiles());
        assertEquals("Broken.qm", result.getErrors().get(0).getFile());
        assertEquals("IllegalStateException", result.getErrors().get(0).getCode());
        assertTrue(result.getErrors().get(0).getStackTrace()
                .contains("invalid query model"));
        verify(validationSession).close();
    }

    @Test
    @DisplayName("path must identify a directory")
    void validate_pathIsFile_shouldReturnError() throws Exception {
        File tempFile = tempDir.resolve("test.txt").toFile();
        Files.writeString(tempFile.toPath(), "test");

        ValidationResult result = validationService.validate(ValidationRequest.builder()
                .path(tempFile.getAbsolutePath())
                .namespace("test")
                .build());

        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).getMessage().contains("路径必须是目录"));
        verifyNoInteractions(validationFactory);
    }

    @Test
    @DisplayName("production service depends only on the detached validation port")
    void productionDependency_shouldNotExposeLiveLoaders() {
        assertNotNull(validationService);
        for (Field field : SemanticLayerValidationService.class.getDeclaredFields()) {
            assertFalse(field.getType().equals(SystemBundlesContext.class));
            assertFalse(field.getType().equals(QueryModelLoader.class));
            assertFalse(field.getType().equals(TableModelLoaderManager.class));
        }
    }

    private BundleResource resource(String filename) throws Exception {
        BundleResource bundleResource = mock(BundleResource.class);
        Resource resource = mock(Resource.class);
        when(bundleResource.getResource()).thenReturn(resource);
        when(resource.getFile()).thenThrow(new java.io.FileNotFoundException(filename));
        when(resource.getFilename()).thenReturn(filename);
        return bundleResource;
    }
}
