package com.foggyframework.bundle.external;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * ExternalBundleProperties 单元测试
 */
public class ExternalBundlePropertiesTest {

    @Test
    public void testDefaultValues() {
        ExternalBundleProperties properties = new ExternalBundleProperties();

        Assertions.assertFalse(properties.isEnabled(), "默认应该是禁用状态");
        Assertions.assertNotNull(properties.getBundles(), "bundles列表不应为null");
        Assertions.assertTrue(properties.getBundles().isEmpty(), "默认bundles列表应该为空");
    }

    @Test
    public void testSetEnabled() {
        ExternalBundleProperties properties = new ExternalBundleProperties();
        properties.setEnabled(true);

        Assertions.assertTrue(properties.isEnabled());
    }

    @Test
    public void testBundleItem() {
        ExternalBundleProperties.ExternalBundleItem item = new ExternalBundleProperties.ExternalBundleItem();
        item.setName("test-bundle");
        item.setPath("/data/models");
        item.setWatch(true);

        Assertions.assertEquals("test-bundle", item.getName());
        Assertions.assertEquals("/data/models", item.getPath());
        Assertions.assertTrue(item.isWatch());
    }

    @Test
    public void testBundleItemDefaultWatch() {
        ExternalBundleProperties.ExternalBundleItem item = new ExternalBundleProperties.ExternalBundleItem();

        Assertions.assertFalse(item.isWatch(), "watch默认应该为false");
    }

    @Test
    public void testAddBundles() {
        ExternalBundleProperties properties = new ExternalBundleProperties();

        ExternalBundleProperties.ExternalBundleItem item1 = new ExternalBundleProperties.ExternalBundleItem();
        item1.setName("bundle1");
        item1.setPath("/path1");

        ExternalBundleProperties.ExternalBundleItem item2 = new ExternalBundleProperties.ExternalBundleItem();
        item2.setName("bundle2");
        item2.setPath("/path2");

        properties.setBundles(Arrays.asList(item1, item2));

        List<ExternalBundleProperties.ExternalBundleItem> bundles = properties.getBundles();
        Assertions.assertEquals(2, bundles.size());
        Assertions.assertEquals("bundle1", bundles.get(0).getName());
        Assertions.assertEquals("bundle2", bundles.get(1).getName());
    }
}
