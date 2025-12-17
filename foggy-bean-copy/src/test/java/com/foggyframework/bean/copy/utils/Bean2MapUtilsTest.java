package com.foggyframework.bean.copy.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bean2MapUtils 完整单元测试
 *
 * @author fengjianguang
 */
@DisplayName("Bean2MapUtils 工具类测试")
class Bean2MapUtilsTest {

    // 测试用的源对象
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceBean {
        private String name;
        private Integer age;
        private int primitiveInt;
        private long primitiveLong;
        private double primitiveDouble;
        private boolean primitiveBoolean;
        private BigDecimal amount;
        private Date createdDate;
        private String nullableField;
    }

    // 测试用的目标对象
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public  static class TargetBean {
        private String name;
        private Integer age;
        private int primitiveInt;
        private long primitiveLong;
        private double primitiveDouble;
        private boolean primitiveBoolean;
        private BigDecimal amount;
        private Date createdDate;
        private String nullableField;
        private String extraField; // 源对象没有的字段
    }

    // 不兼容的目标对象
    @Data
    @NoArgsConstructor
    public   static class PartialTargetBean {
        private String name;
        private Integer age;
        private String differentTypeField;
    }

    // 只有基本类型的Bean
    @Data
    @NoArgsConstructor
    public  static class PrimitiveBean {
        private int intValue;
        private long longValue;
        private double doubleValue;
        private boolean booleanValue;
    }

    // 只有包装类型的Bean
    @Data
    @NoArgsConstructor
    public  static class WrapperBean {
        private Integer intValue;
        private Long longValue;
        private Double doubleValue;
        private Boolean booleanValue;
    }

    // 所有基本类型的Bean
    @Data
    @NoArgsConstructor
    public  static class AllPrimitivesBean {
        private byte byteValue;
        private short shortValue;
        private int intValue;
        private long longValue;
        private float floatValue;
        private double doubleValue;
        private boolean booleanValue;
        private char charValue;
    }

    // 所有包装类型的Bean
    @Data
    @NoArgsConstructor
    public static class AllWrappersBean {
        private Byte byteValue;
        private Short shortValue;
        private Integer intValue;
        private Long longValue;
        private Float floatValue;
        private Double doubleValue;
        private Boolean booleanValue;
        private Character charValue;
    }

    private SourceBean sourceBean;
    private Date testDate;

    @BeforeEach
    void setUp() {
        testDate = new Date();
        sourceBean = new SourceBean(
            "John Doe",
            30,
            100,
            200L,
            3.14,
            true,
            new BigDecimal("999.99"),
            testDate,
            "test"
        );
    }

    // ==================== copyPropertiesSafe 测试 ====================

    @Test
    @DisplayName("测试基本属性复制")
    void testCopyPropertiesBasic() {
        TargetBean target = new TargetBean();

        Bean2MapUtils.copyPropertiesSafe(sourceBean, target);

        assertEquals("John Doe", target.getName());
        assertEquals(30, target.getAge());
        assertEquals(100, target.getPrimitiveInt());
        assertEquals(200L, target.getPrimitiveLong());
        assertEquals(3.14, target.getPrimitiveDouble(), 0.001);
        assertTrue(target.isPrimitiveBoolean());
        assertEquals(new BigDecimal("999.99"), target.getAmount());
        assertEquals(testDate, target.getCreatedDate());
        assertEquals("test", target.getNullableField());
    }

    @Test
    @DisplayName("测试null源对象")
    void testCopyPropertiesWithNullSource() {
        TargetBean target = new TargetBean();
        target.setName("Original");

        Bean2MapUtils.copyPropertiesSafe(null, target);

        // 目标对象应该保持不变
        assertEquals("Original", target.getName());
    }

    @Test
    @DisplayName("测试null目标对象")
    void testCopyPropertiesWithNullTarget() {
        // 不应该抛出异常
        assertDoesNotThrow(() -> Bean2MapUtils.copyPropertiesSafe(sourceBean, null));
    }

    @Test
    @DisplayName("测试源和目标都为null")
    void testCopyPropertiesWithBothNull() {
        assertDoesNotThrow(() -> Bean2MapUtils.copyPropertiesSafe(null, null));
    }

    @Test
    @DisplayName("测试null值复制到包装类型")
    void testCopyNullToWrapperType() {
        SourceBean source = new SourceBean();
        source.setName("Test");
        source.setAge(null); // null 包装类型
        source.setNullableField(null);

        TargetBean target = new TargetBean();
        target.setAge(100); // 原始值
        target.setNullableField("original");

        Bean2MapUtils.copyPropertiesSafe(source, target);

        assertEquals("Test", target.getName());
        assertNull(target.getAge()); // 包装类型应该被设置为null
        assertNull(target.getNullableField());
    }



    @Test
    @DisplayName("测试null值转换为基本类型默认值")
    void testNullNotCopiedToPrimitiveType() {
        PrimitiveBean target = new PrimitiveBean();
        target.setIntValue(100);
        target.setLongValue(200L);
        target.setDoubleValue(3.14);
        target.setBooleanValue(true);

        WrapperBean source = new WrapperBean();
        source.setIntValue(null);
        source.setLongValue(null);
        source.setDoubleValue(null);
        source.setBooleanValue(null);

        Bean2MapUtils.copyPropertiesSafe(source, target);

        // null值应该被转换为基本类型的默认值
        assertEquals(0, target.getIntValue(), "Integer(null) 应该转换为 int(0)");
        assertEquals(0L, target.getLongValue(), "Long(null) 应该转换为 long(0L)");
        assertEquals(0.0, target.getDoubleValue(), 0.001, "Double(null) 应该转换为 double(0.0)");
        assertFalse(target.isBooleanValue(), "Boolean(null) 应该转换为 boolean(false)");
    }

    @Test
    @DisplayName("测试包装类型到基本类型的复制")
    void testWrapperToPrimitiveCopy() {
        WrapperBean source = new WrapperBean();
        source.setIntValue(42);
        source.setLongValue(84L);
        source.setDoubleValue(2.71);
        source.setBooleanValue(false);

        PrimitiveBean target = new PrimitiveBean();

        Bean2MapUtils.copyPropertiesSafe(source, target);

        assertEquals(42, target.getIntValue());
        assertEquals(84L, target.getLongValue());
        assertEquals(2.71, target.getDoubleValue(), 0.001);
        assertFalse(target.isBooleanValue());
    }

    @Test
    @DisplayName("测试部分属性复制")
    void testPartialPropertyCopy() {
        PartialTargetBean target = new PartialTargetBean();

        Bean2MapUtils.copyPropertiesSafe(sourceBean, target);

        assertEquals("John Doe", target.getName());
        assertEquals(30, target.getAge());
        // differentTypeField 不应该被复制
        assertNull(target.getDifferentTypeField());
    }

    @Test
    @DisplayName("测试目标对象有额外字段")
    void testTargetHasExtraFields() {
        TargetBean target = new TargetBean();
        target.setExtraField("extra");

        Bean2MapUtils.copyPropertiesSafe(sourceBean, target);

        // 额外字段应该保持不变
        assertEquals("extra", target.getExtraField());
        // 其他字段应该正常复制
        assertEquals("John Doe", target.getName());
    }

    @Test
    @DisplayName("测试同一对象的复制")
    void testCopySameObject() {
        SourceBean sameSource = new SourceBean(
            "Jane",
            25,
            50,
            100L,
            1.5,
            false,
            new BigDecimal("100"),
            new Date(),
            null
        );

        Bean2MapUtils.copyPropertiesSafe(sameSource, sourceBean);

        assertEquals("Jane", sourceBean.getName());
        assertEquals(25, sourceBean.getAge());
        assertEquals(50, sourceBean.getPrimitiveInt());
    }

    // ==================== toMap 测试 ====================

    @Test
    @DisplayName("测试toMap基本功能")
    void testToMapBasic() {
        Map<String, Object> map = Bean2MapUtils.toMap(sourceBean);

        assertNotNull(map);
        assertEquals("John Doe", map.get("name"));
        assertEquals(30, map.get("age"));
        assertEquals(100, map.get("primitiveInt"));
        assertEquals(200L, map.get("primitiveLong"));
        assertEquals(3.14, map.get("primitiveDouble"));
        assertTrue((Boolean) map.get("primitiveBoolean"));
        assertEquals(new BigDecimal("999.99"), map.get("amount"));
        assertEquals(testDate, map.get("createdDate"));
        assertEquals("test", map.get("nullableField"));
    }

    @Test
    @DisplayName("测试toMap与null对象")
    void testToMapWithNull() {
        Map<String, Object> map = Bean2MapUtils.toMap(null);

        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @Test
    @DisplayName("测试toMap不包含null值")
    void testToMapExcludesNullValues() {
        SourceBean bean = new SourceBean();
        bean.setName("Test");
        bean.setAge(null);
        bean.setNullableField(null);

        Map<String, Object> map = Bean2MapUtils.toMap(bean);

        assertTrue(map.containsKey("name"));
        assertFalse(map.containsKey("age")); // null值不应该在map中
        assertFalse(map.containsKey("nullableField"));
    }

    @Test
    @DisplayName("测试toMap使用提供的map")
    void testToMapWithProvidedMap() {
        Map<String, Object> providedMap = new HashMap<>();
        providedMap.put("extra", "value");

        Map<String, Object> result = Bean2MapUtils.toMap(sourceBean, providedMap);

        assertSame(providedMap, result);
        assertEquals("value", result.get("extra")); // 原有值应该保留
        assertEquals("John Doe", result.get("name")); // 新值应该添加
    }

    @Test
    @DisplayName("测试toMap处理基本类型默认值")
    void testToMapWithPrimitiveDefaults() {
        WrapperBean bean = new WrapperBean();
        bean.setIntValue(0);
        bean.setLongValue(0L);
        bean.setDoubleValue(0.0);
        bean.setBooleanValue(false);

        Map<String, Object> map = Bean2MapUtils.toMap(bean);

        // 基本类型的默认值（0, false等）也会被包含在map中
        assertNotNull(map);
        // 注意：0和false不是null，所以会被包含
        assertEquals(0, map.get("intValue"));
        assertEquals(0L, map.get("longValue"));
        assertEquals(0.0, map.get("doubleValue"));
        assertEquals(false, map.get("booleanValue"));
    }

    // ==================== 性能测试 ====================

    @Test
    @DisplayName("测试缓存机制 - 重复调用应该使用缓存")
    void testCachingMechanism() {
        TargetBean target1 = new TargetBean();
        TargetBean target2 = new TargetBean();
        TargetBean target3 = new TargetBean();

        long startTime = System.nanoTime();

        // 第一次调用 - 会初始化缓存
        Bean2MapUtils.copyPropertiesSafe(sourceBean, target1);
        long firstCallTime = System.nanoTime() - startTime;

        startTime = System.nanoTime();

        // 第二次调用 - 应该使用缓存，更快
        Bean2MapUtils.copyPropertiesSafe(sourceBean, target2);
        long secondCallTime = System.nanoTime() - startTime;

        startTime = System.nanoTime();

        // 第三次调用 - 也应该使用缓存
        Bean2MapUtils.copyPropertiesSafe(sourceBean, target3);
        long thirdCallTime = System.nanoTime() - startTime;

        // 验证结果正确
        assertEquals(sourceBean.getName(), target1.getName());
        assertEquals(sourceBean.getName(), target2.getName());
        assertEquals(sourceBean.getName(), target3.getName());

        // 打印性能信息（仅供参考）
        System.out.println("First call: " + firstCallTime + " ns");
        System.out.println("Second call: " + secondCallTime + " ns");
        System.out.println("Third call: " + thirdCallTime + " ns");

        // 后续调用应该不会慢于第一次调用太多
        // 注意：这是一个宽松的检查，因为性能测试在单元测试中不太可靠
        assertTrue(secondCallTime <= firstCallTime * 3,
            "后续调用性能应该受益于缓存");
    }

    @Test
    @DisplayName("测试大量对象复制的性能")
    void testPerformanceWithManyObjects() {
        int iterations = 1000;
        TargetBean[] targets = new TargetBean[iterations];

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            targets[i] = new TargetBean();
            Bean2MapUtils.copyPropertiesSafe(sourceBean, targets[i]);
        }

        long duration = System.currentTimeMillis() - startTime;

        // 验证第一个和最后一个对象
        assertEquals(sourceBean.getName(), targets[0].getName());
        assertEquals(sourceBean.getName(), targets[iterations - 1].getName());

        System.out.println(iterations + " 次复制耗时: " + duration + " ms");

        // 性能应该在合理范围内（1000次应该在1秒内完成）
        assertTrue(duration < 1000,
            "1000次对象复制应该在1秒内完成，实际耗时: " + duration + "ms");
    }

    @Test
    @DisplayName("测试并发安全性")
    void testThreadSafety() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        TargetBean[] results = new TargetBean[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = new TargetBean();
                Bean2MapUtils.copyPropertiesSafe(sourceBean, results[index]);
            });
            threads[i].start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 验证所有结果
        for (int i = 0; i < threadCount; i++) {
            assertEquals(sourceBean.getName(), results[i].getName());
            assertEquals(sourceBean.getAge(), results[i].getAge());
            assertEquals(sourceBean.getPrimitiveInt(), results[i].getPrimitiveInt());
        }
    }

    // ==================== 边界情况测试 ====================

    @Test
    @DisplayName("测试空字符串复制")
    void testEmptyStringCopy() {
        SourceBean source = new SourceBean();
        source.setName("");
        source.setNullableField("");

        TargetBean target = new TargetBean();

        Bean2MapUtils.copyPropertiesSafe(source, target);

        assertEquals("", target.getName());
        assertEquals("", target.getNullableField());
    }

    @Test
    @DisplayName("测试Date对象复制（引用复制）")
    void testDateCopy() {
        Date originalDate = new Date();
        SourceBean source = new SourceBean();
        source.setCreatedDate(originalDate);

        TargetBean target = new TargetBean();

        Bean2MapUtils.copyPropertiesSafe(source, target);

        // Date是通过引用复制的
        assertSame(originalDate, target.getCreatedDate());
    }

    @Test
    @DisplayName("测试BigDecimal复制")
    void testBigDecimalCopy() {
        BigDecimal amount = new BigDecimal("12345.6789");
        SourceBean source = new SourceBean();
        source.setAmount(amount);

        TargetBean target = new TargetBean();

        Bean2MapUtils.copyPropertiesSafe(source, target);

        assertEquals(amount, target.getAmount());
        assertSame(amount, target.getAmount()); // BigDecimal也是引用复制
    }

    @Test
    @DisplayName("测试所有基本类型的null转换为默认值")
    void testAllPrimitiveTypesDefaultValues() {
        AllWrappersBean source = new AllWrappersBean();
        // 所有包装类型都设置为null
        source.setByteValue(null);
        source.setShortValue(null);
        source.setIntValue(null);
        source.setLongValue(null);
        source.setFloatValue(null);
        source.setDoubleValue(null);
        source.setBooleanValue(null);
        source.setCharValue(null);

        AllPrimitivesBean target = new AllPrimitivesBean();
        // 设置一些非默认值
        target.setByteValue((byte) 100);
        target.setShortValue((short) 200);
        target.setIntValue(300);
        target.setLongValue(400L);
        target.setFloatValue(1.5f);
        target.setDoubleValue(2.5);
        target.setBooleanValue(true);
        target.setCharValue('A');

        Bean2MapUtils.copyPropertiesSafe(source, target);

        // 验证所有null值都被转换为对应的基本类型默认值
        assertEquals((byte) 0, target.getByteValue(), "Byte(null) -> byte(0)");
        assertEquals((short) 0, target.getShortValue(), "Short(null) -> short(0)");
        assertEquals(0, target.getIntValue(), "Integer(null) -> int(0)");
        assertEquals(0L, target.getLongValue(), "Long(null) -> long(0L)");
        assertEquals(0.0f, target.getFloatValue(), 0.001f, "Float(null) -> float(0.0f)");
        assertEquals(0.0, target.getDoubleValue(), 0.001, "Double(null) -> double(0.0)");
        assertFalse(target.isBooleanValue(), "Boolean(null) -> boolean(false)");
        assertEquals('\u0000', target.getCharValue(), "Character(null) -> char('\\u0000')");
    }

    @Test
    @DisplayName("验证Spring BeanUtils直接复制null到基本类型会抛异常，但copyPropertiesSafe不会")
    void testSpringBeanUtilsThrowsExceptionButSafeDoesNot() {
        WrapperBean source = new WrapperBean();
        source.setIntValue(null);
        source.setLongValue(null);
        source.setDoubleValue(null);
        source.setBooleanValue(null);

        PrimitiveBean target1 = new PrimitiveBean();

        // 验证：直接使用 Spring 的 BeanUtils.copyProperties 会抛异常
        assertThrows(Exception.class, () -> {
            org.springframework.beans.BeanUtils.copyProperties(source, target1);
        }, "Spring BeanUtils.copyProperties 应该抛出异常当复制 null 到基本类型时");

        // 验证：我们的 copyPropertiesSafe 不会抛异常
        PrimitiveBean target2 = new PrimitiveBean();
        target2.setIntValue(999);
        target2.setLongValue(888L);

        assertDoesNotThrow(() -> {
            Bean2MapUtils.copyPropertiesSafe(source, target2);
        }, "copyPropertiesSafe 不应该抛出异常");

        // 验证值被正确设置为默认值
        assertEquals(0, target2.getIntValue());
        assertEquals(0L, target2.getLongValue());
        assertEquals(0.0, target2.getDoubleValue(), 0.001);
        assertFalse(target2.isBooleanValue());
    }
}
