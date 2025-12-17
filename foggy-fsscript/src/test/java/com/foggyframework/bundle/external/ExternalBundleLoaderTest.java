package com.foggyframework.bundle.external;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ExternalBundleLoader 单元测试
 */
public class ExternalBundleLoaderTest {

    private Path tempDir;

    @BeforeEach
    public void setUp() throws IOException {
        // 创建临时目录用于测试
        tempDir = Files.createTempDirectory("external-bundle-test");
    }

    @Test
    public void testFromPropertiesWhenDisabled() {
        ExternalBundleProperties properties = new ExternalBundleProperties();
        properties.setEnabled(false);

        ExternalBundleLoader loader = ExternalBundleLoader.fromProperties(properties);

        Assertions.assertNull(loader, "禁用时应该返回null");
    }

    @Test
    public void testFromPropertiesWhenNull() {
        ExternalBundleLoader loader = ExternalBundleLoader.fromProperties(null);

        Assertions.assertNull(loader, "null配置应该返回null");
    }

    @Test
    public void testFromPropertiesWhenEmptyBundles() {
        ExternalBundleProperties properties = new ExternalBundleProperties();
        properties.setEnabled(true);
        properties.setBundles(Collections.emptyList());

        ExternalBundleLoader loader = ExternalBundleLoader.fromProperties(properties);

        Assertions.assertNull(loader, "空bundles列表应该返回null");
    }

    @Test
    public void testFromPropertiesWithValidPath() {
        ExternalBundleProperties properties = new ExternalBundleProperties();
        properties.setEnabled(true);

        ExternalBundleProperties.ExternalBundleItem item = new ExternalBundleProperties.ExternalBundleItem();
        item.setName("test-bundle");
        item.setPath(tempDir.toString());
        item.setWatch(false);

        properties.setBundles(Collections.singletonList(item));

        ExternalBundleLoader loader = ExternalBundleLoader.fromProperties(properties);

        Assertions.assertNotNull(loader, "有效路径应该返回loader");
        Assertions.assertEquals(1, loader.getBundleDefList().size());
        Assertions.assertEquals("test-bundle", loader.getBundleDefList().get(0).getName());
    }

    @Test
    public void testFromPropertiesWithInvalidPath() {
        ExternalBundleProperties properties = new ExternalBundleProperties();
        properties.setEnabled(true);

        ExternalBundleProperties.ExternalBundleItem item = new ExternalBundleProperties.ExternalBundleItem();
        item.setName("invalid-bundle");
        item.setPath("/non/existent/path/that/should/not/exist");
        item.setWatch(false);

        properties.setBundles(Collections.singletonList(item));

        ExternalBundleLoader loader = ExternalBundleLoader.fromProperties(properties);

        Assertions.assertNull(loader, "无效路径应该返回null");
    }

    @Test
    public void testFromPropertiesWithMissingName() {
        ExternalBundleProperties properties = new ExternalBundleProperties();
        properties.setEnabled(true);

        ExternalBundleProperties.ExternalBundleItem item = new ExternalBundleProperties.ExternalBundleItem();
        item.setName(null); // 缺少name
        item.setPath(tempDir.toString());

        properties.setBundles(Collections.singletonList(item));

        ExternalBundleLoader loader = ExternalBundleLoader.fromProperties(properties);

        Assertions.assertNull(loader, "缺少name应该返回null");
    }

    @Test
    public void testFromPropertiesWithMissingPath() {
        ExternalBundleProperties properties = new ExternalBundleProperties();
        properties.setEnabled(true);

        ExternalBundleProperties.ExternalBundleItem item = new ExternalBundleProperties.ExternalBundleItem();
        item.setName("test-bundle");
        item.setPath(null); // 缺少path

        properties.setBundles(Collections.singletonList(item));

        ExternalBundleLoader loader = ExternalBundleLoader.fromProperties(properties);

        Assertions.assertNull(loader, "缺少path应该返回null");
    }

    @Test
    public void testFromPropertiesWithMultipleBundles() throws IOException {
        // 创建第二个临时目录
        Path tempDir2 = Files.createTempDirectory("external-bundle-test2");

        ExternalBundleProperties properties = new ExternalBundleProperties();
        properties.setEnabled(true);

        ExternalBundleProperties.ExternalBundleItem item1 = new ExternalBundleProperties.ExternalBundleItem();
        item1.setName("bundle1");
        item1.setPath(tempDir.toString());

        ExternalBundleProperties.ExternalBundleItem item2 = new ExternalBundleProperties.ExternalBundleItem();
        item2.setName("bundle2");
        item2.setPath(tempDir2.toString());

        properties.setBundles(Arrays.asList(item1, item2));

        ExternalBundleLoader loader = ExternalBundleLoader.fromProperties(properties);

        Assertions.assertNotNull(loader);
        List<ExternalBundleDefinition> defs = loader.getBundleDefList();
        Assertions.assertEquals(2, defs.size());
        Assertions.assertEquals("bundle1", defs.get(0).getName());
        Assertions.assertEquals("bundle2", defs.get(1).getName());
    }

    @Test
    public void testFromPropertiesFiltersInvalidBundles() throws IOException {
        // 创建一个有效目录
        Path validDir = Files.createTempDirectory("valid-bundle");

        ExternalBundleProperties properties = new ExternalBundleProperties();
        properties.setEnabled(true);

        // 有效的bundle
        ExternalBundleProperties.ExternalBundleItem validItem = new ExternalBundleProperties.ExternalBundleItem();
        validItem.setName("valid-bundle");
        validItem.setPath(validDir.toString());

        // 无效的bundle（路径不存在）
        ExternalBundleProperties.ExternalBundleItem invalidItem = new ExternalBundleProperties.ExternalBundleItem();
        invalidItem.setName("invalid-bundle");
        invalidItem.setPath("/non/existent/path");

        properties.setBundles(Arrays.asList(validItem, invalidItem));

        ExternalBundleLoader loader = ExternalBundleLoader.fromProperties(properties);

        Assertions.assertNotNull(loader);
        // 应该只有一个有效的bundle
        Assertions.assertEquals(1, loader.getBundleDefList().size());
        Assertions.assertEquals("valid-bundle", loader.getBundleDefList().get(0).getName());
    }

    @Test
    public void testFromPropertiesWithFileInsteadOfDirectory() throws IOException {
        // 创建一个文件而不是目录
        File tempFile = File.createTempFile("test", ".txt");
        tempFile.deleteOnExit();

        ExternalBundleProperties properties = new ExternalBundleProperties();
        properties.setEnabled(true);

        ExternalBundleProperties.ExternalBundleItem item = new ExternalBundleProperties.ExternalBundleItem();
        item.setName("file-bundle");
        item.setPath(tempFile.getAbsolutePath()); // 指向文件而不是目录

        properties.setBundles(Collections.singletonList(item));

        ExternalBundleLoader loader = ExternalBundleLoader.fromProperties(properties);

        Assertions.assertNull(loader, "文件路径应该被过滤，返回null");
    }
}
