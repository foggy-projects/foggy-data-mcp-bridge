package com.foggyframework.core.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NumberUtilsTest {

    @Test
    void cent2yuan() {
        Assertions.assertEquals(NumberUtils.cent2yuan(1), "0.01");

        Assertions.assertEquals(NumberUtils.cent2yuan(10), "0.10");
        Assertions.assertEquals(NumberUtils.cent2yuan(61), "0.61");
        Assertions.assertEquals(NumberUtils.cent2yuan(111), "1.11");

    }

    @Test
    void split() {
        Assertions.assertArrayEquals(NumberUtils.split(1600, 500), new Integer[]{500, 500, 500, 100});
        Assertions.assertArrayEquals(NumberUtils.split(400, 500), new Integer[]{400});
        Assertions.assertArrayEquals(NumberUtils.split(600, 500), new Integer[]{500, 100});
        Assertions.assertArrayEquals(NumberUtils.split(1400, 500), new Integer[]{500, 500, 400});
    }

    @Test
    void sumInteger() {
        Assertions.assertEquals(2100, NumberUtils.sumInteger(1600, 500));
        Assertions.assertEquals(0, NumberUtils.sumInteger(null, null));
        Assertions.assertEquals(4, NumberUtils.sumInteger(null, 4, null));
    }

    @Test
    void cent2yuanFix() {
        Assertions.assertEquals(NumberUtils.cent2yuanFix(100), "1");

        Assertions.assertEquals(NumberUtils.cent2yuanFix(120), "1.20");
        Assertions.assertEquals(NumberUtils.cent2yuanFix(60000), "600");
        Assertions.assertEquals(NumberUtils.cent2yuanFix(111), "1.11");

    }

    @Test
    void cent2yuanFix2() {
        System.out.println(Double.parseDouble("1.11"));
        System.out.println(Double.parseDouble("1.00"));
        System.out.println(Double.parseDouble("1"));
    }

    @Test
    void hasFlag() {
        Assertions.assertEquals(true, NumberUtils.hasFlag((long) 0b110, 0x2));
        Assertions.assertEquals(true, NumberUtils.hasFlag((long) 0b010, 0x2));
        Assertions.assertEquals(true, NumberUtils.hasFlag((long) 0b110, 0x4));
        Assertions.assertEquals(false, NumberUtils.hasFlag((long) 0b110, 0x8));
    }

    @Test
    void addFlag() {
        Assertions.assertEquals(2, NumberUtils.addFlag(0l, 0x2));
        Assertions.assertEquals(3, NumberUtils.addFlag((long) 0b001, 0x2));
        Assertions.assertEquals(6, NumberUtils.addFlag((long) 0b010, 0x4));
        Assertions.assertEquals(14, NumberUtils.addFlag((long) 0b110, 0x8));
    }

    @Test
    void addFlags() {
//        Assertions.assertEquals((long)0b010, NumberUtils.addFlags(null, 0x2,0x4,0x8));
        // 测试用例1：添加一个标志位
        Long result1 = NumberUtils.addFlags(null, 0b0001); // 假设0b0001是一个标志位
        Assertions.assertEquals(1L, result1.longValue());

        // 测试用例2：添加多个标志位
        Long result2 = NumberUtils.addFlags(0b0010l, 0b0001, 0b1000); // 假设0b0010, 0b0001, 0b1000是三个不同的标志位
        Assertions.assertEquals(0b1011, result2.longValue()); // 预期结果是三个标志位按位或的结果

        // 测试用例3：空标志数组
        Long result3 = NumberUtils.addFlags(0b0100l, new long[]{});
        Assertions.assertEquals(0b0100, result3.longValue()); // 当不添加新的标志时，原值不变

        // 测试用例4：初始source为0
        Long result4 = NumberUtils.addFlags(0L, 0b0011, 0b1100);
        Assertions.assertEquals(0b1111, result4.longValue());
    }

    @Test
    void toMoneyInt() {
        Assertions.assertEquals(2029, NumberUtils.toInteger(2900 * 0.7));
        Assertions.assertEquals(2030, NumberUtils.toMoneyInt(2900 * 0.7));
        Assertions.assertEquals(2100, NumberUtils.toMoneyInt(3000 * 0.7));
        Assertions.assertEquals(1, NumberUtils.toMoneyInt(1.7));
        Assertions.assertEquals(1, NumberUtils.toMoneyInt(1.2));
        Assertions.assertEquals(1, NumberUtils.toMoneyInt(1.999));
    }


    @Test
    void delFlag() {
        Assertions.assertEquals(0, NumberUtils.delFlag(0l, 0x2));
        Assertions.assertEquals(0, NumberUtils.delFlag(null, 0x2));
        Assertions.assertEquals(1, NumberUtils.delFlag((long) 0b001, 0x2));
        Assertions.assertEquals(2, NumberUtils.delFlag((long) 0b010, 0x4));
        Assertions.assertEquals(6, NumberUtils.delFlag((long) 0b1110, 0x8));
    }

    @Test
    void delFlags() {
//        Assertions.assertEquals((long)0b010, NumberUtils.addFlags(null, 0x2,0x4,0x8));
        // 测试用例1：添加一个标志位
        Long result1 = NumberUtils.delFlags(null, 0b0001); // 假设0b0001是一个标志位
        Assertions.assertEquals(0L, result1.longValue());

        // 测试用例2：添加多个标志位
        Long result2 = NumberUtils.delFlags(0b0010l, 0b0001, 0b1000); // 假设0b0010, 0b0001, 0b1000是三个不同的标志位
        Assertions.assertEquals(0b0010, result2.longValue()); // 预期结果是三个标志位按位或的结果

        // 测试用例3：空标志数组
        Long result3 = NumberUtils.delFlags(0b0100l, new long[]{});
        Assertions.assertEquals(0b0100, result3.longValue()); // 当不添加新的标志时，原值不变

        // 测试用例4：初始source为0
        Long result4 = NumberUtils.delFlags(0L, 0b0011, 0b1100);
        Assertions.assertEquals(0b0000, result4.longValue());
    }

}