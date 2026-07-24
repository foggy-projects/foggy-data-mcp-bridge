package com.foggyframework.dataset.model.cache.fingerprint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("StableCanonicalEncoder 测试")
class StableCanonicalEncoderTest {

    @Test
    @DisplayName("segment - Unicode 长度使用 UTF-8 字节数")
    void segmentUsesUtf8ByteLength() {
        assertEquals("6:string4:汉A", StableCanonicalEncoder.segment("string", "汉A"));
    }

    @Test
    @DisplayName("encode - 类型和元素边界不会碰撞")
    void typeAndElementBoundariesDoNotCollide() {
        assertNotEquals(
                StableCanonicalEncoder.encode(Arrays.asList("a,b", "c")),
                StableCanonicalEncoder.encode(Arrays.asList("a", "b,c")));
        assertNotEquals(StableCanonicalEncoder.encode(1), StableCanonicalEncoder.encode(1L));
        assertNotEquals(StableCanonicalEncoder.encode(1), StableCanonicalEncoder.encode("1"));
    }

    @Test
    @DisplayName("encode - Map 插入顺序不影响编码")
    void mapOrderIsCanonical() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("tenant", "a");
        first.put("limit", 10);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("limit", 10);
        second.put("tenant", "a");

        assertEquals(StableCanonicalEncoder.encode(first), StableCanonicalEncoder.encode(second));
    }

    @Test
    @DisplayName("encode - 未支持对象 fail closed")
    void unsupportedObjectFailsClosed() {
        assertTrue(StableCanonicalEncoder.encode(new Object()).isEmpty());
    }
}
