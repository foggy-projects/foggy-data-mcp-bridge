package com.foggyframework.core.utils;

import com.foggyframework.core.common.MapBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

class StringUtilsTest {
    @Test
    void countOfChar() {
        Assertions.assertEquals(0, StringUtils.countOfChar("xxxx", '?'));
        Assertions.assertEquals(3, StringUtils.countOfChar("and (t.goods_code = ? or t.paper_express_id = ? or t.bill_id = ?)", '?'));
        Assertions.assertEquals(2, StringUtils.countOfChar("xxx?x?", '?'));
        Assertions.assertEquals(1, StringUtils.countOfChar("?xxxx", '?'));
    }

    @Test
    void to() {
        Assertions.assertEquals("xxxx", StringUtils.to("xxxx"));
        Assertions.assertEquals("Xxxx", StringUtils.to("Xxxx"));
        Assertions.assertEquals("XXxx", StringUtils.to("X_xxx"));
        Assertions.assertEquals("Xxx", StringUtils.to("_xxx"));
        Assertions.assertEquals("getTime", StringUtils.to("get_time"));
        Assertions.assertEquals("getTimeHh", StringUtils.to("get_time_hh"));
    }

    @Test
    void toLink() {
        Assertions.assertEquals(StringUtils.toLink("xxXx"), "xx-xx");
        Assertions.assertEquals(StringUtils.toLink("我Xxxx"), "我-xxxx");
        Assertions.assertEquals(StringUtils.toLink("XXxx"), "x-xxx");
        Assertions.assertEquals(StringUtils.toLink("Xxx"), "xxx");
        Assertions.assertEquals(StringUtils.toLink("getTime"), "get-time");
        Assertions.assertEquals(StringUtils.toLink("getTimeHh"), "get-time-hh");
    }

    @Test
    void checkEq() {
        boolean v1 = StringUtils.checkEq(new CheckEqTest("a", "b", 1),
                new CheckEqTest("a", "b", 1), null, "a", "b", "c");
        Assertions.assertTrue(v1);

        boolean v2 = StringUtils.checkEq(new CheckEqTest("a", "b", 1),
                new CheckEqTest("a", "b", 2), null, "a", "b", "c");
        Assertions.assertFalse(v2);
        try {
            StringUtils.checkEq(new CheckEqTest("a", "b", 1),
                    new CheckEqTest("a", "b", 2), "", "a", "b", "c");
            Assertions.fail();
        } catch (Throwable t) {

        }
    }

    @Test
    void checkMask() {
        Assertions.assertEquals(StringUtils.txtMSK("💒?🌷幸福🌷"), "\uD83D\uDC92***\uD83C\uDF37");
        Assertions.assertEquals(StringUtils.txtMSK("幸福🌷"), "幸***\uD83C\uDF37");
        Assertions.assertEquals(StringUtils.txtMSK("幸福1🌷"), "幸***\uD83C\uDF37");
        Assertions.assertEquals(StringUtils.txtMSK("💒幸福1🌷"), "\uD83D\uDC92***\uD83C\uDF37");
        Assertions.assertEquals(StringUtils.txtMSK("💒幸福1🌷12"), "\uD83D\uDC92***2");
        Assertions.assertEquals(StringUtils.txtMSK("💒幸福1🌷1"), "\uD83D\uDC92***1");
        Assertions.assertEquals(StringUtils.txtMSK("💒幸福11"), "\uD83D\uDC92***1");

        Assertions.assertEquals(StringUtils.replaceUtf16ToEmpty("💒?🌷幸福🌷"), "?幸福");
        Assertions.assertEquals(StringUtils.replaceUtf16ToEmpty("幸福🌷"), "幸福");
        Assertions.assertEquals(StringUtils.replaceUtf16ToEmpty("幸福1🌷"), "幸福1");
        Assertions.assertEquals(StringUtils.replaceUtf16ToEmpty("💒幸福1🌷"), "幸福1");
        Assertions.assertEquals(StringUtils.replaceUtf16ToEmpty("💒幸福1🌷12"), "幸福112");
        Assertions.assertEquals(StringUtils.replaceUtf16ToEmpty("💒幸福1🌷1"), "幸福11");
        Assertions.assertEquals(StringUtils.replaceUtf16ToEmpty("💒幸福11"), "幸福11");
        Assertions.assertEquals(StringUtils.replaceUtf16ToEmpty("幸福11435y"), "幸福11435y");
    }

    @Test
    void txtSplitByLength() {
        Assertions.assertArrayEquals(new String[]{"", "", ""}, StringUtils.txtSplitByLength(null, 1, 3));
        Assertions.assertArrayEquals(new String[]{"测", "试", ""}, StringUtils.txtSplitByLength("测试", 1, 3));
        Assertions.assertArrayEquals(new String[]{"测试", "", ""}, StringUtils.txtSplitByLength("测试", 2, 3));
        Assertions.assertArrayEquals(new String[]{"测试", "", ""}, StringUtils.txtSplitByLength("测试", 3, 3));
        Assertions.assertArrayEquals(new String[]{"测试1", "234", "56"}, StringUtils.txtSplitByLength("测试123456", 3, 3));
    }

    @AllArgsConstructor
    @Data
    public static class CheckEqTest {
        String a;
        String b;
        Integer c;

    }

    @Test
    void testSplitAndDistinct() {
        Assertions.assertEquals(StringUtils.splitAndDistinct("1;2  ;3;3;", ";"), Arrays.stream(new String[]{"1", "2", "3"}).collect(Collectors.toSet()));
    }

    @Test
    void trimObject() {
        Date d = new Date();
        Map m1 = MapBuilder.builder().put("M1", "\tmm\r").put("M2", "\t mm　").put("M3", 1).put("M4", d).build();
        StringUtils.trimObject(m1);
        Assertions.assertEquals("mm", m1.get("M1"));
        Assertions.assertEquals("mm", m1.get("M2"));
        Assertions.assertEquals(1, m1.get("M3"));
        Assertions.assertEquals(d, m1.get("M4"));
    }

    @Test
    void trim() {
        String r = StringUtils.trimSafe("\t mm　");
        Assertions.assertEquals("mm", r);
    }

    @Test
    void trimObject2() {
        Date d = new Date();
        TOTEST m1 = new TOTEST("\tmm\r","\t mm　",1,d,1);
        StringUtils.trimObject(m1);
        Assertions.assertEquals("mm", m1.getM1());
        Assertions.assertEquals("mm", m1.getM2());
        Assertions.assertEquals(1, m1.getM3());
        Assertions.assertEquals(d, m1.getM4());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TOTEST {
        String m1;
        String m2;
        int m3;
        Date m4;

        Integer m5;

    }

    @Test
    void testJoinWithEmptyList() {
        List<String> emptyList = Arrays.asList();
        String result = StringUtils.join(emptyList, ",");
        Assertions.assertEquals("", result, "The result should be an empty string for an empty list.");
    }

    @Test
    void testJoinWithSingleElement() {
        List<String> singleElementList = Arrays.asList("Hello");
        String result = StringUtils.join(singleElementList, ",");
        Assertions.assertEquals("Hello", result, "The result should be a single element without any separator.");
    }

    @Test
    void testJoinWithMultipleElements() {
        List<String> stringList = Arrays.asList("Hello", "World", "FoggyFramework");
        String result = StringUtils.join(stringList, ",");
        Assertions.assertEquals("Hello,World,FoggyFramework", result, "The result should be a string with elements joined by the specified separator.");
    }

    @Test
    void testJoinWithNullElement() {
        List<String> stringList = Arrays.asList("Hello", null, "World");
        String result = StringUtils.join(stringList, ",");
        Assertions.assertEquals("Hello,World", result, "The result should contain empty string elements for null values.");
    }

    @Test
    void testJoinWithEmptyStringElement() {
        List<String> stringList = Arrays.asList("Hello", "", "World");
        String result = StringUtils.join(stringList, ",");
        Assertions.assertEquals("Hello,,World", result, "The result should contain empty string elements.");
    }

    @Test
    void testJoinWithDifferentSeparator() {
        List<String> stringList = Arrays.asList("Java", "C++", "Python");
        String result = StringUtils.join(stringList, " -> ");
        Assertions.assertEquals("Java -> C++ -> Python", result, "The result should be a string with elements joined by the specified different separator.");
    }

}
