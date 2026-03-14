package com.foggyframework.dataset.mcp.spi.impl;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.fsscript.loadder.FsscriptRemoveEvent;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SemanticServiceResolverImpl 单元测试
 *
 * <p>测试缓存机制、失效逻辑和 namespace 传播
 */
class SemanticServiceResolverImplTest {

    @Mock
    private SemanticServiceV3 semanticServiceV3;

    @Mock
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Mock
    private SystemBundlesContext systemBundlesContext;

    @Mock
    private QueryModelLoader queryModelLoader;

    @Mock
    private Bundle bundle;

    @Mock
    private BundleResource bundleResource;

    @Mock
    private Resource resource;

    @Mock
    private QueryModel queryModel;

    private SemanticServiceResolverImpl resolver;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        resolver = new SemanticServiceResolverImpl(
                semanticServiceV3,
                semanticQueryServiceV3,
                systemBundlesContext,
                queryModelLoader
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("首次调用 getAllModelNames 应该扫描并缓存")
    void testGetAllModelNames_FirstCall_ShouldScan() {
        setupMockBundles("TestModel");

        List<String> result = resolver.getAllModelNames();

        assertEquals(1, result.size());
        assertEquals("TestModel", result.get(0));
        verify(systemBundlesContext, times(1)).getBundleList();
        verify(queryModelLoader, times(1)).loadJdbcQueryModel(any(BundleResource.class));
    }

    @Test
    @DisplayName("第二次调用 getAllModelNames 应该返回缓存")
    void testGetAllModelNames_SecondCall_ShouldReturnCache() {
        setupMockBundles("TestModel");

        resolver.getAllModelNames();
        reset(systemBundlesContext);
        reset(queryModelLoader);

        List<String> result = resolver.getAllModelNames();

        assertEquals(1, result.size());
        assertEquals("TestModel", result.get(0));
        verify(systemBundlesContext, never()).getBundleList();
        verify(queryModelLoader, never()).loadJdbcQueryModel(any());
    }

    @Test
    @DisplayName("invalidateModelCache 应该清除缓存")
    void testInvalidateModelCache_ShouldClearCache() {
        setupMockBundles("Model1");
        resolver.getAllModelNames();

        resolver.invalidateModelCache();
        setupMockBundles("Model2");

        List<String> result = resolver.getAllModelNames();

        assertEquals(1, result.size());
        assertEquals("Model2", result.get(0));
        verify(systemBundlesContext, times(2)).getBundleList();
    }

    @Test
    @DisplayName("FsscriptRemoveEvent 包含 QM 文件变化时应该清除缓存")
    void testOnApplicationEvent_QmFileChange_ShouldInvalidateCache() {
        setupMockBundles("InitialModel");
        resolver.getAllModelNames();

        Fsscript mockFsscript = mock(Fsscript.class);
        when(mockFsscript.getPath()).thenReturn("/some/path/model.qm");
        FsscriptRemoveEvent event = new FsscriptRemoveEvent(List.of(mockFsscript));

        reset(systemBundlesContext);
        setupMockBundles("NewModel");

        resolver.onApplicationEvent(event);

        List<String> result = resolver.getAllModelNames();
        assertEquals("NewModel", result.get(0));
        verify(systemBundlesContext, times(1)).getBundleList();
    }

    @Test
    @DisplayName("FsscriptRemoveEvent 不包含 QM 文件时不应该清除缓存")
    void testOnApplicationEvent_NonQmFileChange_ShouldNotInvalidateCache() {
        setupMockBundles("CachedModel");
        resolver.getAllModelNames();

        Fsscript mockFsscript = mock(Fsscript.class);
        when(mockFsscript.getPath()).thenReturn("/some/path/model.tm");
        FsscriptRemoveEvent event = new FsscriptRemoveEvent(List.of(mockFsscript));

        reset(systemBundlesContext);
        resolver.onApplicationEvent(event);

        List<String> result = resolver.getAllModelNames();
        assertEquals("CachedModel", result.get(0));
        verify(systemBundlesContext, never()).getBundleList();
    }

    @Test
    @DisplayName("FsscriptRemoveEvent 为空时不应该清除缓存")
    void testOnApplicationEvent_EmptyEvent_ShouldNotInvalidateCache() {
        setupMockBundles("CachedModel");
        resolver.getAllModelNames();

        FsscriptRemoveEvent event = new FsscriptRemoveEvent(new ArrayList<>());
        reset(systemBundlesContext);
        resolver.onApplicationEvent(event);

        verify(systemBundlesContext, never()).getBundleList();
    }

    @Test
    @DisplayName("并发调用 getAllModelNames 应该线程安全")
    void testGetAllModelNames_ConcurrentCalls_ShouldBeThreadSafe() throws Exception {
        when(systemBundlesContext.getBundleList()).thenAnswer(inv -> {
            Thread.sleep(50);
            return List.of(bundle);
        });
        when(bundle.findBundleResources("**/*.qm")).thenReturn(new BundleResource[]{bundleResource});
        when(bundleResource.getResource()).thenReturn(resource);
        when(resource.getDescription()).thenReturn("test.qm");
        when(queryModelLoader.loadJdbcQueryModel(any(BundleResource.class))).thenReturn(queryModel);
        when(queryModel.getName()).thenReturn("ConcurrentModel");

        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        List<String>[] results = new List[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> results[index] = resolver.getAllModelNames());
        }
        for (Thread thread : threads) thread.start();
        for (Thread thread : threads) thread.join();

        for (int i = 0; i < threadCount; i++) {
            assertNotNull(results[i]);
            assertEquals(1, results[i].size());
            assertEquals("ConcurrentModel", results[i].get(0));
        }
        verify(systemBundlesContext, times(1)).getBundleList();
    }

    @Test
    @DisplayName("扫描时加载失败的 QM 文件应该被忽略")
    void testGetAllModelNames_FailedQmLoad_ShouldBeIgnored() {
        BundleResource goodResource = mock(BundleResource.class);
        BundleResource badResource = mock(BundleResource.class);
        Resource goodFile = mock(Resource.class);
        Resource badFile = mock(Resource.class);

        when(systemBundlesContext.getBundleList()).thenReturn(List.of(bundle));
        when(bundle.findBundleResources("**/*.qm")).thenReturn(new BundleResource[]{goodResource, badResource});

        when(goodResource.getResource()).thenReturn(goodFile);
        when(goodFile.getDescription()).thenReturn("good.qm");
        when(badResource.getResource()).thenReturn(badFile);
        when(badFile.getDescription()).thenReturn("bad.qm");

        QueryModel goodModel = mock(QueryModel.class);
        when(goodModel.getName()).thenReturn("GoodModel");

        when(queryModelLoader.loadJdbcQueryModel(goodResource)).thenReturn(goodModel);
        when(queryModelLoader.loadJdbcQueryModel(badResource)).thenThrow(new RuntimeException("Parse error"));

        List<String> result = resolver.getAllModelNames();
        assertEquals(1, result.size());
        assertEquals("GoodModel", result.get(0));
    }

    // ==================== getMetadata — SemanticRequestContext 传播测试 ====================

    @Test
    @DisplayName("getMetadata 应将 SemanticRequestContext 透传给 SemanticServiceV3")
    void testGetMetadata_ShouldPassContextToV3() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(List.of("OdooSaleOrderQueryModel"));

        SemanticMetadataResponse expectedResponse = new SemanticMetadataResponse();
        expectedResponse.setContent("test markdown");

        when(semanticServiceV3.getMetadata(any(), eq("markdown"), any(SemanticRequestContext.class)))
                .thenReturn(expectedResponse);

        SemanticRequestContext ctx = SemanticRequestContext.ofNamespace("odoo");
        SemanticMetadataResponse result = resolver.getMetadata(request, "markdown", ctx);

        // 验证 context 被透传
        ArgumentCaptor<SemanticRequestContext> ctxCaptor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceV3).getMetadata(eq(request), eq("markdown"), ctxCaptor.capture());
        assertEquals("odoo", ctxCaptor.getValue().getNamespace());
        assertEquals("test markdown", result.getContent());
    }

    @Test
    @DisplayName("getMetadata 使用 empty context 时 namespace 为 null")
    void testGetMetadata_EmptyContext_ShouldHaveNullNamespace() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();
        request.setQmModels(List.of("TestModel"));

        SemanticMetadataResponse expectedResponse = new SemanticMetadataResponse();
        when(semanticServiceV3.getMetadata(any(), eq("markdown"), any(SemanticRequestContext.class)))
                .thenReturn(expectedResponse);

        resolver.getMetadata(request, "markdown", SemanticRequestContext.empty());

        ArgumentCaptor<SemanticRequestContext> ctxCaptor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceV3).getMetadata(eq(request), eq("markdown"), ctxCaptor.capture());
        assertNull(ctxCaptor.getValue().getNamespace());
    }

    @Test
    @DisplayName("getMetadata 使用带认证的 context")
    void testGetMetadata_WithAuthContext_ShouldPassThrough() {
        SemanticMetadataRequest request = new SemanticMetadataRequest();

        SemanticMetadataResponse expectedResponse = new SemanticMetadataResponse();
        when(semanticServiceV3.getMetadata(any(), eq("json"), any(SemanticRequestContext.class)))
                .thenReturn(expectedResponse);

        SemanticRequestContext ctx = SemanticRequestContext.of("odoo", "Bearer token");
        resolver.getMetadata(request, "json", ctx);

        ArgumentCaptor<SemanticRequestContext> ctxCaptor = ArgumentCaptor.forClass(SemanticRequestContext.class);
        verify(semanticServiceV3).getMetadata(eq(request), eq("json"), ctxCaptor.capture());
        assertEquals("odoo", ctxCaptor.getValue().getNamespace());
        assertEquals("Bearer token", ctxCaptor.getValue().getAuthorization());
    }

    // ==================== 辅助方法 ====================

    private void setupMockBundles(String modelName) {
        when(systemBundlesContext.getBundleList()).thenReturn(List.of(bundle));
        when(bundle.findBundleResources("**/*.qm")).thenReturn(new BundleResource[]{bundleResource});
        when(bundle.findResources("**/*.qm")).thenReturn(new Resource[0]);
        when(bundle.getName()).thenReturn("test-bundle");
        when(bundleResource.getResource()).thenReturn(resource);
        when(resource.getDescription()).thenReturn("test.qm");
        when(resource.isFile()).thenReturn(false);
        when(queryModelLoader.loadJdbcQueryModel(any(BundleResource.class))).thenReturn(queryModel);
        when(queryModel.getName()).thenReturn(modelName);
    }
}
