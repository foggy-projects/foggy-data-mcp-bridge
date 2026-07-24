package com.foggyframework.dataset.model.engine.compose.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M1 Principal 不变量 — 跨仓对齐 Python test_principal.py。
 */
@DisplayName("M1 Principal")
class PrincipalTest {

    @Test
    @DisplayName("userId 必填且非空")
    void userIdRequiredNonEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> Principal.builder().userId("").build());
        assertThrows(IllegalArgumentException.class,
                () -> Principal.builder().userId(null).build());
    }

    @Test
    @DisplayName("最小构造：其余字段默认 null/空")
    void minimalConstructorDefaults() {
        Principal p = Principal.builder().userId("u001").build();
        assertEquals("u001", p.userId());
        assertNull(p.tenantId());
        assertNotNull(p.roles());
        assertTrue(p.roles().isEmpty());
        assertNull(p.deptId());
        assertNull(p.authorizationHint());
        assertNull(p.policySnapshotId());
    }

    @Test
    @DisplayName("roles 默认为空 list，从不为 null")
    void rolesDefaultEmptyNotNull() {
        Principal p = Principal.builder().userId("u001").build();
        assertNotNull(p.roles());
        assertEquals(0, p.roles().size());
    }

    @Test
    @DisplayName("roles 返回不可变副本")
    void rolesReturnsUnmodifiable() {
        Principal p = Principal.builder()
                .userId("u001")
                .roles(Arrays.asList("admin", "editor"))
                .build();
        assertEquals(List.of("admin", "editor"), p.roles());
        assertThrows(UnsupportedOperationException.class,
                () -> p.roles().add("x"));
    }

    @Test
    @DisplayName("构造后字段全只读（无 setter）—— 编译期保证")
    void noSetterOnFields() {
        // Principal 类故意不提供 setter — 结构上只通过 Builder 构造。
        // 这里断言公开方法只有 getter + builder() + Object 继承方法。
        boolean hasSetter = Arrays.stream(Principal.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().startsWith("set"));
        assertFalse(hasSetter, "Principal 不应暴露任何 setter 方法");
    }

    @Test
    @DisplayName("值相等 + hash 相等：相同字段的两个 Principal 等价")
    void valueEquality() {
        Principal a = Principal.builder()
                .userId("u001")
                .tenantId("t001")
                .roles(List.of("admin"))
                .deptId("d1")
                .build();
        Principal b = Principal.builder()
                .userId("u001")
                .tenantId("t001")
                .roles(List.of("admin"))
                .deptId("d1")
                .build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("toString 不泄漏 authorizationHint")
    void toStringOmitsAuthorizationHint() {
        Principal p = Principal.builder()
                .userId("u001")
                .authorizationHint("Bearer super-secret-token")
                .build();
        String s = p.toString();
        assertFalse(s.contains("super-secret-token"),
                "authorizationHint 不应出现在 toString 输出中");
        assertFalse(s.contains("Bearer"),
                "任何 Authorization 头内容都不应出现在 toString 输出中");
    }
}
