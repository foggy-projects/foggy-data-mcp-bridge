package com.foggyframework.dataset.mcp.spi.impl;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.spi.NamespaceContext;
import com.foggyframework.dataset.db.model.spi.NamespaceScope;
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
 * <p>测试共享 catalog authority、兼容扫描和 namespace 传播
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
    private SemanticModelCatalogService semanticModelCatalogService;

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
                queryModelLoader,
                semanticModelCatalogService
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("无参 discovery 应把当前 namespace 委托给共享 catalog authority")
    void noArgDiscoveryShouldDelegateCurrentNamespace() {
        when(semanticModelCatalogService.getAllModelNames("tenant-a"))
                .thenReturn(List.of("TenantAModel"));

        try (NamespaceScope ignored = NamespaceContext.open("tenant-a")) {
            assertEquals(List.of("TenantAModel"), resolver.getAllModelNames());
        }

        verify(semanticModelCatalogService).getAllModelNames("tenant-a");
        verifyNoInteractions(systemBundlesContext, queryModelLoader);
    }

    @Test
    @DisplayName("显式 namespace discovery 不依赖 ambient scope")
    void explicitNamespaceShouldDelegateExactly() {
        when(semanticModelCatalogService.getAllModelNames("tenant-b"))
                .thenReturn(List.of("TenantBModel"));

        try (NamespaceScope ignored = NamespaceContext.open("tenant-a")) {
            assertEquals(List.of("TenantBModel"), resolver.getAllModelNames(" tenant-b "));
        }

        verify(semanticModelCatalogService).getAllModelNames("tenant-b");
    }

    @Test
    @DisplayName("重复 discovery 不经过 MCP names cache")
    void repeatedDiscoveryShouldObservePublishedCatalogWithoutInvalidation() {
        when(semanticModelCatalogService.getAllModelNames(""))
                .thenReturn(List.of("OldModel"), List.of("NewModel"));

        assertEquals(List.of("OldModel"), resolver.getAllModelNames());
        assertEquals(List.of("NewModel"), resolver.getAllModelNames());

        verify(semanticModelCatalogService, times(2)).getAllModelNames("");
    }

    @Test
    @DisplayName("legacy invalidateModelCache 是共享 catalog authority 下的兼容 no-op")
    void legacyInvalidationShouldNotOwnCatalogState() {
        when(semanticModelCatalogService.getAllModelNames(""))
                .thenReturn(List.of("Before"), List.of("After"));

        assertEquals(List.of("Before"), resolver.getAllModelNames());
        resolver.invalidateModelCache();
        assertEquals(List.of("After"), resolver.getAllModelNames());

        verify(semanticModelCatalogService, times(2)).getAllModelNames("");
    }

    @Test
    @DisplayName("QM source event 不触发 MCP 侧第二套 catalog 读取或失效")
    void sourceEventShouldNotInvokeIndependentCatalogAuthority() {
        Fsscript qm = mock(Fsscript.class);
        when(qm.getPath()).thenReturn("/some/path/model.qm");

        resolver.onApplicationEvent(new FsscriptRemoveEvent(List.of(qm)));

        verifyNoInteractions(semanticModelCatalogService, systemBundlesContext, queryModelLoader);
    }

    @Test
    @DisplayName("非 QM 与空 source event 不触发 catalog authority")
    void unrelatedSourceEventsShouldRemainNoOps() {
        Fsscript tm = mock(Fsscript.class);
        when(tm.getPath()).thenReturn("/some/path/model.tm");

        resolver.onApplicationEvent(new FsscriptRemoveEvent(List.of(tm)));
        resolver.onApplicationEvent(new FsscriptRemoveEvent(new ArrayList<>()));

        verifyNoInteractions(semanticModelCatalogService, systemBundlesContext, queryModelLoader);
    }

    @Test
    @DisplayName("并发 discovery 共享同一 model authority 且不使用 sleep")
    void concurrentCallsShouldDelegateWithoutMcpCache() throws Exception {
        when(semanticModelCatalogService.getAllModelNames(""))
                .thenReturn(List.of("ConcurrentModel"));

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
        verify(semanticModelCatalogService, times(threadCount)).getAllModelNames("");
        verifyNoInteractions(systemBundlesContext, queryModelLoader);
    }

    @Test
    @DisplayName("扫描时加载失败的 QM 文件应该被忽略")
    void testGetAllModelNames_FailedQmLoad_ShouldBeIgnored() {
        SemanticServiceResolverImpl legacyResolver = new SemanticServiceResolverImpl(
                semanticServiceV3, semanticQueryServiceV3,
                systemBundlesContext, queryModelLoader);
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

        List<String> result = legacyResolver.getAllModelNames();
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
