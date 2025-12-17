package com.foggyframework.bundle.external;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * ExternalBundleDefinition 单元测试
 */
public class ExternalBundleDefinitionTest {

    @Test
    public void testConstructor() {
        ExternalBundleDefinition def = new ExternalBundleDefinition(
                "test-bundle", "/data/models", true);

        Assertions.assertEquals("test-bundle", def.getName());
        Assertions.assertEquals("/data/models", def.getPath());
        Assertions.assertTrue(def.isWatch());
    }

    @Test
    public void testPackageName() {
        ExternalBundleDefinition def = new ExternalBundleDefinition(
                "my-external", "/some/path", false);

        // 虚拟包名应该以 "external." 开头
        Assertions.assertEquals("external.my-external", def.getPackageName());
    }

    @Test
    public void testFromBundleItem() {
        ExternalBundleProperties.ExternalBundleItem item = new ExternalBundleProperties.ExternalBundleItem();
        item.setName("item-bundle");
        item.setPath("/item/path");
        item.setWatch(true);

        ExternalBundleDefinition def = new ExternalBundleDefinition(item);

        Assertions.assertEquals("item-bundle", def.getName());
        Assertions.assertEquals("/item/path", def.getPath());
        Assertions.assertTrue(def.isWatch());
        Assertions.assertEquals("external.item-bundle", def.getPackageName());
    }

    @Test
    public void testGetDefinitionClass() {
        ExternalBundleDefinition def = new ExternalBundleDefinition(
                "test", "/path", false);

        // 外部Bundle没有对应的Java类，返回定义类本身
        Assertions.assertEquals(ExternalBundleDefinition.class, def.getDefinitionClass());
    }

    @Test
    public void testToString() {
        ExternalBundleDefinition def = new ExternalBundleDefinition(
                "test-bundle", "/data/models", true);

        String str = def.toString();

        Assertions.assertTrue(str.contains("test-bundle"));
        Assertions.assertTrue(str.contains("/data/models"));
        Assertions.assertTrue(str.contains("true"));
    }
}
