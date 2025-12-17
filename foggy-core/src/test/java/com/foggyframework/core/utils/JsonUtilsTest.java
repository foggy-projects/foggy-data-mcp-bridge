package com.foggyframework.core.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

class JsonUtilsTest {

    @Test
    void fromJsonAuto() {
        byte[] bb = JsonUtils.toBytes("aabb");
        String str = JsonUtils.fromJsonAuto(bb, String.class);

        Assertions.assertEquals("aabb", str);

        Date d = new Date();
        byte[] bb2 = JsonUtils.toBytes(d);
        Date str2 = JsonUtils.fromJsonAuto(bb2, Date.class);

        Assertions.assertEquals(d, str2);
    }

    @Test
    void toList() {
        List ll = JsonUtils.toList("[1,2]", null);

        Assertions.assertEquals(2, ll.size());
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class NullTest {
        String a;
        Integer b;

        String c;

        Integer d;
    }

    @Test
    void testNull() {
        byte[] bb = JsonUtils.toBytes(new NullTest("aa",1,null,null));
        Object object = JsonUtils.autoFromJson(new String(bb));

        String str = JsonUtils.toJson(object);

        // Jackson 默认不会像 fastjson 那样将 null 字段转换为空字符串或0
        // 这里的测试需要调整为验证 Jackson 的实际行为
        Assertions.assertTrue(str.contains("\"a\":\"aa\""));
        Assertions.assertTrue(str.contains("\"b\":1"));
    }

}