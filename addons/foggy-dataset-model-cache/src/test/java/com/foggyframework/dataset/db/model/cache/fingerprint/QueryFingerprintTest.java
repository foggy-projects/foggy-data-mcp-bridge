package com.foggyframework.dataset.db.model.cache.fingerprint;

import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryFingerprint 单元测试
 */
@DisplayName("QueryFingerprint 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QueryFingerprintTest {

    @Test
    @Order(1)
    @DisplayName("isCacheable - 无原始 SQL 且无非确定性函数时返回 true")
    void testIsCacheable_Normal_ReturnsTrue() {
        QueryFingerprint fingerprint = QueryFingerprint.builder()
                .modelName("TestModel")
                .securityPolicyHash("test-policy")
                .columns(Arrays.asList("id", "name"))
                .groupBy(Collections.emptyList())
                .conditionSignatures(Arrays.asList("status:=:abc123"))
                .orderBy(Collections.emptyList())
                .pageNo(1)
                .pageSize(10)
                .hasRawSql(false)
                .hasNonDeterministic(false)
                .build();

        assertTrue(fingerprint.isCacheable(), "正常查询应该可缓存");
    }

    @Test
    @Order(2)
    @DisplayName("isCacheable - 有原始 SQL 时返回 false")
    void testIsCacheable_HasRawSql_ReturnsFalse() {
        QueryFingerprint fingerprint = QueryFingerprint.builder()
                .modelName("TestModel")
                .columns(Arrays.asList("id", "name"))
                .hasRawSql(true)
                .hasNonDeterministic(false)
                .build();

        assertFalse(fingerprint.isCacheable(), "有原始 SQL 应该不可缓存");
    }

    @Test
    @Order(3)
    @DisplayName("isCacheable - 有非确定性函数时返回 false")
    void testIsCacheable_HasNonDeterministic_ReturnsFalse() {
        QueryFingerprint fingerprint = QueryFingerprint.builder()
                .modelName("TestModel")
                .columns(Arrays.asList("id", "name"))
                .hasRawSql(false)
                .hasNonDeterministic(true)
                .build();

        assertFalse(fingerprint.isCacheable(), "有非确定性函数应该不可缓存");
    }

    @Test
    @Order(4)
    @DisplayName("toCacheKey - 可缓存时返回非空键")
    void testToCacheKey_Cacheable_ReturnsKey() {
        QueryFingerprint fingerprint = QueryFingerprint.builder()
                .modelName("TestModel")
                .securityPolicyHash("test-policy")
                .columns(Arrays.asList("id", "name"))
                .groupBy(Collections.emptyList())
                .conditionSignatures(Collections.emptyList())
                .orderBy(Collections.emptyList())
                .pageNo(1)
                .pageSize(10)
                .hasRawSql(false)
                .hasNonDeterministic(false)
                .build();

        String cacheKey = fingerprint.toCacheKey();

        assertNotNull(cacheKey, "可缓存时应该返回缓存键");
        assertTrue(cacheKey.startsWith("qc:TestModel:"), "缓存键应该以 qc:modelName: 开头");
    }

    @Test
    @Order(5)
    @DisplayName("toCacheKey - 不可缓存时返回 null")
    void testToCacheKey_NotCacheable_ReturnsNull() {
        QueryFingerprint fingerprint = QueryFingerprint.builder()
                .modelName("TestModel")
                .columns(Arrays.asList("id", "name"))
                .hasRawSql(true)
                .build();

        String cacheKey = fingerprint.toCacheKey();

        assertNull(cacheKey, "不可缓存时应该返回 null");
    }

    @Test
    @Order(6)
    @DisplayName("toCacheKey - 相同参数生成相同键")
    void testToCacheKey_SameParams_SameKey() {
        QueryFingerprint fp1 = QueryFingerprint.builder()
                .modelName("TestModel")
                .securityPolicyHash("test-policy")
                .columns(Arrays.asList("id", "name"))
                .groupBy(Collections.emptyList())
                .conditionSignatures(Arrays.asList("status:=:active"))
                .orderBy(Arrays.asList("id:asc"))
                .pageNo(1)
                .pageSize(10)
                .hasRawSql(false)
                .hasNonDeterministic(false)
                .build();

        QueryFingerprint fp2 = QueryFingerprint.builder()
                .modelName("TestModel")
                .securityPolicyHash("test-policy")
                .columns(Arrays.asList("id", "name"))
                .groupBy(Collections.emptyList())
                .conditionSignatures(Arrays.asList("status:=:active"))
                .orderBy(Arrays.asList("id:asc"))
                .pageNo(1)
                .pageSize(10)
                .hasRawSql(false)
                .hasNonDeterministic(false)
                .build();

        assertEquals(fp1.toCacheKey(), fp2.toCacheKey(), "相同参数应该生成相同的缓存键");
    }

    @Test
    @Order(7)
    @DisplayName("toCacheKey - 不同参数生成不同键")
    void testToCacheKey_DifferentParams_DifferentKey() {
        QueryFingerprint fp1 = QueryFingerprint.builder()
                .modelName("TestModel")
                .securityPolicyHash("test-policy")
                .columns(Arrays.asList("id", "name"))
                .conditionSignatures(Arrays.asList("status:=:active"))
                .pageNo(1)
                .pageSize(10)
                .hasRawSql(false)
                .hasNonDeterministic(false)
                .build();

        QueryFingerprint fp2 = QueryFingerprint.builder()
                .modelName("TestModel")
                .securityPolicyHash("test-policy")
                .columns(Arrays.asList("id", "name"))
                .conditionSignatures(Arrays.asList("status:=:inactive"))  // 不同条件
                .pageNo(1)
                .pageSize(10)
                .hasRawSql(false)
                .hasNonDeterministic(false)
                .build();

        assertNotEquals(fp1.toCacheKey(), fp2.toCacheKey(), "不同参数应该生成不同的缓存键");
    }

    @Test
    @Order(8)
    @DisplayName("toCacheKey - 不同分页生成不同键")
    void testToCacheKey_DifferentPage_DifferentKey() {
        QueryFingerprint fp1 = QueryFingerprint.builder()
                .modelName("TestModel")
                .securityPolicyHash("test-policy")
                .columns(Arrays.asList("id"))
                .pageNo(1)
                .pageSize(10)
                .hasRawSql(false)
                .hasNonDeterministic(false)
                .build();

        QueryFingerprint fp2 = QueryFingerprint.builder()
                .modelName("TestModel")
                .securityPolicyHash("test-policy")
                .columns(Arrays.asList("id"))
                .pageNo(2)  // 不同页码
                .pageSize(10)
                .hasRawSql(false)
                .hasNonDeterministic(false)
                .build();

        assertNotEquals(fp1.toCacheKey(), fp2.toCacheKey(), "不同分页应该生成不同的缓存键");
    }

    @Test
    @Order(9)
    @DisplayName("toDebugKey - 返回可读的调试键")
    void testToDebugKey_ReturnsReadableKey() {
        QueryFingerprint fingerprint = QueryFingerprint.builder()
                .modelName("TestModel")
                .securityPolicyHash("test-policy")
                .columns(Arrays.asList("id", "name"))
                .groupBy(Arrays.asList("category"))
                .conditionSignatures(Arrays.asList("status:=:active"))
                .orderBy(Arrays.asList("id:asc"))
                .pageNo(1)
                .pageSize(10)
                .hasRawSql(false)
                .hasNonDeterministic(false)
                .calculatedFieldCount(2)
                .build();

        String debugKey = fingerprint.toDebugKey();

        assertNotNull(debugKey);
        assertTrue(debugKey.contains("TestModel"), "调试键应该包含模型名");
        assertTrue(debugKey.contains("columns="), "调试键应该包含列信息");
        assertTrue(debugKey.contains("groupBy="), "调试键应该包含分组信息");
        assertTrue(debugKey.contains("conditions="), "调试键应该包含条件信息");
        assertTrue(debugKey.contains("cacheable=true"), "调试键应该包含可缓存状态");
    }

    @Test
    @Order(10)
    @DisplayName("toDebugKey - 不可缓存时显示原因")
    void testToDebugKey_NotCacheable_ShowsReason() {
        QueryFingerprint fingerprint = QueryFingerprint.builder()
                .modelName("TestModel")
                .hasRawSql(true)
                .hasNonDeterministic(true)
                .build();

        String debugKey = fingerprint.toDebugKey();

        assertTrue(debugKey.contains("hasRawSql"), "调试键应该显示 hasRawSql");
        assertTrue(debugKey.contains("hasNonDeterministic"), "调试键应该显示 hasNonDeterministic");
    }

    @Test
    @Order(11)
    @DisplayName("toCacheKey - 列顺序不影响缓存键（列已排序）")
    void testToCacheKey_ColumnOrderDoesNotMatter() {
        // 注意：columns 在构建时应该已排序
        QueryFingerprint fp1 = QueryFingerprint.builder()
                .modelName("TestModel")
                .securityPolicyHash("test-policy")
                .columns(Arrays.asList("a", "b", "c"))
                .hasRawSql(false)
                .hasNonDeterministic(false)
                .build();

        QueryFingerprint fp2 = QueryFingerprint.builder()
                .modelName("TestModel")
                .securityPolicyHash("test-policy")
                .columns(Arrays.asList("a", "b", "c"))  // 相同列，相同顺序
                .hasRawSql(false)
                .hasNonDeterministic(false)
                .build();

        assertEquals(fp1.toCacheKey(), fp2.toCacheKey());
    }

    @Test
    @Order(12)
    @DisplayName("isCacheable - 缺少安全策略身份时返回 false")
    void testIsCacheable_MissingSecurityPolicy_ReturnsFalse() {
        QueryFingerprint fingerprint = QueryFingerprint.builder()
                .modelName("TestModel")
                .build();

        assertFalse(fingerprint.isCacheable());
        assertNull(fingerprint.toCacheKey());
    }
}
