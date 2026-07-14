package com.foggyframework.bundle.dynamic;

import com.foggyframework.bundle.SystemBundlesContextImpl;
import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 动态Bundle管理功能测试
 * <p>
 * 测试addExternalBundle、removeBundle、listExternalBundles功能
 * </p>
 */
public class DynamicBundleManagementTest {

    @TempDir
    Path tempDir;

    private SystemBundlesContextImpl context;
    private ApplicationContext mockAppCtx;
    private FsscriptFileChangeHandler mockChangeHandler;

    @BeforeEach
    public void setup() {
        mockAppCtx = mock(ApplicationContext.class);
        mockChangeHandler = mock(FsscriptFileChangeHandler.class);
        when(mockAppCtx.getBean(FsscriptFileChangeHandler.class))
                .thenReturn(mockChangeHandler);
        when(mockChangeHandler.watchExternalBundle(anyString(), anyString()))
                .thenReturn(true);

        context = new SystemBundlesContextImpl(new ArrayList<>());
        context.setAppCtx(mockAppCtx);
        context.setBundleList(new ArrayList<>());
        context.setName2BundleDefinition(new HashMap<>());
    }

    @Test
    public void testAddExternalBundle() throws IOException {
        // 创建有效的目录
        Path bundlePath = tempDir.resolve("test-bundle");
        Files.createDirectories(bundlePath);

        // 添加bundle
        boolean success = context.addExternalBundle(
                "test-bundle",
                "dev",
                bundlePath.toString(),
                true
        );

        assertTrue(success, "添加bundle应该成功");
        assertTrue(context.containBundle("test-bundle"), "bundle应该存在");
    }

    @Test
    public void testAddedExternalBundleCanBeFoundByNamespace() throws IOException {
        Path bundlePath = tempDir.resolve("namespace-bundle");
        Path modelPath = bundlePath.resolve("model");
        Files.createDirectories(modelPath);
        Files.writeString(modelPath.resolve("TestModel.tm"), "export const model = {};");

        boolean success = context.addExternalBundle(
                "namespace-bundle",
                "odoo",
                bundlePath.toString(),
                false
        );

        assertTrue(success);
        BundleResource resource = context.findResourceByName("TestModel.tm", "odoo", true);
        assertNotNull(resource);
        assertEquals("namespace-bundle", resource.getBundle().getName());
    }

    @Test
    public void testAddExternalBundleWithDefaultNamespace() throws IOException {
        // 测试添加默认命名空间的bundle
        Path bundlePath = tempDir.resolve("default-bundle");
        Files.createDirectories(bundlePath);

        boolean success = context.addExternalBundle(
                "default-bundle",
                "",  // 默认命名空间
                bundlePath.toString(),
                false
        );

        assertTrue(success);
        assertTrue(context.containBundle("default-bundle"));

        // 验证namespace
        List<BundleDefinition> bundles = context.listExternalBundles();
        assertEquals(1, bundles.size());
        assertEquals("", bundles.get(0).getNamespace());
    }

    @Test
    public void testAddExternalBundleDuplicate() throws IOException {
        // 测试添加重复的bundle
        Path bundlePath = tempDir.resolve("duplicate-bundle");
        Files.createDirectories(bundlePath);

        // 第一次添加
        boolean success1 = context.addExternalBundle(
                "duplicate-bundle",
                "dev",
                bundlePath.toString(),
                true
        );
        assertTrue(success1);

        // 第二次添加同名bundle（应该失败）
        boolean success2 = context.addExternalBundle(
                "duplicate-bundle",
                "test",
                bundlePath.toString(),
                true
        );
        assertFalse(success2, "添加重复bundle应该失败");
    }

    @Test
    public void testAddExternalBundleInvalidPath() {
        // 测试添加无效路径的bundle
        boolean success = context.addExternalBundle(
                "invalid-bundle",
                "dev",
                "/nonexistent/path/12345",
                true
        );

        assertFalse(success, "添加无效路径的bundle应该失败");
        assertFalse(context.containBundle("invalid-bundle"));
    }

    @Test
    public void testWatchRegistrationFailureRollsBackBundleRegistry() throws IOException {
        Path bundlePath = tempDir.resolve("watch-registration-failure");
        Files.createDirectories(bundlePath);
        when(mockChangeHandler.watchExternalBundle(anyString(), anyString()))
                .thenReturn(false);

        boolean success = context.addExternalBundle(
                "watch-registration-failure",
                "dev",
                bundlePath.toString(),
                true
        );

        assertFalse(success);
        assertFalse(context.containBundle("watch-registration-failure"));
        assertNull(context.getBundleDefinitionByName("watch-registration-failure"));
        assertTrue(context.listExternalBundles().isEmpty());
    }

    @Test
    public void testAddExternalBundleFilePath() throws IOException {
        // 测试路径是文件而非目录的情况
        Path filePath = tempDir.resolve("file.txt");
        Files.writeString(filePath, "test");

        boolean success = context.addExternalBundle(
                "file-bundle",
                "dev",
                filePath.toString(),
                true
        );

        assertFalse(success, "路径为文件时添加应该失败");
    }

    @Test
    public void testRemoveExternalBundle() throws IOException {
        // 先添加bundle
        Path bundlePath = tempDir.resolve("removable-bundle");
        Files.createDirectories(bundlePath);

        context.addExternalBundle(
                "removable-bundle",
                "dev",
                bundlePath.toString(),
                true
        );

        assertTrue(context.containBundle("removable-bundle"));

        // 移除bundle
        boolean success = context.removeBundle("removable-bundle");

        assertTrue(success, "移除bundle应该成功");
        assertFalse(context.containBundle("removable-bundle"), "bundle应该已被移除");
    }

    @Test
    public void testRemoveNonExistentBundle() {
        // 测试移除不存在的bundle
        boolean success = context.removeBundle("nonexistent-bundle");

        assertFalse(success, "移除不存在的bundle应该失败");
    }

    @Test
    public void testRemoveEmptyBundleName() {
        // 测试空bundle名称
        boolean success1 = context.removeBundle("");
        assertFalse(success1);

        boolean success2 = context.removeBundle(null);
        assertFalse(success2);
    }

    @Test
    public void testListExternalBundles() throws IOException {
        // 添加多个bundle
        Path bundle1Path = tempDir.resolve("bundle1");
        Files.createDirectories(bundle1Path);
        context.addExternalBundle("bundle1", "dev", bundle1Path.toString(), true);

        Path bundle2Path = tempDir.resolve("bundle2");
        Files.createDirectories(bundle2Path);
        context.addExternalBundle("bundle2", "test", bundle2Path.toString(), false);

        Path bundle3Path = tempDir.resolve("bundle3");
        Files.createDirectories(bundle3Path);
        context.addExternalBundle("bundle3", "", bundle3Path.toString(), true);

        // 列出所有外部bundle
        List<BundleDefinition> bundles = context.listExternalBundles();

        assertEquals(3, bundles.size());

        // 验证每个bundle的namespace
        assertTrue(bundles.stream().anyMatch(b -> "dev".equals(b.getNamespace())));
        assertTrue(bundles.stream().anyMatch(b -> "test".equals(b.getNamespace())));
        assertTrue(bundles.stream().anyMatch(b -> "".equals(b.getNamespace())));
    }

    @Test
    public void testListExternalBundlesEmpty() {
        // 没有添加任何bundle时
        List<BundleDefinition> bundles = context.listExternalBundles();

        assertNotNull(bundles);
        assertTrue(bundles.isEmpty());
    }

    @Test
    public void testGetBundleListReturnsSnapshot() throws IOException {
        Path bundlePath = tempDir.resolve("snapshot-bundle");
        Files.createDirectories(bundlePath);

        assertTrue(context.addExternalBundle("snapshot-bundle", "dev", bundlePath.toString(), false));

        List<Bundle> bundleSnapshot = context.getBundleList();
        bundleSnapshot.clear();

        assertTrue(context.containBundle("snapshot-bundle"),
                "调用方修改getBundleList返回值不应破坏SystemBundlesContext内部状态");
        assertEquals(1, context.listExternalBundles().size());
    }

    @Test
    public void testListExternalBundlesDuringRemoveShouldNotFailFast() throws Exception {
        Path bundlePath = tempDir.resolve("blocking-bundle");
        Files.createDirectories(bundlePath);

        CountDownLatch definitionReadStarted = new CountDownLatch(1);
        CountDownLatch allowDefinitionReturn = new CountDownLatch(1);
        BlockingExternalFileBundle bundle = new BlockingExternalFileBundle(
                context, definitionReadStarted, allowDefinitionReturn);
        bundle.setName("blocking-bundle");
        bundle.setBasePath(bundlePath.toString());
        bundle.setRootPath(bundlePath.toString());
        bundle.setBundleDefinition(new ExternalBundleDefinition("blocking-bundle", "dev", bundlePath.toString(), false));
        context.regBundle(bundle);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<BundleDefinition>> listing = executor.submit(() -> context.listExternalBundles());
            assertTrue(definitionReadStarted.await(2, TimeUnit.SECONDS), "listExternalBundles should start reading bundle definitions");

            Future<Boolean> removing = executor.submit(() -> context.removeBundle("blocking-bundle"));
            Thread.sleep(100);
            allowDefinitionReturn.countDown();

            List<BundleDefinition> listedBundles;
            try {
                listedBundles = listing.get(2, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                fail("listExternalBundles must tolerate concurrent remove without fail-fast iteration", e.getCause());
                return;
            }

            assertNotNull(listedBundles);
            assertTrue(removing.get(2, TimeUnit.SECONDS));
            assertFalse(context.containBundle("blocking-bundle"));
        } finally {
            allowDefinitionReturn.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void testAddAndRemoveMultipleBundles() throws IOException {
        // 测试添加和移除多个bundle的组合操作
        Path bundle1Path = tempDir.resolve("multi-bundle1");
        Files.createDirectories(bundle1Path);
        Path bundle2Path = tempDir.resolve("multi-bundle2");
        Files.createDirectories(bundle2Path);
        Path bundle3Path = tempDir.resolve("multi-bundle3");
        Files.createDirectories(bundle3Path);

        // 添加3个bundle
        assertTrue(context.addExternalBundle("multi-bundle1", "dev", bundle1Path.toString(), true));
        assertTrue(context.addExternalBundle("multi-bundle2", "test", bundle2Path.toString(), false));
        assertTrue(context.addExternalBundle("multi-bundle3", "", bundle3Path.toString(), true));

        assertEquals(3, context.listExternalBundles().size());

        // 移除中间一个
        assertTrue(context.removeBundle("multi-bundle2"));
        assertEquals(2, context.listExternalBundles().size());

        // 再次添加
        assertTrue(context.addExternalBundle("multi-bundle2", "test", bundle2Path.toString(), false));
        assertEquals(3, context.listExternalBundles().size());

        // 移除所有
        assertTrue(context.removeBundle("multi-bundle1"));
        assertTrue(context.removeBundle("multi-bundle2"));
        assertTrue(context.removeBundle("multi-bundle3"));
        assertEquals(0, context.listExternalBundles().size());
    }

    @Test
    public void testThreadSafety() throws IOException, InterruptedException {
        // 测试多线程并发添加bundle的线程安全性
        Path basePath = tempDir.resolve("concurrent");
        Files.createDirectories(basePath);

        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            Path bundlePath = basePath.resolve("bundle-" + index);
            Files.createDirectories(bundlePath);

            threads[i] = new Thread(() -> {
                context.addExternalBundle(
                        "bundle-" + index,
                        "ns-" + index,
                        bundlePath.toString(),
                        false
                );
            });
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 验证所有bundle都已添加
        List<BundleDefinition> bundles = context.listExternalBundles();
        assertEquals(threadCount, bundles.size());
    }

    private static class BlockingExternalFileBundle extends ExternalFileBundle {

        private final CountDownLatch definitionReadStarted;
        private final CountDownLatch allowDefinitionReturn;
        private final AtomicBoolean blockOnce = new AtomicBoolean(true);

        BlockingExternalFileBundle(SystemBundlesContextImpl context,
                                   CountDownLatch definitionReadStarted,
                                   CountDownLatch allowDefinitionReturn) {
            super(context);
            this.definitionReadStarted = definitionReadStarted;
            this.allowDefinitionReturn = allowDefinitionReturn;
        }

        @Override
        public BundleDefinition getDefinition() {
            if (blockOnce.compareAndSet(true, false)) {
                definitionReadStarted.countDown();
                try {
                    allowDefinitionReturn.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            return super.getDefinition();
        }
    }
}
