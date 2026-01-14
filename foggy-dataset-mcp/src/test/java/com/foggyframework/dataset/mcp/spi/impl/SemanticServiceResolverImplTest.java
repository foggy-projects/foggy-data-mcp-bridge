package com.foggyframework.dataset.mcp.spi.impl;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.fsscript.loadder.FsscriptRemoveEvent;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SemanticServiceResolverImpl 单元测试
 *
 * <p>测试缓存机制和失效逻辑
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
        // 准备 mock 数据
        setupMockBundles("TestModel");

        // 首次调用
        List<String> result = resolver.getAllModelNames();

        // 验证
        assertEquals(1, result.size());
        assertEquals("TestModel", result.get(0));

        // 验证扫描被调用
        verify(systemBundlesContext, times(1)).getBundleList();
        verify(queryModelLoader, times(1)).loadJdbcQueryModel(any(BundleResource.class));
    }

    @Test
    @DisplayName("第二次调用 getAllModelNames 应该返回缓存")
    void testGetAllModelNames_SecondCall_ShouldReturnCache() {
        // 准备 mock 数据
        setupMockBundles("TestModel");

        // 首次调用
        resolver.getAllModelNames();

        // 重置 mock 调用计数
        reset(systemBundlesContext);
        reset(queryModelLoader);

        // 第二次调用
        List<String> result = resolver.getAllModelNames();

        // 验证返回缓存，不再扫描
        assertEquals(1, result.size());
        assertEquals("TestModel", result.get(0));

        // 验证没有再次扫描
        verify(systemBundlesContext, never()).getBundleList();
        verify(queryModelLoader, never()).loadJdbcQueryModel(any());
    }

    @Test
    @DisplayName("invalidateModelCache 应该清除缓存")
    void testInvalidateModelCache_ShouldClearCache() {
        // 准备 mock 数据
        setupMockBundles("Model1");

        // 首次调用，建立缓存
        resolver.getAllModelNames();

        // 清除缓存
        resolver.invalidateModelCache();

        // 修改 mock 返回不同的模型
        setupMockBundles("Model2");

        // 再次调用，应该重新扫描
        List<String> result = resolver.getAllModelNames();

        // 验证返回新的模型
        assertEquals(1, result.size());
        assertEquals("Model2", result.get(0));

        // 验证进行了两次扫描
        verify(systemBundlesContext, times(2)).getBundleList();
    }

    @Test
    @DisplayName("FsscriptRemoveEvent 包含 QM 文件变化时应该清除缓存")
    void testOnApplicationEvent_QmFileChange_ShouldInvalidateCache() {
        // 准备 mock 数据
        setupMockBundles("InitialModel");

        // 建立缓存
        resolver.getAllModelNames();

        // 创建包含 QM 文件变化的事件
        Fsscript mockFsscript = mock(Fsscript.class);
        when(mockFsscript.getPath()).thenReturn("/some/path/model.qm");

        FsscriptRemoveEvent event = new FsscriptRemoveEvent(List.of(mockFsscript));

        // 重置 mock
        reset(systemBundlesContext);
        setupMockBundles("NewModel");

        // 触发事件
        resolver.onApplicationEvent(event);

        // 再次调用应该重新扫描
        List<String> result = resolver.getAllModelNames();

        // 验证缓存被清除并重新扫描
        assertEquals("NewModel", result.get(0));
        verify(systemBundlesContext, times(1)).getBundleList();
    }

    @Test
    @DisplayName("FsscriptRemoveEvent 不包含 QM 文件时不应该清除缓存")
    void testOnApplicationEvent_NonQmFileChange_ShouldNotInvalidateCache() {
        // 准备 mock 数据
        setupMockBundles("CachedModel");

        // 建立缓存
        resolver.getAllModelNames();

        // 创建不包含 QM 文件的事件
        Fsscript mockFsscript = mock(Fsscript.class);
        when(mockFsscript.getPath()).thenReturn("/some/path/model.tm"); // .tm 文件，不是 .qm

        FsscriptRemoveEvent event = new FsscriptRemoveEvent(List.of(mockFsscript));

        // 重置 mock
        reset(systemBundlesContext);

        // 触发事件
        resolver.onApplicationEvent(event);

        // 再次调用应该返回缓存
        List<String> result = resolver.getAllModelNames();

        // 验证缓存没有被清除
        assertEquals("CachedModel", result.get(0));
        verify(systemBundlesContext, never()).getBundleList();
    }

    @Test
    @DisplayName("FsscriptRemoveEvent 为空时不应该清除缓存")
    void testOnApplicationEvent_EmptyEvent_ShouldNotInvalidateCache() {
        // 准备 mock 数据
        setupMockBundles("CachedModel");

        // 建立缓存
        resolver.getAllModelNames();

        // 创建空事件
        FsscriptRemoveEvent event = new FsscriptRemoveEvent(new ArrayList<>());

        // 重置 mock
        reset(systemBundlesContext);

        // 触发事件
        resolver.onApplicationEvent(event);

        // 验证没有尝试扫描
        verify(systemBundlesContext, never()).getBundleList();
    }

    @Test
    @DisplayName("并发调用 getAllModelNames 应该线程安全")
    void testGetAllModelNames_ConcurrentCalls_ShouldBeThreadSafe() throws Exception {
        // 准备 mock 数据（设置一些延迟来模拟扫描时间）
        when(systemBundlesContext.getBundleList()).thenAnswer(inv -> {
            Thread.sleep(50); // 模拟扫描延迟
            return List.of(bundle);
        });
        when(bundle.findBundleResources("**/*.qm")).thenReturn(new BundleResource[]{bundleResource});
        when(bundleResource.getResource()).thenReturn(resource);
        when(resource.getDescription()).thenReturn("test.qm");
        when(queryModelLoader.loadJdbcQueryModel(any(BundleResource.class))).thenReturn(queryModel);
        when(queryModel.getName()).thenReturn("ConcurrentModel");

        // 启动多个线程并发调用
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        List<String>[] results = new List[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = resolver.getAllModelNames();
            });
        }

        // 同时启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 验证所有结果相同
        for (int i = 0; i < threadCount; i++) {
            assertNotNull(results[i]);
            assertEquals(1, results[i].size());
            assertEquals("ConcurrentModel", results[i].get(0));
        }

        // 验证只扫描了一次（由于 double-check locking）
        verify(systemBundlesContext, times(1)).getBundleList();
    }

    @Test
    @DisplayName("扫描时加载失败的 QM 文件应该被忽略")
    void testGetAllModelNames_FailedQmLoad_ShouldBeIgnored() {
        // 准备 mock 数据 - 两个 QM 文件，一个加载成功，一个失败
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

        // 调用
        List<String> result = resolver.getAllModelNames();

        // 验证只返回成功加载的模型
        assertEquals(1, result.size());
        assertEquals("GoodModel", result.get(0));
    }

    // ==================== 辅助方法 ====================

    /**
     * 设置 mock bundle 返回指定的模型名称
     */
    private void setupMockBundles(String modelName) {
        when(systemBundlesContext.getBundleList()).thenReturn(List.of(bundle));
        when(bundle.findBundleResources("**/*.qm")).thenReturn(new BundleResource[]{bundleResource});
        when(bundle.findResources("**/*.qm")).thenReturn(new Resource[0]);
        when(bundle.getName()).thenReturn("test-bundle");
        when(bundleResource.getResource()).thenReturn(resource);
        when(resource.getDescription()).thenReturn("test.qm");
        when(resource.isFile()).thenReturn(false); // 模拟 JAR 包内资源
        when(queryModelLoader.loadJdbcQueryModel(any(BundleResource.class))).thenReturn(queryModel);
        when(queryModel.getName()).thenReturn(modelName);
    }
}
