package com.foggyframework.dataset.db.model.namespace;

import com.foggyframework.dataset.db.model.spi.NamespaceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NamespaceContext单元测试
 * <p>
 * 测试ThreadLocal命名空间上下文的正确性和线程隔离性
 * </p>
 */
@SuppressWarnings("deprecation")
public class NamespaceContextTest {

    private static final long TIMEOUT_SECONDS = 5;

    @AfterEach
    public void cleanup() {
        // 每个测试后清理ThreadLocal
        NamespaceContext.clear();
    }

    @Test
    public void testSetAndGet() {
        // 测试基本的设置和获取
        NamespaceContext.setNamespace("dev");
        assertEquals("dev", NamespaceContext.getNamespace());

        NamespaceContext.setNamespace("test");
        assertEquals("test", NamespaceContext.getNamespace());

        NamespaceContext.setNamespace("");
        assertEquals("", NamespaceContext.getNamespace());
    }

    @Test
    public void testDefaultValue() {
        // 测试默认值（未设置时应为null）
        assertNull(NamespaceContext.getNamespace());
    }

    @Test
    public void testClear() {
        // 测试清除功能
        NamespaceContext.setNamespace("dev");
        assertEquals("dev", NamespaceContext.getNamespace());

        NamespaceContext.clear();
        assertNull(NamespaceContext.getNamespace());
    }

    @Test
    public void testThreadIsolation() throws Exception {
        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        NamespaceContext.setNamespace("main");

        Future<String> dev = executor.submit(
                () -> observeNamespaceAfterCoordinatedRelease("dev", workersReady, releaseWorkers));
        Future<String> test = executor.submit(
                () -> observeNamespaceAfterCoordinatedRelease("test", workersReady, releaseWorkers));

        try {
            assertTrue(workersReady.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "both workers must establish their namespace before release");
            assertEquals("main", NamespaceContext.getNamespace(),
                    "worker mutations must not affect the owning thread");

            releaseWorkers.countDown();

            assertEquals("dev", dev.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals("test", test.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals("main", NamespaceContext.getNamespace());
        } finally {
            releaseWorkers.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "worker executor must terminate within the bounded deadline");
        }
    }

    @Test
    public void testNullValue() {
        // 测试null值处理
        NamespaceContext.setNamespace(null);
        assertNull(NamespaceContext.getNamespace());
    }

    @Test
    public void testEmptyString() {
        // 测试空字符串（表示默认命名空间）
        NamespaceContext.setNamespace("");
        assertEquals("", NamespaceContext.getNamespace());
        assertNotNull(NamespaceContext.getNamespace());
    }

    @Test
    public void testMultipleSetAndClear() {
        // 测试多次设置和清除
        NamespaceContext.setNamespace("ns1");
        assertEquals("ns1", NamespaceContext.getNamespace());

        NamespaceContext.setNamespace("ns2");
        assertEquals("ns2", NamespaceContext.getNamespace());

        NamespaceContext.clear();
        assertNull(NamespaceContext.getNamespace());

        NamespaceContext.setNamespace("ns3");
        assertEquals("ns3", NamespaceContext.getNamespace());

        NamespaceContext.clear();
        assertNull(NamespaceContext.getNamespace());
    }

    private String observeNamespaceAfterCoordinatedRelease(
            String namespace,
            CountDownLatch workersReady,
            CountDownLatch releaseWorkers
    ) throws InterruptedException {
        NamespaceContext.setNamespace(namespace);
        workersReady.countDown();
        try {
            assertTrue(releaseWorkers.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "worker release must arrive within the bounded deadline");
            return NamespaceContext.getNamespace();
        } finally {
            NamespaceContext.clear();
        }
    }
}
