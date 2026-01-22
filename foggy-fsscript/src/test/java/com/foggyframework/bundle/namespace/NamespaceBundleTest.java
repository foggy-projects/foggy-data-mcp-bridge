package com.foggyframework.bundle.namespace;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.SystemBundlesContextImpl;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.bundle.external.ExternalFileBundle;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.core.ex.RX;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * SystemBundlesContext Namespace功能测试
 * <p>
 * 测试基于namespace的资源查找功能
 * </p>
 */
public class NamespaceBundleTest {

    @TempDir
    Path tempDir;

    private SystemBundlesContextImpl context;
    private ApplicationContext mockAppCtx;

    @BeforeEach
    public void setup() throws IOException {
        mockAppCtx = mock(ApplicationContext.class);

        context = new SystemBundlesContextImpl(new ArrayList<>());
        context.setAppCtx(mockAppCtx);
        context.setBundleList(new ArrayList<>());
        context.setName2BundleDefinition(new HashMap<>());
    }

    @Test
    public void testFindResourceByNameWithDefaultNamespace() throws IOException {
        // 创建默认命名空间的bundle
        Path defaultBundlePath = tempDir.resolve("default-bundle");
        Files.createDirectories(defaultBundlePath);
        Path testFile = defaultBundlePath.resolve("test.tm");
        Files.writeString(testFile, "export const model = {};");

        ExternalBundleDefinition def = new ExternalBundleDefinition(
                "default-bundle", "", defaultBundlePath.toString(), false
        );
        ExternalFileBundle bundle = new ExternalFileBundle(context);
        bundle.setName(def.getName());
        bundle.setBundleDefinition(def);
        bundle.setBasePath(def.getPath());
        bundle.setRootPath(def.getPath());

        context.regBundle(bundle);
        context.getName2BundleDefinition().put("default-bundle", def);  // 添加到 map

        // 使用默认命名空间查找
        BundleResource resource = context.findResourceByName("test.tm", "", true);
        assertNotNull(resource);
        assertEquals("default-bundle", resource.getBundle().getName());
    }

    @Test
    public void testFindResourceByNameWithDevNamespace() throws IOException {
        // 创建dev命名空间的bundle
        Path devBundlePath = tempDir.resolve("dev-bundle");
        Files.createDirectories(devBundlePath);
        Path testFile = devBundlePath.resolve("test.tm");
        Files.writeString(testFile, "export const model = {};");

        ExternalBundleDefinition def = new ExternalBundleDefinition(
                "dev-bundle", "dev", devBundlePath.toString(), false
        );
        ExternalFileBundle bundle = new ExternalFileBundle(context);
        bundle.setName(def.getName());
        bundle.setBundleDefinition(def);
        bundle.setBasePath(def.getPath());
        bundle.setRootPath(def.getPath());

        context.regBundle(bundle);
        context.getName2BundleDefinition().put("dev-bundle", def);  // 添加到 map

        // 使用dev命名空间查找
        BundleResource resource = context.findResourceByName("test.tm", "dev", true);
        assertNotNull(resource);
        assertEquals("dev-bundle", resource.getBundle().getName());
    }

    @Test
    public void testFindResourceByNameInWrongNamespace() throws IOException {
        // 创建dev命名空间的bundle
        Path devBundlePath = tempDir.resolve("dev-bundle");
        Files.createDirectories(devBundlePath);
        Path testFile = devBundlePath.resolve("test.tm");
        Files.writeString(testFile, "export const model = {};");

        ExternalBundleDefinition def = new ExternalBundleDefinition(
                "dev-bundle", "dev", devBundlePath.toString(), false
        );
        ExternalFileBundle bundle = new ExternalFileBundle(context);
        bundle.setName(def.getName());
        bundle.setBundleDefinition(def);
        bundle.setBasePath(def.getPath());
        bundle.setRootPath(def.getPath());

        context.regBundle(bundle);
        context.getName2BundleDefinition().put("dev-bundle", def);  // 添加到 map

        // 使用test命名空间查找（应该找不到）
        try {
            context.findResourceByName("test.tm", "test", true);
            fail("应该抛出异常");
        } catch (Exception e) {
            // 预期异常
            assertTrue(e.getMessage().contains("在命名空间"));
        }
    }

    @Test
    public void testFindResourceByNameWithMultipleNamespaces() throws IOException {
        // 创建dev和test两个命名空间的bundle，都有同名文件
        Path devBundlePath = tempDir.resolve("dev-bundle");
        Files.createDirectories(devBundlePath);
        Path devFile = devBundlePath.resolve("model.tm");
        Files.writeString(devFile, "export const model = {name: 'dev'};");

        Path testBundlePath = tempDir.resolve("test-bundle");
        Files.createDirectories(testBundlePath);
        Path testFile = testBundlePath.resolve("model.tm");
        Files.writeString(testFile, "export const model = {name: 'test'};");

        // 注册dev bundle
        ExternalBundleDefinition devDef = new ExternalBundleDefinition(
                "dev-bundle", "dev", devBundlePath.toString(), false
        );
        ExternalFileBundle devBundle = new ExternalFileBundle(context);
        devBundle.setName(devDef.getName());
        devBundle.setBundleDefinition(devDef);
        devBundle.setBasePath(devDef.getPath());
        devBundle.setRootPath(devDef.getPath());
        context.regBundle(devBundle);
        context.getName2BundleDefinition().put("dev-bundle", devDef);  // 添加到 map

        // 注册test bundle
        ExternalBundleDefinition testDef = new ExternalBundleDefinition(
                "test-bundle", "test", testBundlePath.toString(), false
        );
        ExternalFileBundle testBundle = new ExternalFileBundle(context);
        testBundle.setName(testDef.getName());
        testBundle.setBundleDefinition(testDef);
        testBundle.setBasePath(testDef.getPath());
        testBundle.setRootPath(testDef.getPath());
        context.regBundle(testBundle);
        context.getName2BundleDefinition().put("test-bundle", testDef);  // 添加到 map

        // 在dev命名空间查找
        BundleResource devResource = context.findResourceByName("model.tm", "dev", true);
        assertNotNull(devResource);
        assertEquals("dev-bundle", devResource.getBundle().getName());

        // 在test命名空间查找
        BundleResource testResource = context.findResourceByName("model.tm", "test", true);
        assertNotNull(testResource);
        assertEquals("test-bundle", testResource.getBundle().getName());
    }

    @Test
    public void testFindResourceByNameNotFoundWithErrorFlag() throws IOException {
        // 创建dev命名空间的bundle（但文件不存在）
        Path devBundlePath = tempDir.resolve("dev-bundle");
        Files.createDirectories(devBundlePath);

        ExternalBundleDefinition def = new ExternalBundleDefinition(
                "dev-bundle", "dev", devBundlePath.toString(), false
        );
        ExternalFileBundle bundle = new ExternalFileBundle(context);
        bundle.setName(def.getName());
        bundle.setBundleDefinition(def);
        bundle.setBasePath(def.getPath());
        bundle.setRootPath(def.getPath());

        context.regBundle(bundle);
        context.getName2BundleDefinition().put("dev-bundle", def);  // 添加到 map

        // errorIfNotFound=true 应该抛出异常
        try {
            context.findResourceByName("notexist.tm", "dev", true);
            fail("应该抛出异常");
        } catch (Exception e) {
            // 预期异常
            assertTrue(e.getMessage().contains("notexist.tm"));
        }

        // errorIfNotFound=false 应该返回null
        BundleResource resource = context.findResourceByName("notexist.tm", "dev", false);
        assertNull(resource);
    }

    @Test
    public void testNamespaceNormalization() throws IOException {
        // 测试namespace标准化（null和空字符串都视为默认命名空间）
        Path bundlePath = tempDir.resolve("bundle");
        Files.createDirectories(bundlePath);
        Path testFile = bundlePath.resolve("test.tm");
        Files.writeString(testFile, "export const model = {};");

        ExternalBundleDefinition def = new ExternalBundleDefinition(
                "bundle", "", bundlePath.toString(), false
        );
        ExternalFileBundle bundle = new ExternalFileBundle(context);
        bundle.setName(def.getName());
        bundle.setBundleDefinition(def);
        bundle.setBasePath(def.getPath());
        bundle.setRootPath(def.getPath());

        context.regBundle(bundle);
        context.getName2BundleDefinition().put("bundle", def);  // 添加到 map

        // null和空字符串应该找到同一个资源
        BundleResource resource1 = context.findResourceByName("test.tm", null, true);
        BundleResource resource2 = context.findResourceByName("test.tm", "", true);

        assertNotNull(resource1);
        assertNotNull(resource2);
        assertEquals(resource1.getBundle().getName(), resource2.getBundle().getName());
    }
}
