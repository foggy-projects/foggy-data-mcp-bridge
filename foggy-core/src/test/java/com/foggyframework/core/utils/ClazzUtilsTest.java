package com.foggyframework.core.utils;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class ClazzUtilsTest {
    public static class MyTest{
        public static class MyTest2{

        }
    }
    /**
     * 测试匿名类
     */
    @Test
    public void getClazzName1() {
        String v = ClazzUtils.getClazzName(new ClazzUtilsTest() {
        }.getClass());
        Assertions.assertEquals(v, "ClazzUtilsTest$1");
    }

    /**
     * 测试正常类
     */
    @Test
    public void getClazzName2() {
        String v = ClazzUtils.getClazzName(ClazzUtilsTest.class);
        Assertions.assertEquals(v, "ClazzUtilsTest");
    }
    /**
     * 测试内部类
     */
    @Test
    public void getClazzName3() {
        String v = ClazzUtils.getClazzName(MyTest.class);
        Assertions.assertEquals(v, "ClazzUtilsTest$MyTest");
    }
    /**
     * 测试多级内部类
     */
    @Test
    public void getClazzName4() {
        String v = ClazzUtils.getClazzName(MyTest.MyTest2.class);
        Assertions.assertEquals(v, "ClazzUtilsTest$MyTest$MyTest2");
    }
}