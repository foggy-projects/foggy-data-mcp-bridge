package com.foggyframework.dataset.model.engine.compose.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ComposeSandboxErrorCodes} 单元测试（Compose Query 8.2.0.beta M3 · Java 侧）。
 *
 * <p>对等 Python {@code tests/compose/sandbox/test_sandbox_error_codes.py}。
 * 14 code 字符串 + 7 phase 字符串 + layer/kind 派生逻辑必须与 Python 逐字节一致。</p>
 *
 * @since 8.2.0.beta
 */
@DisplayName("ComposeSandboxErrorCodes · 错误码常量与 layer/kind 派生")
class ComposeSandboxErrorCodesTest {

    // ---- 常量字面量 parity（14 codes + namespace） ----

    @Test
    @DisplayName("NAMESPACE 常量 = compose-sandbox-violation")
    void namespaceIsStable() {
        assertEquals("compose-sandbox-violation",
                ComposeSandboxErrorCodes.NAMESPACE);
    }

    @Test
    @DisplayName("Layer A · 8 个 code 字符串字面量 parity")
    void layerACodeLiterals() {
        assertAll(
                () -> assertEquals(
                        "compose-sandbox-violation/A/eval-denied",
                        ComposeSandboxErrorCodes.LAYER_A_EVAL_DENIED),
                () -> assertEquals(
                        "compose-sandbox-violation/A/async-denied",
                        ComposeSandboxErrorCodes.LAYER_A_ASYNC_DENIED),
                () -> assertEquals(
                        "compose-sandbox-violation/A/network-denied",
                        ComposeSandboxErrorCodes.LAYER_A_NETWORK_DENIED),
                () -> assertEquals(
                        "compose-sandbox-violation/A/io-denied",
                        ComposeSandboxErrorCodes.LAYER_A_IO_DENIED),
                () -> assertEquals(
                        "compose-sandbox-violation/A/global-denied",
                        ComposeSandboxErrorCodes.LAYER_A_GLOBAL_DENIED),
                () -> assertEquals(
                        "compose-sandbox-violation/A/time-denied",
                        ComposeSandboxErrorCodes.LAYER_A_TIME_DENIED),
                () -> assertEquals(
                        "compose-sandbox-violation/A/security-param-denied",
                        ComposeSandboxErrorCodes.LAYER_A_SECURITY_PARAM),
                () -> assertEquals(
                        "compose-sandbox-violation/A/context-access-denied",
                        ComposeSandboxErrorCodes.LAYER_A_CONTEXT_ACCESS)
        );
    }

    @Test
    @DisplayName("Layer B · 3 个 code 字符串字面量 parity")
    void layerBCodeLiterals() {
        assertAll(
                () -> assertEquals(
                        "compose-sandbox-violation/B/function-denied",
                        ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED),
                () -> assertEquals(
                        "compose-sandbox-violation/B/derived-plan-function-denied",
                        ComposeSandboxErrorCodes.LAYER_B_DERIVED_FN_DENIED),
                () -> assertEquals(
                        "compose-sandbox-violation/B/injection-suspected",
                        ComposeSandboxErrorCodes.LAYER_B_INJECTION_SUSPECTED)
        );
    }

    @Test
    @DisplayName("Layer C · 3 个 code 字符串字面量 parity")
    void layerCCodeLiterals() {
        assertAll(
                () -> assertEquals(
                        "compose-sandbox-violation/C/method-denied",
                        ComposeSandboxErrorCodes.LAYER_C_METHOD_DENIED),
                () -> assertEquals(
                        "compose-sandbox-violation/C/result-iteration-denied",
                        ComposeSandboxErrorCodes.LAYER_C_RESULT_ITERATION),
                () -> assertEquals(
                        "compose-sandbox-violation/C/cross-datasource-denied",
                        ComposeSandboxErrorCodes.LAYER_C_CROSS_DS)
        );
    }

    @Test
    @DisplayName("ALL_CODES · 正好 14 条 · 无重复 · 全部属于已知 layer")
    void allCodesMatrix() {
        Set<String> all = ComposeSandboxErrorCodes.ALL_CODES;
        assertEquals(14, all.size(), "Layer A 8 + B 3 + C 3 = 14");
        // 去重自检（Set 自带，但显式再跑一次保障 parity 报告）
        assertEquals(all.size(), new HashSet<>(all).size());
        // 全部以 compose-sandbox-violation/ 开头
        for (String code : all) {
            assertTrue(code.startsWith(ComposeSandboxErrorCodes.NAMESPACE + "/"),
                    "code '" + code + "' 应以 NAMESPACE 前缀开头");
        }
    }

    @Test
    @DisplayName("ALL_CODES · 按 layer 分布：A=8 / B=3 / C=3")
    void allCodesLayerDistribution() {
        long aCount = ComposeSandboxErrorCodes.ALL_CODES.stream()
                .filter(c -> c.startsWith(ComposeSandboxErrorCodes.LAYER_PREFIX_A))
                .count();
        long bCount = ComposeSandboxErrorCodes.ALL_CODES.stream()
                .filter(c -> c.startsWith(ComposeSandboxErrorCodes.LAYER_PREFIX_B))
                .count();
        long cCount = ComposeSandboxErrorCodes.ALL_CODES.stream()
                .filter(c -> c.startsWith(ComposeSandboxErrorCodes.LAYER_PREFIX_C))
                .count();
        assertEquals(8L, aCount, "Layer A 应有 8 条");
        assertEquals(3L, bCount, "Layer B 应有 3 条");
        assertEquals(3L, cCount, "Layer C 应有 3 条");
    }

    // ---- Phase 常量 + VALID_PHASES parity ----

    @Test
    @DisplayName("Phase 常量字符串字面量 parity（7 个）")
    void phaseLiterals() {
        assertAll(
                () -> assertEquals("script-parse",
                        ComposeSandboxErrorCodes.PHASE_SCRIPT_PARSE),
                () -> assertEquals("script-eval",
                        ComposeSandboxErrorCodes.PHASE_SCRIPT_EVAL),
                () -> assertEquals("plan-build",
                        ComposeSandboxErrorCodes.PHASE_PLAN_BUILD),
                () -> assertEquals("schema-derive",
                        ComposeSandboxErrorCodes.PHASE_SCHEMA_DERIVE),
                () -> assertEquals("authority-resolve",
                        ComposeSandboxErrorCodes.PHASE_AUTHORITY_RESOLVE),
                () -> assertEquals("compile",
                        ComposeSandboxErrorCodes.PHASE_COMPILE),
                () -> assertEquals("execute",
                        ComposeSandboxErrorCodes.PHASE_EXECUTE)
        );
    }

    @Test
    @DisplayName("VALID_PHASES · 正好 7 条 · 全部可反查")
    void validPhases() {
        Set<String> phases = ComposeSandboxErrorCodes.VALID_PHASES;
        assertEquals(7, phases.size());
        assertTrue(phases.contains("script-parse"));
        assertTrue(phases.contains("script-eval"));
        assertTrue(phases.contains("plan-build"));
        assertTrue(phases.contains("schema-derive"));
        assertTrue(phases.contains("authority-resolve"));
        assertTrue(phases.contains("compile"));
        assertTrue(phases.contains("execute"));
        assertFalse(phases.contains("no-such-phase"));
    }

    // ---- layerOf / kindOf 派生 ----

    @Test
    @DisplayName("layerOf — 14 个 code 都能正确归类为 A/B/C")
    void layerOfReturnsAbcForAllCodes() {
        assertAll(
                () -> assertEquals("A", ComposeSandboxErrorCodes.layerOf(
                        ComposeSandboxErrorCodes.LAYER_A_EVAL_DENIED)),
                () -> assertEquals("A", ComposeSandboxErrorCodes.layerOf(
                        ComposeSandboxErrorCodes.LAYER_A_CONTEXT_ACCESS)),
                () -> assertEquals("B", ComposeSandboxErrorCodes.layerOf(
                        ComposeSandboxErrorCodes.LAYER_B_FUNCTION_DENIED)),
                () -> assertEquals("B", ComposeSandboxErrorCodes.layerOf(
                        ComposeSandboxErrorCodes.LAYER_B_INJECTION_SUSPECTED)),
                () -> assertEquals("C", ComposeSandboxErrorCodes.layerOf(
                        ComposeSandboxErrorCodes.LAYER_C_METHOD_DENIED)),
                () -> assertEquals("C", ComposeSandboxErrorCodes.layerOf(
                        ComposeSandboxErrorCodes.LAYER_C_CROSS_DS))
        );
    }

    @Test
    @DisplayName("kindOf — 派生 trailing segment（如 eval-denied / injection-suspected）")
    void kindOfReturnsTrailingSegment() {
        assertAll(
                () -> assertEquals("eval-denied", ComposeSandboxErrorCodes.kindOf(
                        ComposeSandboxErrorCodes.LAYER_A_EVAL_DENIED)),
                () -> assertEquals("context-access-denied",
                        ComposeSandboxErrorCodes.kindOf(
                                ComposeSandboxErrorCodes.LAYER_A_CONTEXT_ACCESS)),
                () -> assertEquals("injection-suspected",
                        ComposeSandboxErrorCodes.kindOf(
                                ComposeSandboxErrorCodes.LAYER_B_INJECTION_SUSPECTED)),
                () -> assertEquals("derived-plan-function-denied",
                        ComposeSandboxErrorCodes.kindOf(
                                ComposeSandboxErrorCodes.LAYER_B_DERIVED_FN_DENIED)),
                () -> assertEquals("cross-datasource-denied",
                        ComposeSandboxErrorCodes.kindOf(
                                ComposeSandboxErrorCodes.LAYER_C_CROSS_DS))
        );
    }

    @Test
    @DisplayName("layerOf — 未知 code 抛 IllegalArgumentException（fail-closed · Python ValueError 对等）")
    void layerOfRejectsUnknownCode() {
        assertThrows(IllegalArgumentException.class,
                () -> ComposeSandboxErrorCodes.layerOf(
                        "compose-sandbox-violation/D/unknown"));
        assertThrows(IllegalArgumentException.class,
                () -> ComposeSandboxErrorCodes.layerOf("some-other-namespace/A/x"));
        assertThrows(IllegalArgumentException.class,
                () -> ComposeSandboxErrorCodes.layerOf(""));
        assertThrows(IllegalArgumentException.class,
                () -> ComposeSandboxErrorCodes.layerOf(null));
    }

    @Test
    @DisplayName("kindOf — 未知 code 抛异常（因 layerOf 先抛）")
    void kindOfRejectsUnknownCode() {
        assertThrows(IllegalArgumentException.class,
                () -> ComposeSandboxErrorCodes.kindOf("compose-sandbox-violation/Z/nope"));
    }

    // ---- Layer prefix 常量 ----

    @Test
    @DisplayName("LAYER_PREFIX_A/B/C 正确拼接 NAMESPACE")
    void layerPrefixesCompose() {
        assertEquals("compose-sandbox-violation/A/",
                ComposeSandboxErrorCodes.LAYER_PREFIX_A);
        assertEquals("compose-sandbox-violation/B/",
                ComposeSandboxErrorCodes.LAYER_PREFIX_B);
        assertEquals("compose-sandbox-violation/C/",
                ComposeSandboxErrorCodes.LAYER_PREFIX_C);
    }
}
