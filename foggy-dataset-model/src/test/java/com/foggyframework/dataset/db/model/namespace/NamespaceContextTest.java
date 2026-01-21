package com.foggyframework.dataset.db.model.namespace;

import com.foggyframework.dataset.db.model.spi.NamespaceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NamespaceContext单元测试
 * <p>
 * 测试ThreadLocal命名空间上下文的正确性和线程隔离性
 * </p>
 */
public class NamespaceContextTest {

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
    public void testThreadIsolation() throws InterruptedException {
        // 测试线程隔离性
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<String> thread1Result = new AtomicReference<>();
        AtomicReference<String> thread2Result = new AtomicReference<>();

        // 主线程设置namespace
        NamespaceContext.setNamespace("main");

        // 线程1设置为"dev"
        Thread thread1 = new Thread(() -> {
            NamespaceContext.setNamespace("dev");
            try {
                Thread.sleep(100); // 等待确保线程2也设置了值
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            thread1Result.set(NamespaceContext.getNamespace());
            NamespaceContext.clear();
            latch.countDown();
        });

        // 线程2设置为"test"
        Thread thread2 = new Thread(() -> {
            NamespaceContext.setNamespace("test");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            thread2Result.set(NamespaceContext.getNamespace());
            NamespaceContext.clear();
            latch.countDown();
        });

        thread1.start();
        thread2.start();

        latch.await();

        // 验证每个线程的namespace互不影响
        assertEquals("dev", thread1Result.get());
        assertEquals("test", thread2Result.get());

        // 主线程的namespace应该不受影响
        assertEquals("main", NamespaceContext.getNamespace());
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
}
